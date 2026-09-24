package com.example.Product_Selection_260813.dto.request;

import java.util.List;

import com.example.Product_Selection_260813.constants.ValidationMessage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/settings/festive-campaigns/{id} 的 Request Body：編輯檔期（代碼不可修改）。
 *
 * 2026-09-24（V21）：移除 startDate／endDate，改帶日期規則欄位（見 FestiveCampaignRuleFields）。
 * 既有資料是 WEATHER，或這次帶 WEATHER，一律 400（決議 D1）。
 * tags、regions 皆為整份覆蓋。
 */
public class FestiveCampaignUpdateRequest extends FestiveCampaignRuleFields {

	@NotBlank(message = "檔期名稱不可為空")
	@Size(max = 100, message = ValidationMessage.CAMPAIGN_NAME_TOO_LONG)
	private String campaignName;

	@PositiveOrZero(message = ValidationMessage.CAMPAIGN_LEAD_DAYS_NEGATIVE)
	@Max(value = 180, message = ValidationMessage.CAMPAIGN_LEAD_DAYS_TOO_LARGE)
	private Integer preparationLeadDays;

	@Valid
	private List<FestiveCampaignTagInput> tags;

	public String getCampaignName() {
		return campaignName;
	}

	public void setCampaignName(String campaignName) {
		this.campaignName = campaignName;
	}

	public Integer getPreparationLeadDays() {
		return preparationLeadDays;
	}

	public void setPreparationLeadDays(Integer preparationLeadDays) {
		this.preparationLeadDays = preparationLeadDays;
	}

	public List<FestiveCampaignTagInput> getTags() {
		return tags;
	}

	public void setTags(List<FestiveCampaignTagInput> tags) {
		this.tags = tags;
	}
}
