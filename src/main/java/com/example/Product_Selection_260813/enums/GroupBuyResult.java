package com.example.Product_Selection_260813.enums;

/**
 * 開團結果。
 *
 * 成團率的分母只計 FULFILLED + FAILED，CANCELLED 不計入——取消開團多半是
 * 營運面的決定（供應商臨時出不了貨、檔期調整），不代表客群不買單，
 * 把它算進分母會低估該品類的真實成團能力。
 */
public enum GroupBuyResult {
	FULFILLED("成團"), //
	FAILED("未成團"), //
	CANCELLED("取消開團");

	private final String label;

	private GroupBuyResult(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
