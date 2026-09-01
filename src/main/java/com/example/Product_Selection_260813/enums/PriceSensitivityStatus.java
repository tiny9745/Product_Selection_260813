package com.example.Product_Selection_260813.enums;

public enum PriceSensitivityStatus {
	LOW("低－更重視品質"),//
	MEDIUM("中－價格與品質平衡"),//
	HIGH("高－優先考量折扣");
	
	private final String priceSensitivityStatus;

	private PriceSensitivityStatus(String priceSensitivityStatus) {
		this.priceSensitivityStatus = priceSensitivityStatus;
	}

	public String getPriceSensitivityStatus() {
		return priceSensitivityStatus;
	}

}
