package com.example.Product_Selection_260813.enums;

/**
 * 檔期回應裡 campaignStatus 的來源（V21，D10），供前端說明狀態是怎麼來的。
 *
 * <ul>
 * <li>AUTO：節慶／季節型依本期起訖日與準備天數即時推算。</li>
 * <li>MANUAL：節慶／季節型的手動覆蓋，只對「當期」有效，進入下一期自動失效。</li>
 * <li>SYNC：天氣型，狀態由 WeatherCampaignSyncService 同步寫入（或其手動覆蓋）。</li>
 * </ul>
 */
public enum CampaignStatusSource {
	AUTO,
	MANUAL,
	SYNC
}
