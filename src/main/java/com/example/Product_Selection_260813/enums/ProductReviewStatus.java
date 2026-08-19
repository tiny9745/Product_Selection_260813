package com.example.Product_Selection_260813.enums;

public enum ProductReviewStatus {
	PENDING("尚未審核"),//
	REJECTED("已審核拒絕"),//
	APPROVED("已審核通過");
	
	private final String productReviewStatus;

	private ProductReviewStatus(String productReviewStatus) {
		this.productReviewStatus = productReviewStatus;
	}

	public String getProductReviewStatus() {
		return productReviewStatus;
	}
	
	
}
