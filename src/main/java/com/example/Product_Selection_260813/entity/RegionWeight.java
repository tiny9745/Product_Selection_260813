package com.example.Product_Selection_260813.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 四大天氣監控區域（見 WeatherRegionConfig.REGION_CITIES）各自的業務占比設定，
 * WeatherCampaignSyncService 計算 region_coverage_ratio 時的資料來源（見
 * V20 migration 的欄位註解）。
 *
 * <b>為什麼用 region 字串當 PK，不另外開 id 自增欄位：</b>區域集合固定為
 * NORTH／CENTRAL／SOUTH／EAST 四筆，不開放新增或刪除（比照 WeatherRegionConfig
 * 本身是寫死的 Map，不是可自由增刪的一般設定表），region 字串本身就是穩定
 * 且唯一的自然鍵，不需要額外的代理鍵。
 *
 * <b>不使用 @ManyToOne 關聯：</b>沿用本專案既有慣例（見 WeatherSignalTagMapping
 * 類別註解），Entity 層維持 plain 欄位。
 */
@Entity
@Table(name = "region_weights")
public class RegionWeight {

	@Id
	@Column(name = "region", length = 10)
	private String region;

	@Column(name = "weight_percentage", nullable = false, precision = 5, scale = 2)
	private BigDecimal weightPercentage;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@Column(name = "updated_by")
	private Long updatedBy;

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public BigDecimal getWeightPercentage() {
		return weightPercentage;
	}

	public void setWeightPercentage(BigDecimal weightPercentage) {
		this.weightPercentage = weightPercentage;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Long getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(Long updatedBy) {
		this.updatedBy = updatedBy;
	}
}
