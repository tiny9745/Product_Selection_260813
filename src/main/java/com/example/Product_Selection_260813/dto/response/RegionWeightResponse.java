package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.Product_Selection_260813.entity.RegionWeight;

/** GET／PUT /api/settings/region-weights 共用的回應格式。 */
public class RegionWeightResponse {

	private String region;
	private BigDecimal weightPercentage;
	private LocalDateTime updatedAt;

	public static RegionWeightResponse from(RegionWeight entity) {
		RegionWeightResponse dto = new RegionWeightResponse();
		dto.region = entity.getRegion();
		dto.weightPercentage = entity.getWeightPercentage();
		dto.updatedAt = entity.getUpdatedAt();
		return dto;
	}

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
}
