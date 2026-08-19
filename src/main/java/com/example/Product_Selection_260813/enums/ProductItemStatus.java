package com.example.Product_Selection_260813.enums;

public enum ProductItemStatus {
	ACTIVE("進行中"),//
	ARCHIVED("已封存");
	
	private final String productItemStatus;

	private ProductItemStatus(String productItemStatus) {
		this.productItemStatus = productItemStatus;
	}

	public String getProductItemStatus() {
		return productItemStatus;
	}
}
