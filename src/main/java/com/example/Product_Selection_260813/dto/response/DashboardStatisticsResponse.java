package com.example.Product_Selection_260813.dto.response;

/**
 * GET /api/dashboard/statistics：商品總數、待審核數、通過數、拒絕數等統計。
 *
 * totalProducts為products表全部筆數（不分審核/品項/候選狀態），
 * pendingCount/approvedCount/rejectedCount三項分別對應
 * review_status=PENDING/APPROVED/REJECTED的不重複商品數，不分candidate_status。
 *
 * aiSuggestedPendingCount：pendingCount的子集，candidate_status=AI_SUGGESTED
 * 且review_status=PENDING的商品數。這批商品計入pendingCount，但不會出現在
 * 品項管理主清單（該清單預設只查candidate_status=CANDIDATE），是兩邊清單
 * 「候選品項數」與「待人工審核數」對不起來的主要原因之一，獨立揭露方便前端
 * 在卡片上說明差異來源。
 */
public class DashboardStatisticsResponse {

	private long totalProducts;
	private long pendingCount;
	private long approvedCount;
	private long rejectedCount;
	private long aiSuggestedPendingCount;

	public long getTotalProducts() {
		return totalProducts;
	}

	public void setTotalProducts(long totalProducts) {
		this.totalProducts = totalProducts;
	}

	public long getPendingCount() {
		return pendingCount;
	}

	public void setPendingCount(long pendingCount) {
		this.pendingCount = pendingCount;
	}

	public long getApprovedCount() {
		return approvedCount;
	}

	public void setApprovedCount(long approvedCount) {
		this.approvedCount = approvedCount;
	}

	public long getRejectedCount() {
		return rejectedCount;
	}

	public void setRejectedCount(long rejectedCount) {
		this.rejectedCount = rejectedCount;
	}

	public long getAiSuggestedPendingCount() {
		return aiSuggestedPendingCount;
	}

	public void setAiSuggestedPendingCount(long aiSuggestedPendingCount) {
		this.aiSuggestedPendingCount = aiSuggestedPendingCount;
	}
}
