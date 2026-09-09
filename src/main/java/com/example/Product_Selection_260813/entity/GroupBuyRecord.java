package com.example.Product_Selection_260813.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 歷史開團紀錄。資料由外部匯入，系統內唯讀——選品系統不負責審核之後的營運事宜，
 * 因此不提供單筆新增／編輯端點（這與其他 Controller 的 CRUD 慣例不一致，
 * 是刻意的系統邊界設計，code review 時請勿「補齊」）。
 *
 * <b>productId 可為空、productTypeId 必填</b>，與一般直覺相反，但這是刻意的：
 * 選品評估的對象是新商品，本來就不會有自己的開團歷史；歷史分數的主要來源是
 * 品類而非商品本身。匯入檔案必須指定品類，不做商品名稱模糊比對——自動比對會
 * 錯配，而錯配的資料會進入不可覆蓋的審核快照。
 */
@Entity
@Table(name = "group_buy_records")
public class GroupBuyRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 對應系統內商品；匯入資料的商品多半不在系統內，故可為空。 */
	@Column(name = "product_id")
	private Long productId;

	/** 所屬品類；歷史分數的主要聚合維度，必填。 */
	@Column(name = "product_type_id", nullable = false)
	private Long productTypeId;

	@Column(name = "external_product_name", nullable = false, length = 200)
	private String externalProductName;

	@Column(name = "supplier_name", length = 100)
	private String supplierName;

	@Column(name = "campaign_start_date", nullable = false)
	private LocalDate campaignStartDate;

	@Column(name = "campaign_end_date", nullable = false)
	private LocalDate campaignEndDate;

	/** 開團當下的 MOQ（快照，不 JOIN 現值）。半年後商品改價，歷史紀錄不能跟著變。 */
	@Column(name = "moq_at_time")
	private Integer moqAtTime;

	@Column(name = "sale_price_at_time", precision = 10, scale = 2)
	private BigDecimal salePriceAtTime;

	@Column(name = "target_quantity")
	private Integer targetQuantity;

	/** 實際集單量；GATE_MOQ_FEASIBILITY 的分位數基準來源。 */
	@Column(name = "actual_quantity", nullable = false)
	private Integer actualQuantity;

	@Column(name = "participant_count")
	private Integer participantCount;

	/** FULFILLED / FAILED / CANCELLED，對應 GroupBuyResult。 */
	@Column(name = "result", nullable = false, length = 20)
	private String result;

	@Column(name = "complaint_count", nullable = false)
	private Integer complaintCount = 0;

	@Column(name = "return_count", nullable = false)
	private Integer returnCount = 0;

	/**
	 * 是否為模擬資料。從第一天就有這個欄位，不是之後再補——已經寫進審核快照的
	 * 紀錄無法回頭標記，補得再晚都救不回來。
	 */
	@Column(name = "is_simulated", nullable = false)
	private Boolean isSimulated = false;

	/** 匯入批次；供整批回退使用，匯入錯誤時不需要逐筆刪。 */
	@Column(name = "import_batch_id", length = 50)
	private String importBatchId;

	@Column(name = "imported_at", nullable = false)
	private LocalDateTime importedAt;

	@Column(name = "imported_by")
	private Long importedBy;

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

	public Long getProductTypeId() {
		return productTypeId;
	}

	public void setProductTypeId(Long productTypeId) {
		this.productTypeId = productTypeId;
	}

	public String getExternalProductName() {
		return externalProductName;
	}

	public void setExternalProductName(String externalProductName) {
		this.externalProductName = externalProductName;
	}

	public String getSupplierName() {
		return supplierName;
	}

	public void setSupplierName(String supplierName) {
		this.supplierName = supplierName;
	}

	public LocalDate getCampaignStartDate() {
		return campaignStartDate;
	}

	public void setCampaignStartDate(LocalDate campaignStartDate) {
		this.campaignStartDate = campaignStartDate;
	}

	public LocalDate getCampaignEndDate() {
		return campaignEndDate;
	}

	public void setCampaignEndDate(LocalDate campaignEndDate) {
		this.campaignEndDate = campaignEndDate;
	}

	public Integer getMoqAtTime() {
		return moqAtTime;
	}

	public void setMoqAtTime(Integer moqAtTime) {
		this.moqAtTime = moqAtTime;
	}

	public BigDecimal getSalePriceAtTime() {
		return salePriceAtTime;
	}

	public void setSalePriceAtTime(BigDecimal salePriceAtTime) {
		this.salePriceAtTime = salePriceAtTime;
	}

	public Integer getTargetQuantity() {
		return targetQuantity;
	}

	public void setTargetQuantity(Integer targetQuantity) {
		this.targetQuantity = targetQuantity;
	}

	public Integer getActualQuantity() {
		return actualQuantity;
	}

	public void setActualQuantity(Integer actualQuantity) {
		this.actualQuantity = actualQuantity;
	}

	public Integer getParticipantCount() {
		return participantCount;
	}

	public void setParticipantCount(Integer participantCount) {
		this.participantCount = participantCount;
	}

	public String getResult() {
		return result;
	}

	public void setResult(String result) {
		this.result = result;
	}

	public Integer getComplaintCount() {
		return complaintCount;
	}

	public void setComplaintCount(Integer complaintCount) {
		this.complaintCount = complaintCount;
	}

	public Integer getReturnCount() {
		return returnCount;
	}

	public void setReturnCount(Integer returnCount) {
		this.returnCount = returnCount;
	}

	public Boolean getIsSimulated() {
		return isSimulated;
	}

	public void setIsSimulated(Boolean isSimulated) {
		this.isSimulated = isSimulated;
	}

	public String getImportBatchId() {
		return importBatchId;
	}

	public void setImportBatchId(String importBatchId) {
		this.importBatchId = importBatchId;
	}

	public LocalDateTime getImportedAt() {
		return importedAt;
	}

	public void setImportedAt(LocalDateTime importedAt) {
		this.importedAt = importedAt;
	}

	public Long getImportedBy() {
		return importedBy;
	}

	public void setImportedBy(Long importedBy) {
		this.importedBy = importedBy;
	}
}
