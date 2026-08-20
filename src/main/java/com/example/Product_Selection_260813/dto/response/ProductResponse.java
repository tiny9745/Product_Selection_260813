package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.enums.ProductCandidateStatus;
import com.example.Product_Selection_260813.enums.ProductItemStatus;
import com.example.Product_Selection_260813.enums.ProductPricingStatus;
import com.example.Product_Selection_260813.enums.ProductPricingType;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;

/**
 * 品項管理列表／詳情共用的商品資料格式。
 *
 * 只回傳products表本身的欄位；評估分數／趨勢／AI分析／風險提示等屬於其他Service
 * 的職責範圍（見十二-13分層決議：ScoringService／TrendService／AiSelectionService），
 * GET /api/products/{id} 這種聚合端點的組裝交由Controller呼叫多個Service後合併，
 * ProductResponse不越界去裝其他網域的資料。
 */
public class ProductResponse {

	private Long id;
	private Long productTypeId;
	private ProductPricingType pricingType;
	private String name;
	private String description;
	private String imageUrl;
	private String supplierName;
	private BigDecimal costPrice;
	private BigDecimal salePrice;
	private BigDecimal marketPrice;
	private String campaignTags;
	private Integer moq;
	private BigDecimal supplyStability;
	private BigDecimal priceCompetitiveness;
	private String targetCustomerDescription;
	private BigDecimal estimatedPurchaseRate;
	private ProductReviewStatus reviewStatus;
	private ProductCandidateStatus candidateStatus;
	private ProductPricingStatus pricingStatus;
	private ProductItemStatus itemStatus;
	private Integer submissionCount;
	private Long createdBy;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
	private Long updatedBy;

	public static ProductResponse from(Product product) {
		ProductResponse dto = new ProductResponse();
		dto.id = product.getId();
		dto.productTypeId = product.getProductTypeId();
		dto.pricingType = product.getPricingType();
		dto.name = product.getName();
		dto.description = product.getDescription();
		dto.imageUrl = product.getImageUrl();
		dto.supplierName = product.getSupplierName();
		dto.costPrice = product.getCostPrice();
		dto.salePrice = product.getSalePrice();
		dto.marketPrice = product.getMarketPrice();
		dto.campaignTags = product.getCampaignTags();
		dto.moq = product.getMoq();
		dto.supplyStability = product.getSupplyStability();
		dto.priceCompetitiveness = product.getPriceCompetitiveness();
		dto.targetCustomerDescription = product.getTargetCustomerDescription();
		dto.estimatedPurchaseRate = product.getEstimatedPurchaseRate();
		dto.reviewStatus = product.getReviewStatus();
		dto.candidateStatus = product.getCandidateStatus();
		dto.pricingStatus = product.getPricingStatus();
		dto.itemStatus = product.getItemStatus();
		dto.submissionCount = product.getSubmissionCount();
		dto.createdBy = product.getCreatedBy();
		dto.createdAt = product.getCreatedAt();
		dto.updatedAt = product.getUpdatedAt();
		dto.updatedBy = product.getUpdatedBy();
		return dto;
	}

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
