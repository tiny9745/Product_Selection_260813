package com.example.Product_Selection_260813.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/group-buy-records/claim 的 Request Body。
 *
 * 把選定的歷史紀錄（product_id 目前為 null）連結到剛建立或正在編輯的商品。
 * 這是 group_buy_records 表刻意「不提供單筆編輯」原則下的一個窄範圍例外
 * ——只允許改動 product_id 這一個外鍵欄位，且只能從 null 連到一筆商品，
 * 不是開放任意編輯；見 GroupBuyRecordService.claimRecords() 的驗證邏輯。
 */
public class ClaimGroupBuyRecordsRequest {

	@NotNull(message = "商品 id 必填")
	private Long productId;

	@NotEmpty(message = "至少要選一筆要認領的歷史紀錄")
	private List<Long> groupBuyRecordIds;

	public Long getProductId() {
		return productId;
	}

	public void setProductId(Long productId) {
		this.productId = productId;
	}

	public List<Long> getGroupBuyRecordIds() {
		return groupBuyRecordIds;
	}

	public void setGroupBuyRecordIds(List<Long> groupBuyRecordIds) {
		this.groupBuyRecordIds = groupBuyRecordIds;
	}
}
