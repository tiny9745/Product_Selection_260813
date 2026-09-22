package com.example.Product_Selection_260813.dto.response;

import com.example.Product_Selection_260813.entity.WeatherSignalTagMapping;
import com.example.Product_Selection_260813.enums.FestiveCampaignTagMatchTier;
import com.example.Product_Selection_260813.enums.WeatherSignalType;

/** GET／POST／PUT／disable／enable /api/settings/weather-signal-tags 共用的回應格式。 */
public class WeatherSignalTagMappingResponse {

	private Long id;
	private WeatherSignalType weatherSignalType;
	private String tag;
	private FestiveCampaignTagMatchTier matchTier;
	private Boolean isActive;
	private Boolean isSystemDefault;

	public static WeatherSignalTagMappingResponse from(WeatherSignalTagMapping mapping) {
		WeatherSignalTagMappingResponse dto = new WeatherSignalTagMappingResponse();
		dto.id = mapping.getId();
		dto.weatherSignalType = mapping.getWeatherSignalType();
		dto.tag = mapping.getTag();
		dto.matchTier = mapping.getMatchTier();
		dto.isActive = mapping.getIsActive();
		dto.isSystemDefault = mapping.getIsSystemDefault();
		return dto;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

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

	public Boolean getIsActive() {
		return isActive;
	}

	public void setIsActive(Boolean isActive) {
		this.isActive = isActive;
	}

	public Boolean getIsSystemDefault() {
		return isSystemDefault;
	}

	public void setIsSystemDefault(Boolean isSystemDefault) {
		this.isSystemDefault = isSystemDefault;
	}
}
