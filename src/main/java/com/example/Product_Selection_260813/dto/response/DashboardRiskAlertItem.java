package com.example.Product_Selection_260813.dto.response;

import java.util.List;

/**
 * GET /api/dashboard/risk-alerts 清單裡的單一項目。
 *
 * 資料來源為該商品最新一筆ai_analyses.summary／reasons文字，命中
 * risk_options.alert_keywords（逗號分隔關鍵字組）其中任一詞即列入。
 * matchedKeywords記錄實際命中的關鍵字，方便畫面標示「為什麼這筆被示警」。
 */
public class DashboardRiskAlertItem {

	private Long productId;
	private String productName;
	private List<String> matchedKeywords;
	private String aiReasons;

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

	public List<String> getMatchedKeywords() {
		return matchedKeywords;
	}

	public void setMatchedKeywords(List<String> matchedKeywords) {
		this.matchedKeywords = matchedKeywords;
	}

	public String getAiReasons() {
		return aiReasons;
	}

	public void setAiReasons(String aiReasons) {
		this.aiReasons = aiReasons;
	}
}
