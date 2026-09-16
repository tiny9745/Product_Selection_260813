package com.example.Product_Selection_260813.dto.response;

/**
 * GET /api/dashboard/statistics：商品總數、待審核數、通過數、拒絕數等統計。
 *
 * totalProducts為products表全部筆數（不分審核/品項/候選狀態），
 * approvedCount/rejectedCount分別對應review_status=APPROVED/REJECTED的
 * 不重複商品數，不分candidate_status。
 *
 * pendingCount／aiSuggestedPendingCount：兩者互斥（2026-09-16修正），
 * pendingCount只算candidate_status=CANDIDATE且review_status=PENDING，
 * aiSuggestedPendingCount只算candidate_status=AI_SUGGESTED且
 * review_status=PENDING，兩者相加才等於「全部review_status=PENDING」的
 * 商品數。AI建議尚未轉正候選的商品不該被視為「待人工審核」（見
 * DashboardService.getStatistics()／ReviewService.getPendingReviews()
 * 的說明），因此獨立成一個不重疊的欄位。
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
