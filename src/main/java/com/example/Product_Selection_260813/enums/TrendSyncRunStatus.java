package com.example.Product_Selection_260813.enums;

/** PTT 熱度全商品同步的執行狀態（trend_sync_runs.status）。 */
public enum TrendSyncRunStatus {
	RUNNING("執行中"), //
	COMPLETED("已完成"), //
	FAILED("中斷"), //
	SKIPPED("已略過");

	private final String trendSyncRunStatus;

	private TrendSyncRunStatus(String trendSyncRunStatus) {
		this.trendSyncRunStatus = trendSyncRunStatus;
	}

	public String getTrendSyncRunStatus() {
		return trendSyncRunStatus;
	}
}
