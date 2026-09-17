package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;

/**
 * GET /api/dashboard/recommendations 清單裡的單一項目。
 *
 * 依企劃書「二、前端功能樹狀圖」AI推薦Top10備註：
 * 「已審核拒絕商品重新入榜時，附加『曾被拒絕．第N次送審』標籤、上次拒絕審核
 * 留言摘要」——reentryLabel／lastRejectionComment在submissionCount>1時才有值，
 * 首次送審（submissionCount=1）時皆為null。
 *
 * ⚠️ 2026-09-17補上recommendationReason：先前這裡完全沒有推薦理由文字，
 * 前端固定寫死顯示「—」。原本的顧慮（見git歷史／舊註解）是企劃書提到的
 * 「動態建議文案」沒有具體規則，怕自己虛構行銷文案。但AiAnalysis實體
 * （ai_analyses表）本來就有真實的、AI分析當下產生的recommendation欄位
 * （見AiSelectionService.getLatestAnalysis()，審核詳情頁「AI推薦摘要」用的
 * 就是同一份資料）——這不是要虛構新文案，是把已經存在、已經算好的AI推薦
 * 理由接進這支API而已，跟原本顧慮的情境不一樣。沒有分析紀錄時仍是null，
 * 前端一樣顯示「—」，但這次是「這件商品真的還沒做過AI分析」，不是「這個
 * 功能沒做」。
 */
public class DashboardRecommendationItem {

	private Long productId;
	private String productName;
	/**
	 * ⚠️ 這次補上：本方法對應的查詢（findTopRecommendations）已在記憶體裡
	 * 持有 Product 物件，productTypeId 直接讀取即可，不需要額外查詢。
	 * 前端拿這個 id 去對照 GET /api/settings/product-types 顯示分類名稱。
	 */
	private Long productTypeId;
	private BigDecimal finalScore;
	/**
	 * ⚠️ 這次補上：ProductEvaluation 物件本來就會被查出來計算 finalScore，
	 * 這裡只是多讀一個既有欄位，不會多一次查詢。
	 */
	private BigDecimal dataCompleteness;
	private Integer submissionCount;
	private String reentryLabel;
	private String lastRejectionComment;
	/** 見上方類別註解——來自 ai_analyses.recommendation，該商品尚無分析紀錄時為 null。 */
	private String recommendationReason;

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

	public Long getProductTypeId() {
		return productTypeId;
	}

	public void setProductTypeId(Long productTypeId) {
		this.productTypeId = productTypeId;
	}

	public BigDecimal getDataCompleteness() {
		return dataCompleteness;
	}

	public void setDataCompleteness(BigDecimal dataCompleteness) {
		this.dataCompleteness = dataCompleteness;
	}

	public BigDecimal getFinalScore() {
		return finalScore;
	}

	public void setFinalScore(BigDecimal finalScore) {
		this.finalScore = finalScore;
	}

	public Integer getSubmissionCount() {
		return submissionCount;
	}

	public void setSubmissionCount(Integer submissionCount) {
		this.submissionCount = submissionCount;
	}

	public String getReentryLabel() {
		return reentryLabel;
	}

	public void setReentryLabel(String reentryLabel) {
		this.reentryLabel = reentryLabel;
	}

	public String getLastRejectionComment() {
		return lastRejectionComment;
	}

	public void setLastRejectionComment(String lastRejectionComment) {
		this.lastRejectionComment = lastRejectionComment;
	}

	public String getRecommendationReason() {
		return recommendationReason;
	}

	public void setRecommendationReason(String recommendationReason) {
		this.recommendationReason = recommendationReason;
	}
}