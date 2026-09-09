package com.example.Product_Selection_260813.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.service.resolver.AlgorithmSettings;
import com.example.Product_Selection_260813.algorithm.ScoringAlgorithms;
import com.example.Product_Selection_260813.constants.FactorCode;
import com.example.Product_Selection_260813.repository.ProductTypeRepository;
import com.example.Product_Selection_260813.dto.response.EvaluationResponse;
import com.example.Product_Selection_260813.dto.response.FestivalBoostResponse;
import com.example.Product_Selection_260813.entity.AudienceProfile;
import com.example.Product_Selection_260813.entity.EvaluationFactor;
import com.example.Product_Selection_260813.entity.EvaluationMode;
import com.example.Product_Selection_260813.entity.FestiveCampaign;
import com.example.Product_Selection_260813.entity.FestiveCampaignTag;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductEvaluation;
import com.example.Product_Selection_260813.entity.ReviewRecord;
import com.example.Product_Selection_260813.entity.ProductType;
import com.example.Product_Selection_260813.entity.SystemSetting;
import com.example.Product_Selection_260813.entity.TrendSignal;
import com.example.Product_Selection_260813.enums.FestiveCampaignStatus;
import com.example.Product_Selection_260813.enums.FestiveCategory;
import com.example.Product_Selection_260813.enums.ProductPricingType;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;
import com.example.Product_Selection_260813.json.MatchedCampaignSnapshot;
import com.example.Product_Selection_260813.json.TrendSnapshot;
import com.example.Product_Selection_260813.json.WeightFactorSnapshot;
import com.example.Product_Selection_260813.json.WeightSnapshot;
import com.example.Product_Selection_260813.repository.AudienceProfileRepository;
import com.example.Product_Selection_260813.repository.EvaluationFactorRepository;
import com.example.Product_Selection_260813.repository.EvaluationModeRepository;
import com.example.Product_Selection_260813.repository.FestiveCampaignRepository;
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

	// 季節型檔期PREPARING期間固定係數（企劃書節慶加成計分規則明訂，非本類別臆測）。
	private static final BigDecimal SEASON_PREPARING_URGENCY_FACTOR = new BigDecimal("0.20");

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
	private AlgorithmSettings algorithmSettings;

	@Autowired
	private ProductTypeAttributeResolver productTypeAttributeResolver;

	@Autowired
	private ProductTypeRepository productTypeRepository;

	@Autowired
	private FestiveCampaignRepository festiveCampaignRepository;

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

	// system_settings的key，對應「目前生效評估模式」的id（見SystemSettingRepository
	// 類別註解裡的使用範例，本方法沿用同一把key，不重新發明）。
	private static final String CURRENT_EVALUATION_MODE_KEY = "current_evaluation_mode_id";

	// 資料完整度門檻（規格書QA3）：未達60%不進入固定評估模式計分。
	private static final BigDecimal DATA_COMPLETENESS_THRESHOLD = new BigDecimal("60");

	/** 商品目前的即時評估結果（product_evaluations，唯讀）。可能為空——見類別註解。 */
	@Transactional(readOnly = true)
	public Optional<ProductEvaluation> getCurrentEvaluation(Long productId) {
		return productEvaluationRepository.findByProductId(productId);
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
	@Transactional(readOnly = true)
	public WeightSnapshot buildWeightSnapshot(Long evaluationModeId) {
		return getEvaluationMode(evaluationModeId).map(mode -> {
			List<EvaluationFactor> factors = evaluationFactorRepository
					.findByEvaluationModeIdOrderBySortOrderAsc(mode.getId());

			WeightSnapshot snapshot = new WeightSnapshot();
			snapshot.setModeCode(mode.getModeCode());
			snapshot.setModeName(mode.getModeName());
			snapshot.setVersion(mode.getVersion());
			snapshot.setFactors(factors.stream().map(this::toWeightFactorSnapshot).toList());

			// 演算法參數一併存進快照。只存權重不存參數，事後仍然無法重現當時的
			// 計算——這些數字都放在 system_settings 且刻意設計成可調，而可調就
			// 代表會被調。用今天的參數重算半年前的分數必然對不上。
			snapshot.setShrinkageKCategory(algorithmSettings.getShrinkageKCategory());
			snapshot.setShrinkageKProduct(algorithmSettings.getShrinkageKProduct());
			snapshot.setTrendHalfLifeDays(algorithmSettings.getTrendHalfLifeDays());
			return snapshot;
		}).orElse(null);
	}

	private WeightFactorSnapshot toWeightFactorSnapshot(EvaluationFactor factor) {
		WeightFactorSnapshot dto = new WeightFactorSnapshot();
		dto.setFactorCode(factor.getFactorCode());
		dto.setFactorName(factor.getFactorName());
		dto.setCategory(factor.getCategory());
		dto.setWeight(factor.getWeight());
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
	 * - 僅比對campaign_status IN (PREPARING, ACTIVE)的檔期
	 * - 命中＝product.campaign_tags與該檔期festive_campaign_tags.tag集合的交集非空
	 * - Match Weight＝命中標籤中最高等級的match_tier權重（核心1.0／一般0.6／弱0.3），
	 *   多標籤取最高等級不加總（見FestiveCampaignTagMatchTier）
	 * - 多節慶重疊：以「該檔期算出的Festival Boost」取最大值(MAX)，不加總
	 * - 未命中任何檔期時回傳null（對應「未命中檔期時不顯示此區塊，Festival Boost=0」）
	 *
	 * 一次批次查出所有候選檔期的標籤明細（findByCampaignIdIn），而非對每個候選檔期
	 * 各自查一次，避免候選檔期數量增加時的N+1查詢。
	 */
	@Transactional(readOnly = true)
	public MatchedCampaignSnapshot buildMatchedCampaignSnapshot(Product product) {
		Set<String> productTags = splitTags(product.getCampaignTags());
		if (productTags.isEmpty()) {
			return null;
		}

		List<FestiveCampaign> candidates = festiveCampaignRepository
				.findByCampaignStatusIn(List.of(FestiveCampaignStatus.PREPARING, FestiveCampaignStatus.ACTIVE));
		if (candidates.isEmpty()) {
			return null;
		}

		List<Long> candidateIds = candidates.stream().map(FestiveCampaign::getId).toList();
		Map<Long, List<FestiveCampaignTag>> tagsByCampaign = festiveCampaignTagRepository
				.findByCampaignIdIn(candidateIds).stream()
				.collect(Collectors.groupingBy(FestiveCampaignTag::getCampaignId));

		FestiveCampaign bestCampaign = null;
		Set<String> bestMatchedTags = Set.of();
		BigDecimal bestMatchWeight = BigDecimal.ZERO;
		BigDecimal bestUrgencyFactor = BigDecimal.ZERO;
		BigDecimal bestBoost = BigDecimal.ZERO;

		for (FestiveCampaign campaign : candidates) {
			List<FestiveCampaignTag> campaignTags = tagsByCampaign.getOrDefault(campaign.getId(), List.of());

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
			BigDecimal urgencyFactor = calculateUrgencyFactor(campaign);
			BigDecimal boost = matchWeight.multiply(urgencyFactor).multiply(BOOST_CAP);

			if (boost.compareTo(bestBoost) > 0) {
				bestBoost = boost;
				bestCampaign = campaign;
				bestMatchedTags = matchedTags;
				bestMatchWeight = matchWeight;
				bestUrgencyFactor = urgencyFactor;
			}
		}

		if (bestCampaign == null) {
			return null;
		}

		MatchedCampaignSnapshot snapshot = new MatchedCampaignSnapshot();
		snapshot.setCampaignId(bestCampaign.getId());
		snapshot.setCampaignName(bestCampaign.getCampaignName());
		snapshot.setMatchedTags(new ArrayList<>(bestMatchedTags));
		snapshot.setMatchWeight(bestMatchWeight);
		snapshot.setUrgencyFactor(bestUrgencyFactor);
		return snapshot;
	}

	/**
	 * Urgency Factor（時間緊迫係數）：
	 * - ACTIVE：固定1.0
	 * - PREPARING＋節日型(FESTIVAL)：線性遞增 1－(剩餘天數/準備天數)
	 * - PREPARING＋季節型(SEASON)：固定0.2（不遞增）
	 * 其餘狀態（UPCOMING／EXPIRED）理論上不會進到這裡（呼叫端已用
	 * findByCampaignStatusIn(PREPARING, ACTIVE)過濾），此處僅防禦性回傳0。
	 */
	private BigDecimal calculateUrgencyFactor(FestiveCampaign campaign) {
		if (campaign.getCampaignStatus() == FestiveCampaignStatus.ACTIVE) {
			return BigDecimal.ONE;
		}
		if (campaign.getCampaignStatus() != FestiveCampaignStatus.PREPARING) {
			return BigDecimal.ZERO;
		}
		if (campaign.getCategory() == FestiveCategory.SEASON) {
			return SEASON_PREPARING_URGENCY_FACTOR;
		}

		long leadDays = campaign.getPreparationLeadDays() != null && campaign.getPreparationLeadDays() > 0
				? campaign.getPreparationLeadDays()
				: 1;
		long remainingDays = Math.max(ChronoUnit.DAYS.between(LocalDate.now(), campaign.getStartDate()), 0);

		BigDecimal ratio = BigDecimal.valueOf(remainingDays).divide(BigDecimal.valueOf(leadDays), 4,
				RoundingMode.HALF_UP);
		BigDecimal factor = BigDecimal.ONE.subtract(ratio);

		if (factor.compareTo(BigDecimal.ZERO) < 0) {
			factor = BigDecimal.ZERO;
		}
		if (factor.compareTo(BigDecimal.ONE) > 0) {
			factor = BigDecimal.ONE;
		}
		return factor.setScale(2, RoundingMode.HALF_UP);
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
		response.setMatchedCampaign(buildMatchedCampaignSnapshot(product));
		getCurrentEvaluation(productId).ifPresent(evaluation -> {
			response.setFestivalBoost(evaluation.getFestivalBoost());
			response.setFinalScore(evaluation.getFinalScore());
		});
		return response;
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
		BigDecimal totalScore = ScoringAlgorithms.weightedAverage(
				FactorCode.ALL.stream()
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
		BigDecimal festivalBoost = campaignSnapshot != null
				? campaignSnapshot.getMatchWeight().multiply(campaignSnapshot.getUrgencyFactor()).multiply(BOOST_CAP)
						.setScale(2, RoundingMode.HALF_UP)
				: BigDecimal.ZERO;

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