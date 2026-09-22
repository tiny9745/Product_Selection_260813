package com.example.Product_Selection_260813.dto.request;

import com.example.Product_Selection_260813.constants.ValidationMessage;
import com.example.Product_Selection_260813.enums.FestiveCampaignTagMatchTier;
import com.example.Product_Selection_260813.enums.WeatherSignalType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/settings/weather-signal-tags 的 Request Body：新增一筆「天氣訊號
 * 類型 → 商品標籤」對照。
 *
 * weatherSignalType 不接受 NORMAL——一般天氣不該命中任何商品（見
 * WeatherCampaignSyncService 既有註解），SettingsService.
 * validateWeatherSignalTagMapping() 會拒絕，不在這裡用更窄的型別擋（原因見
 * WeatherSignalTagMapping 類別註解）。
 *
 * isSystemDefault／isActive 不開放外部指定，比照 RiskOptionCreateRequest
 * 既有原則：新增的一律為自訂項目、預設啟用。
 */
public class WeatherSignalTagMappingCreateRequest {

	@NotNull(message = "天氣訊號類型不可為空")
	private WeatherSignalType weatherSignalType;

	@NotBlank(message = "標籤內容不可為空")
	@Size(max = 50, message = ValidationMessage.WEATHER_SIGNAL_TAG_TOO_LONG)
	private String tag;

	@NotNull(message = "標籤命中權重層級不可為空")
	private FestiveCampaignTagMatchTier matchTier;

	public WeatherSignalType getWeatherSignalType() {
		return weatherSignalType;
	}

	public void setWeatherSignalType(WeatherSignalType weatherSignalType) {
		this.weatherSignalType = weatherSignalType;
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public FestiveCampaignTagMatchTier getMatchTier() {
		return matchTier;
	}

	public void setMatchTier(FestiveCampaignTagMatchTier matchTier) {
		this.matchTier = matchTier;
	}
}
