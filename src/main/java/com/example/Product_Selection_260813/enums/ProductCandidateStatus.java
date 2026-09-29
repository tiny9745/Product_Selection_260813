package com.example.Product_Selection_260813.enums;

/**
 * 候選狀態。2026-09-29 起只會是 CANDIDATE：熱度建議清單與熱度規則選品批次移除，
 * 既有 AI_SUGGESTED 資料由 V36 轉回 CANDIDATE。
 */
public enum ProductCandidateStatus {
	/**
	 * @deprecated 2026-09-29 起不再產生。保留這個值只是為了與資料庫 candidate_status 的 ENUM 定義一致
	 *             （避免 Hibernate schema validate 風險，見 V36 說明）；程式不應再使用。
	 */
	@Deprecated
	AI_SUGGESTED("AI候選"),//
	CANDIDATE("一般候選");
	
	private final String productCandidateStatus;

	private ProductCandidateStatus(String productCandidateStatus) {
		this.productCandidateStatus = productCandidateStatus;
	}

	public String getProductCandidateStatus() {
		return productCandidateStatus;
	}
}
