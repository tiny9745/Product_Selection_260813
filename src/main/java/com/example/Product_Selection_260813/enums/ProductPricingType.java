package com.example.Product_Selection_260813.enums;

public enum ProductPricingType {
	NEW("新品"), //
	RESALE("再販售");
	
	private final String productPricingType;

	private ProductPricingType(String productPricingType) {
		this.productPricingType = productPricingType;
	}

	public String getProductPricingType() {
		return productPricingType;
	}
}
