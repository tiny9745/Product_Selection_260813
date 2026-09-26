package com.example.Product_Selection_260813.dto.response;

import java.time.LocalDate;

/**
 * GET /api/products/submission-batches 的一筆：一個送審批次（同一人、同一曆日送審）。
 *
 * @param batchId       前端下拉的值，原樣帶回 GET /api/products 與匯出的 submissionBatch；
 *                      "NONE" 代表「（無批次資料）」
 * @param submittedDate 送審日期；NONE 為 null
 * @param submitterId   送審人 id；NONE 為 null
 * @param submitterName 送審人姓名；NONE 為 null
 * @param productCount  該批次的商品數（正式候選，不分審核狀態）
 */
public record SubmissionBatchResponse(String batchId, LocalDate submittedDate, Long submitterId,
		String submitterName, long productCount) {
}
