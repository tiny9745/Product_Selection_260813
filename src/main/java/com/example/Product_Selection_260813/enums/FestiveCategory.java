package com.example.Product_Selection_260813.enums;

public enum FestiveCategory {
	FESTIVAL("節慶型"),//
	SEASON("季節型"),//
	// 2026-09-16新增：天氣型檔期，由 WeatherCampaignSyncService 自動 upsert，
	// 不是主管手動建立（is_manual_override=true 的天氣檔期例外，見該服務）。
	// 沿用「季節其實是 festive_campaigns 的一種 category」這個既有慣例，
	// 不另開資料表——見 ScoringService.calculateUrgencyFactor() 的 WEATHER 分支。
	WEATHER("天氣型");

	private final String festiveCategory;

	private FestiveCategory(String festiveCategory) {
		this.festiveCategory = festiveCategory;
	}
	
	public String getFestiveCategory() {
		return festiveCategory;
	}
}
