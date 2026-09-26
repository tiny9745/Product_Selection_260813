package com.example.Product_Selection_260813.repository;

import java.time.LocalDateTime;

import com.example.Product_Selection_260813.enums.ProductCandidateStatus;
import com.example.Product_Selection_260813.enums.ProductItemStatus;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;

/**
 * 品項清單搜尋條件（ProductRepository.search() 的參數物件）。
 *
 * 2026-09 CSV 匯出新增了送審批次、審核日期、未曾匯出三組條件，原本 7 個位置參數
 * 的 search() 會膨脹到十幾個，呼叫端很容易把兩個 LocalDateTime 對調而不自知。
 * 品項清單（GET /api/products）與匯出（POST /api/products/export）共用同一個物件，
 * 保證「畫面上看到的清單」與「匯出的內容」用的是同一套篩選邏輯。
 *
 * 所有欄位 null＝不篩選。日期區間一律已由 Service 換算成「起（含）～迄（不含）」
 * 或既有的閉區間（updatedFrom/updatedTo，沿用原本語意不動）。
 *
 * @param submittedBy／submittedFrom／submittedToExclusive 送審批次（見 SubmissionBatchId）
 * @param withoutSubmissionBatch TRUE＝只要沒有送審批次資料的商品（submittedAt IS NULL）
 * @param reviewedFrom／reviewedToExclusive 以「最新一筆審核紀錄」的審核時間篩選
 * @param neverExported TRUE＝只要從未匯出過的商品
 */
public record ProductSearchCriteria(
		ProductReviewStatus reviewStatus,
		ProductItemStatus itemStatus,
		ProductCandidateStatus candidateStatus,
		Long productTypeId,
		String keyword,
		LocalDateTime updatedFrom,
		LocalDateTime updatedTo,
		Long submittedBy,
		LocalDateTime submittedFrom,
		LocalDateTime submittedToExclusive,
		Boolean withoutSubmissionBatch,
		LocalDateTime reviewedFrom,
		LocalDateTime reviewedToExclusive,
		Boolean neverExported) {

	/** 覆寫審核狀態（匯出固定只取 APPROVED）。 */
	public ProductSearchCriteria withReviewStatus(ProductReviewStatus status) {
		return new ProductSearchCriteria(status, itemStatus, candidateStatus, productTypeId, keyword, updatedFrom,
				updatedTo, submittedBy, submittedFrom, submittedToExclusive, withoutSubmissionBatch, reviewedFrom,
				reviewedToExclusive, neverExported);
	}

	/** 覆寫候選狀態（Service 層套用「預設只看 CANDIDATE」的業務預設值）。 */
	public ProductSearchCriteria withCandidateStatus(ProductCandidateStatus status) {
		return new ProductSearchCriteria(reviewStatus, itemStatus, status, productTypeId, keyword, updatedFrom,
				updatedTo, submittedBy, submittedFrom, submittedToExclusive, withoutSubmissionBatch, reviewedFrom,
				reviewedToExclusive, neverExported);
	}
}
