package com.example.Product_Selection_260813.dto.response;

/**
 * GET /api/dashboard/statistics：商品總數、待審核數、通過數、拒絕數等統計。
 *
 * totalProducts為products表全部筆數（不分審核/品項/候選狀態），
 * approvedCount/rejectedCount分別對應review_status=APPROVED/REJECTED的
 * 不重複商品數，不分candidate_status。
 *
 * pendingCount 只算 candidate_status=CANDIDATE 且 review_status=PENDING（與待審清單同口徑）。
 * 2026-09-29：熱度建議清單移除，aiSuggestedPendingCount 欄位一併移除（API contract 變更，前端同步）。
 *
 * <b>2026-09-29：依角色切換口徑（scope）</b>，比照 DashboardConversionRateResponse：
 * <ul>
 * <li>MANAGER → {@link #SCOPE_COMPANY}：上述全公司口徑，數字與修正前相同。</li>
 * <li>PURCHASER → {@link #SCOPE_PERSONAL}：四個數字都只算 created_by＝登入者的商品，
 * 其餘判斷條件完全相同。</li>
 * </ul>
 * 前端依 scope 決定文案與連結要不要帶 createdByMe，不要自行用角色推斷。
 */
public class DashboardStatisticsResponse {

	public static final String SCOPE_PERSONAL = DashboardConversionRateResponse.SCOPE_PERSONAL;
	public static final String SCOPE_COMPANY = DashboardConversionRateResponse.SCOPE_COMPANY;

	private String scope;

	private long totalProducts;
	private long pendingCount;
	private long approvedCount;
	private long rejectedCount;

	public String getScope() {
		return scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}

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
