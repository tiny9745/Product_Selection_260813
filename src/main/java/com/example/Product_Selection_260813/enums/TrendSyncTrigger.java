package com.example.Product_Selection_260813.enums;

/** PTT 熱度全商品同步的觸發來源（trend_sync_runs.trigger_type）。 */
public enum TrendSyncTrigger {
	SCHEDULED("每日排程"), //
	MANUAL("手動觸發");

	private final String trendSyncTrigger;

	private TrendSyncTrigger(String trendSyncTrigger) {
		this.trendSyncTrigger = trendSyncTrigger;
	}

	public String getTrendSyncTrigger() {
		return trendSyncTrigger;
	}
}
