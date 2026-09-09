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

	// ---- Gate 屬性欄位（設計文件 4.2）----
	// 全部 nullable，null 代表繼承品類預設而非「未設定」。
	// 採級距化勾選而非精確數值：Gate 是二元判斷，只需要知道「是不是 30 天以內」，
	// 要求填「23 天」是白白拉高資料成本。系統只有採購與主管兩個角色，沒有物流
	// 角色，這些欄位的填寫責任會落在採購身上——他未必拿得到精確材積，
	// 但一定分得出冷凍還是常溫。

	@Column(name = "temperature_zone", length = 20)
	private String temperatureZone;

	@Column(name = "shelf_life_tier", length = 20)
	private String shelfLifeTier;

	@Column(name = "supplier_lead_time_tier", length = 20)
	private String supplierLeadTimeTier;

	/** 材積級距；運費估算依此查 system_settings 的 freight_cost_*。 */
	@Column(name = "package_size_tier", length = 20)
	private String packageSizeTier;

	@Column(name = "packing_type", length = 20)
	private String packingType;

	/** 處理注意事項（逗號分隔），如 FRAGILE。沿用 campaign_tags 的多值字串風格。 */
	@Column(name = "handling_flags", length = 200)
	private String handlingFlags;

	@Column(name = "certification_flags", length = 200)
	private String certificationFlags;

	@Column(name = "supplier_max_capacity")
	private Integer supplierMaxCapacity;

	/** 再販售參考商品；供歷史分數的商品層查詢使用。限同小類，由 Service 驗證。 */
	@Column(name = "resale_reference_product_id")
	private Long resaleReferenceProductId;

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


	public String getTemperatureZone() {
		return temperatureZone;
	}

	public void setTemperatureZone(String temperatureZone) {
		this.temperatureZone = temperatureZone;
	}

	public String getShelfLifeTier() {
		return shelfLifeTier;
	}

	public void setShelfLifeTier(String shelfLifeTier) {
		this.shelfLifeTier = shelfLifeTier;
	}

	public String getSupplierLeadTimeTier() {
		return supplierLeadTimeTier;
	}

	public void setSupplierLeadTimeTier(String supplierLeadTimeTier) {
		this.supplierLeadTimeTier = supplierLeadTimeTier;
	}

	public String getPackageSizeTier() {
		return packageSizeTier;
	}

	public void setPackageSizeTier(String packageSizeTier) {
		this.packageSizeTier = packageSizeTier;
	}

	public String getPackingType() {
		return packingType;
	}

	public void setPackingType(String packingType) {
		this.packingType = packingType;
	}

	public String getHandlingFlags() {
		return handlingFlags;
	}

	public void setHandlingFlags(String handlingFlags) {
		this.handlingFlags = handlingFlags;
	}

	public String getCertificationFlags() {
		return certificationFlags;
	}

	public void setCertificationFlags(String certificationFlags) {
		this.certificationFlags = certificationFlags;
	}

	public Integer getSupplierMaxCapacity() {
		return supplierMaxCapacity;
	}

	public void setSupplierMaxCapacity(Integer supplierMaxCapacity) {
		this.supplierMaxCapacity = supplierMaxCapacity;
	}

	public Long getResaleReferenceProductId() {
		return resaleReferenceProductId;
	}

	public void setResaleReferenceProductId(Long resaleReferenceProductId) {
		this.resaleReferenceProductId = resaleReferenceProductId;
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