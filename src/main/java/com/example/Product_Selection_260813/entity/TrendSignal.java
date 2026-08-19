package com.example.Product_Selection_260813.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.Product_Selection_260813.enums.TrendSignalTrendDirection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class TrendSignal {
	
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @Column(name = "product_id", nullable = false)
    private Long productId;
    
    @Column(name = "source", nullable = false, length = 50)
    private String source;
    
    @Column(name = "keyword", nullable = false, length = 100)
    private String keyword;
    
    @Column(name = "trend_score", precision = 5, scale = 2)
    private BigDecimal trendScore;
    
    @Column(name = "popularity_score", precision = 5, scale = 2)
    private BigDecimal popularityScore;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "trend_direction")
    private TrendSignalTrendDirection trendDirection;
    
    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

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

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getKeyword() {
		return keyword;
	}

	public void setKeyword(String keyword) {
		this.keyword = keyword;
	}

	public BigDecimal getTrendScore() {
		return trendScore;
	}

	public void setTrendScore(BigDecimal trendScore) {
		this.trendScore = trendScore;
	}

	public BigDecimal getPopularityScore() {
		return popularityScore;
	}

	public void setPopularityScore(BigDecimal popularityScore) {
		this.popularityScore = popularityScore;
	}

	public TrendSignalTrendDirection getTrendDirection() {
		return trendDirection;
	}

	public void setTrendDirection(TrendSignalTrendDirection trendDirection) {
		this.trendDirection = trendDirection;
	}

	public LocalDateTime getCollectedAt() {
		return collectedAt;
	}

	public void setCollectedAt(LocalDateTime collectedAt) {
		this.collectedAt = collectedAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
