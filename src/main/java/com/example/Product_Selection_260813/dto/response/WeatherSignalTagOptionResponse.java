package com.example.Product_Selection_260813.dto.response;

import com.example.Product_Selection_260813.entity.WeatherSignalTagMapping;
import com.example.Product_Selection_260813.enums.WeatherSignalType;

/**
 * GET /api/settings/weather-signal-tags/options 的回應格式。
 *
 * <b>為什麼不重用 WeatherSignalTagMappingResponse：</b>那個 DTO 是給設定頁
 * CRUD 用的（含 id／isSystemDefault 等管理欄位），對應的端點固定
 * {@code @PreAuthorize("hasRole('MANAGER')")}。這支端點是給商品表單「可選
 * 標籤」下拉使用，操作角色也要能打（比照 GET /api/settings/festive-campaigns
 * 不限角色的既有慣例，見 SettingsController 該端點註解），只回傳畫面需要的
 * 三個欄位，不把管理用的 metadata 一併開放給操作角色。
 */
public class WeatherSignalTagOptionResponse {

	private String tag;
	private WeatherSignalType weatherSignalType;
	private String weatherSignalTypeLabel;

	public static WeatherSignalTagOptionResponse from(WeatherSignalTagMapping mapping) {
		WeatherSignalTagOptionResponse dto = new WeatherSignalTagOptionResponse();
		dto.tag = mapping.getTag();
		dto.weatherSignalType = mapping.getWeatherSignalType();
		dto.weatherSignalTypeLabel = mapping.getWeatherSignalType().getLabel();
		return dto;
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public WeatherSignalType getWeatherSignalType() {
		return weatherSignalType;
	}

	public void setWeatherSignalType(WeatherSignalType weatherSignalType) {
		this.weatherSignalType = weatherSignalType;
	}

	public String getWeatherSignalTypeLabel() {
		return weatherSignalTypeLabel;
	}

	public void setWeatherSignalTypeLabel(String weatherSignalTypeLabel) {
		this.weatherSignalTypeLabel = weatherSignalTypeLabel;
	}
}
