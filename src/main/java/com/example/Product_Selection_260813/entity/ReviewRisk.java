package com.example.Product_Selection_260813.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name="review_risks")
public class ReviewRisk {

    @EmbeddedId
    private ReviewRiskId id;

    public ReviewRisk() {
    }

    public ReviewRiskId getId() {
        return id;
    }

    public void setId(ReviewRiskId id) {
        this.id = id;
    }
}