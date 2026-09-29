package com.example.Product_Selection_260813.enums;

/**
 * PTT 新品探索的略過原因（V34 discovered_items.dismiss_reason_code）。
 *
 * 原因不只是紀錄，還會回饋到下一次探索（見 DiscoveryService）：
 * NOT_A_PRODUCT 當作 AI 抽取階段的反例；其餘原因當作適配評分階段的反例。
 */
public enum DiscoveryDismissReason {
	NOT_A_PRODUCT("不是實體商品"),
	NOT_FOR_GROUP_BUY("不適合團購"),
	OUT_OF_SCOPE("超出經營品類"),
	SIMILAR_EXISTS("已有類似商品"),
	OTHER("其他");

	private final String label;

	DiscoveryDismissReason(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
