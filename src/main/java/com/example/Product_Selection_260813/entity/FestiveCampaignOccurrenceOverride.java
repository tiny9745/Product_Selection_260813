package com.example.Product_Selection_260813.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 逐年日期覆寫（V21）：規則推算不出的年度例外，由管理端人工填寫。
 *
 * 例：除夕及春節逢例假日「得」於前一個或次一個上班日補假、彈性放假、節氣誤差，
 * 都是年度公告才確定的選擇，無法用規則推導。有覆寫時，該週期年的起訖日一律以覆寫為準。
 */
@Entity
@Table(name = "festive_campaign_occurrence_overrides")
public class FestiveCampaignOccurrenceOverride {

	@EmbeddedId
	private FestiveCampaignOccurrenceOverrideId id;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "note", length = 200)
	private String note;

	@Column(name = "updated_by")
	private Long updatedBy;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	public FestiveCampaignOccurrenceOverrideId getId() {
		return id;
	}

	public void setId(FestiveCampaignOccurrenceOverrideId id) {
		this.id = id;
	}

	public Long getCampaignId() {
		return id == null ? null : id.getCampaignId();
	}

	public Integer getCycleYear() {
		return id == null ? null : id.getCycleYear();
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

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public Long getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(Long updatedBy) {
		this.updatedBy = updatedBy;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
}
