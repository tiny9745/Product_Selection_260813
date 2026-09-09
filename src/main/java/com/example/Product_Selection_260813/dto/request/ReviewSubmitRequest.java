package com.example.Product_Selection_260813.dto.request;

import java.util.List;

import com.example.Product_Selection_260813.constants.ValidationMessage;
import com.example.Product_Selection_260813.enums.ReviewRecordReviewStatus;

import jakarta.validation.constraints.NotNull;

/**
 * POST /api/reviews 的 Request Body。
 *
 * 對應企劃書「選品審核」：管理提交人工風險評估、審核留言及核准／拒絕結果，
 * ReviewService建立新審核紀錄並保存本次審核資料快照（見五、選品審核）。
 *
 * productId放在Body而非路徑（API定義為POST /api/reviews，非POST /api/reviews/{productId}）。
 *
 * riskOptionIds／reviewComment刻意不加@NotEmpty／@NotBlank：
 * 人工風險評估是「複選」，核准商品可能一項風險都不勾；審核留言功能樹狀圖只寫
 * 「保存管理層本次審核意見」，未要求必填，故Service層也不做必填檢查
 * ——若日後確認「其他」風險選項需要強制搭配留言，屬於另一項待確認的業務規則，
 * 目前不在此處臆測。
 */
public class ReviewSubmitRequest {

	@NotNull(message = ValidationMessage.REVIEW_PRODUCT_ID_NULL)
	private Long productId;

	@NotNull(message = ValidationMessage.REVIEW_STATUS_NULL)
	private ReviewRecordReviewStatus reviewStatus;

	private List<Long> riskOptionIds;

	/**
	 * 系統原本建議勾選的風險項目（Gate 判定不通過而預先勾選的）。
	 *
	 * <b>主管取消勾選的項目也要留在這份清單裡一起送出。</b>
	 * 如果只送最終勾選的 riskOptionIds，後端就無法區分「系統沒建議」和
	 * 「系統建議了但被主管推翻」——而後者是稽核價值最高的一筆紀錄：
	 * 它記錄了「系統說有問題，但主管認為可以」。
	 *
	 * 未送出時視為空清單（沒有任何系統建議），全部項目都會被記為主管手動勾選。
	 * 這讓不支援 Gate 的舊版前端仍能正常送審，行為與改版前一致。
	 */
	private List<Long> systemSuggestedRiskOptionIds;


	private String reviewComment;

	public Long getProductId() {
		return productId;
	}

	public void setProductId(Long productId) {
		this.productId = productId;
	}

	public ReviewRecordReviewStatus getReviewStatus() {
		return reviewStatus;
	}

	public void setReviewStatus(ReviewRecordReviewStatus reviewStatus) {
		this.reviewStatus = reviewStatus;
	}

	public List<Long> getRiskOptionIds() {
		return riskOptionIds;
	}

	public void setRiskOptionIds(List<Long> riskOptionIds) {
		this.riskOptionIds = riskOptionIds;
	}

	public String getReviewComment() {
		return reviewComment;
	}

	public void setReviewComment(String reviewComment) {
		this.reviewComment = reviewComment;
	}

	public List<Long> getSystemSuggestedRiskOptionIds() {
		return systemSuggestedRiskOptionIds;
	}

	public void setSystemSuggestedRiskOptionIds(List<Long> systemSuggestedRiskOptionIds) {
		this.systemSuggestedRiskOptionIds = systemSuggestedRiskOptionIds;
	}
}
