package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * GET/PUT /api/settings/weather/boost-settings 的回應（V26）。
 * historyDays／forecastDays 為唯讀的計算窗口天數（application 設定），供畫面說明用。
 */
public record WeatherBoostSettingsResponse(BigDecimal historyWeightPercentage, BigDecimal forecastWeightPercentage,
		BigDecimal boostCap, int historyDays, int forecastDays, LocalDateTime updatedAt) {
}
