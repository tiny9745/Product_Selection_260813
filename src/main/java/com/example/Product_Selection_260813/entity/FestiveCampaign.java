package com.example.Product_Selection_260813.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.Product_Selection_260813.enums.FestiveCampaignStatus;
import com.example.Product_Selection_260813.enums.FestiveCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name="festive_campaigns")
public class FestiveCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "campaign_code", nullable = false, length = 50, unique = true)
    private String campaignCode;

    @Column(name = "campaign_name", nullable = false, length = 100)
    private String campaignName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private FestiveCategory category;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "preparation_lead_days", nullable = false)
    private Integer preparationLeadDays = 30;

    // target_tags(VARCHAR)欄位已移除：無法記錄「這個標籤屬於核心/一般/弱命中」的分級，
    // 改由festive_campaign_tags表承接（一檔期對多標籤、每個標籤各自帶match_tier），
    // 見FestiveCampaignTag.java。標籤清單查詢改用FestiveCampaignTagRepository.findByCampaignId()。

    @Enumerated(EnumType.STRING)
    @Column(name = "campaign_status", nullable = false)
    private FestiveCampaignStatus campaignStatus = FestiveCampaignStatus.UPCOMING;

    @Column(name = "is_manual_override", nullable = false)
    private Boolean isManualOverride = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
}