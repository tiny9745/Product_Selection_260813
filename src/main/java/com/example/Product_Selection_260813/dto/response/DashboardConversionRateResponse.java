package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;

/**
 * GET /api/dashboard/conversion-rate：選品轉換率（採方案B：商品層級核准率）。
 *
 * 公式：分子＝目前review_status=APPROVED的不重複商品數；
 *      分母＝submission_count>0（曾送審過）的不重複商品數。
 * 以「商品」為單位計算，一個商品不管被拒絕重送幾次只算一次
 * （企劃書「二、前端功能樹狀圖」選品轉換率備註）。
 *
 * ratePercentage分母為0時（尚無任何商品送審過）回傳null，不強制算出
 * 一個沒有意義的數字，也不拋錯——這是系統剛啟用、demo初始狀態的正常情況。
 */
public class DashboardConversionRateResponse {

	private long approvedCount;
	private long submittedCount;
	private BigDecimal ratePercentage;

	public long getApprovedCount() {
		return approvedCount;
	}

	public void setApprovedCount(long approvedCount) {
		this.approvedCount = approvedCount;
	}

	public long getSubmittedCount() {
		return submittedCount;
	}

	public void setSubmittedCount(long submittedCount) {
		this.submittedCount = submittedCount;
	}

	public BigDecimal getRatePercentage() {
		return ratePercentage;
	}

	public void setRatePercentage(BigDecimal ratePercentage) {
		this.ratePercentage = ratePercentage;
	}
}
