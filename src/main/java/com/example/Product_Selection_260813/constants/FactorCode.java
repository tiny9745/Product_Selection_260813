package com.example.Product_Selection_260813.constants;

import java.util.List;

/**
 * 計分因子代碼。對應 evaluation_factors.factor_code。
 *
 * <b>權重採扁平結構</b>：七個因子各自在 evaluation_factors 有一筆自己的權重，
 * 七項加總為 100。不做「象限權重 × 象限內比例」的兩層結構——兩層之下，主管
 * 要算「把毛利率從 20 調到 30，總分會怎麼變」得先做 20% × 40% = 8% 的換算，
 * 很難直覺掌握，調權重會變成盲目試誤。
 *
 * <b>evaluation_factors.category 保留但不參與計算</b>：它現在的用途純粹是
 * 畫面上的分組標題（把七個因子歸到商業條件／客群匹配／歷史銷售／預測人氣
 * 四組顯示），方便閱讀。任何計分邏輯都不應該再讀這個欄位。
 */
public final class FactorCode {

	/** 毛利率（售價扣成本再扣運費估算後，依大類目標區間正規化）。 */
	public static final String MARGIN_RATE = "MARGIN_RATE";

	/** 折扣深度（團購價相對市價的折讓幅度）。 */
	public static final String DISCOUNT_DEPTH = "DISCOUNT_DEPTH";

	/** 供應穩定性（人工量級評估 1~5，×20 正規化）。 */
	public static final String SUPPLY_STABILITY = "SUPPLY_STABILITY";

	/** 核心客群匹配度（關鍵字命中率）。 */
	public static final String AUDIENCE_MATCH = "AUDIENCE_MATCH";

	/** 歷史成團率（巢狀貝氏收縮）。 */
	public static final String HISTORY_FULFILLMENT = "HISTORY_FULFILLMENT";

	/** 預估購買率（人工預估值 ×100）。 */
	public static final String PURCHASE_RATE = "PURCHASE_RATE";

	/** 市場趨勢熱度（指數衰減後）。 */
	public static final String TREND_HEAT = "TREND_HEAT";

	/** 全部七個因子，供權重加總驗證與設定頁列舉使用。 */
	public static final List<String> ALL = List.of(
			MARGIN_RATE, DISCOUNT_DEPTH, SUPPLY_STABILITY,
			AUDIENCE_MATCH, HISTORY_FULFILLMENT, PURCHASE_RATE, TREND_HEAT);

	// ---- 以下為畫面分組用的 category 值，不參與計分 ----

	public static final String GROUP_BUSINESS = "BUSINESS";
	public static final String GROUP_AUDIENCE = "AUDIENCE";
	public static final String GROUP_HISTORY = "HISTORY";
	public static final String GROUP_FORECAST = "FORECAST";

	/** 哪些因子屬於商業條件分組（供 product_evaluations.business_score 的展示彙總使用）。 */
	public static final List<String> BUSINESS_GROUP = List.of(MARGIN_RATE, DISCOUNT_DEPTH, SUPPLY_STABILITY);

	/** 哪些因子屬於預測人氣分組（供 product_evaluations.forecast_score 的展示彙總使用）。 */
	public static final List<String> FORECAST_GROUP = List.of(PURCHASE_RATE, TREND_HEAT);

	private FactorCode() {
	}
}
