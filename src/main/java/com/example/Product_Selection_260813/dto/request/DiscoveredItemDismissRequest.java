package com.example.Product_Selection_260813.dto.request;

import com.example.Product_Selection_260813.enums.DiscoveryDismissReason;

import jakarta.validation.constraints.Size;

/** POST /api/discoveries/{id}/dismiss 的 body（整個 body 可省略）。 */
public class DiscoveredItemDismissRequest {

	/**
	 * 略過原因代碼（第二階段）。會回饋到下一次探索：NOT_A_PRODUCT 當作 AI 抽取的反例，其餘當作適配評分的反例。
	 * 不帶時視為 OTHER——舊版前端或直接呼叫 API 不會因此失敗。
	 */
	private DiscoveryDismissReason reasonCode;

	/** 補充說明，選填。 */
	@Size(max = 255, message = "略過原因不可超過 255 字")
	private String reason;

	public DiscoveryDismissReason getReasonCode() {
		return reasonCode;
	}

	public void setReasonCode(DiscoveryDismissReason reasonCode) {
		this.reasonCode = reasonCode;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}
}
