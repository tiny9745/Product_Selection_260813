package com.example.Product_Selection_260813.enums;

public enum FestiveCategory {
	FESTIVAL("節慶型"),//
	// 2026-09-25（V26）：天氣型檔期（WEATHER）移除。檔期只有節慶與季節；天氣改為依每日
	// 天氣數據直接計算的獨立加成（見 WeatherBoostService），不再經過檔期機制。
	SEASON("季節型");

	private final String festiveCategory;

	private FestiveCategory(String festiveCategory) {
		this.festiveCategory = festiveCategory;
	}
	
	public String getFestiveCategory() {
		return festiveCategory;
	}
}
