package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;

import com.example.Product_Selection_260813.json.WeightSnapshot;

/**
 * GET /api/products/{id}/evaluation：商品目前評估模式、固定權重、各項分數、
 * Base/Final Score。
 *
 * dataSource標明本次回傳的數字來自哪一軌，對應四-「雙軌讀取邏輯」：
 * - "SNAPSHOT"：review_status=APPROVED，讀取review_records最新一筆的凍結值
 * - "LIVE"：其餘狀態，讀取product_evaluations的即時值
 * 這個規則寫在product_evaluations.final_score欄位本身的DB註解上
 * （「已審核通過商品的此值改讀review_records的Snapshot凍結值」），代表是
 * 全域規則、不限於本端點，前端可以用這個欄位判斷「畫面上看到的是不是已經
 * 凍結、不會再變動的數字」，避免誤以為APPROVED商品的分數還會隨即時資料異動。
 */
public class EvaluationResponse {

	private String dataSource;

	private Long evaluationModeId;
	private String evaluationModeName;
	private Integer evaluationModeVersion;
	private WeightSnapshot weights;

	private BigDecimal businessScore;
	private BigDecimal audienceScore;
	private BigDecimal historicalScore;
	private BigDecimal purchaseScore;
	private BigDecimal trendScore;
	private BigDecimal forecastScore;
	private BigDecimal totalScore;
	private BigDecimal dataCompleteness;

	private BigDecimal festivalBoost;
	private BigDecimal finalScore;

	public String getDataSource() {
		return dataSource;
	}

	public void setDataSource(String dataSource) {
		this.dataSource = dataSource;
	}

	public Long getEvaluationModeId() {
		return evaluationModeId;
	}

	public void setEvaluationModeId(Long evaluationModeId) {
		this.evaluationModeId = evaluationModeId;
	}

	public String getEvaluationModeName() {
		return evaluationModeName;
	}

	public void setEvaluationModeName(String evaluationModeName) {
		this.evaluationModeName = evaluationModeName;
	}

	public Integer getEvaluationModeVersion() {
		return evaluationModeVersion;
	}

	public void setEvaluationModeVersion(Integer evaluationModeVersion) {
		this.evaluationModeVersion = evaluationModeVersion;
	}

	public WeightSnapshot getWeights() {
		return weights;
	}

	public void setWeights(WeightSnapshot weights) {
		this.weights = weights;
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

	public BigDecimal getFinalScore() {
		return finalScore;
	}

	public void setFinalScore(BigDecimal finalScore) {
		this.finalScore = finalScore;
	}
}
