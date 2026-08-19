package com.example.Product_Selection_260813.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ReviewRiskId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(name = "risk_option_id", nullable = false)
    private Long riskOptionId;

    public ReviewRiskId() {
    }

    public ReviewRiskId(Long reviewId, Long riskOptionId) {
        this.reviewId = reviewId;
        this.riskOptionId = riskOptionId;
    }

    public Long getReviewId() {
        return reviewId;
    }

    public void setReviewId(Long reviewId) {
        this.reviewId = reviewId;
    }

    public Long getRiskOptionId() {
        return riskOptionId;
    }

    public void setRiskOptionId(Long riskOptionId) {
        this.riskOptionId = riskOptionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof ReviewRiskId)) {
            return false;
        }

        ReviewRiskId that = (ReviewRiskId) o;

        return Objects.equals(reviewId, that.reviewId)
                && Objects.equals(riskOptionId, that.riskOptionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reviewId, riskOptionId);
    }
}