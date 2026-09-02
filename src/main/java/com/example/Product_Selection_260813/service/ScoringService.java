package com.example.Product_Selection_260813.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

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
import com.example.Product_Selection_260813.repository.SystemSettingRepository;
import com.example.Product_Selection_260813.repository.TrendSignalRepository;

/**
 * 對應企劃書十二-13分層決議：本類別目前僅實作「快照組裝所需的讀取」與
 * 「Festival Boost可解釋性明細計算」，範圍對應GET /api/reviews/{productId}與
 * POST /api/reviews當下所需的資料，以及POST /api/products/{id}/trend/sync
 * 觸發後對product_evaluations.trend_score的局部更新（見updateTrendScoreFromLatestSignal()）。
 * <b>不包含</b>六大分項Base Score的完整評分重算引擎
 * （六大分項如何依商品商業條件／核心客群／歷史銷售等原始資料算出business_score等分數，
 * 屬於另一項獨立任務，商品評估結果目前假設由其他流程寫入product_evaluations表，
 * 本類別對該表僅做唯讀）。
 *
 * 之後若要補上ScoringController／完整評分重算邏輯，直接在本類別擴充方法即可，
 * ReviewService已經是透過本類別取得資料、不直接注入Repository，符合分層決議，
 * 屆時不需要更動ReviewService的呼叫方式。
 */
@Service
public class ScoringService {

	// Boost Cap：企劃書「節慶加成計分規則」明訂為「暫訂+5，絕對分數」，
	// 之後的歷史資料回測校準屬於Phase 2待辦（見十三），此處先照文件明訂值寫死。
	private static final BigDecimal BOOST_CAP = new BigDecimal("5");

	// 季節型檔期PREPARING期間固定係數（企劃書節慶加成計分規則明訂，非本類別臆測）。
	private static final BigDecimal SEASON_PREPARING_URGENCY_FACTOR = new BigDecimal("0.20");

	// Match Weight三層數值已改由FestiveCampaignTagMatchTier enum攜帶（見該類別），
	// 這裡不再需要暫定的DEFAULT_MATCH_WEIGHT——festive_campaign_tags表補上分級資料後，
	// 缺口已解決，見buildMatchedCampaignSnapshot()。

	// ⚠️ MVP暫定值（2026-08-31 demo前緊急補上，見calculateEvaluation()類別內註解）：
	// 資料完整度未達此門檻（百分制）時，不計算正式分數，僅寫入completeness本身。
	// 門檻數值本身企劃書QA3已明訂為60，非本次暫定。
	private static final BigDecimal DATA_COMPLETENESS_THRESHOLD = new BigDecimal("60");

	// 六大分項評分公式全部是MVP暫定版，非企劃書定案內容（企劃書本身未定義這層公式，
	// 見類別註解與calculateEvaluation()方法註解）。demo後補齊完整規格時，
	// 這一區的常數與calculate*Score()系列方法是預期會被整批置換的範圍。
	private static final BigDecimal NEUTRAL_SCORE = new BigDecimal("60");
	private static final BigDecimal SCALE_0_5_TO_100 = new BigDecimal("20");

	@Autowired
	private ProductEvaluationRepository productEvaluationRepository;

	@Autowired
	private EvaluationModeRepository evaluationModeRepository;

	@Autowired
	private EvaluationFactorRepository evaluationFactorRepository;

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

	// ============================================================
	// 評分重算引擎（MVP暫定版，2026-08-31 demo前緊急補上）
	// ============================================================
	//
	// ⚠️ 重要聲明：以下calculateEvaluation()及其呼叫的calculate*Score()系列方法，
	// 是為了8/29(五)第一次demo「趕出來」的MVP版本，公式本身是暫定值，
	// 不是企劃書定案內容——企劃書本身從未定義六大分項如何從products表原始欄位
	// 算出business_score／audience_score／historical_score／purchase_score
	// （見本類別最上方類別註解的說明）。之後有正式決議的公式時，
	// 只需要置換這一區塊的calculate*Score()方法內容，calculateEvaluation()
	// 的整體骨架（完整度檢查→六大分項→加權→節慶加成→寫入）不需要大改。
	//
	// 已知限制（demo後優先補齊）：
	// 1. 沒有任何單元測試覆蓋這裡的計算邏輯
	// 2. HISTORY分數固定回傳60分（企劃書五章沒有歷史銷售資料表，模擬階段本來就
	//    沒有真實資料可用，不是這次漏做）
	// 3. AUDIENCE分數用最簡單的關鍵字字串比對，不是NLP語意比對
	// 4. 目前只支援「單一啟用中客群」，若audience_profiles未來真的允許多筆
	//    is_active=true同時存在，這裡只會取第一筆，需要另外決議規則

	/**
	 * 商品完整重算入口：完整度檢查 → 六大分項 → 依目前生效模式加權 → 節慶加成 →
	 * 寫入product_evaluations（新增或更新既有列）。
	 *
	 * 呼叫時機（十二-13分層決議：ProductService／TrendService單向依賴本類別）：
	 * - ProductService新增／編輯商品成功後
	 * - TrendService.syncTrend()同步完趨勢資料後（取代原本只更新trend_score的
	 *   updateTrendScoreFromLatestSignal()，該方法暫時保留但不再是主要呼叫路徑，
	 *   避免刪除後如果還有其他呼叫端沒改到會直接編譯失敗）
	 *
	 * 資料完整度未達60%門檻（企劃書QA3）時，只寫入data_completeness本身，
	 * 六大分項與total/final分數維持null，不讓不完整商品的分數看起來「已經算完」。
	 */
	@Transactional
	public void calculateEvaluation(Long productId) {
		Product product = findProductOrThrow(productId);

		BigDecimal completeness = calculateDataCompleteness(product);

		ProductEvaluation evaluation = productEvaluationRepository.findByProductId(productId)
				.orElseGet(() -> {
					ProductEvaluation newEvaluation = new ProductEvaluation();
					newEvaluation.setProductId(productId);
					return newEvaluation;
				});
		evaluation.setDataCompleteness(completeness);

		if (completeness.compareTo(DATA_COMPLETENESS_THRESHOLD) < 0) {
			// 未達門檻：只記錄完整度，不計算/不覆蓋正式分數，避免「資料不足卻有分數」
			// 誤導使用者。若這是一筆全新商品的第一次寫入，其餘分數欄位維持null即可
			// （ProductEvaluation.calculatedAt是NOT NULL，仍必須賦值，見該欄位註解）。
			evaluation.setCalculatedAt(LocalDateTime.now());
			productEvaluationRepository.save(evaluation);
			return;
		}

		Long evaluationModeId = resolveCurrentEvaluationModeId();
		Map<String, BigDecimal> weightByCategory = getWeightsByCategory(evaluationModeId);

		BigDecimal businessScore = calculateBusinessScore(product);
		BigDecimal audienceScore = calculateAudienceScore(product);
		BigDecimal historicalScore = calculateHistoricalScore(product);
		BigDecimal purchaseScore = calculatePurchaseScore(product);
		BigDecimal trendScore = resolveLatestTrendScore(productId);
		BigDecimal forecastScore = purchaseScore.add(trendScore).divide(BigDecimal.valueOf(2), 2,
				RoundingMode.HALF_UP);

		BigDecimal totalScore = weighted(businessScore, weightByCategory.get("BUSINESS"))
				.add(weighted(audienceScore, weightByCategory.get("AUDIENCE")))
				.add(weighted(historicalScore, weightByCategory.get("HISTORY")))
				.add(weighted(forecastScore, weightByCategory.get("FORECAST")))
				.setScale(2, RoundingMode.HALF_UP);

		MatchedCampaignSnapshot campaignSnapshot = buildMatchedCampaignSnapshot(product);
		BigDecimal festivalBoost = BigDecimal.ZERO;
		Long matchedCampaignId = null;
		if (campaignSnapshot != null) {
			festivalBoost = campaignSnapshot.getMatchWeight().multiply(campaignSnapshot.getUrgencyFactor())
					.multiply(BOOST_CAP).setScale(2, RoundingMode.HALF_UP);
			matchedCampaignId = campaignSnapshot.getCampaignId();
		}
		BigDecimal finalScore = totalScore.add(festivalBoost).setScale(2, RoundingMode.HALF_UP);

		evaluation.setEvaluationModeId(evaluationModeId);
		evaluation.setBusinessScore(businessScore);
		evaluation.setAudienceScore(audienceScore);
		evaluation.setHistoricalScore(historicalScore);
		evaluation.setPurchaseScore(purchaseScore);
		evaluation.setTrendScore(trendScore);
		evaluation.setForecastScore(forecastScore);
		evaluation.setTotalScore(totalScore);
		evaluation.setFestivalBoost(festivalBoost);
		evaluation.setMatchedCampaignId(matchedCampaignId);
		evaluation.setFinalScore(finalScore);
		evaluation.setCalculatedAt(LocalDateTime.now());

		productEvaluationRepository.save(evaluation);
	}

	private BigDecimal weighted(BigDecimal score, BigDecimal weightPercent) {
		if (score == null || weightPercent == null) {
			return BigDecimal.ZERO;
		}
		// weight是百分制（例如25代表25%），除以100換算成係數再乘上分數。
		return score.multiply(weightPercent).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
	}

	/**
	 * 資料完整度（QA3，百分制0-100）：MVP暫定版本，只檢查「評分引擎實際會用到」的
	 * 欄位，依pricing_type動態調整分母——NEW商品此階段允許cost_price/sale_price
	 * 空白（PENDING_PRICING狀態，QA1明訂），不計入分母，避免新品被不合理地卡在
	 * 60%門檻外。
	 */
	private BigDecimal calculateDataCompleteness(Product product) {
		int total = 0;
		int filled = 0;

		total++;
		if (product.getDescription() != null && !product.getDescription().isBlank()) {
			filled++;
		}
		total++;
		if (product.getMoq() != null) {
			filled++;
		}
		total++;
		if (product.getSupplyStability() != null) {
			filled++;
		}
		total++;
		if (product.getPriceCompetitiveness() != null) {
			filled++;
		}
		total++;
		if (product.getTargetCustomerDescription() != null && !product.getTargetCustomerDescription().isBlank()) {
			filled++;
		}
		total++;
		if (product.getEstimatedPurchaseRate() != null) {
			filled++;
		}

		if (product.getPricingType() == ProductPricingType.RESALE) {
			total++;
			if (product.getCostPrice() != null) {
				filled++;
			}
			total++;
			if (product.getSalePrice() != null) {
				filled++;
			}
		}

		return BigDecimal.valueOf(filled).multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
	}

	/**
	 * BUSINESS分項：毛利率(40%) + 供應穩定性(30%) + 價格競爭力(30%)。
	 * NEW商品（無成本/售價，PENDING_PRICING）毛利率一律給中性值NEUTRAL_SCORE，
	 * 不因欄位空白被扣分（QA1精神：不強迫新品當下判定毛利率）。
	 * moq目前刻意不計入這條公式——MOQ對商業條件是正向還是負向、該用什麼權重，
	 * 企劃書未定義，暫不臆測，避免引入一個沒有依據的假設。
	 */
	private BigDecimal calculateBusinessScore(Product product) {
		BigDecimal marginScore;
		if (product.getPricingType() == ProductPricingType.RESALE && product.getCostPrice() != null
				&& product.getSalePrice() != null && product.getSalePrice().compareTo(BigDecimal.ZERO) > 0) {
			BigDecimal margin = product.getSalePrice().subtract(product.getCostPrice())
					.divide(product.getSalePrice(), 4, RoundingMode.HALF_UP)
					.multiply(BigDecimal.valueOf(100));
			marginScore = clamp0to100(margin);
		} else {
			marginScore = NEUTRAL_SCORE;
		}

		BigDecimal supplyStabilityScore = scaleZeroToFive(product.getSupplyStability());
		BigDecimal priceCompetitivenessScore = scaleZeroToFive(product.getPriceCompetitiveness());

		return marginScore.multiply(new BigDecimal("0.4"))
				.add(supplyStabilityScore.multiply(new BigDecimal("0.3")))
				.add(priceCompetitivenessScore.multiply(new BigDecimal("0.3")))
				.setScale(2, RoundingMode.HALF_UP);
	}

	/**
	 * AUDIENCE分項：MVP暫定用最簡單的關鍵字字串比對（非NLP語意分析，見本區塊
	 * 最上方聲明），比對target_customer_description是否包含audience_profiles.
	 * keywords（逗號分隔）當中的字詞，算命中率×100。查無啟用中客群設定或
	 * 商品未填目標客群描述時，回傳中性值。
	 */
	private BigDecimal calculateAudienceScore(Product product) {
		if (product.getTargetCustomerDescription() == null || product.getTargetCustomerDescription().isBlank()) {
			return NEUTRAL_SCORE;
		}
		List<AudienceProfile> activeProfiles = audienceProfileRepository.findByIsActiveTrue();
		if (activeProfiles.isEmpty()) {
			return NEUTRAL_SCORE;
		}
		AudienceProfile profile = activeProfiles.get(0);
		if (profile.getKeywords() == null || profile.getKeywords().isBlank()) {
			return NEUTRAL_SCORE;
		}

		String description = product.getTargetCustomerDescription().toLowerCase();
		String[] keywords = profile.getKeywords().split(",");
		int total = 0;
		int hit = 0;
		for (String keyword : keywords) {
			String trimmed = keyword.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			total++;
			if (description.contains(trimmed.toLowerCase())) {
				hit++;
			}
		}
		if (total == 0) {
			return NEUTRAL_SCORE;
		}
		return BigDecimal.valueOf(hit).multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
	}

	/**
	 * HISTORY分項：固定回傳中性值，畫面須明確標註「歷史資料為模擬／尚未串接」。
	 * 企劃書五章沒有任何歷史銷售資料表，六週雛型階段本來就沒有真實資料可用，
	 * 這不是demo前來不及做，而是這個階段本來就沒有資料來源（見本區塊最上方聲明）。
	 */
	private BigDecimal calculateHistoricalScore(Product product) {
		return NEUTRAL_SCORE;
	}

	/** PURCHASE分項：estimated_purchase_rate（0-1機率值）直接換算成百分制。 */
	private BigDecimal calculatePurchaseScore(Product product) {
		if (product.getEstimatedPurchaseRate() == null) {
			return NEUTRAL_SCORE;
		}
		return clamp0to100(product.getEstimatedPurchaseRate().multiply(BigDecimal.valueOf(100)));
	}

	/** 沿用TrendService既有的模擬趨勢資料，取最新一筆trend_score／popularity_score平均值。 */
	private BigDecimal resolveLatestTrendScore(Long productId) {
		return trendSignalRepository.findFirstByProductIdOrderByCollectedAtDesc(productId)
				.map(this::calculatePlaceholderTrendScore)
				.orElse(NEUTRAL_SCORE);
	}

	/** 0-5分主觀評分欄位（supply_stability／price_competitiveness）換算成0-100百分制。 */
	private BigDecimal scaleZeroToFive(BigDecimal value) {
		if (value == null) {
			return NEUTRAL_SCORE;
		}
		return clamp0to100(value.multiply(SCALE_0_5_TO_100));
	}

	private BigDecimal clamp0to100(BigDecimal value) {
		BigDecimal result = value.setScale(2, RoundingMode.HALF_UP);
		if (result.compareTo(BigDecimal.ZERO) < 0) {
			return BigDecimal.ZERO;
		}
		if (result.compareTo(BigDecimal.valueOf(100)) > 0) {
			return BigDecimal.valueOf(100);
		}
		return result;
	}

	/**
	 * 取得system_settings.current_evaluation_mode_id指向的模式ID
	 * （用法見SystemSettingRepository類別註解的範例）。查無設定時直接丟例外，
	 * 不臆測預設模式——這件事應該在demo前的seed資料階段就先設好，
	 * 若還沒設好，寧可讓建立商品時明確報錯，也不要讓商品用一個猜測的模式算分。
	 */
	private Long resolveCurrentEvaluationModeId() {
		SystemSetting setting = systemSettingRepository.findById("current_evaluation_mode_id")
				.orElseThrow(() -> new IllegalStateException("尚未設定目前生效的評估模式(current_evaluation_mode_id)，"
						+ "請先透過PUT /api/settings/evaluation-mode/current設定，或直接寫入system_settings做demo前準備"));
		return Long.valueOf(setting.getSettingValue());
	}

	/** 該評估模式底下，四大類別(BUSINESS/AUDIENCE/HISTORY/FORECAST)各自的權重(百分制)。 */
	private Map<String, BigDecimal> getWeightsByCategory(Long evaluationModeId) {
		return evaluationFactorRepository.findByEvaluationModeIdOrderBySortOrderAsc(evaluationModeId).stream()
				.collect(Collectors.toMap(EvaluationFactor::getCategory, EvaluationFactor::getWeight,
						BigDecimal::add));
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
