package com.example.Product_Selection_260813.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 各因子的固定目標區間，供 ScoringAlgorithms.normalizeByBand() 使用。
 *
 * 用凍結的設定值而非即時資料算分位數，是為了維持分數的可重現性——用即時資料
 * 正規化的話，新增一件極端值商品會讓所有既有商品的分數位移，審核快照就失去
 * 對照意義。初次設定可用歷史分位數推導，之後固定下來；要調整時建新版本、
 * 留舊版本，不覆蓋。
 *
 * productTypeId 為 null 代表全域預設區間（例如折扣深度 0~50% 不分品類）。
 */
@Entity
@Table(name = "product_type_score_bands")
public class ProductTypeScoreBand {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 指向大類；null 代表全域預設區間。 */
	@Column(name = "product_type_id")
	private Long productTypeId;

	/** MARGIN_RATE / DISCOUNT_DEPTH 等因子代碼。 */
	@Column(name = "factor_code", nullable = false, length = 50)
	private String factorCode;

	/** 對應 0 分的值。 */
	@Column(name = "lower_bound", nullable = false, precision = 10, scale = 4)
	private BigDecimal lowerBound;

	/** 對應 100 分的值。 */
	@Column(name = "upper_bound", nullable = false, precision = 10, scale = 4)
	private BigDecimal upperBound;

	@Column(name = "version", nullable = false)
	private Integer version = 1;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getProductTypeId() {
		return productTypeId;
	}

	public void setProductTypeId(Long productTypeId) {
		this.productTypeId = productTypeId;
	}

	public String getFactorCode() {
		return factorCode;
	}

	public void setFactorCode(String factorCode) {
		this.factorCode = factorCode;
	}

	public BigDecimal getLowerBound() {
		return lowerBound;
	}

	public void setLowerBound(BigDecimal lowerBound) {
		this.lowerBound = lowerBound;
	}

	public BigDecimal getUpperBound() {
		return upperBound;
	}

	public void setUpperBound(BigDecimal upperBound) {
		this.upperBound = upperBound;
	}

	public Integer getVersion() {
		return version;
	}

	public void setVersion(Integer version) {
		this.version = version;
	}

	public Boolean getIsActive() {
		return isActive;
	}

	public void setIsActive(Boolean isActive) {
		this.isActive = isActive;
	}
}
