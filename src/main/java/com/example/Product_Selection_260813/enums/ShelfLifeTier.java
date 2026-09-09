package com.example.Product_Selection_260813.enums;

/**
 * 效期級距。採級距化勾選而非精確天數：Gate 是二元判斷，只需要知道「是不是 30 天以內」，
 * 要求採購填「23 天」是白白拉高資料成本，而沒人填的精確欄位價值是零。
 *
 * minDays 是該級距的下界（最壞情況）。GATE_SHELF_LIFE 一律用下界與門檻天數比較，
 * 因為級距內的實際效期可能落在任何位置，取下界才是保守判斷。
 * NA（不適用）代表此商品無效期概念，minDays 以 null 表示，Gate 視為通過。
 */
public enum ShelfLifeTier {
	D7("7天內", 0), //
	D8_30("8-30天", 8), //
	D31_90("31-90天", 31), //
	D90_PLUS("90天以上", 90), //
	NA("不適用", null);

	private final String label;
	private final Integer minDays;

	private ShelfLifeTier(String label, Integer minDays) {
		this.label = label;
		this.minDays = minDays;
	}

	public String getLabel() {
		return label;
	}

	/** 該級距的最短天數（最壞情況）；NA 回傳 null 代表無效期概念。 */
	public Integer getMinDays() {
		return minDays;
	}
}
