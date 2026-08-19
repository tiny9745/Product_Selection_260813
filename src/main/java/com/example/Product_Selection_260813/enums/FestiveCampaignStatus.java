package com.example.Product_Selection_260813.enums;

public enum FestiveCampaignStatus {
	UPCOMING("即將推出"),//
	PREPARING("準備中"),//
	ACTIVE("進行中"),//
	EXPIRED("已截止");
	
	private final String festiveCampaignStatus;

	private FestiveCampaignStatus(String festiveCampaignStatus) {
		this.festiveCampaignStatus = festiveCampaignStatus;
	}

	public String getFestiveCampaignStatus() {
		return festiveCampaignStatus;
	}
		
}
