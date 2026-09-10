package com.example.Product_Selection_260813.dto.request;

import java.math.BigDecimal;

import com.example.Product_Selection_260813.constants.ValidationMessage;
import com.example.Product_Selection_260813.enums.ProductPricingType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/products/{id} 的 Request Body。
 *
 * 採「整份覆蓋」語意（標準PUT），前端需送出商品目前完整的可編輯欄位，而非只送
 * 想改的欄位——與既有ProductRepository.search()等既有寫法一致，專案目前 沒有Partial
 * Update（PATCH）的既有慣例，這裡不額外引入。
 *
 * 欄位分組鎖定規則（四-2「欄位分組」）由ProductService.updateProduct()執行： -
 * 一般基本資料（name/description/imageUrl/supplierName）：任何審核狀態下都可改 -
 * 選品核心資料（productTypeId/pricingType/costPrice/salePrice/campaignTags/
 * moq/supplyStability/priceCompetitiveness/targetCustomerDescription/
 * estimatedPurchaseRate）：review_status=APPROVED時，若送來的值與目前值不同，
 * Service層會丟IllegalStateException(409)拒絕，而非靜默忽略——避免前端誤以為
 * 修改已生效但實際上後端沒有套用，造成資料落差。
 *
 * review_status／candidate_status／pricing_status／item_status／submission_count
 * 這五個狀態欄位刻意不開放由這支DTO傳入：狀態轉換一律透過對應的專屬端點
 * （resubmit／archive／restore／promote-to-candidate）處理，PUT只負責「資料」，
 * 不負責「狀態機」，混在一起會讓同一個欄位有兩條互相打架的修改路徑。
 */
public class ProductUpdateRequest {

	@NotNull(message = ValidationMessage.PRODUCT_TYPE_ID_NULL)
	private Long productTypeId;

	@NotNull(message = ValidationMessage.PRODUCT_PRICING_TYPE_NULL)
	private ProductPricingType pricingType;

	@NotBlank(message = ValidationMessage.PRODUCT_NAME_NULL)
	@Size(max = 100, message = ValidationMessage.PRODUCT_NAME_TOO_LONG)
	private String name;

	// description對應TEXT欄位，不設長度上限
	private String description;

	@Size(max = 500, message = ValidationMessage.PRODUCT_IMAGE_URL_TOO_LONG)
	private String imageUrl;

	@Size(max = 100, message = ValidationMessage.PRODUCT_SUPPLIER_NAME_TOO_LONG)
	private String supplierName;

	// 驗證規則與ProductCreateRequest完全一致：整份覆蓋語意下，PUT能送進來的
	// 值域必須跟POST一樣受限，否則會出現「新增擋得住、改一次就繞過去」的漏洞。
	@PositiveOrZero(message = ValidationMessage.PRODUCT_COST_PRICE_NEGATIVE)
	@Digits(integer = 8, fraction = 2, message = ValidationMessage.PRODUCT_COST_PRICE_OVER_DIGITS)
	private BigDecimal costPrice;

	@PositiveOrZero(message = ValidationMessage.PRODUCT_SALE_PRICE_NEGATIVE)
	@Digits(integer = 8, fraction = 2, message = ValidationMessage.PRODUCT_SALE_PRICE_OVER_DIGITS)
	private BigDecimal salePrice;

	@PositiveOrZero(message = ValidationMessage.PRODUCT_MARKET_PRICE_NEGATIVE)
	@Digits(integer = 8, fraction = 2, message = ValidationMessage.PRODUCT_MARKET_PRICE_OVER_DIGITS)
	private BigDecimal marketPrice;

	/**
	 * 僅 RESALE 商品可修改，語意與 ProductCreateRequest 相同。更新時同樣
	 * 交由 Service 驗證：NEW 商品帶值會被拒絕、引用的商品須存在且同小類。
	 */
	private Long resaleReferenceProductId;

	@Size(max = 255, message = ValidationMessage.PRODUCT_CAMPAIGN_TAGS_TOO_LONG)
	private String campaignTags;

	@PositiveOrZero(message = ValidationMessage.PRODUCT_MOQ_NEGATIVE)
	private Integer moq;

	@DecimalMin(value = "1.0", message = ValidationMessage.PRODUCT_SUPPLY_STABILITY_RANGE)
	@DecimalMax(value = "5.0", message = ValidationMessage.PRODUCT_SUPPLY_STABILITY_RANGE)
	private BigDecimal supplyStability;

	@DecimalMin(value = "1.0", message = ValidationMessage.PRODUCT_PRICE_COMPETITIVENESS_RANGE)
	@DecimalMax(value = "5.0", message = ValidationMessage.PRODUCT_PRICE_COMPETITIVENESS_RANGE)
	private BigDecimal priceCompetitiveness;

	// targetCustomerDescription對應TEXT欄位，不設長度上限
	private String targetCustomerDescription;

	@DecimalMin(value = "0.0", message = ValidationMessage.PRODUCT_ESTIMATED_PURCHASE_RATE_RANGE)
	@DecimalMax(value = "1.0", message = ValidationMessage.PRODUCT_ESTIMATED_PURCHASE_RATE_RANGE)
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

	public Long getResaleReferenceProductId() {
		return resaleReferenceProductId;
	}

	public void setResaleReferenceProductId(Long resaleReferenceProductId) {
		this.resaleReferenceProductId = resaleReferenceProductId;
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
