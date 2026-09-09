package com.example.Product_Selection_260813.service.gate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.algorithm.ScoringAlgorithms;
import com.example.Product_Selection_260813.entity.FestiveCampaign;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.enums.ShelfLifeTier;
import com.example.Product_Selection_260813.enums.SupplierLeadTimeTier;
import com.example.Product_Selection_260813.enums.TemperatureZone;
import com.example.Product_Selection_260813.json.MatchedCampaignSnapshot;
import com.example.Product_Selection_260813.repository.FestiveCampaignRepository;
import com.example.Product_Selection_260813.repository.GroupBuyRecordRepository;
import com.example.Product_Selection_260813.service.resolver.AlgorithmSettings;
import com.example.Product_Selection_260813.service.resolver.MoqResolver;
import com.example.Product_Selection_260813.service.resolver.ProductTypeAttributeResolver;
import com.example.Product_Selection_260813.service.resolver.ResolvedProductTypeAttributes;
import com.example.Product_Selection_260813.service.resolver.ResolvedValue;

/**
 * Gate 判定：五項非補償性篩選。
 *
 * <b>為什麼要有 Gate 這一層</b>：加權總和有個先天弱點——完全可補償，某項極差
 * 可以被其他項的高分蓋過去。一個冷凍商品因為毛利率高、折扣深，總分衝到 88 分
 * 排在第一，主管審核時才發現配送根本做不到，分數把不可行的選項排到了最前面。
 * Gate 在加權之前先做二元篩選，解決這個問題。
 *
 * <b>採軟擋而非硬擋</b>：Gate 不通過仍可送審，只是審核端會強制顯示、主管可
 * 例外放行並留下紀錄。硬擋會逼人繞過系統（把溫層改成常溫再送），資料變髒
 * 而且完全沒有紀錄，比放行更糟。
 *
 * <b>每個 Gate 是獨立的私有方法</b>，統一由 {@link #evaluate} 彙總。這樣可以
 * 逐一單元測試，之後增減 Gate 也不影響其他判斷。
 */
@Service
public class GateEvaluationService {

	// Gate 代碼。同時對應 risk_options.auto_trigger_code，
	// 讓審核端知道某個 Gate 不通過時要預先勾選哪一項風險選項。
	public static final String GATE_DATA_COMPLETENESS = "GATE_DATA_COMPLETENESS";
	public static final String GATE_TEMPERATURE_ZONE = "GATE_TEMPERATURE_ZONE";
	public static final String GATE_SHELF_LIFE = "GATE_SHELF_LIFE";
	public static final String GATE_LEAD_TIME = "GATE_LEAD_TIME";
	public static final String GATE_MOQ_FEASIBILITY = "GATE_MOQ_FEASIBILITY";

	// 五大風險面向
	private static final String RISK_DATA = "DATA";
	private static final String RISK_SUPPLY = "SUPPLY";
	private static final String RISK_QUALITY = "QUALITY";

	/** 資料完整度門檻，沿用既有的 60%。 */
	private static final BigDecimal DATA_COMPLETENESS_THRESHOLD = BigDecimal.valueOf(60);

	private final ProductTypeAttributeResolver attributeResolver;
	private final MoqResolver moqResolver;
	private final AlgorithmSettings algorithmSettings;
	private final GroupBuyRecordRepository groupBuyRecordRepository;
	private final FestiveCampaignRepository festiveCampaignRepository;

	@Autowired
	public GateEvaluationService(ProductTypeAttributeResolver attributeResolver,
			MoqResolver moqResolver,
			AlgorithmSettings algorithmSettings,
			GroupBuyRecordRepository groupBuyRecordRepository,
			FestiveCampaignRepository festiveCampaignRepository) {
		this.attributeResolver = attributeResolver;
		this.moqResolver = moqResolver;
		this.algorithmSettings = algorithmSettings;
		this.groupBuyRecordRepository = groupBuyRecordRepository;
		this.festiveCampaignRepository = festiveCampaignRepository;
	}

	/**
	 * 執行全部五項判定。
	 *
	 * @param dataCompleteness 資料完整度（0~100）。由呼叫端傳入而非在這裡重算，
	 *                         避免同一件事在 ScoringService 與這裡各算一次而可能不一致
	 * @param matchedCampaign  商品命中的節慶檔期；null 代表沒有對到任何檔期。
	 *                         由呼叫端傳入而非注入 ScoringService，避免兩個 Service
	 *                         互相依賴形成循環
	 */
	@Transactional(readOnly = true)
	public GateResult.Summary evaluate(Product product, BigDecimal dataCompleteness,
			MatchedCampaignSnapshot matchedCampaign) {
		ResolvedProductTypeAttributes attrs = attributeResolver.resolve(product.getProductTypeId());

		List<GateResult> results = new ArrayList<>();
		// 排序依重要性：零庫存預購下 MOQ 可行性最關鍵（集不到量是整團作廢，
		// 而且已經跟客人收過訂單），備貨前置期次之。
		results.add(evaluateMoqFeasibility(product));
		results.add(evaluateLeadTime(product, matchedCampaign));
		results.add(evaluateShelfLife(product, attrs));
		results.add(evaluateTemperatureZone(product, attrs));
		results.add(evaluateDataCompleteness(dataCompleteness));
		return GateResult.Summary.of(results);
	}

	// =====================================================================
	// GATE_MOQ_FEASIBILITY
	// =====================================================================

	/**
	 * MOQ 可行性：解析後 MOQ 是否低於該品類歷史集單量的分位數基準。
	 *
	 * <pre>
	 * 基準量 = 該品類成團案例（result = FULFILLED）的 actual_quantity 分位數
	 * 判定：解析後 MOQ ≤ 基準量 × 安全係數 → 通過
	 * </pre>
	 *
	 * <b>為什麼用分位數而非歷史最大值</b>：最大值往往是唯一一次爆紅的離群值，
	 * 用它當基準等於假設每次都能複製那次奇蹟。以 [40,55,60,75,80,90,110,120,150,300]
	 * 為例，最大值 300 只達成過一次，用它判定會讓 MOQ 250 的商品通過，
	 * 但實際上只有十分之一機率達標。P75（117.5）才是務實的基準。
	 *
	 * 只取 FULFILLED：未成團的集單量代表「沒達到的量」，拿它當可達成基準會低估能力。
	 */
	private GateResult evaluateMoqFeasibility(Product product) {
		ResolvedValue<Integer> resolvedMoq = moqResolver.resolve(product);
		if (!resolvedMoq.hasValue()) {
			return GateResult.insufficientData(GATE_MOQ_FEASIBILITY, RISK_SUPPLY,
					"商品、品類與全域皆未設定最低訂購量，無法評估集單可行性");
		}
		if (product.getProductTypeId() == null) {
			return GateResult.insufficientData(GATE_MOQ_FEASIBILITY, RISK_SUPPLY,
					"商品未指定品類，無法取得歷史集單量基準");
		}

		List<Integer> quantities = groupBuyRecordRepository
				.findFulfilledQuantitiesByProductType(product.getProductTypeId());
		int minSample = algorithmSettings.getMoqMinSampleSize();
		if (quantities.size() < minSample) {
			// 樣本不足時分位數不穩定（3 筆資料算 P75 沒有意義）。
			// 回傳資料不足而非不通過——不知道就說不知道，不猜。
			return GateResult.insufficientData(GATE_MOQ_FEASIBILITY, RISK_SUPPLY,
					String.format("該品類成團案例僅 %d 筆（需 %d 筆以上），樣本不足無法判斷集單可行性",
							quantities.size(), minSample));
		}

		int percentile = algorithmSettings.getMoqBenchmarkPercentile();
		BigDecimal benchmark = ScoringAlgorithms.percentile(quantities, percentile);
		BigDecimal threshold = benchmark.multiply(algorithmSettings.getMoqSafetyFactor());
		BigDecimal moq = BigDecimal.valueOf(resolvedMoq.value());

		if (moq.compareTo(threshold) <= 0) {
			return GateResult.passed(GATE_MOQ_FEASIBILITY, RISK_SUPPLY,
					String.format("最低訂購量 %d，低於該品類 P%d 集單基準 %s，集單可行性合理",
							resolvedMoq.value(), percentile, benchmark.stripTrailingZeros().toPlainString()));
		}
		return GateResult.failed(GATE_MOQ_FEASIBILITY, RISK_SUPPLY,
				String.format("最低訂購量 %d 高於該品類 P%d 集單基準 %s（共 %d 筆成團案例），"
						+ "歷史上鮮少達到此量，集單失敗風險高",
						resolvedMoq.value(), percentile,
						benchmark.stripTrailingZeros().toPlainString(), quantities.size()));
	}

	// =====================================================================
	// GATE_LEAD_TIME
	// =====================================================================

	/**
	 * 備貨前置期是否來得及趕上檔期。
	 *
	 * 零庫存預購下這一項僅次於 MOQ 可行性：先收單再進貨，從收單到出貨的時間
	 * 全壓在供應商前置期上，備貨慢就是消費者等太久。
	 *
	 * <b>取級距上界（最壞情況）</b>：「8-14 天」取 14 天。備貨要多久是風險來源，
	 * 樂觀估計會讓來不及的商品通過。
	 *
	 * <b>沒對到檔期回傳 NOT_APPLICABLE 而非 INSUFFICIENT_DATA</b>：沒有檔期就
	 * 沒有備貨期限，這個檢查對這件商品本來就不適用，不是缺資料。若標成資料不足，
	 * 多數商品都會落在這一格，會把「資料不足」的數量灌水到主管開始忽略它。
	 */
	private GateResult evaluateLeadTime(Product product, MatchedCampaignSnapshot matchedCampaign) {
		if (matchedCampaign == null || matchedCampaign.getCampaignId() == null) {
			return GateResult.notApplicable(GATE_LEAD_TIME, RISK_SUPPLY,
					"商品未對應任何節慶檔期，無備貨期限壓力");
		}

		String rawTier = product.getSupplierLeadTimeTier();
		if (rawTier == null || rawTier.isBlank()) {
			return GateResult.insufficientData(GATE_LEAD_TIME, RISK_SUPPLY,
					"未填寫供應商備貨前置期，無法判斷是否來得及趕上檔期");
		}
		SupplierLeadTimeTier tier = parseEnum(SupplierLeadTimeTier.class, rawTier);
		if (tier == null) {
			return GateResult.insufficientData(GATE_LEAD_TIME, RISK_SUPPLY,
					"供應商備貨前置期的值無法辨識：" + rawTier);
		}

		Optional<FestiveCampaign> campaignOpt = festiveCampaignRepository.findById(matchedCampaign.getCampaignId());
		if (campaignOpt.isEmpty() || campaignOpt.get().getStartDate() == null) {
			return GateResult.insufficientData(GATE_LEAD_TIME, RISK_SUPPLY,
					"命中的檔期查無起始日期，無法計算剩餘備貨天數");
		}

		LocalDate startDate = campaignOpt.get().getStartDate();
		long daysUntilStart = ChronoUnit.DAYS.between(LocalDate.now(), startDate);
		String campaignName = matchedCampaign.getCampaignName() != null
				? matchedCampaign.getCampaignName() : "檔期";

		if (daysUntilStart < 0) {
			return GateResult.notApplicable(GATE_LEAD_TIME, RISK_SUPPLY,
					String.format("「%s」已開始，備貨期限判斷不再適用", campaignName));
		}

		Integer maxDays = tier.getMaxDays();
		if (maxDays == null) {
			// D15_PLUS 沒有上界。除非距檔期還很久，否則無法保證來得及——
			// 但也不能斷定不行（可能只要 16 天而距檔期還有 60 天）。
			// 用一個保守的下限 15 天來判斷。
			if (daysUntilStart >= 15) {
				return GateResult.passed(GATE_LEAD_TIME, RISK_SUPPLY, String.format(
						"距「%s」開始尚有 %d 天，前置期 %s 可能來得及，建議向供應商確認確切天數",
						campaignName, daysUntilStart, tier.getLabel()));
			}
			return GateResult.failed(GATE_LEAD_TIME, RISK_SUPPLY, String.format(
					"距「%s」開始僅剩 %d 天，供應商前置期為 %s，備貨來不及",
					campaignName, daysUntilStart, tier.getLabel()));
		}

		if (maxDays <= daysUntilStart) {
			return GateResult.passed(GATE_LEAD_TIME, RISK_SUPPLY, String.format(
					"距「%s」開始尚有 %d 天，供應商前置期最長 %d 天，備貨來得及",
					campaignName, daysUntilStart, maxDays));
		}
		return GateResult.failed(GATE_LEAD_TIME, RISK_SUPPLY, String.format(
				"距「%s」開始僅剩 %d 天，供應商前置期最長 %d 天，備貨來不及",
				campaignName, daysUntilStart, maxDays));
	}

	// =====================================================================
	// GATE_SHELF_LIFE
	// =====================================================================

	/**
	 * 效期是否足以撐過整個團購流程（開團收單 + 備貨 + 配送 + 消費者食用緩衝）。
	 *
	 * <b>取級距下界（最壞情況）</b>：「8-30 天」取 8 天與門檻比較。
	 *
	 * 這樣做的理由是兩種錯誤的代價不對稱：誤擋（其實可以但被標示不通過）只需要
	 * 主管看一眼點放行，成本是幾秒鐘；誤放（其實不行但被放行）是商品到消費者
	 * 手上快過期，而團購幾乎不能退換，代價是客訴加團主信任受損。既然 Gate 本來
	 * 就是軟擋、主管隨時能放行，保守判斷幾乎沒有代價。
	 *
	 * <b>已知限制與後續修正方向</b>：級距的切點應該對齊門檻天數。若門檻常落在
	 * 10 天左右，「8-30 天」這個級距橫跨門檻兩側就是切得不好——級距內有些過關
	 * 有些不過關，取哪個值都不對。等各品類的門檻天數定案後，應回頭調整
	 * {@link ShelfLifeTier} 的切點，讓每個級距完整落在門檻的一側。這比在演算法
	 * 裡糾結取下界還是中位數有效得多。目前門檻未定，先不動切點以免改兩次。
	 */
	private GateResult evaluateShelfLife(Product product, ResolvedProductTypeAttributes attrs) {
		// 品類明確標示無效期概念（文具、五金）→ 不適用，不是缺資料
		if (Boolean.FALSE.equals(attrs.hasShelfLife().value())) {
			return GateResult.notApplicable(GATE_SHELF_LIFE, RISK_QUALITY,
					"此品類商品無效期概念，不需判斷效期充足性");
		}

		String rawTier = product.getShelfLifeTier();
		if (rawTier == null || rawTier.isBlank()) {
			rawTier = attrs.shelfLifeTier().value();
		}
		if (rawTier == null || rawTier.isBlank()) {
			return GateResult.insufficientData(GATE_SHELF_LIFE, RISK_QUALITY,
					"未填寫效期級距，且品類無預設值，無法判斷效期是否充足");
		}
		ShelfLifeTier tier = parseEnum(ShelfLifeTier.class, rawTier);
		if (tier == null) {
			return GateResult.insufficientData(GATE_SHELF_LIFE, RISK_QUALITY,
					"效期級距的值無法辨識：" + rawTier);
		}
		if (tier == ShelfLifeTier.NA) {
			return GateResult.notApplicable(GATE_SHELF_LIFE, RISK_QUALITY,
					"此商品標示為無效期概念，不需判斷效期充足性");
		}

		int threshold = attrs.shelfLifeThresholdDays().hasValue()
				? attrs.shelfLifeThresholdDays().value()
				: algorithmSettings.getGlobalShelfLifeThresholdDays();

		Integer minDays = tier.getMinDays();
		if (minDays == null) {
			return GateResult.insufficientData(GATE_SHELF_LIFE, RISK_QUALITY,
					"效期級距無法換算為天數，無法判斷");
		}

		if (minDays >= threshold) {
			return GateResult.passed(GATE_SHELF_LIFE, RISK_QUALITY, String.format(
					"效期 %s（最短 %d 天）達到品類門檻 %d 天",
					tier.getLabel(), minDays, threshold));
		}
		return GateResult.failed(GATE_SHELF_LIFE, RISK_QUALITY, String.format(
				"效期 %s（最短 %d 天）低於品類門檻 %d 天，商品送達消費者時可能所剩無幾，"
						+ "而團購幾乎無法退換",
				tier.getLabel(), minDays, threshold));
	}

	// =====================================================================
	// GATE_TEMPERATURE_ZONE
	// =====================================================================

	/**
	 * 商品溫層是否在通路支援範圍內。
	 *
	 * <b>⚠️ 這個 Gate 現階段不會擋掉任何商品。</b>
	 * 通路目前支援常溫、冷藏、冷凍三種溫層（system_settings 的
	 * supported_temperature_zones 預設為三種全開），因此判定結果永遠是 PASSED
	 * 或 INSUFFICIENT_DATA。
	 *
	 * 保留它不是失誤——冷鏈條件可能變動（冷鏈合約到期、某段期間冷凍配送暫停），
	 * 屆時只要改一個設定值就能立即生效，保留成本只是一個設定 key。
	 * 這段註解存在的目的，是避免之後接手的人看到一個永遠 PASS 的 Gate
	 * 而以為它壞了、把它刪掉。
	 *
	 * 註：雞蛋等易碎品不是溫層問題，是處理特性，由 products.handling_flags 的
	 * FRAGILE 標記處理，不會走到這個 Gate。
	 */
	private GateResult evaluateTemperatureZone(Product product, ResolvedProductTypeAttributes attrs) {
		String rawZone = product.getTemperatureZone();
		if (rawZone == null || rawZone.isBlank()) {
			rawZone = attrs.temperatureZone().value();
		}
		if (rawZone == null || rawZone.isBlank()) {
			return GateResult.insufficientData(GATE_TEMPERATURE_ZONE, RISK_SUPPLY,
					"未填寫溫層，且品類無預設值，無法判斷通路是否支援配送");
		}
		TemperatureZone zone = parseEnum(TemperatureZone.class, rawZone);
		if (zone == null) {
			return GateResult.insufficientData(GATE_TEMPERATURE_ZONE, RISK_SUPPLY,
					"溫層的值無法辨識：" + rawZone);
		}

		List<String> supported = algorithmSettings.getSupportedTemperatureZones();
		if (supported.isEmpty()) {
			return GateResult.insufficientData(GATE_TEMPERATURE_ZONE, RISK_SUPPLY,
					"系統尚未設定通路支援的溫層，無法判斷");
		}

		if (supported.contains(zone.name())) {
			return GateResult.passed(GATE_TEMPERATURE_ZONE, RISK_SUPPLY,
					String.format("商品為%s品，通路支援此溫層配送", zone.getLabel()));
		}
		return GateResult.failed(GATE_TEMPERATURE_ZONE, RISK_SUPPLY,
				String.format("商品為%s品，通路目前不支援此溫層配送", zone.getLabel()));
	}

	// =====================================================================
	// GATE_DATA_COMPLETENESS
	// =====================================================================

	/**
	 * 資料完整度門檻。沿用既有的 60% 規則，只是把它從「不計算分數」的隱性行為
	 * 提升為明確的 Gate 判定，讓主管在審核畫面直接看到。
	 */
	private GateResult evaluateDataCompleteness(BigDecimal dataCompleteness) {
		if (dataCompleteness == null) {
			return GateResult.insufficientData(GATE_DATA_COMPLETENESS, RISK_DATA,
					"尚未計算資料完整度");
		}
		if (dataCompleteness.compareTo(DATA_COMPLETENESS_THRESHOLD) >= 0) {
			return GateResult.passed(GATE_DATA_COMPLETENESS, RISK_DATA,
					String.format("資料完整度 %s%%，達到 %s%% 門檻",
							dataCompleteness.stripTrailingZeros().toPlainString(),
							DATA_COMPLETENESS_THRESHOLD.stripTrailingZeros().toPlainString()));
		}
		return GateResult.failed(GATE_DATA_COMPLETENESS, RISK_DATA,
				String.format("資料完整度僅 %s%%，未達 %s%% 門檻，評分結果參考價值有限",
						dataCompleteness.stripTrailingZeros().toPlainString(),
						DATA_COMPLETENESS_THRESHOLD.stripTrailingZeros().toPlainString()));
	}

	// =====================================================================
	// 共用
	// =====================================================================

	/**
	 * 寬鬆解析 enum。
	 *
	 * 資料庫裡有不認得的值（手動改過資料、或 enum 改版後未同步）時回傳 null
	 * 讓呼叫端標示為資料不足，而不是拋例外——一筆髒資料不該讓整個 Gate 判定中斷。
	 */
	private <E extends Enum<E>> E parseEnum(Class<E> type, String raw) {
		try {
			return Enum.valueOf(type, raw.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
