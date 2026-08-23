package com.example.Product_Selection_260813.dto.response;

/**
 * GET /api/dashboard/statistics：商品總數、待審核數、通過數、拒絕數等統計。
 *
 * totalProducts為products表全部筆數（不分審核/品項/候選狀態），
 * 其餘三項分別對應review_status=PENDING/APPROVED/REJECTED的不重複商品數。
 */
public class DashboardStatisticsResponse {

	private long totalProducts;
	private long pendingCount;
	private long approvedCount;
	private long rejectedCount;

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
}
