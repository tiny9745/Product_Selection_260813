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

	// 刻意不用@CreationTimestamp／@UpdateTimestamp：calculated_at語意是「評估結果
	// 計算時間」，跟updated_at（單純的最後寫入時間）刻意分開——若未來有程式碼
	// 不透過重算、只是改動其他欄位（例如人工修正單一分數），updated_at該變、
	// calculated_at不該變。故比照reviewedAt（ReviewService手動設定）／collectedAt
	// （TrendService手動設定）的既有慣例，由「真正執行計算」的程式碼手動賦值。
	//
	// ⚠️ TODO（評分重算引擎尚未實作，見ScoringService類別註解）：目前完全沒有任何
	// 程式碼會建立全新的ProductEvaluation列（只有ScoringService.
	// updateTrendScoreFromLatestSignal()這種UPDATE既有列的路徑），所以這個
	// NOT NULL欄位尚未被違反過。但未來補上「幫全新商品建立第一筆評估結果」的
	// INSERT路徑時，務必記得手動設定這個欄位，否則會出現與RiskOption.createdAt／
	// TrendSignal.createdAt同一類的「Column cannot be null」錯誤。
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
