package com.example.Product_Selection_260813.dto.response;

import java.util.List;

/**
 * POST /api/products/batch 的回應本體（包在 ApiResponse.data 裡）。
 *
 * 整批請求本身一律回 200（除非 items 通過不了 Bean Validation，例如
 * 整批是空陣列，那種情況連逐列處理都還沒開始，才是真的 400）——只要
 * 開始逐列處理，不論每列成功或失敗都算「這次批次請求本身成功執行完畢」，
 * 個別列的結果請看 results，不要把 totalCount/successCount/failCount
 * 這三個數字之外的語意過度解讀成整批的成敗。
 */
public class ProductBatchCreateResponse {

	private int totalCount;
	private int successCount;
	private int failCount;
	private List<ProductBatchItemResult> results;

	public int getTotalCount() {
		return totalCount;
	}

	public void setTotalCount(int totalCount) {
		this.totalCount = totalCount;
	}

	public int getSuccessCount() {
		return successCount;
	}

	public void setSuccessCount(int successCount) {
		this.successCount = successCount;
	}

	public int getFailCount() {
		return failCount;
	}

	public void setFailCount(int failCount) {
		this.failCount = failCount;
	}

	public List<ProductBatchItemResult> getResults() {
		return results;
	}

	public void setResults(List<ProductBatchItemResult> results) {
		this.results = results;
	}
}
