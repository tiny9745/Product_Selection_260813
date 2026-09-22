package com.example.Product_Selection_260813.dto.response;

/**
 * POST /api/settings/weather/sync 的回應內容——把
 * WeatherCampaignSyncService.syncWeatherCampaigns() 這次執行的結果數字化，
 * 讓呼叫端（目前是WeatherController，之後如果要在Angular後台顯示「上次
 * 同步結果」也是同一組數字）不用另外再查一次festive_campaigns才知道
 * 這次做了什麼。
 */
public class WeatherSyncResponse {

	/** WeatherSignalProvider這次回報的訊號總數（分類未對到商品標籤的類型不算，見同步邏輯的filter）。 */
	private int totalSignalCount;
	/** 實際被upsert（新增或更新）成festive_campaigns的筆數。 */
	private int syncedCampaignCount;
	/** 這次同步中，因為訊號消失而被標記為EXPIRED的既有天氣檔期數。 */
	private int expiredCampaignCount;

	public WeatherSyncResponse() {
	}

	public WeatherSyncResponse(int totalSignalCount, int syncedCampaignCount, int expiredCampaignCount) {
		this.totalSignalCount = totalSignalCount;
		this.syncedCampaignCount = syncedCampaignCount;
		this.expiredCampaignCount = expiredCampaignCount;
	}

	public int getTotalSignalCount() {
		return totalSignalCount;
	}

	public void setTotalSignalCount(int totalSignalCount) {
		this.totalSignalCount = totalSignalCount;
	}

	public int getSyncedCampaignCount() {
		return syncedCampaignCount;
	}

	public void setSyncedCampaignCount(int syncedCampaignCount) {
		this.syncedCampaignCount = syncedCampaignCount;
	}

	public int getExpiredCampaignCount() {
		return expiredCampaignCount;
	}

	public void setExpiredCampaignCount(int expiredCampaignCount) {
		this.expiredCampaignCount = expiredCampaignCount;
	}
}
