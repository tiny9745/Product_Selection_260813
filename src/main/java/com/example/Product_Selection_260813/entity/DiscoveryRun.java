package com.example.Product_Selection_260813.entity;

import java.time.LocalDateTime;

import com.example.Product_Selection_260813.enums.TrendSyncRunStatus;
import com.example.Product_Selection_260813.enums.TrendSyncTrigger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * PTT 新品探索的一次執行紀錄（V33）。觸發方式與狀態沿用 PTT 熱度同步的
 * TrendSyncTrigger／TrendSyncRunStatus——值與語意完全相同，不另外複製一組 enum。
 */
@Entity
@Table(name = "discovery_runs")
public class DiscoveryRun {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "trigger_type", nullable = false)
	private TrendSyncTrigger triggerType;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private TrendSyncRunStatus status;

	@Column(name = "started_at", nullable = false)
	private LocalDateTime startedAt;

	@Column(name = "finished_at")
	private LocalDateTime finishedAt;

	@Column(name = "post_count", nullable = false)
	private Integer postCount = 0;

	@Column(name = "title_count", nullable = false)
	private Integer titleCount = 0;

	@Column(name = "ai_call_count", nullable = false)
	private Integer aiCallCount = 0;

	@Column(name = "extracted_count", nullable = false)
	private Integer extractedCount = 0;

	@Column(name = "rejected_count", nullable = false)
	private Integer rejectedCount = 0;

	@Column(name = "matched_existing_count", nullable = false)
	private Integer matchedExistingCount = 0;

	@Column(name = "similar_dismissed_count", nullable = false)
	private Integer similarDismissedCount = 0;

	@Column(name = "fit_count", nullable = false)
	private Integer fitCount = 0;

	@Column(name = "google_count", nullable = false)
	private Integer googleCount = 0;

	@Column(name = "new_count", nullable = false)
	private Integer newCount = 0;

	@Column(name = "updated_count", nullable = false)
	private Integer updatedCount = 0;

	@Column(name = "message", length = 255)
	private String message;

	@Column(name = "triggered_by")
	private Long triggeredBy;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public TrendSyncTrigger getTriggerType() { return triggerType; }
	public void setTriggerType(TrendSyncTrigger triggerType) { this.triggerType = triggerType; }
	public TrendSyncRunStatus getStatus() { return status; }
	public void setStatus(TrendSyncRunStatus status) { this.status = status; }
	public LocalDateTime getStartedAt() { return startedAt; }
	public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
	public LocalDateTime getFinishedAt() { return finishedAt; }
	public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
	public Integer getPostCount() { return postCount; }
	public void setPostCount(Integer postCount) { this.postCount = postCount; }
	public Integer getTitleCount() { return titleCount; }
	public void setTitleCount(Integer titleCount) { this.titleCount = titleCount; }
	public Integer getAiCallCount() { return aiCallCount; }
	public void setAiCallCount(Integer aiCallCount) { this.aiCallCount = aiCallCount; }
	public Integer getExtractedCount() { return extractedCount; }
	public void setExtractedCount(Integer extractedCount) { this.extractedCount = extractedCount; }
	public Integer getRejectedCount() { return rejectedCount; }
	public void setRejectedCount(Integer rejectedCount) { this.rejectedCount = rejectedCount; }
	public Integer getMatchedExistingCount() { return matchedExistingCount; }
	public void setMatchedExistingCount(Integer matchedExistingCount) { this.matchedExistingCount = matchedExistingCount; }
	public Integer getNewCount() { return newCount; }
	public void setNewCount(Integer newCount) { this.newCount = newCount; }
	public Integer getUpdatedCount() { return updatedCount; }
	public void setUpdatedCount(Integer updatedCount) { this.updatedCount = updatedCount; }
	public Integer getSimilarDismissedCount() { return similarDismissedCount; }
	public void setSimilarDismissedCount(Integer similarDismissedCount) { this.similarDismissedCount = similarDismissedCount; }
	public Integer getFitCount() { return fitCount; }
	public void setFitCount(Integer fitCount) { this.fitCount = fitCount; }
	public Integer getGoogleCount() { return googleCount; }
	public void setGoogleCount(Integer googleCount) { this.googleCount = googleCount; }
	public String getMessage() { return message; }
	public void setMessage(String message) { this.message = message; }
	public Long getTriggeredBy() { return triggeredBy; }
	public void setTriggeredBy(Long triggeredBy) { this.triggeredBy = triggeredBy; }
}
