package com.example.Product_Selection_260813.dto.request;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.example.Product_Selection_260813.enums.FestiveCategory;

/**
 * PUT /api/settings/festive-campaigns/{id} 的 Request Body：編輯檔期設定。
 *
 * campaignCode不可編輯（企劃書UI樹狀圖「編輯檔期」欄位清單本就不含代碼），
 * 且campaign_status／is_manual_override一樣不在這支DTO——狀態切換是
 * POST .../manual-status的職責，跟「編輯基本資料」是刻意拆開的兩個按鈕
 * （見企劃書十四-1「與『編輯』拆成兩個按鈕，避免使用者誤以為編輯儲存
 * 即等於切換狀態」）。
 */
public class FestiveCampaignUpdateRequest {

	@NotBlank(message = "檔期名稱不可為空")
	private String campaignName;

	@NotNull(message = "檔期類別不可為空")
	private FestiveCategory category;

	@NotNull(message = "檔期開始日期不可為空")
	private LocalDate startDate;

	@NotNull(message = "檔期結束日期不可為空")
	private LocalDate endDate;

	private Integer preparationLeadDays;

	@Valid
	private List<FestiveCampaignTagInput> tags;

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
