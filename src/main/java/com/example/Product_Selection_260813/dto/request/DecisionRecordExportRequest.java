package com.example.Product_Selection_260813.dto.request;

import java.time.LocalDate;

import com.example.Product_Selection_260813.enums.ReviewRecordReviewStatus;

/**
 * POST /api/reviews/decision-records/export 的 body（2026-09-26）：與決策紀錄頁的查詢條件相同
 * （GET /api/reviews/decision-records），皆選填。排序固定為審核時間新到舊。
 */
public class DecisionRecordExportRequest {

	private ReviewRecordReviewStatus reviewResult;
	private String keyword;
	private LocalDate reviewedFrom;
	private LocalDate reviewedTo;

	public ReviewRecordReviewStatus getReviewResult() { return reviewResult; }
	public void setReviewResult(ReviewRecordReviewStatus reviewResult) { this.reviewResult = reviewResult; }
	public String getKeyword() { return keyword; }
	public void setKeyword(String keyword) { this.keyword = keyword; }
	public LocalDate getReviewedFrom() { return reviewedFrom; }
	public void setReviewedFrom(LocalDate reviewedFrom) { this.reviewedFrom = reviewedFrom; }
	public LocalDate getReviewedTo() { return reviewedTo; }
	public void setReviewedTo(LocalDate reviewedTo) { this.reviewedTo = reviewedTo; }
}
