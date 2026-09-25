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

/** PTT 熱度全商品同步的一次執行紀錄，見 V23 migration 說明。 */
@Entity
@Table(name = "trend_sync_runs")
public class TrendSyncRun {

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

	@Column(name = "total_count", nullable = false)
	private Integer totalCount = 0;

	@Column(name = "real_count", nullable = false)
	private Integer realCount = 0;

	@Column(name = "fallback_count", nullable = false)
	private Integer fallbackCount = 0;

	@Column(name = "failed_count", nullable = false)
	private Integer failedCount = 0;

	@Column(name = "message", length = 255)
	private String message;

	@Column(name = "triggered_by")
	private Long triggeredBy;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public TrendSyncTrigger getTriggerType() {
		return triggerType;
	}

	public void setTriggerType(TrendSyncTrigger triggerType) {
		this.triggerType = triggerType;
	}

	public TrendSyncRunStatus getStatus() {
		return status;
	}

	public void setStatus(TrendSyncRunStatus status) {
		this.status = status;
	}

	public LocalDateTime getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(LocalDateTime startedAt) {
		this.startedAt = startedAt;
	}

	public LocalDateTime getFinishedAt() {
		return finishedAt;
	}

	public void setFinishedAt(LocalDateTime finishedAt) {
		this.finishedAt = finishedAt;
	}

	public Integer getTotalCount() {
		return totalCount;
	}

	public void setTotalCount(Integer totalCount) {
		this.totalCount = totalCount;
	}

	public Integer getRealCount() {
		return realCount;
	}

	public void setRealCount(Integer realCount) {
		this.realCount = realCount;
	}

	public Integer getFallbackCount() {
		return fallbackCount;
	}

	public void setFallbackCount(Integer fallbackCount) {
		this.fallbackCount = fallbackCount;
	}

	public Integer getFailedCount() {
		return failedCount;
	}

	public void setFailedCount(Integer failedCount) {
		this.failedCount = failedCount;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Long getTriggeredBy() {
		return triggeredBy;
	}

	public void setTriggeredBy(Long triggeredBy) {
		this.triggeredBy = triggeredBy;
	}
}
