package com.example.Product_Selection_260813.dto.request;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.example.Product_Selection_260813.enums.FestiveCategory;

/**
 * POST /api/settings/festive-campaigns 的 Request Body：新增檔期。
 *
 * campaignStatus／isManualOverride不開放由這支DTO傳入：新檔期一律從
 * campaign_status=UPCOMING開始（Entity預設值），狀態轉換交給Daily Cron
 * 自動判斷或POST .../manual-status手動切換，不該在「新增」當下就決定狀態。
 *
 * tags對應「標籤＋分級」清單（見FestiveCampaignTagInput），取代企劃書原始
 * UI樹狀圖裡的單一target_tags字串——這是配合資料表異動（新增
 * festive_campaign_tags表）調整後的欄位。
 */
public class FestiveCampaignCreateRequest {

	@NotBlank(message = "檔期代碼不可為空")
	private String campaignCode;

	@NotBlank(message = "檔期名稱不可為空")
	private String campaignName;

	@NotNull(message = "檔期類別不可為空")
	private FestiveCategory category;

	@NotNull(message = "檔期開始日期不可為空")
	private LocalDate startDate;

	@NotNull(message = "檔期結束日期不可為空")
	private LocalDate endDate;

	// 不填時Entity預設值30天生效
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

	public FestiveCategory getCategory() {
		return category;
	}

	public void setCategory(FestiveCategory category) {
		this.category = category;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public void setEndDate(LocalDate endDate) {
		this.endDate = endDate;
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
