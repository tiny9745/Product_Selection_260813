package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.Product_Selection_260813.entity.ProductTypeScoreBand;

public class ProductTypeScoreBandResponse {

	private Long id;
	private Long productTypeId;
	private String factorCode;
	private BigDecimal lowerBound;
	private BigDecimal upperBound;
	private Integer version;
	private String sourceMode;
	private Integer sampleSize;
	private Boolean includesSimulated;
	private LocalDateTime computedAt;
	private LocalDateTime updatedAt;

	public static ProductTypeScoreBandResponse from(ProductTypeScoreBand band) {
		ProductTypeScoreBandResponse r = new ProductTypeScoreBandResponse();
		r.id = band.getId();
		r.productTypeId = band.getProductTypeId();
		r.factorCode = band.getFactorCode();
		r.lowerBound = band.getLowerBound();
		r.upperBound = band.getUpperBound();
		r.version = band.getVersion();
		r.sourceMode = band.getSourceMode();
		r.sampleSize = band.getSampleSize();
		r.includesSimulated = band.getIncludesSimulated();
		r.computedAt = band.getComputedAt();
		r.updatedAt = band.getUpdatedAt();
		return r;
	}

	public Long getId() { return id; }
	public Long getProductTypeId() { return productTypeId; }
	public String getFactorCode() { return factorCode; }
	public BigDecimal getLowerBound() { return lowerBound; }
	public BigDecimal getUpperBound() { return upperBound; }
	public Integer getVersion() { return version; }
	public String getSourceMode() { return sourceMode; }
	public Integer getSampleSize() { return sampleSize; }
	public Boolean getIncludesSimulated() { return includesSimulated; }
	public LocalDateTime getComputedAt() { return computedAt; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
}
