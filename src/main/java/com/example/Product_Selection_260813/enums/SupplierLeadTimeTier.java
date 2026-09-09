package com.example.Product_Selection_260813.enums;

/**
 * 供應商備貨前置期級距。
 *
 * maxDays 是該級距的上界（最壞情況）。GATE_LEAD_TIME 一律用上界與「距檔期天數」
 * 比較——備貨要多久是風險來源，取上界才是保守判斷。
 * D15_PLUS 沒有上界，以 null 表示，Gate 視為一定來不及（除非距檔期天數也無限）。
 */
public enum SupplierLeadTimeTier {
	D3("3天內", 3), //
	D4_7("4-7天", 7), //
	D8_14("8-14天", 14), //
	D15_PLUS("15天以上", null);

	private final String label;
	private final Integer maxDays;

	private SupplierLeadTimeTier(String label, Integer maxDays) {
		this.label = label;
		this.maxDays = maxDays;
	}

	public String getLabel() {
		return label;
	}

	/** 該級距的最長天數（最壞情況）；D15_PLUS 回傳 null 代表無上界。 */
	public Integer getMaxDays() {
		return maxDays;
	}
}
