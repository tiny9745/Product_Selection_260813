package com.example.Product_Selection_260813.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/settings/weather/status：天氣資料涵蓋狀態（V26）。
 *
 * @param historyDays            歷史窗口天數（設定值，預設 30）
 * @param forecastDays           預報天數（設定值，預設 14）
 * @param coldStartThresholdDays 歷史天數少於此值時，下次同步會補齊完整歷史
 * @param lastFetchedAt          四區中最近一次取得資料的時間；尚無資料時為 null
 */
public record WeatherDataStatusResponse(int historyDays, int forecastDays, int coldStartThresholdDays,
		LocalDateTime lastFetchedAt, List<RegionStatus> regions) {

	/**
	 * @param historyDayCount  歷史窗口內已有資料的天數
	 * @param forecastDayCount 今天起已有預報的天數
	 */
	public record RegionStatus(String region, String regionLabel, int historyDayCount, int forecastDayCount,
			LocalDateTime lastFetchedAt) {
	}
}
