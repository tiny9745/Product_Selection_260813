package com.example.Product_Selection_260813.dto.response;

import java.time.LocalDate;
import java.util.List;

import com.example.Product_Selection_260813.entity.FestiveCampaign;
import com.example.Product_Selection_260813.enums.FestiveCampaignStatus;
import com.example.Product_Selection_260813.enums.FestiveCategory;

public class FestiveCampaignResponse {

	private Long id;
	private String campaignCode;
	private String campaignName;
	private FestiveCategory category;
	private LocalDate startDate;
	private LocalDate endDate;
	private Integer preparationLeadDays;
	private FestiveCampaignStatus campaignStatus;
	private Boolean isManualOverride;
	private List<FestiveCampaignTagView> tags;

	public static FestiveCampaignResponse from(FestiveCampaign campaign, List<FestiveCampaignTagView> tags) {
		FestiveCampaignResponse dto = new FestiveCampaignResponse();
		dto.id = campaign.getId();
		dto.campaignCode = campaign.getCampaignCode();
		dto.campaignName = campaign.getCampaignName();
		dto.category = campaign.getCategory();
		dto.startDate = campaign.getStartDate();
		dto.endDate = campaign.getEndDate();
		dto.preparationLeadDays = campaign.getPreparationLeadDays();
		dto.campaignStatus = campaign.getCampaignStatus();
		dto.isManualOverride = campaign.getIsManualOverride();
		dto.tags = tags;
		return dto;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

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

	public FestiveCampaignStatus getCampaignStatus() {
		return campaignStatus;
	}

	public void setCampaignStatus(FestiveCampaignStatus campaignStatus) {
		this.campaignStatus = campaignStatus;
	}

	public Boolean getIsManualOverride() {
		return isManualOverride;
	}

	public void setIsManualOverride(Boolean isManualOverride) {
		this.isManualOverride = isManualOverride;
	}

	public List<FestiveCampaignTagView> getTags() {
		return tags;
	}

	public void setTags(List<FestiveCampaignTagView> tags) {
		this.tags = tags;
	}
}
