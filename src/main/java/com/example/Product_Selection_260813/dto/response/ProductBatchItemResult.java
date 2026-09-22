package com.example.Product_Selection_260813.dto.response;

/**
 * POST /api/products/batch 回應陣列中的單一列結果。
 *
 * 批次新增採「逐列各自成功或失敗」（見 ProductService.createProductsBatch()
 * 類別註解），不是全部成功才回應、其中一列失敗就整批 400——前端要能拿到
 * 「這 30 列裡，第幾列成功、第幾列為什麼失敗」的完整清單，讓使用者只需要
 * 修正失敗的列重新送出，不用整批重新匯入。
 *
 * success=true 時 product 一定有值、errorMessage 一定是 null；
 * success=false 時反過來，product 一定是 null。warningMessage 與
 * success 無關，任何一列都可能有（目前唯一情境：商品建立成功，但這一列
 * 指定的圖片檔名在這次夾帶的檔案清單裡找不到對應檔案）。
 */
public class ProductBatchItemResult {

	private Integer rowNumber;
	private boolean success;
	private String errorMessage;
	private String warningMessage;
	private ProductResponse product;

	public Integer getRowNumber() {
		return rowNumber;
	}

	public void setRowNumber(Integer rowNumber) {
		this.rowNumber = rowNumber;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public String getWarningMessage() {
		return warningMessage;
	}

	public void setWarningMessage(String warningMessage) {
		this.warningMessage = warningMessage;
	}

	public ProductResponse getProduct() {
		return product;
	}

	public void setProduct(ProductResponse product) {
		this.product = product;
	}
}
