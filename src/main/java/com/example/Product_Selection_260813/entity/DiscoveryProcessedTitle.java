package com.example.Product_Selection_260813.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * PTT 新品探索已處理過的標題（V35）。只存標題的雜湊，不存標題原文；
 * 用途是讓每天的探索只把新標題送給 AI。
 */
@Entity
@Table(name = "discovery_processed_titles")
public class DiscoveryProcessedTitle {

	@Id
	@Column(name = "title_key", length = 64, nullable = false)
	private String titleKey;

	@Column(name = "processed_at", nullable = false)
	private LocalDateTime processedAt;

	protected DiscoveryProcessedTitle() {
	}

	public DiscoveryProcessedTitle(String titleKey, LocalDateTime processedAt) {
		this.titleKey = titleKey;
		this.processedAt = processedAt;
	}

	public String getTitleKey() {
		return titleKey;
	}

	public LocalDateTime getProcessedAt() {
		return processedAt;
	}
}
