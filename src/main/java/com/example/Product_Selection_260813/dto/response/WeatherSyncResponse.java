package com.example.Product_Selection_260813.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * POST /api/settings/weather/sync 的回應（V26 改版：天氣只同步資料，不再產生檔期）。
 *
 * @param syncedRegions    本次成功寫入的區域代碼
 * @param failedRegions    所有代表城市都取得失敗而跳過的區域（既有資料保留）
 * @param coldStartRegions 本次以冷啟動方式補齊過去 30 天的區域
 * @param upsertedDays     寫入（新增或更新）的「區域×日期」列數
 * @param syncedAt         本次同步時間
 */
public record WeatherSyncResponse(List<String> syncedRegions, List<String> failedRegions,
		List<String> coldStartRegions, int upsertedDays, LocalDateTime syncedAt) {
}
