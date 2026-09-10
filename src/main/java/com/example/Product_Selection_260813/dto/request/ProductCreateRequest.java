package com.example.Product_Selection_260813.dto.request;

import java.math.BigDecimal;

import com.example.Product_Selection_260813.constants.ValidationMessage;
import com.example.Product_Selection_260813.enums.ProductPricingType;
import com.example.Product_Selection_260813.enums.TemperatureZone;
import com.example.Product_Selection_260813.enums.ShelfLifeTier;
import com.example.Product_Selection_260813.enums.SupplierLeadTimeTier;
import com.example.Product_Selection_260813.enums.PackageSizeTier;
import com.example.Product_Selection_260813.enums.PackingType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

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
	@Size(max = 100, message = ValidationMessage.PRODUCT_NAME_TOO_LONG)
	private String name;

	// description對應TEXT欄位，不設長度上限
	private String description;

	@Size(max = 500, message = ValidationMessage.PRODUCT_IMAGE_URL_TOO_LONG)
	private String imageUrl;

	@Size(max = 100, message = ValidationMessage.PRODUCT_SUPPLIER_NAME_TOO_LONG)
	private String supplierName;

	// 新品(NEW)可為空，待議價完成後回填【QA1】；Service層不對NEW商品的costPrice/salePrice做必填驗證。
	// 但「可為空」不等於「可為任意值」：一旦有值就必須是非負數，且符合decimal(10,2)精度，
	// 否則錯誤值會被寫進不可覆蓋的review_records.product_snapshot審核快照。
	@PositiveOrZero(message = ValidationMessage.PRODUCT_COST_PRICE_NEGATIVE)
	@Digits(integer = 8, fraction = 2, message = ValidationMessage.PRODUCT_COST_PRICE_OVER_DIGITS)
	private BigDecimal costPrice;

	@PositiveOrZero(message = ValidationMessage.PRODUCT_SALE_PRICE_NEGATIVE)
	@Digits(integer = 8, fraction = 2, message = ValidationMessage.PRODUCT_SALE_PRICE_OVER_DIGITS)
	private BigDecimal salePrice;

	// 僅RESALE商品填寫，NEW商品不適用；若pricingType=NEW卻帶了值，Service層會拒絕【十四-1】
	@PositiveOrZero(message = ValidationMessage.PRODUCT_MARKET_PRICE_NEGATIVE)
	@Digits(integer = 8, fraction = 2, message = ValidationMessage.PRODUCT_MARKET_PRICE_OVER_DIGITS)
	private BigDecimal marketPrice;

	/**
	 * 僅 RESALE 商品填寫，指向系統內既有商品的 id，供歷史成團率分數改查那件
	 * 參考商品的紀錄（見 HistoricalScoreCalculator）。NEW 商品帶了值，
	 * Service 層會拒絕，驗證方式與 marketPrice 一致。
	 *
	 * 不在這裡加 @NotNull——RESALE 商品完全可以不指定參考商品，這種情況下
	 * 歷史分數自然退回品類層，是預期內的優雅降級，不是錯誤。
	 *
	 * 候選清單建議透過 GET /api/products/similar-candidates 取得，由系統
	 * 依品類／名稱相似度／供應商排序建議，最終由使用者手動確認後才送出這個
	 * id——系統本身不做自動合併判定，避免誤判把兩件不同商品的歷史紀錄
	 * 混在一起計算。
	 */
	private Long resaleReferenceProductId;

	@Size(max = 255, message = ValidationMessage.PRODUCT_CAMPAIGN_TAGS_TOO_LONG)
	private String campaignTags;

	@PositiveOrZero(message = ValidationMessage.PRODUCT_MOQ_NEGATIVE)
	private Integer moq;

	// 1~5分制人工評估。ScoringService.calculateBusinessScore()雖有clamp()保護分數不超出
	// 0~100，但那只保護「分數」，不保護「存進資料庫的原始值」——沒有這層驗證，
	// supply_stability=999會原封不動存入並寫進審核快照。
	@DecimalMin(value = "1.0", message = ValidationMessage.PRODUCT_SUPPLY_STABILITY_RANGE)
	@DecimalMax(value = "5.0", message = ValidationMessage.PRODUCT_SUPPLY_STABILITY_RANGE)
	private BigDecimal supplyStability;

	@DecimalMin(value = "1.0", message = ValidationMessage.PRODUCT_PRICE_COMPETITIVENESS_RANGE)
	@DecimalMax(value = "5.0", message = ValidationMessage.PRODUCT_PRICE_COMPETITIVENESS_RANGE)
	private BigDecimal priceCompetitiveness;

	// targetCustomerDescription對應TEXT欄位，不設長度上限
	private String targetCustomerDescription;

	// 後端以0~1的比率儲存（前端表單為0~100%，送出前換算）。超過1會讓
	// ScoringService算出的購買分數失真，必須在進Service前擋下。
	@DecimalMin(value = "0.0", message = ValidationMessage.PRODUCT_ESTIMATED_PURCHASE_RATE_RANGE)
	@DecimalMax(value = "1.0", message = ValidationMessage.PRODUCT_ESTIMATED_PURCHASE_RATE_RANGE)
	private BigDecimal estimatedPurchaseRate;

	/**
	 * 以下 9 個欄位原本只有資料庫欄位與 Entity，這支 DTO 從未真正開放過——
	 * 商品建立當下沒有管道能設定它們，這 9 欄實際上永遠是 NULL。這是本次
	 * 補上的缺口：接進來之後，Gate 判定（GateEvaluationService）才能真正
	 * 讀到商品層自己填的值，而不是永遠只能靠品類繼承。
	 *
	 * 用實際的列舉型別而非 String，跟 pricingType 的既有寫法一致——送入
	 * 不合法的值會被 Jackson 在反序列化階段直接拒絕（400），不需要額外寫
	 * 驗證邏輯去檢查字串是不是合法的列舉值。
	 */
	private TemperatureZone temperatureZone;
	private ShelfLifeTier shelfLifeTier;
	private SupplierLeadTimeTier supplierLeadTimeTier;
	private PackageSizeTier packageSizeTier;
	private PackingType packingType;

	/** 逗號分隔的自由文字標籤，不是列舉——處理注意事項的組合方式沒有固定選項集。 */
	@Size(max = 200, message = "處理注意事項長度不可超過200字元")
	private String handlingFlags;

	@Size(max = 200, message = "認證狀態長度不可超過200字元")
	private String certificationFlags;

	@PositiveOrZero(message = "供應商產能上限不可為負數")
	private Integer supplierMaxCapacity;

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

	public TemperatureZone getTemperatureZone() {
		return temperatureZone;
	}

	public void setTemperatureZone(TemperatureZone temperatureZone) {
		this.temperatureZone = temperatureZone;
	}

	public ShelfLifeTier getShelfLifeTier() {
		return shelfLifeTier;
	}

	public void setShelfLifeTier(ShelfLifeTier shelfLifeTier) {
		this.shelfLifeTier = shelfLifeTier;
	}

	public SupplierLeadTimeTier getSupplierLeadTimeTier() {
		return supplierLeadTimeTier;
	}

	public void setSupplierLeadTimeTier(SupplierLeadTimeTier supplierLeadTimeTier) {
		this.supplierLeadTimeTier = supplierLeadTimeTier;
	}

	public PackageSizeTier getPackageSizeTier() {
		return packageSizeTier;
	}

	public void setPackageSizeTier(PackageSizeTier packageSizeTier) {
		this.packageSizeTier = packageSizeTier;
	}

	public PackingType getPackingType() {
		return packingType;
	}

	public void setPackingType(PackingType packingType) {
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
