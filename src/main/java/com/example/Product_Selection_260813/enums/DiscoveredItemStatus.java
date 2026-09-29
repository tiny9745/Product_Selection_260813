package com.example.Product_Selection_260813.enums;

/**
 * PTT 新品探索結果的處理狀態（V33 discovered_items.status）。
 *
 * 與商品的 review／item／candidate 三種狀態完全無關：探索結果不是商品，
 * 只是「值得人工看一眼的線索」，轉成商品後由商品自己的狀態接手。
 */
public enum DiscoveredItemStatus {
	/** 待處理：操作人員還沒決定要不要建立商品。 */
	NEW("待處理"),
	/** 已略過：之後再被 PTT 提及也維持略過，不會重新冒出來。 */
	DISMISSED("已略過"),
	/** 已建立商品：converted_product_id 指向該商品。 */
	CONVERTED("已建立商品");

	private final String label;

	DiscoveredItemStatus(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
