package com.example.Product_Selection_260813.enums;

public enum ProductCandidateStatus {
	AI_SUGGESTED("AI候選"),//
	CANDIDATE("一般候選");
	
	private final String productCandidateStatus;

	private ProductCandidateStatus(String productCandidateStatus) {
		this.productCandidateStatus = productCandidateStatus;
	}

	public String getProductCandidateStatus() {
		return productCandidateStatus;
	}
}
