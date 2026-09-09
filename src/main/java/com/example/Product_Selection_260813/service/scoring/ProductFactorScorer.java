package com.example.Product_Selection_260813.service.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.algorithm.ScoringAlgorithms;
import com.example.Product_Selection_260813.constants.FactorCode;
import com.example.Product_Selection_260813.entity.AudienceProfile;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductTypeScoreBand;
import com.example.Product_Selection_260813.entity.TrendSignal;
import com.example.Product_Selection_260813.enums.PackageSizeTier;
import com.example.Product_Selection_260813.repository.AudienceProfileRepository;
import com.example.Product_Selection_260813.repository.TrendSignalRepository;
import com.example.Product_Selection_260813.service.resolver.AlgorithmSettings;
import com.example.Product_Selection_260813.service.resolver.ScoreBandResolver;

/**
 * 逐一計算七個扁平因子的分數（0~100）。
 *
 * 這個類別只負責「算出每個因子幾分」，不負責加權、不負責寫入資料庫——
 * 加權由 {@link ScoringAlgorithms#weightedAverage} 處理，寫入由 ScoringService 負責。
 * 拆開的理由是這一層有最多的商業規則，獨立出來才能單獨測試。
 *
 * <b>缺漏值一律回傳 null，不給中性值 50</b>：給中性值會讓「資料填齊但條件普通」
 * 和「什麼都沒填」拿到一樣的分數。回傳 null 讓加權階段把該因子從分母排除並
 * 重新正規化，「沒資料」這件事則由 GATE_DATA_COMPLETENESS 與 Signal 缺漏清單呈現。
 */
@Component
public class ProductFactorScorer {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private final AudienceProfileRepository audienceProfileRepository;
	private final TrendSignalRepository trendSignalRepository;
	private final ScoreBandResolver scoreBandResolver;
	private final AlgorithmSettings algorithmSettings;
	private final HistoricalScoreCalculator historicalScoreCalculator;

	@Autowired
	public ProductFactorScorer(AudienceProfileRepository audienceProfileRepository,
			TrendSignalRepository trendSignalRepository,
			ScoreBandResolver scoreBandResolver,
			AlgorithmSettings algorithmSettings,
			HistoricalScoreCalculator historicalScoreCalculator) {
		this.audienceProfileRepository = audienceProfileRepository;
		this.trendSignalRepository = trendSignalRepository;
		this.scoreBandResolver = scoreBandResolver;
		this.algorithmSettings = algorithmSettings;
		this.historicalScoreCalculator = historicalScoreCalculator;
	}

	/**
	 * 一次算出七個因子的分數。
	 *
	 * @return factorCode → 分數（0~100）；值為 null 代表該因子無資料，加權時應排除
	 */
	public Map<String, BigDecimal> scoreAll(Product product) {
		Map<String, BigDecimal> scores = new LinkedHashMap<>();
		scores.put(FactorCode.MARGIN_RATE, scoreMarginRate(product));
		scores.put(FactorCode.DISCOUNT_DEPTH, scoreDiscountDepth(product));
		scores.put(FactorCode.SUPPLY_STABILITY, scoreSupplyStability(product));
		scores.put(FactorCode.AUDIENCE_MATCH, scoreAudienceMatch(product));
		scores.put(FactorCode.HISTORY_FULFILLMENT, historicalScoreCalculator.calculate(product).score());
		scores.put(FactorCode.PURCHASE_RATE, scorePurchaseRate(product));
		scores.put(FactorCode.TREND_HEAT, scoreTrendHeat(product));
		return scores;
	}

	// =====================================================================
	// 商業條件
	// =====================================================================

	/**
	 * 毛利率（已扣運費），依大類的固定目標區間正規化。
	 *
	 * <pre>
	 * 運費估算   = 依 package_size_tier 查 system_settings 的 freight_cost_*
	 * 調整後毛利 = (售價 − 成本價) − 運費估算
	 * 毛利率     = 調整後毛利 / 售價
	 * </pre>
	 *
	 * <b>為什麼扣運費</b>：大型商品（洗衣機、衣櫃、大型收納）的運費會實質吃掉毛利。
	 * 這件事不做成 Gate 而是讓它誠實反映在分數上——「不划算」是經濟性問題不是
	 * 可行性問題，做成 Gate 會誤擋那些偶爾真的划算的案子。一台 8,000 元的洗衣機
	 * 如果運費 800 元，毛利率會被壓下去、排序自然往後；但如果毛利夠厚，它仍然
	 * 可以贏過小件商品。
	 *
	 * <b>為什麼用固定目標區間而非直接乘 100</b>：毛利率 30% 在生鮮是好成績、在
	 * 文具只是普通。直接乘 100 當分數會讓系統偏袒高毛利品類，排序被品類主導
	 * 而非商品本身的優劣。
	 *
	 * NEW 商品尚未訂價時回傳 null（從分母排除），不再給中性分 50——待訂價是
	 * 「還沒有資料」，不是「條件普通」。
	 */
	public BigDecimal scoreMarginRate(Product product) {
		BigDecimal cost = product.getCostPrice();
		BigDecimal sale = product.getSalePrice();
		if (cost == null || sale == null || sale.compareTo(BigDecimal.ZERO) <= 0) {
			return null;
		}

		BigDecimal freight = estimateFreightCost(product);
		BigDecimal netMargin = sale.subtract(cost).subtract(freight);
		BigDecimal marginRate = netMargin.divide(sale, 6, RoundingMode.HALF_UP);

		return normalizeWithBand(product, ScoreBandResolver.FACTOR_MARGIN_RATE, marginRate);
	}

	/**
	 * 折扣深度：團購價相對市價的折讓幅度。
	 *
	 * market_price 只有 RESALE 商品會填（NEW 商品填了會被 ProductService 拒絕），
	 * 所以 NEW 商品在這個因子一律回傳 null 從分母排除，這是預期行為而非缺陷。
	 */
	public BigDecimal scoreDiscountDepth(Product product) {
		BigDecimal market = product.getMarketPrice();
		BigDecimal sale = product.getSalePrice();
		if (market == null || sale == null || market.compareTo(BigDecimal.ZERO) <= 0) {
			return null;
		}

		BigDecimal discountRate = market.subtract(sale).divide(market, 6, RoundingMode.HALF_UP);
		return normalizeWithBand(product, ScoreBandResolver.FACTOR_DISCOUNT_DEPTH, discountRate);
	}

	/**
	 * 供應穩定性：人工量級評估 1~5，×20 映射到 20~100。
	 *
	 * <b>主觀因子刻意保留</b>。它承載了系統拿不到的資訊——業務知道這家供應商
	 * 上個月出貨延遲。全換成客觀因子等於把採購的專業判斷排除在評分之外，
	 * 違背「AI 輔助、人做決定」的定位。
	 *
	 * 前端顯示為「暫時缺貨／供應量短少／供應量普通／供應穩定／供應充足」，
	 * 但送出與儲存的仍是 1~5 的數值，後端邏輯不受影響。
	 */
	public BigDecimal scoreSupplyStability(Product product) {
		BigDecimal raw = product.getSupplyStability();
		if (raw == null) {
			return null;
		}
		return ScoringAlgorithms.clamp(raw.multiply(BigDecimal.valueOf(20)), BigDecimal.ZERO, HUNDRED);
	}

	// =====================================================================
	// 客群匹配
	// =====================================================================

	/**
	 * 客群關鍵字命中率。
	 *
	 * 沿用既有邏輯：把客群設定的 keywords 逐一與「目標客群描述 + 商品名稱」
	 * 做 contains 比對，命中比例 ×100。
	 *
	 * 已知限制（維持現狀未改）：contains 對中文沒有詞界，關鍵字「缺貨」會命中
	 * 「不缺貨」。改成完整詞比對需要斷詞，成本與效益要另行評估。
	 *
	 * 沒有生效客群設定或 keywords 為空時回傳 null——這是系統設定不完整，
	 * 應該從分母排除，而不是讓所有商品在這一項都拿中性分。
	 */
	public BigDecimal scoreAudienceMatch(Product product) {
		List<AudienceProfile> profiles = audienceProfileRepository.findByIsActiveTrue();
		if (profiles.isEmpty()) {
			return null;
		}
		// 可能有多筆 is_active 的客群設定，沿用既有做法先取第一筆；
		// 多客群比對策略仍待團隊決議，此處不自行擴充。
		AudienceProfile profile = profiles.get(0);
		if (profile.getKeywords() == null || profile.getKeywords().isBlank()) {
			return null;
		}

		List<String> keywords = Arrays.stream(profile.getKeywords().split("[,、\\s]+"))
				.map(String::trim)
				.map(String::toLowerCase)
				.filter(k -> !k.isEmpty())
				.distinct()
				.toList();
		if (keywords.isEmpty()) {
			return null;
		}

		String productText = ((product.getTargetCustomerDescription() != null
				? product.getTargetCustomerDescription()
				: "") + " " + (product.getName() != null ? product.getName() : "")).toLowerCase();

		long matched = keywords.stream().filter(productText::contains).count();
		return BigDecimal.valueOf(matched)
				.divide(BigDecimal.valueOf(keywords.size()), 6, RoundingMode.HALF_UP)
				.multiply(HUNDRED);
	}

	// =====================================================================
	// 預測人氣
	// =====================================================================

	/** 預估購買率：人工填的 0~1 比率 ×100。無資料回傳 null 從分母排除。 */
	public BigDecimal scorePurchaseRate(Product product) {
		BigDecimal rate = product.getEstimatedPurchaseRate();
		if (rate == null) {
			return null;
		}
		return ScoringAlgorithms.clamp(rate.multiply(HUNDRED), BigDecimal.ZERO, HUNDRED);
	}

	/**
	 * 市場趨勢熱度，依資料距今天數做指數衰減。
	 *
	 * <pre>
	 * freshness = 0.5 ^ (距今天數 / 半衰期)
	 * 調整後    = freshness × 原始分 + (1 − freshness) × 中性基準分
	 * </pre>
	 *
	 * 用指數而非線性衰減：線性（1 − 天數/30）在第 30 天剛好歸零，是人為斷崖，
	 * 而且 30 這個數字沒有依據。指數衰減永遠不會真的歸零，而是平滑收斂到中性值。
	 *
	 * 沒有衰減的話，三個月前同步的一筆熱度會與今天的資料同等影響排序——
	 * 而熱度本來就是時效性最強的訊號。
	 */
	public BigDecimal scoreTrendHeat(Product product) {
		if (product.getId() == null) {
			return null;
		}
		Optional<TrendSignal> latest = trendSignalRepository
				.findFirstByProductIdOrderByCollectedAtDesc(product.getId());
		if (latest.isEmpty()) {
			return null;
		}

		TrendSignal signal = latest.get();
		BigDecimal rawScore = averageOfTrendAndPopularity(signal);
		if (rawScore == null) {
			return null;
		}

		LocalDateTime collectedAt = signal.getCollectedAt();
		if (collectedAt == null) {
			// 沒有採集時間就無從判斷時效，保守起見不套用衰減，直接用原始分
			return rawScore;
		}
		long daysAgo = ChronoUnit.DAYS.between(collectedAt, LocalDateTime.now());
		return ScoringAlgorithms.applyFreshnessDecay(rawScore, daysAgo,
				algorithmSettings.getTrendHalfLifeDays(), algorithmSettings.getNeutralBaselineScore());
	}

	/** 趨勢分與熱門度分取平均；兩者皆無值回傳 null。 */
	private BigDecimal averageOfTrendAndPopularity(TrendSignal signal) {
		List<BigDecimal> present = new ArrayList<>();
		if (signal.getTrendScore() != null) {
			present.add(signal.getTrendScore());
		}
		if (signal.getPopularityScore() != null) {
			present.add(signal.getPopularityScore());
		}
		if (present.isEmpty()) {
			return null;
		}
		BigDecimal sum = present.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		return sum.divide(BigDecimal.valueOf(present.size()), 6, RoundingMode.HALF_UP);
	}

	// =====================================================================
	// 共用
	// =====================================================================

	/**
	 * 依材積級距估算每件運費。
	 *
	 * 未填材積級距時視為 0——不能猜一個運費，那會讓沒填資料的商品分數
	 * 被一個編出來的成本影響。運費為 0 等同於「暫不考慮運費」，
	 * 而材積未填這件事由 Signal 的缺漏清單呈現。
	 */
	public BigDecimal estimateFreightCost(Product product) {
		String tier = product.getPackageSizeTier();
		if (tier == null || tier.isBlank()) {
			return BigDecimal.ZERO;
		}
		try {
			PackageSizeTier sizeTier = PackageSizeTier.valueOf(tier.trim().toUpperCase());
			return algorithmSettings.getFreightCost(sizeTier.getFreightSettingKey());
		} catch (IllegalArgumentException e) {
			// 資料庫裡有不認得的級距值（手動改過資料、或 enum 改版後未同步）。
			// 視為未填而非拋例外——一筆髒資料不該讓整條計分鏈路中斷。
			return BigDecimal.ZERO;
		}
	}

	/**
	 * 用固定目標區間正規化。查不到區間時回傳 null 從分母排除，
	 * 不自行編一組區間——編出來的區間會讓分數看起來正常但實際無依據。
	 */
	private BigDecimal normalizeWithBand(Product product, String factorCode, BigDecimal rawValue) {
		Optional<ProductTypeScoreBand> band = scoreBandResolver.resolve(product.getProductTypeId(), factorCode);
		if (band.isEmpty()) {
			return null;
		}
		return ScoringAlgorithms.normalizeByBand(rawValue, band.get().getLowerBound(), band.get().getUpperBound());
	}
}
