package com.example.Product_Selection_260813.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.service.resolver.ScoreBandResolver;
import com.example.Product_Selection_260813.service.scoring.HistoricalScoreCalculator;
import com.example.Product_Selection_260813.service.resolver.AlgorithmSettings;
import com.example.Product_Selection_260813.algorithm.ScoringAlgorithms;
import com.example.Product_Selection_260813.constants.BusinessTimeZone;
import com.example.Product_Selection_260813.service.campaign.ActiveCampaignWindow;
import com.example.Product_Selection_260813.service.campaign.CampaignUrgencyCalculator;
import com.example.Product_Selection_260813.service.campaign.FestiveCampaignRuleService;
import com.example.Product_Selection_260813.constants.FactorCode;
import com.example.Product_Selection_260813.repository.ProductTypeRepository;
import com.example.Product_Selection_260813.dto.response.EvaluationResponse;
import com.example.Product_Selection_260813.dto.response.FestivalBoostResponse;
import com.example.Product_Selection_260813.entity.AudienceProfile;
import com.example.Product_Selection_260813.entity.EvaluationFactor;
import com.example.Product_Selection_260813.entity.EvaluationMode;
import com.example.Product_Selection_260813.entity.CustomFieldDefinition;
import com.example.Product_Selection_260813.entity.FactorDefinition;
import com.example.Product_Selection_260813.entity.FestiveCampaign;
import com.example.Product_Selection_260813.entity.FestiveCampaignTag;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductEvaluation;
import com.example.Product_Selection_260813.entity.ReviewRecord;
import com.example.Product_Selection_260813.entity.ProductType;
import com.example.Product_Selection_260813.entity.SystemSetting;
import com.example.Product_Selection_260813.entity.TrendSignal;
import com.example.Product_Selection_260813.enums.ProductPricingType;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;
import com.example.Product_Selection_260813.json.MatchedCampaignSnapshot;
import com.example.Product_Selection_260813.json.TrendSnapshot;
import com.example.Product_Selection_260813.json.WeightFactorSnapshot;
import com.example.Product_Selection_260813.json.WeightSnapshot;
import com.example.Product_Selection_260813.repository.AudienceProfileRepository;
import com.example.Product_Selection_260813.repository.EvaluationFactorRepository;
import com.example.Product_Selection_260813.repository.EvaluationModeRepository;
import com.example.Product_Selection_260813.repository.CustomFieldDefinitionRepository;
import com.example.Product_Selection_260813.repository.FactorDefinitionRepository;
import com.example.Product_Selection_260813.repository.FestiveCampaignTagRepository;
import com.example.Product_Selection_260813.repository.ProductEvaluationRepository;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.ReviewRecordRepository;
import com.example.Product_Selection_260813.service.scoring.ProductFactorScorer;
import com.example.Product_Selection_260813.service.resolver.ProductTypeAttributeResolver;
import com.example.Product_Selection_260813.repository.SystemSettingRepository;
import com.example.Product_Selection_260813.repository.TrendSignalRepository;

/**
 * 對應企劃書十二-13分層決議：本類別實作「快照組裝所需的讀取」「Festival Boost
 * 可解釋性明細計算」，以及2026-08-31補上的<b>完整六大分項評分重算引擎</b>
 * （見{@link #calculateEvaluation}）——取代原本「假設由其他流程寫入」的TODO。
 *
 * 評分公式依團隊MVP版本定案（demo週前緊急定案，非規格書逐字規定，規格書
 * 僅定義FORECAST=、(purchase+trend)/2與節慶加成公式，其餘分項映射規則
 * 由團隊在demo時程壓力下拍板，之後可依真實資料校準）：
 * <ul>
 *   <li>資料完整度<60%：僅寫入data_completeness，不計算六大分項，不進Top10</li>
 *   <li>BUSINESS：毛利率(40%)+供應穩定性(30%)+價格競爭力(30%)</li>
 *   <li>AUDIENCE：目標客群描述與audience_profiles.keywords關鍵字命中率</li>
 *   <li>HISTORY：固定60分（企劃書承認尚無真實歷史資料，demo前不強做假資料）</li>
 *   <li>PURCHASE：estimated_purchase_rate×100，無資料給中性值50</li>
 *   <li>TREND：沿用最新trend_signals計算值，無資料給中性值50</li>
 *   <li>FORECAST：(PURCHASE+TREND)/2（規格書明訂）</li>
 *   <li>權重：從DB目前生效evaluation_mode的evaluation_factors讀取，不寫死在程式碼</li>
 * </ul>
 */
@Service
public class ScoringService {

	private static final Logger log = LoggerFactory.getLogger(ScoringService.class);

	// Boost Cap：企劃書「節慶加成計分規則」明訂為「暫訂+5，絕對分數」，
	// 之後的歷史資料回測校準屬於Phase 2待辦（見十三），此處先照文件明訂值寫死。
	private static final BigDecimal BOOST_CAP = new BigDecimal("5");

	// 季節型檔期PREPARING期間固定係數 0.20（企劃書明訂）：2026-09-24 隨急迫係數公式移到
	// CampaignUrgencyCalculator.SEASON_PREPARING_TIME_FACTOR，數值不變。

	// Match Weight三層數值已改由FestiveCampaignTagMatchTier enum攜帶（見該類別），
	// 這裡不再需要暫定的DEFAULT_MATCH_WEIGHT——festive_campaign_tags表補上分級資料後，
	// 缺口已解決，見buildMatchedCampaignSnapshot()。

	@Autowired
	private ProductEvaluationRepository productEvaluationRepository;

	@Autowired
	private EvaluationModeRepository evaluationModeRepository;

	@Autowired
	private EvaluationFactorRepository evaluationFactorRepository;

	// 七因子計分。獨立成元件而非留在本類別，是因為那一層有最多商業規則，
	// 拆出來才能單獨測試而不需要啟動整個 ScoringService。
	@Autowired
	private ProductFactorScorer productFactorScorer;

	@Autowired
	private HistoricalScoreCalculator historicalScoreCalculator;

	@Autowired
	private ScoreBandResolver scoreBandResolver;

	@Autowired
	private AlgorithmSettings algorithmSettings;

	@Autowired
	private ProductTypeAttributeResolver productTypeAttributeResolver;

	@Autowired
	private ProductTypeRepository productTypeRepository;

	/** 2026-09-24 V21：計分候選檔期改由規則推算（取代 FestiveCampaignRepository.findByCampaignStatusIn）。 */
	@Autowired
	private FestiveCampaignRuleService festiveCampaignRuleService;

	@Autowired
	private FestiveCampaignTagRepository festiveCampaignTagRepository;

	@Autowired
	private TrendSignalRepository trendSignalRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ReviewRecordRepository reviewRecordRepository;

	@Autowired
	private AudienceProfileRepository audienceProfileRepository;

	@Autowired
	private SystemSettingRepository systemSettingRepository;

	@Autowired
	private FactorDefinitionRepository factorDefinitionRepository;

	@Autowired
	private CustomFieldDefinitionRepository customFieldDefinitionRepository;

	// system_settings的key，對應「目前生效評估模式」的id（見SystemSettingRepository
	// 類別註解裡的使用範例，本方法沿用同一把key，不重新發明）。
	private static final String CURRENT_EVALUATION_MODE_KEY = "current_evaluation_mode_id";

	// 資料完整度門檻（規格書QA3）：未達60%不進入固定評估模式計分。
	private static final BigDecimal DATA_COMPLETENESS_THRESHOLD = new BigDecimal("60");

	/**
	 * 全部目前生效中的因子代碼：既有寫死的七個＋factor_definitions 裡 is_active
	 * 的自訂因子。取代原本直接用 FactorCode.ALL（固定七個）的地方——
	 * 加權計算與「因子代碼必須認得、必須齊全」的驗證都要涵蓋自訂因子，
	 * 否則自訂因子永遠不會被算進總分，或者永遠無法通過權重驗證。
	 */
	@Transactional(readOnly = true)
	public List<String> getAllActiveFactorCodes() {
		List<String> codes = new ArrayList<>(FactorCode.ALL);
		factorDefinitionRepository.findByIsActiveTrue().stream().map(FactorDefinition::getFactorCode)
				.forEach(codes::add);
		return codes;
	}

	/** 商品目前的即時評估結果（product_evaluations，唯讀）。可能為空——見類別註解。 */
	@Transactional(readOnly = true)
	public Optional<ProductEvaluation> getCurrentEvaluation(Long productId) {
		return productEvaluationRepository.findByProductId(productId);
	}

	/**
	 * 批次版本：一次查多個商品的即時評估結果，回傳 productId -&gt; evaluation。
	 *
	 * 給 ReviewService.getPendingReviews() 這類「查一頁商品，順便帶出每筆的
	 * finalScore／dataCompleteness」的清單型 API 用，比照
	 * ProductService.resolveEvaluations() 同樣的批次查詢寫法，避免在
	 * .map() 裡對每筆商品各自呼叫 getCurrentEvaluation()（N+1）。依
	 * 十二-13分層決議，ReviewService 不直接注入 ProductEvaluationRepository，
	 * 透過這裡取得。
	 *
	 * 查無評估紀錄的商品 id 不會出現在回傳的 Map 裡（不是塞 null 值），
	 * 呼叫端用 Map.get(id) 取值時自然會拿到 null，寫法與
	 * ProductService.resolveEvaluations() 的既有慣例一致。
	 */
	@Transactional(readOnly = true)
	public Map<Long, ProductEvaluation> getCurrentEvaluations(Collection<Long> productIds) {
		if (productIds == null || productIds.isEmpty()) {
			return Map.of();
		}
		return productEvaluationRepository.findByProductIdIn(productIds).stream()
				.collect(Collectors.toMap(ProductEvaluation::getProductId, evaluation -> evaluation));
	}

	/** 依評估模式ID查詢完整模式資料（用於補上評估模式名稱／版本）。 */
	@Transactional(readOnly = true)
	public Optional<EvaluationMode> getEvaluationMode(Long evaluationModeId) {
		if (evaluationModeId == null) {
			return Optional.empty();
		}
		return evaluationModeRepository.findById(evaluationModeId);
	}

	/**
	 * 組裝review_records.weight_snapshot：該評估模式當下的完整固定權重明細。
	 * evaluationModeId為null（商品尚未評估過）或查無此模式時回傳null。
	 */
	/**
	 * 不含商品情境的版本——供純粹顯示評估模式結構使用（例如設定頁的
	 * GET /evaluation-modes/{id}/factors，那裡沒有特定商品可以參照）。
	 * 這個版本的 scoreBands／historySampleSize* 一律為 null，因為這些
	 * 資訊依商品所屬品類而定，沒有商品就沒有品類可查。
	 */
	@Transactional(readOnly = true)
	public WeightSnapshot buildWeightSnapshot(Long evaluationModeId) {
		return buildWeightSnapshot(evaluationModeId, null);
	}

	/**
	 * 含商品情境的版本——審核流程（getReviewDetail／submitReview）都應該用
	 * 這個版本，才能把「當時用的目標區間、歷史樣本數」一併凍結進快照。
	 *
	 * 這裡曾經是一個已知缺口：scoreBands／historySampleSizeCategory／
	 * historySampleSizeProduct／historyIncludesSimulated 這四個欄位存在於
	 * WeightSnapshot 的資料結構裡，但先前從未被賦值——目標區間之後如果被
	 * 調整，已審核商品的快照裡查不到「當時用的是哪一組區間」。現在補上。
	 */
	@Transactional(readOnly = true)
	public WeightSnapshot buildWeightSnapshot(Long evaluationModeId, Product product) {
		return getEvaluationMode(evaluationModeId).map(mode -> {
			List<EvaluationFactor> factors = evaluationFactorRepository
					.findByEvaluationModeIdOrderBySortOrderAsc(mode.getId());

			WeightSnapshot snapshot = new WeightSnapshot();
			snapshot.setModeCode(mode.getModeCode());
			snapshot.setModeName(mode.getModeName());
			snapshot.setVersion(mode.getVersion());
			// 2026-09-20修正N+1：原本toWeightFactorSnapshot()對每個factor各自查一次
			// factorDefinitionRepository.findByFactorCode()，一個模式7~11個因子就是
			// 7~11次獨立查詢，且這支方法在Settings頁載入模式權重時每個模式都會呼叫一次。
			// 改成先用findByFactorCodeInAndIsActiveTrue()一次撈完這個模式所有因子代碼
			// 對應的定義，组成Map後查表，整個buildWeightSnapshot()只多一次查詢，
			// 不受因子數量影響。V14修正：原本用findByFactorCodeIn()（不篩isActive），
			// 版本鏈設計上線後，一個代碼可能同時存在舊版本（isActive=false）與新版本
			// （isActive=true）兩列，若沿用原本的方法，Collectors.toMap()會因為同一個
			// key出現兩次直接丟IllegalStateException（Duplicate key）。只查生效中的，
			// 天生保證每個代碼最多一列。
			Map<String, FactorDefinition> definitionsByCode = factorDefinitionRepository
					.findByFactorCodeInAndIsActiveTrue(factors.stream().map(EvaluationFactor::getFactorCode).toList())
					.stream()
					.collect(Collectors.toMap(FactorDefinition::getFactorCode, d -> d));
			// 同一批次把可能用到的自訂商品屬性代碼也查出來，理由跟上面一致：
			// 避免 toWeightFactorSnapshot() 對每個綁了自訂屬性的因子各自查一次
			// custom_field_definitions。凍結 fieldCode 字串（不是只存 id）是為了
			// 可重現性——就算這個自訂屬性題目之後被刪除，Snapshot 仍然清楚記著
			// 「當時讀的是哪個代碼」，不會因為外鍵對應的資料消失就看不出來源。
			List<Long> customFieldIds = definitionsByCode.values().stream()
					.map(FactorDefinition::getCustomFieldDefinitionId).filter(Objects::nonNull).toList();
			Map<Long, String> customFieldCodesById = customFieldIds.isEmpty() ? Map.of()
					: customFieldDefinitionRepository.findAllById(customFieldIds).stream()
							.collect(Collectors.toMap(CustomFieldDefinition::getId, CustomFieldDefinition::getFieldCode));
			snapshot.setFactors(factors.stream()
					.map(f -> toWeightFactorSnapshot(f, definitionsByCode, customFieldCodesById)).toList());

			// 演算法參數一併存進快照。只存權重不存參數，事後仍然無法重現當時的
			// 計算——這些數字都放在 system_settings 且刻意設計成可調，而可調就
			// 代表會被調。用今天的參數重算半年前的分數必然對不上。
			snapshot.setShrinkageKCategory(algorithmSettings.getShrinkageKCategory());
			snapshot.setShrinkageKProduct(algorithmSettings.getShrinkageKProduct());
			snapshot.setTrendHalfLifeDays(algorithmSettings.getTrendHalfLifeDays());

			if (product != null) {
				// 歷史樣本數：直接重用 HistoricalScoreCalculator 已經算好的結果，
				// 不重新查一次資料庫——避免同一次審核裡兩處查詢可能因為
				// 極端情況下的並發寫入而得到微幅不同的樣本數。
				var historyResult = historicalScoreCalculator.calculate(product);
				snapshot.setHistorySampleSizeCategory(historyResult.categorySampleSize());
				snapshot.setHistorySampleSizeProduct(historyResult.productSampleSize());
				snapshot.setHistoryIncludesSimulated(historyResult.includesSimulatedData());

				// 目標區間：只有 MARGIN_RATE／DISCOUNT_DEPTH 兩個因子有對應區間，
				// 其餘因子沒有「目標區間」這個概念，不強行塞入空區間。
				Map<String, List<BigDecimal>> bands = new LinkedHashMap<>();
				for (String code : List.of(ScoreBandResolver.FACTOR_MARGIN_RATE, ScoreBandResolver.FACTOR_DISCOUNT_DEPTH)) {
					scoreBandResolver.resolve(product.getProductTypeId(), code).ifPresent(band ->
							bands.put(code, List.of(band.getLowerBound(), band.getUpperBound())));
				}
				snapshot.setScoreBands(bands);
			}

			return snapshot;
		}).orElse(null);
	}

	private WeightFactorSnapshot toWeightFactorSnapshot(EvaluationFactor factor,
			Map<String, FactorDefinition> definitionsByCode, Map<Long, String> customFieldCodesById) {
		WeightFactorSnapshot dto = new WeightFactorSnapshot();
		dto.setFactorCode(factor.getFactorCode());
		dto.setFactorName(factor.getFactorName());
		dto.setCategory(factor.getCategory());
		dto.setWeight(factor.getWeight());
		// 既有七個因子沒有對應的 FactorDefinition（刻意不遷移，見該類別註解），
		// 這裡查不到是正常情況，strategyCode／strategyParams 維持 null；
		// 只有自訂因子才會查到值。凍結這兩個欄位是為了可重現性——因子定義
		// 之後可能被改策略或改參數，Snapshot 要留住「當時真正生效的是哪一版」。
		FactorDefinition definition = definitionsByCode.get(factor.getFactorCode());
		if (definition != null) {
			dto.setStrategyCode(definition.getStrategyCode() == null ? null : definition.getStrategyCode().name());
			dto.setStrategyParams(definition.getStrategyParams());
			// 2026-09-20新增：資料源同樣要凍結，理由跟 strategyCode 一致——
			// 因子綁定的資料源之後可能被改（例如原本綁自訂屬性A，後來改綁B），
			// Snapshot 要留住「當時真正讀的是哪一個」。二選一，只會有一個非null。
			if (definition.getDataSourceCode() != null) {
				dto.setDataSourceCode(definition.getDataSourceCode().name());
			} else if (definition.getCustomFieldDefinitionId() != null) {
				dto.setCustomFieldCode(customFieldCodesById.get(definition.getCustomFieldDefinitionId()));
			}
		}
		return dto;
	}

	/**
	 * 組裝review_records.trend_snapshot：該商品最新一筆趨勢資料。無資料時回傳null。
	 */
	@Transactional(readOnly = true)
	public TrendSnapshot buildTrendSnapshot(Long productId) {
		return trendSignalRepository.findFirstByProductIdOrderByCollectedAtDesc(productId).map(signal -> {
			TrendSnapshot snapshot = new TrendSnapshot();
			snapshot.setSource(signal.getSource());
			snapshot.setKeyword(signal.getKeyword());
			snapshot.setTrendScore(signal.getTrendScore());
			snapshot.setPopularityScore(signal.getPopularityScore());
			snapshot.setTrendDirection(signal.getTrendDirection() != null ? signal.getTrendDirection().name() : null);
			snapshot.setCollectedAt(signal.getCollectedAt() != null ? signal.getCollectedAt().toString() : null);
			return snapshot;
		}).orElse(null);
	}

	/**
	 * 供TrendService.syncTrend()呼叫（十二-13：TrendService → ScoringService單向依賴，
	 * 觸發「同步趨勢資料後」的重算）。
	 *
	 * <b>TODO（評分重算引擎尚未實作）：</b>這裡只更新product_evaluations.trend_score
	 * 這一個欄位，計算方式是trend_signals最新一筆的trend_score／popularity_score取
	 * 平均值——這是暫定的簡化算法，不是團隊定案的六大分項「趨勢」類別公式（該公式
	 * 從未在企劃書中定義過，屬於另一項獨立任務）。<b>刻意不觸碰total_score／
	 * final_score</b>：這兩個欄位是六大分項加權後的結果，只更新其中一項分數卻
	 * 沒有能力重新加權其餘五項，若順便更新total_score/final_score，等於用不完整
	 * 的資料產出一個「看起來是正式重算結果」的分數，比維持舊值不變更容易誤導使用者。
	 * 待完整評分重算引擎完成後，這個方法應該被該引擎的正式重算流程取代或呼叫。
	 *
	 * 商品尚未有product_evaluations紀錄時（evaluation_mode_id要選哪個模式，屬於
	 * 評分重算引擎的職責，此處不臆測預設值），本次同步僅完成trend_signals寫入，
	 * 不建立不完整的評估紀錄，直接略過。
	 */
	@Transactional
	public void updateTrendScoreFromLatestSignal(Long productId) {
		Optional<TrendSignal> latestSignal = trendSignalRepository.findFirstByProductIdOrderByCollectedAtDesc(productId);
		if (latestSignal.isEmpty()) {
			return;
		}

		productEvaluationRepository.findByProductId(productId).ifPresent(evaluation -> {
			evaluation.setTrendScore(calculatePlaceholderTrendScore(latestSignal.get()));
			// 這裡確實改動了分數（即使只是trend_score這一項），語意上算一次「計算」，
			// 故calculated_at也要跟著更新，維持「trend_score變了、calculated_at
			// 就該反映最新一次計算」的一致性（見calculatedAt欄位註解的設計說明）。
			evaluation.setCalculatedAt(LocalDateTime.now());
			productEvaluationRepository.save(evaluation);
		});
	}

	private BigDecimal calculatePlaceholderTrendScore(TrendSignal signal) {
		BigDecimal trendScore = signal.getTrendScore() != null ? signal.getTrendScore() : BigDecimal.ZERO;
		BigDecimal popularityScore = signal.getPopularityScore() != null ? signal.getPopularityScore() : BigDecimal.ZERO;
		return trendScore.add(popularityScore).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
	}

	/**
	 * 組裝review_records.matched_campaign_snapshot：Festival Boost可解釋性明細。
	 *
	 * 命中判定與係數計算依企劃書「節慶加成計分規則」：
	 * - 只比對「推算後」為 PREPARING／ACTIVE 的檔期（2026-09-24 V21 修正 B1）：節慶／季節型的狀態
	 *   依日期規則即時推算，不再讀資料表裡從不自動推進的 campaign_status；天氣型仍依同步寫入的狀態。
	 *   候選清單由 FestiveCampaignRuleService.findLiveWindows() 批次產生（區域、覆寫一次載入）。
	 * - 命中＝product.campaign_tags與該檔期festive_campaign_tags.tag集合的交集非空
	 * - Match Weight＝命中標籤中最高等級的match_tier權重（核心1.0／一般0.6／弱0.3），
	 *   多標籤取最高等級不加總（見FestiveCampaignTagMatchTier）
	 * - 多節慶重疊：以「該檔期算出的Festival Boost」取最大值(MAX)，不加總
	 * - 未命中任何檔期時回傳null（對應「未命中檔期時不顯示此區塊，Festival Boost=0」）
	 *
	 * 快照新增命中期間、地域、覆蓋率與各係數（見 MatchedCampaignSnapshot 類別註解），
	 * 已審核商品的重現性由快照保證，檔期規則或占比之後再改都不影響。
	 */
	@Transactional(readOnly = true)
	public MatchedCampaignSnapshot buildMatchedCampaignSnapshot(Product product) {
		Set<String> productTags = splitTags(product.getCampaignTags());
		if (productTags.isEmpty()) {
			return null;
		}

		LocalDate today = LocalDate.now(BusinessTimeZone.TAIPEI);
		List<ActiveCampaignWindow> candidates = festiveCampaignRuleService.findLiveWindows(today);
		if (candidates.isEmpty()) {
			return null;
		}
		return matchCampaign(productTags, candidates, loadCampaignTags(candidates), today);
	}

	/** 候選檔期的標籤，一次批次載入後依檔期分組（避免逐檔期查詢）。 */
	private Map<Long, List<FestiveCampaignTag>> loadCampaignTags(List<ActiveCampaignWindow> candidates) {
		List<Long> candidateIds = candidates.stream().map(window -> window.campaign().getId()).toList();
		return festiveCampaignTagRepository.findByCampaignIdIn(candidateIds).stream()
				.collect(Collectors.groupingBy(FestiveCampaignTag::getCampaignId));
	}

	/**
	 * 命中判定本體（2026-09-24 由 buildMatchedCampaignSnapshot() 抽出）：候選檔期與其標籤由呼叫端
	 * 提供，讓 refreshFestivalBoosts() 對整批商品只查一次檔期與標籤，單筆與批次走同一套判定。
	 */
	private MatchedCampaignSnapshot matchCampaign(Set<String> productTags, List<ActiveCampaignWindow> candidates,
			Map<Long, List<FestiveCampaignTag>> tagsByCampaign, LocalDate today) {
		ActiveCampaignWindow bestWindow = null;
		CampaignUrgencyCalculator.Result bestUrgency = null;
		Set<String> bestMatchedTags = Set.of();
		BigDecimal bestMatchWeight = BigDecimal.ZERO;
		BigDecimal bestBoost = BigDecimal.ZERO;

		for (ActiveCampaignWindow window : candidates) {
			List<FestiveCampaignTag> campaignTags = tagsByCampaign.getOrDefault(window.campaign().getId(), List.of());

			// 該檔期底下，命中商品標籤的所有tag，取其中match_tier權重最高者
			FestiveCampaignTag bestMatch = null;
			Set<String> matchedTags = new LinkedHashSet<>();
			for (FestiveCampaignTag campaignTag : campaignTags) {
				if (!productTags.contains(campaignTag.getTag())) {
					continue;
				}
				matchedTags.add(campaignTag.getTag());
				if (bestMatch == null || campaignTag.getMatchTier().getMatchWeight()
						.compareTo(bestMatch.getMatchTier().getMatchWeight()) > 0) {
					bestMatch = campaignTag;
				}
			}
			if (bestMatch == null) {
				continue; // 未命中
			}

			BigDecimal matchWeight = bestMatch.getMatchTier().getMatchWeight();
			CampaignUrgencyCalculator.Result urgency = calculateUrgencyFactor(window, today);
			BigDecimal boost = matchWeight.multiply(urgency.urgencyFactor()).multiply(BOOST_CAP);

			if (boost.compareTo(bestBoost) > 0) {
				bestBoost = boost;
				bestWindow = window;
				bestUrgency = urgency;
				bestMatchedTags = matchedTags;
				bestMatchWeight = matchWeight;
			}
		}

		if (bestWindow == null) {
			return null;
		}

		FestiveCampaign campaign = bestWindow.campaign();
		MatchedCampaignSnapshot snapshot = new MatchedCampaignSnapshot();
		snapshot.setCampaignId(campaign.getId());
		snapshot.setCampaignName(campaign.getCampaignName());
		snapshot.setMatchedTags(new ArrayList<>(bestMatchedTags));
		snapshot.setMatchWeight(bestMatchWeight);
		snapshot.setUrgencyFactor(bestUrgency.urgencyFactor());
		snapshot.setCategory(campaign.getCategory().name());
		snapshot.setCycleYear(bestWindow.occurrence().cycleYear());
		snapshot.setOccurrenceStartDate(bestWindow.occurrence().startDate().toString());
		snapshot.setOccurrenceEndDate(bestWindow.occurrence().endDate().toString());
		snapshot.setOccurrenceOverridden(bestWindow.occurrence().overridden());
		snapshot.setRegions(new ArrayList<>(bestWindow.regions()));
		snapshot.setRegionCoverageRatio(bestUrgency.regionCoverage());
		snapshot.setTimeFactor(bestUrgency.timeFactor());
		snapshot.setWeatherConfidenceFactor(bestUrgency.weatherConfidenceFactor());
		return snapshot;
	}

	/** 急迫係數與其三個乘數，公式見 CampaignUrgencyCalculator（修正 B2）。 */
	private CampaignUrgencyCalculator.Result calculateUrgencyFactor(ActiveCampaignWindow window, LocalDate today) {
		FestiveCampaign campaign = window.campaign();
		return CampaignUrgencyCalculator.calculate(campaign.getCategory(), window.status(),
				campaign.getPreparationLeadDays(), window.occurrence().startDate(), today,
				campaign.getWeatherConfidence(), window.regionCoverage());
	}

	/**
	 * GET /api/products/{id}/evaluation：商品目前評估模式、固定權重、各項分數、
	 * Base/Final Score。
	 *
	 * <b>雙軌讀取邏輯</b>（product_evaluations.final_score欄位DB註解明訂的全域規則，
	 * 非本端點自訂）：review_status=APPROVED時讀取review_records最新一筆的
	 * Snapshot凍結值；其餘狀態讀取product_evaluations即時值。
	 */
	@Transactional(readOnly = true)
	public EvaluationResponse getEvaluation(Long productId) {
		Product product = findProductOrThrow(productId);

		if (product.getReviewStatus() == ProductReviewStatus.APPROVED) {
			Optional<ReviewRecord> latestRecord = reviewRecordRepository
					.findFirstByProductIdOrderByReviewedAtDesc(productId);
			if (latestRecord.isPresent()) {
				return buildEvaluationResponseFromSnapshot(latestRecord.get());
			}
			// 防禦性：理論上APPROVED商品必然有審核紀錄（審核當下才會轉為APPROVED），
			// 若資料異常導致真的找不到，退回即時值而非讓畫面直接掛掉。
		}
		return buildEvaluationResponseFromLive(productId);
	}

	private EvaluationResponse buildEvaluationResponseFromSnapshot(ReviewRecord record) {
		EvaluationResponse response = new EvaluationResponse();
		response.setDataSource("SNAPSHOT");
		response.setEvaluationModeId(record.getEvaluationModeId());
		response.setEvaluationModeName(record.getEvaluationModeName());
		response.setEvaluationModeVersion(record.getEvaluationModeVersion());
		response.setWeights(record.getWeightSnapshot());
		response.setBusinessScore(record.getBusinessScore());
		response.setAudienceScore(record.getAudienceScore());
		response.setHistoricalScore(record.getHistoricalScore());
		response.setPurchaseScore(record.getPurchaseScore());
		response.setTrendScore(record.getTrendScore());
		response.setForecastScore(record.getForecastScore());
		response.setTotalScore(record.getTotalScore());
		response.setDataCompleteness(record.getDataCompleteness());
		response.setFestivalBoost(record.getFestivalBoostSnapshot());
		response.setFinalScore(record.getFinalScoreSnapshot());
		return response;
	}

	private EvaluationResponse buildEvaluationResponseFromLive(Long productId) {
		EvaluationResponse response = new EvaluationResponse();
		response.setDataSource("LIVE");

		Optional<ProductEvaluation> evaluationOpt = getCurrentEvaluation(productId);
		Long evaluationModeId = evaluationOpt.map(ProductEvaluation::getEvaluationModeId).orElse(null);

		getEvaluationMode(evaluationModeId).ifPresent(mode -> {
			response.setEvaluationModeId(mode.getId());
			response.setEvaluationModeName(mode.getModeName());
			response.setEvaluationModeVersion(mode.getVersion());
		});
		response.setWeights(buildWeightSnapshot(evaluationModeId));

		evaluationOpt.ifPresent(evaluation -> {
			response.setBusinessScore(evaluation.getBusinessScore());
			response.setAudienceScore(evaluation.getAudienceScore());
			response.setHistoricalScore(evaluation.getHistoricalScore());
			response.setPurchaseScore(evaluation.getPurchaseScore());
			response.setTrendScore(evaluation.getTrendScore());
			response.setForecastScore(evaluation.getForecastScore());
			response.setTotalScore(evaluation.getTotalScore());
			response.setDataCompleteness(evaluation.getDataCompleteness());
			response.setFestivalBoost(evaluation.getFestivalBoost());
			response.setFinalScore(evaluation.getFinalScore());
		});
		return response;
	}

	/**
	 * GET /api/products/{id}/festival-boost：該商品目前命中的檔期、Match Weight、
	 * Urgency Factor、Festival Boost、Final Score等可解釋性明細。
	 *
	 * 雙軌讀取邏輯與getEvaluation()相同（同一條DB欄位註解規則涵蓋的範圍）。
	 */
	@Transactional(readOnly = true)
	public FestivalBoostResponse getFestivalBoostDetail(Long productId) {
		Product product = findProductOrThrow(productId);

		if (product.getReviewStatus() == ProductReviewStatus.APPROVED) {
			Optional<ReviewRecord> latestRecord = reviewRecordRepository
					.findFirstByProductIdOrderByReviewedAtDesc(productId);
			if (latestRecord.isPresent()) {
				ReviewRecord record = latestRecord.get();
				FestivalBoostResponse response = new FestivalBoostResponse();
				response.setDataSource("SNAPSHOT");
				response.setMatchedCampaign(record.getMatchedCampaignSnapshot());
				response.setFestivalBoost(record.getFestivalBoostSnapshot());
				response.setFinalScore(record.getFinalScoreSnapshot());
				return response;
			}
		}

		FestivalBoostResponse response = new FestivalBoostResponse();
		response.setDataSource("LIVE");

		// 命中明細（含當下的 urgencyFactor）本來就是即時重算，維持不變。
		MatchedCampaignSnapshot campaignSnapshot = buildMatchedCampaignSnapshot(product);
		response.setMatchedCampaign(campaignSnapshot);

		// 2026-09-24 修正（Bug B）：festivalBoost／finalScore 原本讀 product_evaluations 裡「上次
		// calculateEvaluation() 寫入時」的值，但 matchedCampaign 是即時重算。product_evaluations
		// 沒有排程重算（只在商品新增／編輯、趨勢同步時更新），PREPARING／WEATHER 檔期的
		// urgencyFactor 隨日期遞增、或檔期轉 ACTIVE 後，就會出現「urgencyFactor 100%、命中核心標籤，
		// festivalBoost 卻不是 matchWeight×urgencyFactor×5」的矛盾（實例：顯示 4.44，應為 5.00）。
		// 改為用同一份即時 campaignSnapshot 算 festivalBoost；finalScore＝已存的 totalScore
		// （加權分數，不受時間漂移影響）＋這個即時 festivalBoost，整包回傳值都基於同一時間點。
		// 只影響本次 Response，不回寫 product_evaluations；列表排序／Top10 用的仍是已存值，
		// 那是排程重算的議題（獨立 backlog），不在本次範圍。
		BigDecimal freshFestivalBoost = calculateFestivalBoost(campaignSnapshot);
		response.setFestivalBoost(freshFestivalBoost);
		getCurrentEvaluation(productId).ifPresent(evaluation -> {
			// totalScore 為 null（資料完整度未達門檻、從未成功計分）時 finalScore 維持 null，
			// 前端顯示「—」，不用 0 誤導。
			if (evaluation.getTotalScore() != null) {
				response.setFinalScore(evaluation.getTotalScore().add(freshFestivalBoost)
						.setScale(2, RoundingMode.HALF_UP));
			}
		});
		return response;
	}

	/**
	 * Festival Boost＝matchWeight × urgencyFactor × BOOST_CAP（未命中＝0）。
	 * calculateEvaluation()（寫入 product_evaluations）、getFestivalBoostDetail()（LIVE 明細）與
	 * ReviewService.submitReview()（審核快照）共用這一個公式，避免各寫一份、日後只改到其中一邊
	 * 又出現數字對不上。
	 */
	public static BigDecimal calculateFestivalBoost(MatchedCampaignSnapshot campaignSnapshot) {
		if (campaignSnapshot == null) {
			return BigDecimal.ZERO;
		}
		return campaignSnapshot.getMatchWeight().multiply(campaignSnapshot.getUrgencyFactor()).multiply(BOOST_CAP)
				.setScale(2, RoundingMode.HALF_UP);
	}

	// ============================================================
	// 節慶加成每日重算（2026-09-24，方案 2）
	// ============================================================

	/**
	 * 只重算 product_evaluations 裡「會隨日期與檔期設定變動」的三欄：festival_boost、
	 * matched_campaign_id、final_score（＝已存 total_score＋新加成）。加權總分等其餘欄位不動，
	 * calculated_at 也不動（它代表完整評分的計算時間）。
	 *
	 * <b>為什麼需要：</b>urgencyFactor 以「天」為單位隨檔期接近而上升、檔期狀態會轉換，但
	 * product_evaluations 原本只在商品新增／編輯、趨勢同步時才重算，詳情頁、清單、Top10（資料庫
	 * 依 final_score 排序）、審核頁讀到的都是舊加成，與即時算出的命中明細對不上（實例：急迫係數
	 * 100%、核心標籤，加成卻顯示 4.44 而非 5.00）。
	 *
	 * <b>範圍：</b>尚未核准的商品（已核准商品一律讀審核快照）；total_score 為 null（從未達資料
	 * 完整度門檻）的跳過，沒有分數可加。未達門檻但保留舊分數的商品照樣更新加成，
	 * 讓同一個舊 total_score 搭配的加成與畫面上的即時命中明細一致。
	 *
	 * <b>觸發：</b>每天 05:10（台灣時間），接在 05:00 天氣同步之後；另外在天氣同步、節慶檔期
	 * 新增／編輯／切換狀態／逐年覆寫、地域占比調整後，由 FestivalBoostRefreshListener 在交易
	 * 提交後立即觸發（見 FestiveCampaignsChangedEvent）。
	 *
	 * REQUIRES_NEW：事件監聽在原交易提交後（AFTER_COMMIT）執行，此時原交易資源仍綁定在執行緒上，
	 * 用 REQUIRED 會加入一個已提交的交易而寫不進去，必須開新交易。
	 *
	 * @return 實際有變動而寫回的筆數
	 */
	@Scheduled(cron = "0 10 5 * * *", zone = "Asia/Taipei")
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int refreshFestivalBoosts() {
		List<Product> products = productRepository.findByReviewStatusNot(ProductReviewStatus.APPROVED);
		if (products.isEmpty()) {
			return 0;
		}
		Map<Long, ProductEvaluation> evaluationsByProductId = productEvaluationRepository
				.findByProductIdIn(products.stream().map(Product::getId).toList()).stream()
				.collect(Collectors.toMap(ProductEvaluation::getProductId, evaluation -> evaluation));

		// 候選檔期與其標籤整批只查一次，所有商品共用同一個「今天」，結果彼此一致。
		LocalDate today = LocalDate.now(BusinessTimeZone.TAIPEI);
		List<ActiveCampaignWindow> candidates = festiveCampaignRuleService.findLiveWindows(today);
		Map<Long, List<FestiveCampaignTag>> tagsByCampaign = candidates.isEmpty() ? Map.of()
				: loadCampaignTags(candidates);

		List<ProductEvaluation> changed = new ArrayList<>();
		for (Product product : products) {
			ProductEvaluation evaluation = evaluationsByProductId.get(product.getId());
			if (evaluation == null || evaluation.getTotalScore() == null) {
				continue;
			}
			Set<String> productTags = splitTags(product.getCampaignTags());
			MatchedCampaignSnapshot snapshot = productTags.isEmpty() || candidates.isEmpty() ? null
					: matchCampaign(productTags, candidates, tagsByCampaign, today);
			BigDecimal festivalBoost = calculateFestivalBoost(snapshot);
			Long matchedCampaignId = snapshot != null ? snapshot.getCampaignId() : null;
			BigDecimal finalScore = evaluation.getTotalScore().add(festivalBoost).setScale(2, RoundingMode.HALF_UP);

			if (sameValue(evaluation.getFestivalBoost(), festivalBoost)
					&& sameValue(evaluation.getFinalScore(), finalScore)
					&& Objects.equals(evaluation.getMatchedCampaignId(), matchedCampaignId)) {
				continue; // 沒變就不寫，避免每天整批無意義 UPDATE
			}
			evaluation.setFestivalBoost(festivalBoost);
			evaluation.setMatchedCampaignId(matchedCampaignId);
			evaluation.setFinalScore(finalScore);
			changed.add(evaluation);
		}
		productEvaluationRepository.saveAll(changed);
		log.info("節慶加成重算完成：檢查 {} 個未核准商品，更新 {} 筆", products.size(), changed.size());
		return changed.size();
	}

	/** BigDecimal 以數值比較（4.4 與 4.40 視為相同），null 只等於 null。 */
	private static boolean sameValue(BigDecimal stored, BigDecimal computed) {
		return stored == null ? computed == null : computed != null && stored.compareTo(computed) == 0;
	}

	// ============================================================
	// 完整評分重算引擎（2026-08-31補上，取代原本「假設由其他流程寫入」的TODO）
	// ============================================================

	/**
	 * 對商品重新計算完整評估結果並寫入product_evaluations。
	 *
	 * 觸發時機（見類別Java Doc）：ProductService新增/編輯成功後、
	 * TrendService同步趨勢資料後。
	 *
	 * @param productId       要重算的商品ID
	 * @param evaluationModeId 使用的評估模式ID；傳null時自動讀取system_settings裡
	 *                          「目前生效模式」（CURRENT_EVALUATION_MODE_KEY）
	 */
	@Transactional
	public void calculateEvaluation(Long productId, Long evaluationModeId) {
		Product product = findProductOrThrow(productId);

		Long modeId = evaluationModeId != null ? evaluationModeId : resolveEvaluationModeId(product);

		BigDecimal dataCompleteness = calculateDataCompleteness(product);

		ProductEvaluation evaluation = productEvaluationRepository.findByProductId(productId)
				.orElseGet(() -> {
					ProductEvaluation newEvaluation = new ProductEvaluation();
					newEvaluation.setProductId(productId);
					return newEvaluation;
				});
		evaluation.setEvaluationModeId(modeId);
		evaluation.setDataCompleteness(dataCompleteness);

		// 規格書QA3：未達60%門檻，只寫入資料完整度，不計算六大分項與total/final_score，
		// 不清空既有分數（維持商品「資料待補」但仍保留上一次有效分數以供UI參考的彈性；
		// 若團隊希望未達門檻時把舊分數一併清空，需另行決議，此處先採取「不覆蓋」較保守
		// 的做法，避免demo時分數忽有忽無造成混淆）。
		if (dataCompleteness.compareTo(DATA_COMPLETENESS_THRESHOLD) < 0) {
			evaluation.setCalculatedAt(LocalDateTime.now());
			productEvaluationRepository.save(evaluation);
			return;
		}

		// 七個扁平因子各自計分。缺漏因子回傳 null，由 weightedAverage() 從分母排除
		// 並重新正規化——不給中性值 50，那會讓「資料填齊但條件普通」和「什麼都
		// 沒填」拿到一樣的分數。
		Map<String, BigDecimal> factorScores = productFactorScorer.scoreAll(product);
		Map<String, BigDecimal> weights = resolveFactorWeights(modeId);

		// 加權總和。weightedAverage() 內部已除以「有值因子的權重總和」，
		// 因此不需要再除以 100——舊版的 divide(100) 是因為當時把權重當成
		// 固定加總 100 的除數，改用有效權重當分母後這個假設不再成立。
		// 2026-09-20改用 getAllActiveFactorCodes()：涵蓋既有七個因子＋
		// factor_definitions 裡目前生效中的自訂因子，缺一個都會讓自訂因子
		// 永遠算不進總分。
		BigDecimal totalScore = ScoringAlgorithms.weightedAverage(
				getAllActiveFactorCodes().stream()
						.map(code -> new ScoringAlgorithms.WeightedScore(
								code, factorScores.get(code), weights.get(code)))
						.toList());
		if (totalScore == null) {
			// 七個因子全部無資料。理論上已被 60% 完整度門檻擋下，
			// 這裡是防禦性處理：維持既有分數不覆蓋，與門檻未達時的行為一致。
			evaluation.setCalculatedAt(LocalDateTime.now());
			productEvaluationRepository.save(evaluation);
			return;
		}
		totalScore = totalScore.setScale(2, RoundingMode.HALF_UP);

		// 以下四個象限分數改為「展示用彙總」，不參與 totalScore 計算。
		// 保留它們是為了讓 product_evaluations 既有欄位與前端畫面繼續可用，
		// 讓主管能看到「商業條件這一組拿幾分」。組內比例沿用各因子自己的權重。
		BigDecimal businessScore = groupScore(FactorCode.BUSINESS_GROUP, factorScores, weights);
		BigDecimal audienceScore = factorScores.get(FactorCode.AUDIENCE_MATCH);
		BigDecimal historicalScore = factorScores.get(FactorCode.HISTORY_FULFILLMENT);
		BigDecimal purchaseScore = factorScores.get(FactorCode.PURCHASE_RATE);
		BigDecimal trendScore = factorScores.get(FactorCode.TREND_HEAT);
		BigDecimal forecastScore = groupScore(FactorCode.FORECAST_GROUP, factorScores, weights);

		MatchedCampaignSnapshot campaignSnapshot = buildMatchedCampaignSnapshot(product);
		BigDecimal festivalBoost = calculateFestivalBoost(campaignSnapshot);

		evaluation.setBusinessScore(businessScore);
		evaluation.setAudienceScore(audienceScore);
		evaluation.setHistoricalScore(historicalScore);
		evaluation.setPurchaseScore(purchaseScore);
		evaluation.setTrendScore(trendScore);
		evaluation.setForecastScore(forecastScore);
		evaluation.setTotalScore(totalScore);
		evaluation.setFestivalBoost(festivalBoost);
		evaluation.setMatchedCampaignId(campaignSnapshot != null ? campaignSnapshot.getCampaignId() : null);
		evaluation.setFinalScore(totalScore.add(festivalBoost).setScale(2, RoundingMode.HALF_UP));
		evaluation.setCalculatedAt(LocalDateTime.now());

		productEvaluationRepository.save(evaluation);
	}

	/**
	 * 資料完整度：依pricing_type動態調整分母（規格書QA3／QA1）。
	 * RESALE分母10（含定價欄位），NEW分母8（不含成本價/售價/市場行情價，
	 * 改為要求targetCustomerDescription，維持「有意義的必填清單」而非單純減項）。
	 */
	public BigDecimal calculateDataCompleteness(Product product) {
		int filled = 0;
		int total;

		filled += isFilled(product.getName()) ? 1 : 0;
		filled += product.getProductTypeId() != null ? 1 : 0;
		filled += isFilled(product.getSupplierName()) ? 1 : 0;
		filled += isFilled(product.getCampaignTags()) ? 1 : 0;
		filled += product.getMoq() != null ? 1 : 0;
		filled += product.getSupplyStability() != null ? 1 : 0;
		filled += product.getPriceCompetitiveness() != null ? 1 : 0;

		if (product.getPricingType() == ProductPricingType.NEW) {
			total = 8;
			filled += isFilled(product.getTargetCustomerDescription()) ? 1 : 0;
		} else {
			total = 10;
			filled += product.getCostPrice() != null ? 1 : 0;
			filled += product.getSalePrice() != null ? 1 : 0;
			filled += product.getMarketPrice() != null ? 1 : 0;
		}

		return BigDecimal.valueOf(filled)
				.divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100))
				.setScale(2, RoundingMode.HALF_UP);
	}

	private boolean isFilled(String value) {
		return value != null && !value.trim().isEmpty();
	}






	/**
	 * 讀取指定評估模式底下，BUSINESS/AUDIENCE/HISTORY/FORECAST四大分類的權重，
	 * 以Map回傳（key為category字串，value為權重，通常0-1之間的小數）。
	 * 找不到對應模式或無factors設定時，回傳空Map（呼叫端用getOrDefault(0)防呆，
	 * 等同於「這個分類這次不計分」，而非讓NullPointerException中斷整個計算）。
	 */
	/**
	 * 讀取指定模式的因子權重，以 factor_code 為 key。
	 *
	 * <b>改用 factor_code 而非 category 的原因</b>：改版後同一個 category 底下
	 * 有多個因子（BUSINESS 有毛利率、折扣深度、供應穩定性三個）。舊版用 category
	 * 當 key 並在衝突時「取第一筆」，會靜默丟棄後面的權重——BUSINESS 只會拿到
	 * 10 而不是 25，總權重從 100 掉到 72.5，所有商品分數少約 27%，而且完全不會
	 * 報錯。factor_code 在同一個模式內是唯一的，不會發生這個問題。
	 *
	 * category 欄位仍保留，但語意已改為「畫面上的分組標題」，不參與計算。
	 */
	private Map<String, BigDecimal> resolveFactorWeights(Long evaluationModeId) {
		if (evaluationModeId == null) {
			return Map.of();
		}
		List<EvaluationFactor> factors = evaluationFactorRepository
				.findByEvaluationModeIdOrderBySortOrderAsc(evaluationModeId);
		Map<String, BigDecimal> weights = new LinkedHashMap<>();
		for (EvaluationFactor factor : factors) {
			// 同一個模式內 factor_code 應該唯一。萬一資料有重複，取後蓋前並不安全，
			// 因此明確以第一筆為準並記 warn，讓設定錯誤能被發現而不是靜默生效。
			if (weights.putIfAbsent(factor.getFactorCode(), factor.getWeight()) != null) {
				log.warn("評估模式 {} 的因子代碼重複：{}，已忽略後續筆數", evaluationModeId, factor.getFactorCode());
			}
		}
		return weights;
	}

	/**
	 * 計算展示用的分組彙總分數（例如商業條件三個因子合起來幾分）。
	 *
	 * 這個值只給畫面看，不參與 totalScore——totalScore 是七個因子直接扁平加權。
	 * 分開算的話兩者不會完全一致（分組彙總是組內正規化後的結果），這是預期的：
	 * 分組分數回答「這一組表現如何」，總分回答「整體幾分」。
	 */
	private BigDecimal groupScore(List<String> factorCodes, Map<String, BigDecimal> scores,
			Map<String, BigDecimal> weights) {
		BigDecimal result = ScoringAlgorithms.weightedAverage(factorCodes.stream()
				.map(code -> new ScoringAlgorithms.WeightedScore(code, scores.get(code), weights.get(code)))
				.toList());
		return result == null ? null : result.setScale(2, RoundingMode.HALF_UP);
	}

	/**
	 * 讀取system_settings裡「目前生效評估模式」的id。查無設定時拋出例外，
	 * 而非靜默給一個猜測值——沒有生效模式代表系統設定尚未完成初始化，
	 * 讓呼叫端明確知道問題所在，比算出一個不知道套用哪套權重的分數更安全。
	 */
	private Long resolveEvaluationModeId(Product product) {
		// 品類（大類）指定的模式優先。這讓生鮮與常溫耐儲存品可以套用不同權重——
		// 兩者的風險結構相反，用同一組權重排序，文具類會系統性地贏過生鮮，
		// 而那不代表文具比較值得開團。
		if (product != null && product.getProductTypeId() != null) {
			Long rootTypeId = productTypeAttributeResolver.resolveRootTypeId(product.getProductTypeId());
			if (rootTypeId != null) {
				Long modeId = productTypeRepository.findById(rootTypeId)
						.map(ProductType::getDefaultEvaluationModeId).orElse(null);
				if (modeId != null) {
					return modeId;
				}
			}
		}
		// 品類未指定時退回全域設定，維持改版前的行為。
		// 由於所有品類的 default_evaluation_mode_id 預設為 null，剛上線時
		// 全部商品都會走這條路徑，分數與改版前一致——這是刻意的，
		// 讓「換算法」與「換權重」兩件事分開發生，出問題時才分得出是哪一個造成的。
		return systemSettingRepository.findById(CURRENT_EVALUATION_MODE_KEY)
				.map(SystemSetting::getSettingValue)
				.map(Long::valueOf)
				.orElseThrow(() -> new IllegalStateException(
						"尚未設定目前生效評估模式（system_settings." + CURRENT_EVALUATION_MODE_KEY + "），請先於設定頁指定"));
	}

	private BigDecimal nullToZero(BigDecimal value) {
		return value != null ? value : BigDecimal.ZERO;
	}

	private BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
		if (value.compareTo(min) < 0) {
			return min;
		}
		if (value.compareTo(max) > 0) {
			return max;
		}
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	private Product findProductOrThrow(Long productId) {
		return productRepository.findById(productId).orElseThrow(() -> new IllegalArgumentException("商品不存在"));
	}

	private Set<String> splitTags(String tags) {
		if (tags == null || tags.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isEmpty())
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}
}