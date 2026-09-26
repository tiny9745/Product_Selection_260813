package com.example.Product_Selection_260813.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 天氣加成設定（V26，weather_boost_settings，單列 id=1）。
 *
 * 歷史／預測比重（加總 100）與加成上限。比照 region_weights 的「獨立小表＋Service 驗證」模式，
 * 不放進 system_settings 或七因子權重——天氣加成是與節慶加成並列的額外加成，不是計分因子。
 */
@Entity
@Table(name = "weather_boost_settings")
public class WeatherBoostSetting {

	/** 單列設定表的固定主鍵。 */
	public static final Integer SINGLETON_ID = 1;

	@Id
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "id")
	private Integer id;

	@Column(name = "history_weight_percentage", nullable = false, precision = 5, scale = 2)
	private BigDecimal historyWeightPercentage;

	@Column(name = "forecast_weight_percentage", nullable = false, precision = 5, scale = 2)
	private BigDecimal forecastWeightPercentage;

	@Column(name = "boost_cap", nullable = false, precision = 4, scale = 2)
	private BigDecimal boostCap;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "updated_by")
	private Long updatedBy;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public BigDecimal getHistoryWeightPercentage() {
		return historyWeightPercentage;
	}

	public void setHistoryWeightPercentage(BigDecimal historyWeightPercentage) {
		this.historyWeightPercentage = historyWeightPercentage;
	}

	public BigDecimal getForecastWeightPercentage() {
		return forecastWeightPercentage;
	}

	public void setForecastWeightPercentage(BigDecimal forecastWeightPercentage) {
		this.forecastWeightPercentage = forecastWeightPercentage;
	}

	public BigDecimal getBoostCap() {
		return boostCap;
	}

	public void setBoostCap(BigDecimal boostCap) {
		this.boostCap = boostCap;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public Long getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(Long updatedBy) {
		this.updatedBy = updatedBy;
	}
}
