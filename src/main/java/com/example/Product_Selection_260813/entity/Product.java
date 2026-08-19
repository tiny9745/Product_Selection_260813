package com.example.Product_Selection_260813.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.Product_Selection_260813.enums.ProductCandidateStatus;
import com.example.Product_Selection_260813.enums.ProductItemStatus;
import com.example.Product_Selection_260813.enums.ProductPricingStatus;
import com.example.Product_Selection_260813.enums.ProductPricingType;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name="products")
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "product_type_id", nullable = false)
	private Long productTypeId;

	@Enumerated(EnumType.STRING)
	@Column(name = "pricing_type", nullable = false)
	private ProductPricingType pricingType;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "description", columnDefinition = "TEXT")
	private String description;

	@Column(name = "image_url", length = 500)
	private String imageUrl;

	@Column(name = "supplier_name", length = 100)
	private String supplierName;

	@Column(name = "cost_price", precision = 10, scale = 2)
	private BigDecimal costPrice;

	@Column(name = "sale_price", precision = 10, scale = 2)
	private BigDecimal salePrice;

	@Column(name = "market_price", precision = 10, scale = 2)
	private BigDecimal marketPrice;

	@Column(name = "campaign_tags", length = 255)
	private String campaignTags;

	@Column(name = "moq")
	private Integer moq;

	@Column(name = "supply_stability", precision = 5, scale = 2)
	private BigDecimal supplyStability;

	@Column(name = "price_competitiveness", precision = 5, scale = 2)
	private BigDecimal priceCompetitiveness;

	@Column(name = "target_customer_description", columnDefinition = "TEXT")
	private String targetCustomerDescription;

	@Column(name = "estimated_purchase_rate", precision = 5, scale = 2)
	private BigDecimal estimatedPurchaseRate;

	@Enumerated(EnumType.STRING)
	@Column(name = "review_status", nullable = false)
	private ProductReviewStatus reviewStatus = ProductReviewStatus.PENDING;

	@Enumerated(EnumType.STRING)
	@Column(name = "candidate_status", nullable = false)
	private ProductCandidateStatus candidateStatus = ProductCandidateStatus.CANDIDATE;

	@Enumerated(EnumType.STRING)
	@Column(name = "pricing_status")
	private ProductPricingStatus pricingStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "item_status", nullable = false)
	private ProductItemStatus itemStatus = ProductItemStatus.ACTIVE;

	@Column(name = "submission_count", nullable = false)
	private Integer submissionCount = 0;

	@Column(name = "created_by")
	private Long createdBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "updated_by")
	private Long updatedBy;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getProductTypeId() {
		return productTypeId;
	}

	public void setProductTypeId(Long productTypeId) {
		this.productTypeId = productTypeId;
	}

	public ProductPricingType getPricingType() {
		return pricingType;
	}

	public void setPricingType(ProductPricingType pricingType) {
		this.pricingType = pricingType;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public void setImageUrl(String imageUrl) {
		this.imageUrl = imageUrl;
	}

	public String getSupplierName() {
		return supplierName;
	}

	public void setSupplierName(String supplierName) {
		this.supplierName = supplierName;
	}

	public BigDecimal getCostPrice() {
		return costPrice;
	}

	public void setCostPrice(BigDecimal costPrice) {
		this.costPrice = costPrice;
	}

	public BigDecimal getSalePrice() {
		return salePrice;
	}

	public void setSalePrice(BigDecimal salePrice) {
		this.salePrice = salePrice;
	}

	public BigDecimal getMarketPrice() {
		return marketPrice;
	}

	public void setMarketPrice(BigDecimal marketPrice) {
		this.marketPrice = marketPrice;
	}

	public String getCampaignTags() {
		return campaignTags;
	}

	public void setCampaignTags(String campaignTags) {
		this.campaignTags = campaignTags;
	}

	public Integer getMoq() {
		return moq;
	}

	public void setMoq(Integer moq) {
		this.moq = moq;
	}

	public BigDecimal getSupplyStability() {
		return supplyStability;
	}

	public void setSupplyStability(BigDecimal supplyStability) {
		this.supplyStability = supplyStability;
	}

	public BigDecimal getPriceCompetitiveness() {
		return priceCompetitiveness;
	}

	public void setPriceCompetitiveness(BigDecimal priceCompetitiveness) {
		this.priceCompetitiveness = priceCompetitiveness;
	}

	public String getTargetCustomerDescription() {
		return targetCustomerDescription;
	}

	public void setTargetCustomerDescription(String targetCustomerDescription) {
		this.targetCustomerDescription = targetCustomerDescription;
	}

	public BigDecimal getEstimatedPurchaseRate() {
		return estimatedPurchaseRate;
	}

	public void setEstimatedPurchaseRate(BigDecimal estimatedPurchaseRate) {
		this.estimatedPurchaseRate = estimatedPurchaseRate;
	}

	public ProductReviewStatus getReviewStatus() {
		return reviewStatus;
	}

	public void setReviewStatus(ProductReviewStatus reviewStatus) {
		this.reviewStatus = reviewStatus;
	}

	public ProductCandidateStatus getCandidateStatus() {
		return candidateStatus;
	}

	public void setCandidateStatus(ProductCandidateStatus candidateStatus) {
		this.candidateStatus = candidateStatus;
	}

	public ProductPricingStatus getPricingStatus() {
		return pricingStatus;
	}

	public void setPricingStatus(ProductPricingStatus pricingStatus) {
		this.pricingStatus = pricingStatus;
	}

	public ProductItemStatus getItemStatus() {
		return itemStatus;
	}

	public void setItemStatus(ProductItemStatus itemStatus) {
		this.itemStatus = itemStatus;
	}

	public Integer getSubmissionCount() {
		return submissionCount;
	}

	public void setSubmissionCount(Integer submissionCount) {
		this.submissionCount = submissionCount;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(Long createdBy) {
		this.createdBy = createdBy;
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

	public Long getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(Long updatedBy) {
		this.updatedBy = updatedBy;
	}
}