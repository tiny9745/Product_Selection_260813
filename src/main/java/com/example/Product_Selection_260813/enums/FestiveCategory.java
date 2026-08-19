package com.example.Product_Selection_260813.enums;

public enum FestiveCategory {
	FESTIVAL("節慶型"),//
	SEASON("季節型");

	private final String festiveCategory;

	private FestiveCategory(String festiveCategory) {
		this.festiveCategory = festiveCategory;
	}
	
	public String getFestiveCategory() {
		return festiveCategory;
	}
}
