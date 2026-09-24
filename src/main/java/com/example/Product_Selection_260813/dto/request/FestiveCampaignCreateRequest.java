package com.example.Product_Selection_260813.dto.request;

import java.util.List;

import com.example.Product_Selection_260813.constants.ValidationMessage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * POST /api/settings/festive-campaigns 的 Request Body：新增檔期。
 *
 * campaignStatus／isManualOverride不開放由這支DTO傳入：節慶／季節型的狀態由日期規則即時推算
 * （V21，決議 D10），手動覆蓋走 POST .../manual-status。
 *
 * 2026-09-24（V21）：移除 startDate／endDate，改帶日期規則欄位（見 FestiveCampaignRuleFields）。
 * category 只接受 FESTIVAL／SEASON，WEATHER 一律 400（決議 D1）。campaignCode 不可帶年份（D5）。
 *
 * tags對應「標籤＋分級」清單（見FestiveCampaignTagInput）。
 */
public class FestiveCampaignCreateRequest extends FestiveCampaignRuleFields {

	@NotBlank(message = "檔期代碼不可為空")
	@Size(max = 50, message = ValidationMessage.CAMPAIGN_CODE_TOO_LONG)
	private String campaignCode;

	@NotBlank(message = "檔期名稱不可為空")
	@Size(max = 100, message = ValidationMessage.CAMPAIGN_NAME_TOO_LONG)
	private String campaignName;

	// 不填時Entity預設值30天生效。負數會讓急迫係數的分母失真，必須在進Service前擋下。
	@PositiveOrZero(message = ValidationMessage.CAMPAIGN_LEAD_DAYS_NEGATIVE)
	@Max(value = 180, message = ValidationMessage.CAMPAIGN_LEAD_DAYS_TOO_LARGE)
	private Integer preparationLeadDays;

	@Valid
	private List<FestiveCampaignTagInput> tags;

	public String getCampaignCode() {
		return campaignCode;
	}

	public void setCampaignCode(String campaignCode) {
		this.campaignCode = campaignCode;
	}

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
