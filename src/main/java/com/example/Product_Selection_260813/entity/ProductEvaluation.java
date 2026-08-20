package com.example.Product_Selection_260813.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	    name = "product_evaluations",
	    uniqueConstraints = @UniqueConstraint(
	        name = "uk_product_evaluations_product_id",
	        columnNames = {"product_id"}
	    )
	)
public class ProductEvaluation {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "evaluation_mode_id", nullable = false)
	private Long evaluationModeId;

	@Column(name = "business_score", precision = 5, scale = 2)
	private BigDecimal businessScore;

	@Column(name = "audience_score", precision = 5, scale = 2)
	private BigDecimal audienceScore;

	@Column(name = "historical_score", precision = 5, scale = 2)
	private BigDecimal historicalScore;

	@Column(name = "purchase_score", precision = 5, scale = 2)
	private BigDecimal purchaseScore;

	@Column(name = "trend_score", precision = 5, scale = 2)
	private BigDecimal trendScore;

	@Column(name = "forecast_score", precision = 5, scale = 2)
	private BigDecimal forecastScore;

	@Column(name = "total_score", precision = 5, scale = 2)
	private BigDecimal totalScore;

	@Column(name = "data_completeness", precision = 5, scale = 2)
	private BigDecimal dataCompleteness;

	@Column(name = "festival_boost", precision = 5, scale = 2)
	private BigDecimal festivalBoost = BigDecimal.ZERO;

	@Column(name = "matched_campaign_id")
	private Long matchedCampaignId;

	@Column(name = "final_score", precision = 5, scale = 2)
	private BigDecimal finalScore;

	@Column(name = "calculated_at", nullable = false)
	private LocalDateTime calculatedAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

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

	public Long getEvaluationModeId() {
		return evaluationModeId;
	}

	public void setEvaluationModeId(Long evaluationModeId) {
		this.evaluationModeId = evaluationModeId;
	}

	public BigDecimal getBusinessScore() {
		return businessScore;
	}

	public void setBusinessScore(BigDecimal businessScore) {
		this.businessScore = businessScore;
	}

	public BigDecimal getAudienceScore() {
		return audienceScore;
	}

	public void setAudienceScore(BigDecimal audienceScore) {
		this.audienceScore = audienceScore;
	}

	public BigDecimal getHistoricalScore() {
		return historicalScore;
	}

	public void setHistoricalScore(BigDecimal historicalScore) {
		this.historicalScore = historicalScore;
	}

	public BigDecimal getPurchaseScore() {
		return purchaseScore;
	}

	public void setPurchaseScore(BigDecimal purchaseScore) {
		this.purchaseScore = purchaseScore;
	}

	public BigDecimal getTrendScore() {
		return trendScore;
	}

	public void setTrendScore(BigDecimal trendScore) {
		this.trendScore = trendScore;
	}

	public BigDecimal getForecastScore() {
		return forecastScore;
	}

	public void setForecastScore(BigDecimal forecastScore) {
		this.forecastScore = forecastScore;
	}

	public BigDecimal getTotalScore() {
		return totalScore;
	}

	public void setTotalScore(BigDecimal totalScore) {
		this.totalScore = totalScore;
	}

	public BigDecimal getDataCompleteness() {
		return dataCompleteness;
	}

	public void setDataCompleteness(BigDecimal dataCompleteness) {
		this.dataCompleteness = dataCompleteness;
	}

	public BigDecimal getFestivalBoost() {
		return festivalBoost;
	}

	public void setFestivalBoost(BigDecimal festivalBoost) {
		this.festivalBoost = festivalBoost;
	}

	public Long getMatchedCampaignId() {
		return matchedCampaignId;
	}

	public void setMatchedCampaignId(Long matchedCampaignId) {
		this.matchedCampaignId = matchedCampaignId;
	}

	public BigDecimal getFinalScore() {
		return finalScore;
	}

	public void setFinalScore(BigDecimal finalScore) {
		this.finalScore = finalScore;
	}

	public LocalDateTime getCalculatedAt() {
		return calculatedAt;
	}

	public void setCalculatedAt(LocalDateTime calculatedAt) {
		this.calculatedAt = calculatedAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
}
