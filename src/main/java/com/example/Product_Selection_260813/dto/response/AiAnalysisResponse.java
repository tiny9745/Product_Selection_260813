package com.example.Product_Selection_260813.dto.response;

import java.time.LocalDateTime;

/**
 * GET /api/products/{id}/ai-analysis 與 POST /api/products/{id}/ai-analysis/generate
 * 共用的Response。
 *
 * GET在無快取時，依企劃書規則「無快取則回傳空值」，回傳全部欄位皆為null的
 * 空物件（見AiSelectionService.getAiAnalysisResponse()），不拋錯、不回404——
 * 「這個商品還沒有AI分析」是正常狀態，不是錯誤狀態。
 */
public class AiAnalysisResponse {

	private String summary;
	private String recommendation;
	private String reasons;
	private String modelName;
	private LocalDateTime generatedAt;

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public String getRecommendation() {
		return recommendation;
	}

	public void setRecommendation(String recommendation) {
		this.recommendation = recommendation;
	}

	public String getReasons() {
		return reasons;
	}

	public void setReasons(String reasons) {
		this.reasons = reasons;
	}

	public String getModelName() {
		return modelName;
	}

	public void setModelName(String modelName) {
		this.modelName = modelName;
	}

	public LocalDateTime getGeneratedAt() {
		return generatedAt;
	}

	public void setGeneratedAt(LocalDateTime generatedAt) {
		this.generatedAt = generatedAt;
	}
}
