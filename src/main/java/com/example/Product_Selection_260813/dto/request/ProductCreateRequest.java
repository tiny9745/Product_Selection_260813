package com.example.Product_Selection_260813.dto.request;

import java.math.BigDecimal;

import com.example.Product_Selection_260813.constants.ValidationMessage;
import com.example.Product_Selection_260813.enums.ProductPricingType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/products 的 Request Body。
 *
 * 對應企劃書「新增品項」：操作人員手動建立即代表已確認要送審，Service層會直接 帶入
 * review_status=PENDING／item_status=ACTIVE／candidate_status=CANDIDATE，
 * 這三個狀態欄位不開放由這支DTO傳入（見QA4：手動新增不是AI_SUGGESTED，
 * 語意上這三個狀態欄位在「新增」當下本來就沒有選擇空間，不屬於Request的職責）。
 *
 * pricing_status不在這支DTO：pricing_type=NEW時Service層固定帶入PENDING_PRICING，
 * RESALE固定留空，同樣不開放外部指定（四-2備註）。
 */
public class ProductCreateRequest {

	@NotNull(message = ValidationMessage.PRODUCT_TYPE_ID_NULL)
	private Long productTypeId;

	@NotNull(message = ValidationMessage.PRODUCT_PRICING_TYPE_NULL)
	private ProductPricingType pricingType;

	@NotBlank(message = ValidationMessage.PRODUCT_NAME_NULL)
	private String name;

	private String description;

	private String imageUrl;

	private String supplierName;

	// 新品(NEW)可為空，待議價完成後回填【QA1】；Service層不對NEW商品的costPrice/salePrice做必填驗證
	private BigDecimal costPrice;

	private BigDecimal salePrice;

	// 僅RESALE商品填寫，NEW商品不適用；若pricingType=NEW卻帶了值，Service層會拒絕【十四-1】
	private BigDecimal marketPrice;

	private String campaignTags;

	private Integer moq;

	private BigDecimal supplyStability;

	private BigDecimal priceCompetitiveness;

	private String targetCustomerDescription;

	private BigDecimal estimatedPurchaseRate;

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
}
