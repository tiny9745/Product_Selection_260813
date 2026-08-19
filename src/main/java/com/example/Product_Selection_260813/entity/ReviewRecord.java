package com.example.Product_Selection_260813.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.example.Product_Selection_260813.enums.ReviewRecordReviewStatus;
import com.example.Product_Selection_260813.json.MatchedCampaignSnapshot;
import com.example.Product_Selection_260813.json.ProductSnapshot;
import com.example.Product_Selection_260813.json.TrendSnapshot;
import com.example.Product_Selection_260813.json.WeightSnapshot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name="review_records")
public class ReviewRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(name = "submission_count", nullable = false)
    private Integer submissionCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false)
    private ReviewRecordReviewStatus reviewStatus;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;

    @Column(name = "evaluation_mode_id")
    private Long evaluationModeId;

    @Column(name = "evaluation_mode_name", length = 50)
    private String evaluationModeName;

    @Column(name = "evaluation_mode_version")
    private Integer evaluationModeVersion;

    @Column(name = "total_score", precision = 5, scale = 2)
    private BigDecimal totalScore;

    @Column(name = "festival_boost_snapshot", precision = 5, scale = 2)
    private BigDecimal festivalBoostSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matched_campaign_snapshot", columnDefinition = "json")
    private MatchedCampaignSnapshot matchedCampaignSnapshot;

    @Column(name = "final_score_snapshot", precision = 5, scale = 2)
    private BigDecimal finalScoreSnapshot;

    @Column(name = "data_completeness", precision = 5, scale = 2)
    private BigDecimal dataCompleteness;

    @Column(name = "business_score", precision = 5, scale = 2)
    private BigDecimal businessScore;

    @Column(name = "audience_score", precision = 5, scale = 2)
    private BigDecimal audienceScore;

    @Column(name = "historical_score", precision = 5, scale = 2)
    private BigDecimal historicalScore;

    @Column(name = "purchase_score", precision = 5, scale = 2)
    private BigDecimal purchaseScore;

    @Column(name = "trend_score", precision = 5, scale = 2)
    private BigDecimal trendScore;

    @Column(name = "forecast_score", precision = 5, scale = 2)
    private BigDecimal forecastScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "weight_snapshot", columnDefinition = "json")
    private WeightSnapshot weightSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "product_snapshot", columnDefinition = "json")
    private ProductSnapshot productSnapshot;

    @Column(name = "ai_summary_snapshot", columnDefinition = "TEXT")
    private String aiSummarySnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trend_snapshot", columnDefinition = "json")
    private TrendSnapshot trendSnapshot;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    public Integer getSubmissionCount() {
        return submissionCount;
    }

    public void setSubmissionCount(Integer submissionCount) {
        this.submissionCount = submissionCount;
    }

    public ReviewRecordReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(ReviewRecordReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Long getEvaluationModeId() {
        return evaluationModeId;
    }

    public void setEvaluationModeId(Long evaluationModeId) {
        this.evaluationModeId = evaluationModeId;
    }

    public String getEvaluationModeName() {
        return evaluationModeName;
    }

    public void setEvaluationModeName(String evaluationModeName) {
        this.evaluationModeName = evaluationModeName;
    }

    public Integer getEvaluationModeVersion() {
        return evaluationModeVersion;
    }

    public void setEvaluationModeVersion(Integer evaluationModeVersion) {
        this.evaluationModeVersion = evaluationModeVersion;
    }

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(BigDecimal totalScore) {
        this.totalScore = totalScore;
    }

    public BigDecimal getFestivalBoostSnapshot() {
        return festivalBoostSnapshot;
    }

    public void setFestivalBoostSnapshot(BigDecimal festivalBoostSnapshot) {
        this.festivalBoostSnapshot = festivalBoostSnapshot;
    }

    public MatchedCampaignSnapshot getMatchedCampaignSnapshot() {
        return matchedCampaignSnapshot;
    }

    public void setMatchedCampaignSnapshot(
            MatchedCampaignSnapshot matchedCampaignSnapshot) {
        this.matchedCampaignSnapshot = matchedCampaignSnapshot;
    }

    public BigDecimal getFinalScoreSnapshot() {
        return finalScoreSnapshot;
    }

    public void setFinalScoreSnapshot(BigDecimal finalScoreSnapshot) {
        this.finalScoreSnapshot = finalScoreSnapshot;
    }

    public BigDecimal getDataCompleteness() {
        return dataCompleteness;
    }

    public void setDataCompleteness(BigDecimal dataCompleteness) {
        this.dataCompleteness = dataCompleteness;
    }

    public BigDecimal getBusinessScore() {
        return businessScore;
    }

    public void setBusinessScore(BigDecimal businessScore) {
        this.businessScore = businessScore;
    }

    public BigDecimal getAudienceScore() {
        return audienceScore;
    }

    public void setAudienceScore(BigDecimal audienceScore) {
        this.audienceScore = audienceScore;
    }

    public BigDecimal getHistoricalScore() {
        return historicalScore;
    }

    public void setHistoricalScore(BigDecimal historicalScore) {
        this.historicalScore = historicalScore;
    }

    public BigDecimal getPurchaseScore() {
        return purchaseScore;
    }

    public void setPurchaseScore(BigDecimal purchaseScore) {
        this.purchaseScore = purchaseScore;
    }

    public BigDecimal getTrendScore() {
        return trendScore;
    }

    public void setTrendScore(BigDecimal trendScore) {
        this.trendScore = trendScore;
    }

    public BigDecimal getForecastScore() {
        return forecastScore;
    }

    public void setForecastScore(BigDecimal forecastScore) {
        this.forecastScore = forecastScore;
    }

    public WeightSnapshot getWeightSnapshot() {
        return weightSnapshot;
    }

    public void setWeightSnapshot(WeightSnapshot weightSnapshot) {
        this.weightSnapshot = weightSnapshot;
    }

    public ProductSnapshot getProductSnapshot() {
        return productSnapshot;
    }

    public void setProductSnapshot(ProductSnapshot productSnapshot) {
        this.productSnapshot = productSnapshot;
    }

    public String getAiSummarySnapshot() {
        return aiSummarySnapshot;
    }

    public void setAiSummarySnapshot(String aiSummarySnapshot) {
        this.aiSummarySnapshot = aiSummarySnapshot;
    }

    public TrendSnapshot getTrendSnapshot() {
        return trendSnapshot;
    }

    public void setTrendSnapshot(TrendSnapshot trendSnapshot) {
        this.trendSnapshot = trendSnapshot;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}