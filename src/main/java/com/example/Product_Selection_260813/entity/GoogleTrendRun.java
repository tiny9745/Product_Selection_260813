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

/** Google 趨勢批次查詢的一次執行紀錄，見 V30 migration 說明。狀態與觸發方式沿用 PTT 同步的 enum。 */
@Entity
@Table(name = "google_trend_runs")
public class GoogleTrendRun {

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

	@Column(name = "ok_count", nullable = false)
	private Integer okCount = 0;

	@Column(name = "no_data_count", nullable = false)
	private Integer noDataCount = 0;

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

	public Integer getOkCount() {
		return okCount;
	}

	public void setOkCount(Integer okCount) {
		this.okCount = okCount;
	}

	public Integer getNoDataCount() {
		return noDataCount;
	}

	public void setNoDataCount(Integer noDataCount) {
		this.noDataCount = noDataCount;
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
