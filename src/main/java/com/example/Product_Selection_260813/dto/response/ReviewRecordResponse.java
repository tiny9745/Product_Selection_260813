package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.example.Product_Selection_260813.entity.ReviewRecord;
import com.example.Product_Selection_260813.enums.ReviewRecordReviewStatus;
import com.example.Product_Selection_260813.json.MatchedCampaignSnapshot;
import com.example.Product_Selection_260813.json.WeatherBoostSnapshot;
import com.example.Product_Selection_260813.json.ProductSnapshot;
import com.example.Product_Selection_260813.json.TrendSnapshot;
import com.example.Product_Selection_260813.json.WeightSnapshot;

/**
 * 審核紀錄完整內容，共用於：
 * POST /api/reviews（提交後回傳本次紀錄）／GET /api/reviews/decision-records（決策紀錄列表）／
 * GET /api/products/{id}/reviews（單一商品審核歷史）。
 *
 * productName刻意從productSnapshot.name取得，不另外查Product表：
 * 決策紀錄列表的定位是「當時的完整快照」，即使該商品之後改名甚至被刪除，
 * 這裡顯示的仍應是審核當下的名稱，額外join products表反而會顯示「現在的名稱」，
 * 與「決策紀錄列表」的Snapshot精神矛盾。
 */
public class ReviewRecordResponse {

	private Long id;
	private Long productId;
	private String productName;
	private Long reviewerId;
	private String reviewerName;
	private Integer submissionCount;
	private ReviewRecordReviewStatus reviewStatus;
	private LocalDateTime reviewedAt;

	private Long evaluationModeId;
	private String evaluationModeName;
	private Integer evaluationModeVersion;

	private BigDecimal businessScore;
	private BigDecimal audienceScore;
	private BigDecimal historicalScore;
	private BigDecimal purchaseScore;
	private BigDecimal trendScore;
	private BigDecimal forecastScore;
	private BigDecimal totalScore;

	private BigDecimal festivalBoostSnapshot;
	private MatchedCampaignSnapshot matchedCampaignSnapshot;
	/** V26：審核當時的天氣加成與明細；V26 前的紀錄為 null（畫面顯示「—」，不是 0）。 */
	private BigDecimal weatherBoostSnapshot;
	private WeatherBoostSnapshot weatherBoostDetailSnapshot;
	private BigDecimal finalScoreSnapshot;
	private BigDecimal dataCompleteness;

	private WeightSnapshot weightSnapshot;
	private ProductSnapshot productSnapshot;
	private String aiSummarySnapshot;
	private TrendSnapshot trendSnapshot;

	private String reviewComment;
	private List<Long> riskOptionIds;

	/**
	 * 勾選「其他」風險選項時的補充說明（review_risks.manual_note）。
	 * 一般風險選項恆為 null，見 RiskOption.isFreeTextOption 的類別說明。
	 */
	private String otherRiskNote;

	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	public static ReviewRecordResponse from(ReviewRecord record, List<Long> riskOptionIds) {
		ReviewRecordResponse dto = new ReviewRecordResponse();
		dto.id = record.getId();
		dto.productId = record.getProductId();
		dto.productName = record.getProductSnapshot() != null ? record.getProductSnapshot().getName() : null;
		dto.reviewerId = record.getReviewerId();
		dto.submissionCount = record.getSubmissionCount();
		dto.reviewStatus = record.getReviewStatus();
		dto.reviewedAt = record.getReviewedAt();

		dto.evaluationModeId = record.getEvaluationModeId();
		dto.evaluationModeName = record.getEvaluationModeName();
		dto.evaluationModeVersion = record.getEvaluationModeVersion();

		dto.businessScore = record.getBusinessScore();
		dto.audienceScore = record.getAudienceScore();
		dto.historicalScore = record.getHistoricalScore();
		dto.purchaseScore = record.getPurchaseScore();
		dto.trendScore = record.getTrendScore();
		dto.forecastScore = record.getForecastScore();
		dto.totalScore = record.getTotalScore();

		dto.festivalBoostSnapshot = record.getFestivalBoostSnapshot();
		dto.matchedCampaignSnapshot = record.getMatchedCampaignSnapshot();
		dto.weatherBoostSnapshot = record.getWeatherBoostSnapshot();
		dto.weatherBoostDetailSnapshot = record.getWeatherBoostDetailSnapshot();
		dto.finalScoreSnapshot = record.getFinalScoreSnapshot();
		dto.dataCompleteness = record.getDataCompleteness();

		dto.weightSnapshot = record.getWeightSnapshot();
		dto.productSnapshot = record.getProductSnapshot();
		dto.aiSummarySnapshot = record.getAiSummarySnapshot();
		dto.trendSnapshot = record.getTrendSnapshot();

		dto.reviewComment = record.getReviewComment();
		dto.riskOptionIds = riskOptionIds;

		dto.createdAt = record.getCreatedAt();
		dto.updatedAt = record.getUpdatedAt();
		return dto;
	}

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

	public String getProductName() {
		return productName;
	}

	public void setProductName(String productName) {
		this.productName = productName;
	}

	public Long getReviewerId() {
		return reviewerId;
	}

	public void setReviewerId(Long reviewerId) {
		this.reviewerId = reviewerId;
	}

	public String getReviewerName() {
		return reviewerName;
	}

	public void setReviewerName(String reviewerName) {
		this.reviewerName = reviewerName;
	}

	/**
	 * 補上批次解析好的審核人姓名，回傳 this 方便鏈式呼叫，用法同
	 * ProductResponse.withEvaluationSummary()：
	 * ReviewRecordResponse.from(record, riskIds).withReviewerName(
	 *     record.getReviewerId() == null ? null : nameById.get(record.getReviewerId()))
	 *
	 * reviewerId 只是使用者編號，ReviewRecord 本身沒有存姓名（正確──姓名屬於
	 * app_users，不是審核紀錄該存的欄位），解析姓名要批次查 app_users，
	 * 因此跟 finalScore／dataCompleteness 一樣是「查完才填」，不透過
	 * from() 直接組出來，避免在 from() 裡面藏一次額外的資料庫查詢。
	 */
	public ReviewRecordResponse withReviewerName(String reviewerName) {
		this.reviewerName = reviewerName;
		return this;
	}

	public String getOtherRiskNote() {
		return otherRiskNote;
	}

	public void setOtherRiskNote(String otherRiskNote) {
		this.otherRiskNote = otherRiskNote;
	}

	/**
	 * 補上「其他」風險選項的補充說明，回傳 this 方便鏈式呼叫，用法同
	 * withReviewerName()。submitReview() 送審當下可以直接用 request 裡的值
	 * 補上；getDecisionRecords()／getProductReviewHistory() 這類清單型查詢
	 * 則從 review_risks.manual_note 讀回（見 ReviewService.getOtherRiskNote()）。
	 */
	public ReviewRecordResponse withOtherRiskNote(String otherRiskNote) {
		this.otherRiskNote = otherRiskNote;
		return this;
	}

	public Integer getSubmissionCount() {
		return submissionCount;
	}

	public void setSubmissionCount(Integer submissionCount) {
		this.submissionCount = submissionCount;
	}

	public ReviewRecordReviewStatus getReviewStatus() {
		return reviewStatus;
	}

	public void setReviewStatus(ReviewRecordReviewStatus reviewStatus) {
		this.reviewStatus = reviewStatus;
	}

	public LocalDateTime getReviewedAt() {
		return reviewedAt;
	}

	public void setReviewedAt(LocalDateTime reviewedAt) {
		this.reviewedAt = reviewedAt;
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

	public BigDecimal getWeatherBoostSnapshot() {
		return weatherBoostSnapshot;
	}

	public WeatherBoostSnapshot getWeatherBoostDetailSnapshot() {
		return weatherBoostDetailSnapshot;
	}

	public BigDecimal getFestivalBoostSnapshot() {
		return festivalBoostSnapshot;
	}

	public void setFestivalBoostSnapshot(BigDecimal festivalBoostSnapshot) {
		this.festivalBoostSnapshot = festivalBoostSnapshot;
	}

	public MatchedCampaignSnapshot getMatchedCampaignSnapshot() {
		return matchedCampaignSnapshot;
	}

	public void setMatchedCampaignSnapshot(MatchedCampaignSnapshot matchedCampaignSnapshot) {
		this.matchedCampaignSnapshot = matchedCampaignSnapshot;
	}

	public BigDecimal getFinalScoreSnapshot() {
		return finalScoreSnapshot;
	}

	public void setFinalScoreSnapshot(BigDecimal finalScoreSnapshot) {
		this.finalScoreSnapshot = finalScoreSnapshot;
	}

	public BigDecimal getDataCompleteness() {
		return dataCompleteness;
	}

	public void setDataCompleteness(BigDecimal dataCompleteness) {
		this.dataCompleteness = dataCompleteness;
	}

	public WeightSnapshot getWeightSnapshot() {
		return weightSnapshot;
	}

	public void setWeightSnapshot(WeightSnapshot weightSnapshot) {
		this.weightSnapshot = weightSnapshot;
	}

	public ProductSnapshot getProductSnapshot() {
		return productSnapshot;
	}

	public void setProductSnapshot(ProductSnapshot productSnapshot) {
		this.productSnapshot = productSnapshot;
	}

	public String getAiSummarySnapshot() {
		return aiSummarySnapshot;
	}

	public void setAiSummarySnapshot(String aiSummarySnapshot) {
		this.aiSummarySnapshot = aiSummarySnapshot;
	}

	public TrendSnapshot getTrendSnapshot() {
		return trendSnapshot;
	}

	public void setTrendSnapshot(TrendSnapshot trendSnapshot) {
		this.trendSnapshot = trendSnapshot;
	}

	public String getReviewComment() {
		return reviewComment;
	}

	public void setReviewComment(String reviewComment) {
		this.reviewComment = reviewComment;
	}

	public List<Long> getRiskOptionIds() {
		return riskOptionIds;
	}

	public void setRiskOptionIds(List<Long> riskOptionIds) {
		this.riskOptionIds = riskOptionIds;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
}
