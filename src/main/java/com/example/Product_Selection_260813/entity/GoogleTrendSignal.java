package com.example.Product_Selection_260813.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.Product_Selection_260813.enums.GoogleTrendStatus;
import com.example.Product_Selection_260813.enums.TrendSignalTrendDirection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Google 趨勢（SerpApi）的一次查詢結果，見 V30 migration 說明。
 * 只存方向與成長率，不存絕對熱度——Google 的 0～100 是相對於該次查詢峰值的縮放，
 * 不同商品之間不可比，也不併入 trend_signals 的熱度分數。
 */
@Entity
@Table(name = "google_trend_signals")
public class GoogleTrendSignal {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "keyword", nullable = false, length = 100)
	private String keyword;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private GoogleTrendStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "direction")
	private TrendSignalTrendDirection direction;

	@Column(name = "growth_rate", precision = 8, scale = 2)
	private BigDecimal growthRate;

	@Column(name = "recent_avg", precision = 6, scale = 2)
	private BigDecimal recentAvg;

	@Column(name = "baseline_avg", precision = 6, scale = 2)
	private BigDecimal baselineAvg;

	@Column(name = "point_count", nullable = false)
	private Integer pointCount = 0;

	@Column(name = "collected_at", nullable = false)
	private LocalDateTime collectedAt;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getProductId() {
		return productId;
	}

	public void setProductId(Long productId) {
		this.productId = productId;
	}

	public String getKeyword() {
		return keyword;
	}

	public void setKeyword(String keyword) {
		this.keyword = keyword;
	}

	public GoogleTrendStatus getStatus() {
		return status;
	}

	public void setStatus(GoogleTrendStatus status) {
		this.status = status;
	}

	public TrendSignalTrendDirection getDirection() {
		return direction;
	}

	public void setDirection(TrendSignalTrendDirection direction) {
		this.direction = direction;
	}

	public BigDecimal getGrowthRate() {
		return growthRate;
	}

	public void setGrowthRate(BigDecimal growthRate) {
		this.growthRate = growthRate;
	}

	public BigDecimal getRecentAvg() {
		return recentAvg;
	}

	public void setRecentAvg(BigDecimal recentAvg) {
		this.recentAvg = recentAvg;
	}

	public BigDecimal getBaselineAvg() {
		return baselineAvg;
	}

	public void setBaselineAvg(BigDecimal baselineAvg) {
		this.baselineAvg = baselineAvg;
	}

	public Integer getPointCount() {
		return pointCount;
	}

	public void setPointCount(Integer pointCount) {
		this.pointCount = pointCount;
	}

	public LocalDateTime getCollectedAt() {
		return collectedAt;
	}

	public void setCollectedAt(LocalDateTime collectedAt) {
		this.collectedAt = collectedAt;
	}
}
