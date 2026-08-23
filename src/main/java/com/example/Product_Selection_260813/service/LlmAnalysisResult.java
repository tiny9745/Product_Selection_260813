package com.example.Product_Selection_260813.service;

/**
 * {@link LlmAnalysisService#generate}的回傳值，AiSelectionService與LLM廠商實作
 * 之間傳遞資料的中介物件，不直接對應任何資料表或API回應格式。
 */
public class LlmAnalysisResult {

	private String summary;
	private String recommendation;
	private String reasons;
	private String modelName;

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
}
