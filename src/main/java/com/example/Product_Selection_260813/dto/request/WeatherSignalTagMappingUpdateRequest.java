package com.example.Product_Selection_260813.dto.request;

import com.example.Product_Selection_260813.enums.FestiveCampaignTagMatchTier;

import jakarta.validation.constraints.NotNull;

/**
 * PUT /api/settings/weather-signal-tags/{id} 的 Request Body：調整一筆既有
 * 對照的命中權重層級。
 *
 * <b>只能改 matchTier，不能改 weatherSignalType／tag：</b>這兩個欄位合起來
 * 是這筆資料的身分（見 uk_weather_signal_tag_mappings_type_tag），改其中
 * 任一個等於變成另一筆完全不同的對照，語意上是「刪掉這筆、新增另一筆」，
 * 不是「編輯」——要調整對應到哪個天氣類型或哪個標籤，請停用這筆、
 * 另外新增一筆，不要用這支端點硬改身分欄位。
 */
public class WeatherSignalTagMappingUpdateRequest {

	@NotNull(message = "標籤命中權重層級不可為空")
	private FestiveCampaignTagMatchTier matchTier;

	public FestiveCampaignTagMatchTier getMatchTier() {
		return matchTier;
	}

	public void setMatchTier(FestiveCampaignTagMatchTier matchTier) {
		this.matchTier = matchTier;
	}
}
