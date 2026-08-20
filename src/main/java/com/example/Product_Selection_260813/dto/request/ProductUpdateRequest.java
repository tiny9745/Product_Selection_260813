package com.example.Product_Selection_260813.dto.request;

import java.math.BigDecimal;

import com.example.Product_Selection_260813.enums.ProductPricingType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * PUT /api/products/{id} 的 Request Body。
 *
 * 採「整份覆蓋」語意（標準PUT），前端需送出商品目前完整的可編輯欄位，而非只送
 * 想改的欄位——與既有ProductRepository.search()等既有寫法一致，專案目前
 * 沒有Partial Update（PATCH）的既有慣例，這裡不額外引入。
 *
 * 欄位分組鎖定規則（四-2「欄位分組」）由ProductService.updateProduct()執行：
 *   - 一般基本資料（name/description/imageUrl/supplierName）：任何審核狀態下都可改
 *   - 選品核心資料（productTypeId/pricingType/costPrice/salePrice/campaignTags/
 *     moq/supplyStability/priceCompetitiveness/targetCustomerDescription/
 *     estimatedPurchaseRate）：review_status=APPROVED時，若送來的值與目前值不同，
 *     Service層會丟IllegalStateException(409)拒絕，而非靜默忽略——避免前端誤以為
 *     修改已生效但實際上後端沒有套用，造成資料落差。
 *
 * review_status／candidate_status／pricing_status／item_status／submission_count
 * 這五個狀態欄位刻意不開放由這支DTO傳入：狀態轉換一律透過對應的專屬端點
 * （resubmit／archive／restore／promote-to-candidate）處理，PUT只負責「資料」，
 * 不負責「狀態機」，混在一起會讓同一個欄位有兩條互相打架的修改路徑。
 */
public class ProductUpdateRequest {

	@NotNull(message = "商品類型不可為空")
	private Long productTypeId;

	@NotNull(message = "商品分流不可為空")
	private ProductPricingType pricingType;

	@NotBlank(message = "商品名稱不可為空")
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
