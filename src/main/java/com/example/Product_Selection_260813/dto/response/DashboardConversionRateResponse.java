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
 *
 * <b>2026-09-23 分支整併：依角色切換計算口徑（scope）</b>
 * <ul>
 * <li>PURCHASER（操作人員）→ {@link #SCOPE_PERSONAL}：分子分母都只計
 * createdBy = 目前登入者的商品，供選品人員自我檢視「我建立的選品通過率
 * 是不是偏低」。同一支端點、不同帳號呼叫會拿到不同數字，這是設計行為，
 * 不是快取或計算錯誤。</li>
 * <li>MANAGER（管理人員）→ {@link #SCOPE_COMPANY}：維持原本的全公司口徑。
 * 管理人員通常不自己建立選品，若也套用個人口徑，儀表板上會恆為「尚無
 * 資料」，對主管沒有參考價值。</li>
 * </ul>
 * 前端依 scope 決定文案（「我的選品」或「全公司選品」），不要自行用角色
 * 重新推導一次，避免前後端規則分歧。
 *
 * ⚠️ 個人口徑以 createdBy（建立者）認定歸屬，不是「送審者」：A 建立、
 * B 重新送審的商品仍算在 A 身上；批次匯入的商品算在匯入者身上。
 */
public class DashboardConversionRateResponse {

	/** 只計算目前登入者建立的商品。 */
	public static final String SCOPE_PERSONAL = "PERSONAL";
	/** 全公司所有商品。 */
	public static final String SCOPE_COMPANY = "COMPANY";

	private String scope;
	private long approvedCount;
	private long submittedCount;
	private BigDecimal ratePercentage;

	public String getScope() {
		return scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}

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
