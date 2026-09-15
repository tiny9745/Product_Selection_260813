package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.enums.ProductCandidateStatus;
import com.example.Product_Selection_260813.enums.ProductItemStatus;
import com.example.Product_Selection_260813.enums.ProductPricingStatus;
import com.example.Product_Selection_260813.enums.ProductPricingType;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;
import com.example.Product_Selection_260813.service.gate.GateResult;

/**
 * 品項管理列表／詳情共用的商品資料格式。
 *
 * 只回傳products表本身的欄位；評估分數／趨勢／AI分析／風險提示等屬於其他Service
 * 的職責範圍（見十二-13分層決議：ScoringService／TrendService／AiSelectionService），
 * GET /api/products/{id} 這種聚合端點的組裝交由Controller呼叫多個Service後合併，
 * ProductResponse不越界去裝其他網域的資料。
 *
 * ⚠️ finalScore／dataCompleteness 是這條規則的例外：GET /api/products 清單
 * 頁需要顯示這兩個欄位，但逐筆呼叫 /evaluation 是前端 N+1、後端逐筆查也是
 * 服務層 N+1。做法比照 createdByName——批次查詢（見 ProductService.
 * resolveEvaluations()）後透過 withEvaluationSummary() 補上，from(Product)
 * 本身仍然不碰 product_evaluations 表，只是多了兩個「查完才填」的欄位。
 *
 * ⚠️ gateResults 是第二個例外，語意同上：只有 GET /api/products/{id}（單筆
 * 詳情）才會透過 withGateResults() 補上，清單／搜尋端點恆為 null——一次
 * 回傳多筆時重算五項 Gate 判定的成本太高，見 ProductService.getProduct()。
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
	private Integer supplyStability;
	private Integer priceCompetitiveness;
	private String targetCustomerDescription;
	private BigDecimal estimatedPurchaseRate;
	/** 再販售參考商品；僅 RESALE 商品可能有值。語意見 Product Entity 同名欄位。 */
	private Long resaleReferenceProductId;

	/**
	 * 以下 9 個欄位供 Gate 判定（GateEvaluationService）讀取，對應 Entity 同名欄位。
	 * 先前這批完全沒有被任何端點回傳過——商品層填了溫層、效期級距等屬性，
	 * 畫面上看不到，Gate 判定也只能靠品類層繼承。這裡補上之後才第一次
	 * 真正串起來（配合 ProductService.createProduct()／updateProduct() 這次
	 * 同步補上的寫入路徑）。
	 *
	 * ⚠️ 全部宣告為 String，不是對應的列舉型別（TemperatureZone 等）：
	 * GateEvaluationService.parseEnum() 本身就是寬鬆解析、容忍資料庫裡的
	 * 不合法值（回 null 讓 Gate 判定標示資料不足，而非整支查詢 500）。
	 * 若這裡改用 Enum.valueOf() 反序列化，一筆髒資料就會讓 GET
	 * /api/products/{id} 直接壞掉，兩邊的容錯态度不一致。
	 */
	private String temperatureZone;
	private String shelfLifeTier;
	private String supplierLeadTimeTier;
	private String packageSizeTier;
	private String packingType;
	private String handlingFlags;
	private String certificationFlags;
	private Integer supplierMaxCapacity;

	/**
	 * Gate 判定結果彙總。⚠️ 這是 finalScore／dataCompleteness 之後的第二個
	 * 「查完才填」例外：from(Product) 本身不執行 Gate 判定（那要多查 MOQ
	 * 分位數、節慶檔期等資料，清單頁 N 筆各跑一次成本太高），只有
	 * ProductService.getProduct()（單筆詳情）才會透過 withGateResults() 補上，
	 * 做法與 ReviewService.getReviewDetail() 完全一致。清單／搜尋端點
	 * 這個欄位恆為 null，前端 product-api.contract.ts 的 gateResults 已宣告
	 * 為 optional，樣板不應假設清單頁每一筆都有值。
	 */
	private GateResult.Summary gateResults;

	private ProductReviewStatus reviewStatus;
	private ProductCandidateStatus candidateStatus;
	private ProductPricingStatus pricingStatus;
	private ProductItemStatus itemStatus;
	private Integer submissionCount;
	private Long createdBy;
	/**
	 * 送審／建立者姓名。
	 *
	 * ⚠️ 這一欄刻意不是「跨界去查其他網域」——app_users 是使用者帳號本身的資料，
	 * 不屬於評分／趨勢／AI 等其他 Service 的職責範圍（十二-13分層決議規範的是
	 * ScoringService／TrendService／AiSelectionService 這幾個網域，不含帳號查詢）。
	 * 由 ProductService／ReviewService 批次查詢後透過 withCreatedByName() 補上，
	 * from(Product) 本身不查資料庫，維持這個類別原本「只讀 Product 欄位」的單純性。
	 * 找不到對應帳號時為 null（例如帳號後續被刪除，理論上不會發生），前端顯示「—」。
	 */
	private String createdByName;
	/**
	 * ⚠️ 這兩個欄位跟 createdByName 是同一種例外處理：批次查詢後才填入，
	 * from(Product) 本身不查 product_evaluations 表。該商品若尚無評估紀錄
	 * （從未計算過、或剛新增還沒觸發計算），維持 null，前端顯示「—」，
	 * 不要顯示成 0（0 分跟「還沒有分數」意義完全不同）。
	 */
	private BigDecimal finalScore;
	private BigDecimal dataCompleteness;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
	private Long updatedBy;
	/**
	 * 「為什麼被 AI 推薦」的說明文字。只有 GET /api/products/ai-suggested
	 * 這支端點會填值，其餘所有回傳 ProductResponse 的端點一律是 null——
	 * 原本前端這個欄位是恆為 null 的死欄位，因為判定「要不要推薦」的
	 * AiSuggestionBatchService 只把結果寫成一個布林值（改
	 * candidate_status），沒有把「為什麼」這件事存下來或回傳過。
	 * 這裡不改動批次判定邏輯本身，只是在查詢清單時，用同樣的兩個判定
	 * 條件（最新熱度分數／連續三天上升趨勢）重新算一次，組成一句人看得懂
	 * 的說明——判定條件是固定、可重現的，不需要在批次當下就存起來。
	 */
	private String suggestionReason;

	public String getSuggestionReason() {
		return suggestionReason;
	}

	public void setSuggestionReason(String suggestionReason) {
		this.suggestionReason = suggestionReason;
	}

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
		dto.resaleReferenceProductId = product.getResaleReferenceProductId();
		dto.temperatureZone = product.getTemperatureZone();
		dto.shelfLifeTier = product.getShelfLifeTier();
		dto.supplierLeadTimeTier = product.getSupplierLeadTimeTier();
		dto.packageSizeTier = product.getPackageSizeTier();
		dto.packingType = product.getPackingType();
		dto.handlingFlags = product.getHandlingFlags();
		dto.certificationFlags = product.getCertificationFlags();
		dto.supplierMaxCapacity = product.getSupplierMaxCapacity();
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

	/**
	 * 補上批次查詢好的姓名，回傳 this 方便鏈式呼叫：
	 * ProductResponse.from(product).withCreatedByName(nameById.get(product.getCreatedBy()))
	 *
	 * 刻意拆成兩步而不是把 AppUserRepository 查詢塞進 from()：
	 * from() 一旦內部查資料庫，呼叫端在迴圈裡呼叫 from() 就會變成逐筆查詢（N+1）。
	 * 批次查詢的職責留在 Service 層（一次 findAllById 查整頁），這裡只負責賦值。
	 */
	public ProductResponse withCreatedByName(String createdByName) {
		this.createdByName = createdByName;
		return this;
	}

	/**
	 * 補上批次查詢好的分數，回傳 this 方便鏈式呼叫，用法同 withCreatedByName()：
	 * ProductResponse.from(product).withEvaluationSummary(
	 *     evaluation != null ? evaluation.getFinalScore() : null,
	 *     evaluation != null ? evaluation.getDataCompleteness() : null)
	 *
	 * 兩個參數一起傳、一起為 null，是因為兩者來自同一筆 ProductEvaluation，
	 * 沒有「只有其中一個有值」的情況，分開傳容易在呼叫端漏帶其中一個。
	 */
	public ProductResponse withEvaluationSummary(BigDecimal finalScore, BigDecimal dataCompleteness) {
		this.finalScore = finalScore;
		this.dataCompleteness = dataCompleteness;
		return this;
	}

	/**
	 * 補上單筆詳情頁才會算的 Gate 判定結果，回傳 this 方便鏈式呼叫，用法同
	 * withCreatedByName()：ProductResponse.from(product).withGateResults(gateResults)
	 */
	public ProductResponse withGateResults(GateResult.Summary gateResults) {
		this.gateResults = gateResults;
		return this;
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

	public Integer getSupplyStability() {
		return supplyStability;
	}

	public void setSupplyStability(Integer supplyStability) {
		this.supplyStability = supplyStability;
	}

	public Integer getPriceCompetitiveness() {
		return priceCompetitiveness;
	}

	public void setPriceCompetitiveness(Integer priceCompetitiveness) {
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

	public Long getResaleReferenceProductId() {
		return resaleReferenceProductId;
	}

	public void setResaleReferenceProductId(Long resaleReferenceProductId) {
		this.resaleReferenceProductId = resaleReferenceProductId;
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

	public GateResult.Summary getGateResults() {
		return gateResults;
	}

	public void setGateResults(GateResult.Summary gateResults) {
		this.gateResults = gateResults;
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

	public String getCreatedByName() {
		return createdByName;
	}

	public void setCreatedByName(String createdByName) {
		this.createdByName = createdByName;
	}

	public BigDecimal getFinalScore() {
		return finalScore;
	}

	public void setFinalScore(BigDecimal finalScore) {
		this.finalScore = finalScore;
	}

	public BigDecimal getDataCompleteness() {
		return dataCompleteness;
	}

	public void setDataCompleteness(BigDecimal dataCompleteness) {
		this.dataCompleteness = dataCompleteness;
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