package com.example.Product_Selection_260813.dto.weather;

import java.time.LocalDate;

import com.example.Product_Selection_260813.enums.WeatherForecastConfidence;
import com.example.Product_Selection_260813.enums.WeatherSignalType;

/**
 * 正規化後的天氣需求訊號，是 WeatherSignalProvider 的輸出、
 * WeatherCampaignSyncService 的輸入——兩邊都只依賴這個 DTO，
 * 不直接依賴任何外部氣象 API 的原始格式（規劃文件第23節：
 * External API → Client → DTO → Normalizer → Internal Model 分層）。
 *
 * 這裡刻意不用 Lombok（沿用本專案其他 DTO 手寫 getter/setter 的既有風格，
 * 比對 FestiveCampaignTagInput／DashboardStatisticsResponse 等既有類別）。
 */
public class WeatherSignal {

	/** 區域代碼，例如 "SOUTH"／"NORTH"；聚合多個代表城市後的結果，不是單一城市。 */
	private String region;

	private WeatherSignalType type;

	/** 這個訊號預期發生的時間窗口起訖日（含）。 */
	private LocalDate windowStart;
	private LocalDate windowEnd;

	private WeatherForecastConfidence confidence;

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public WeatherSignalType getType() {
		return type;
	}

	public void setType(WeatherSignalType type) {
		this.type = type;
	}

	public LocalDate getWindowStart() {
		return windowStart;
	}

	public void setWindowStart(LocalDate windowStart) {
		this.windowStart = windowStart;
	}

	public LocalDate getWindowEnd() {
		return windowEnd;
	}

	public void setWindowEnd(LocalDate windowEnd) {
		this.windowEnd = windowEnd;
	}

	public WeatherForecastConfidence getConfidence() {
		return confidence;
	}

	public void setConfidence(WeatherForecastConfidence confidence) {
		this.confidence = confidence;
	}
}
