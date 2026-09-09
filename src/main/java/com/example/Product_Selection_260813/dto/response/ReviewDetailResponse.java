package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.example.Product_Selection_260813.entity.AiAnalysis;
import com.example.Product_Selection_260813.entity.EvaluationMode;
import com.example.Product_Selection_260813.entity.ProductEvaluation;
import com.example.Product_Selection_260813.service.gate.GateResult;
import com.example.Product_Selection_260813.json.MatchedCampaignSnapshot;
import com.example.Product_Selection_260813.json.WeightSnapshot;

/**
 * GET /api/reviews/{productId}：管理進行審核所需的完整資訊（含節慶加成明細）。
 *
 * 對應功能樹狀圖「審核品項」子項目：查看品項詳情／資料完整度／評估模式／
 * 綜合加權分數／節慶加成／最終分數／AI推薦摘要／人工風險評估（複選）可選清單。
 *
 * evaluation／evaluationMode／matchedCampaign皆可能為null（商品尚未經過評分計算，
 * 或未命中任何節慶檔期），前端需自行處理「尚無評估資料」的Empty狀態，
 * 不應假設這些欄位一定有值——ScoringService尚未完整建立評分重算引擎前，
 * product_evaluations可能還沒有對應資料列，這是目前架構下的真實狀態，不是bug。
 */
public class ReviewDetailResponse {

	private ProductResponse product;
	private Integer submissionCount;

	private BigDecimal dataCompleteness;

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

	private BigDecimal festivalBoost;
	private MatchedCampaignSnapshot matchedCampaign;
	private BigDecimal finalScore;

	private String aiSummary;
	private String aiRecommendation;
	private String aiReasons;

	private List<RiskOptionResponse> availableRiskOptions;

	/**
	 * Gate 判定結果。放在回應裡而非另開端點，是因為審核畫面一定要一起顯示——
	 * 分開拉會多一次請求，而且兩次請求之間商品資料可能被改，兩邊會對不起來。
	 *
	 * 前端應把它顯示在分數區塊「之前」：可不可行比分數高低更優先看。
	 */
	private GateResult.Summary gateResults;

	/**
	 * Gate 判定的一句話摘要，例如「通過 3 項，不通過 1 項，資料不足 1 項」。
	 *
	 * 前端顯示在備註輸入框「上方」，不可自動填入 review_comment——
	 * review_comment 是主管的話，系統文字混進去之後，事後查核會讀到一段
	 * 主管其實沒寫過的內容。要提供「引用到備註」按鈕讓主管主動採用。
	 */
	private String systemGateSummary;


	public static ReviewDetailResponse build(ProductResponse product, Integer submissionCount,
			ProductEvaluation evaluation, EvaluationMode evaluationMode, WeightSnapshot weights,
			MatchedCampaignSnapshot matchedCampaign, AiAnalysis aiAnalysis,
			List<RiskOptionResponse> availableRiskOptions,
			GateResult.Summary gateResults) {

		ReviewDetailResponse dto = new ReviewDetailResponse();
		dto.product = product;
		dto.submissionCount = submissionCount;
		dto.weights = weights;
		dto.matchedCampaign = matchedCampaign;
		dto.availableRiskOptions = availableRiskOptions;
		dto.gateResults = gateResults;
		dto.systemGateSummary = gateResults == null ? null : gateResults.toDisplaySummary();

		if (evaluation != null) {
			dto.dataCompleteness = evaluation.getDataCompleteness();
			dto.businessScore = evaluation.getBusinessScore();
			dto.audienceScore = evaluation.getAudienceScore();
			dto.historicalScore = evaluation.getHistoricalScore();
			dto.purchaseScore = evaluation.getPurchaseScore();
			dto.trendScore = evaluation.getTrendScore();
			dto.forecastScore = evaluation.getForecastScore();
			dto.totalScore = evaluation.getTotalScore();
			dto.festivalBoost = evaluation.getFestivalBoost();
			dto.finalScore = evaluation.getFinalScore();
		}

		if (evaluationMode != null) {
			dto.evaluationModeId = evaluationMode.getId();
			dto.evaluationModeName = evaluationMode.getModeName();
			dto.evaluationModeVersion = evaluationMode.getVersion();
		}

		if (aiAnalysis != null) {
			dto.aiSummary = aiAnalysis.getSummary();
			dto.aiRecommendation = aiAnalysis.getRecommendation();
			dto.aiReasons = aiAnalysis.getReasons();
		}

		return dto;
	}

	public ProductResponse getProduct() {
		return product;
	}

	public void setProduct(ProductResponse product) {
		this.product = product;
	}

	public Integer getSubmissionCount() {
		return submissionCount;
	}

	public void setSubmissionCount(Integer submissionCount) {
		this.submissionCount = submissionCount;
	}

	public BigDecimal getDataCompleteness() {
		return dataCompleteness;
	}

	public void setDataCompleteness(BigDecimal dataCompleteness) {
		this.dataCompleteness = dataCompleteness;
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

	public BigDecimal getFestivalBoost() {
		return festivalBoost;
	}

	public void setFestivalBoost(BigDecimal festivalBoost) {
		this.festivalBoost = festivalBoost;
	}

	public MatchedCampaignSnapshot getMatchedCampaign() {
		return matchedCampaign;
	}

	public void setMatchedCampaign(MatchedCampaignSnapshot matchedCampaign) {
		this.matchedCampaign = matchedCampaign;
	}

	public BigDecimal getFinalScore() {
		return finalScore;
	}

	public void setFinalScore(BigDecimal finalScore) {
		this.finalScore = finalScore;
	}

	public String getAiSummary() {
		return aiSummary;
	}

	public void setAiSummary(String aiSummary) {
		this.aiSummary = aiSummary;
	}

	public String getAiRecommendation() {
		return aiRecommendation;
	}

	public void setAiRecommendation(String aiRecommendation) {
		this.aiRecommendation = aiRecommendation;
	}

	public String getAiReasons() {
		return aiReasons;
	}

	public void setAiReasons(String aiReasons) {
		this.aiReasons = aiReasons;
	}

	public List<RiskOptionResponse> getAvailableRiskOptions() {
		return availableRiskOptions;
	}

	public void setAvailableRiskOptions(List<RiskOptionResponse> availableRiskOptions) {
		this.availableRiskOptions = availableRiskOptions;
	}

	public GateResult.Summary getGateResults() {
		return gateResults;
	}

	public String getSystemGateSummary() {
		return systemGateSummary;
	}
}
