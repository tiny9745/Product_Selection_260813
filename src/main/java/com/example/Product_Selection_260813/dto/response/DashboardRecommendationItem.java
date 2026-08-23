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
 * 「動態建議文案」（企劃書原文提及但未給出具體內容範例／規則）本次不實作，
 * 避免虛構未定案的文案內容——待團隊確認實際文案規則後再補上，不在此處自行
 * 編造行銷用語。
 */
public class DashboardRecommendationItem {

	private Long productId;
	private String productName;
	private BigDecimal finalScore;
	private Integer submissionCount;
	private String reentryLabel;
	private String lastRejectionComment;

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
}
