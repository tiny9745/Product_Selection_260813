package com.example.Product_Selection_260813.enums;

public enum ProductPricingStatus {
	PENDING_PRICING("待定價"), //
	PRICED("定價");

	private final String productPricingStatus;

	private ProductPricingStatus(String productPricingStatus) {
		this.productPricingStatus = productPricingStatus;
	}

	public String getProductPricingStatus() {
		return productPricingStatus;
	}

}
