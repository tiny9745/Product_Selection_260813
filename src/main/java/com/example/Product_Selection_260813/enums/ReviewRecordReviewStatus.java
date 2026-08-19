package com.example.Product_Selection_260813.enums;

public enum ReviewRecordReviewStatus {
	APPROVED("審核通過"), //
	REJECTED("審核拒絕");
	
	private final String reviewRecordReviewStatus;

	private ReviewRecordReviewStatus(String reviewRecordReviewStatus) {
		this.reviewRecordReviewStatus = reviewRecordReviewStatus;
	}

	public String getReviewRecordReviewStatus() {
		return reviewRecordReviewStatus;
	}
}
