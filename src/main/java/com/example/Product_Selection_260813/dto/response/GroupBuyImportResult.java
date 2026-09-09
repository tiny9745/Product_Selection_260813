package com.example.Product_Selection_260813.dto.response;

import java.util.List;

/**
 * CSV 匯入結果。
 *
 * <b>採全有或全無</b>：任何一列有錯就整批拒絕，不做「部分成功部分失敗」。
 * 理由是部分成功會讓使用者難以判斷資料庫現在是什麼狀態——他得逐筆比對才知道
 * 哪些進去了、哪些沒有，而修正後重匯又會造成重複。整批拒絕並列出所有錯誤列，
 * 使用者改完一次重送即可。
 */
public record GroupBuyImportResult(
		boolean success,
		String importBatchId,
		int totalRows,
		int importedRows,
		List<RowError> errors) {

	/**
	 * @param rowNumber CSV 的實際列號（含標頭列，從 1 起算），方便使用者
	 *                  直接在 Excel 裡跳到那一列，不用自己換算
	 */
	public record RowError(int rowNumber, String field, String message) {
	}

	public static GroupBuyImportResult ofSuccess(String batchId, int totalRows) {
		return new GroupBuyImportResult(true, batchId, totalRows, totalRows, List.of());
	}

	public static GroupBuyImportResult ofFailure(int totalRows, List<RowError> errors) {
		return new GroupBuyImportResult(false, null, totalRows, 0, errors);
	}
}
