package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.Product_Selection_260813.entity.GroupBuyRecord;

/** 開團紀錄的唯讀查詢回應。 */
public class GroupBuyRecordResponse {

	private Long id;
	private Long productId;
	private Long productTypeId;
	private String externalProductName;
	private String supplierName;
	private LocalDate campaignStartDate;
	private LocalDate campaignEndDate;
	private Integer moqAtTime;
	private BigDecimal salePriceAtTime;
	private BigDecimal costPriceAtTime;
	private BigDecimal marketPriceAtTime;
	private Integer targetQuantity;
	private Integer actualQuantity;
	private Integer participantCount;
	private String result;
	private Integer complaintCount;
	private Integer returnCount;
	private Boolean isSimulated;
	private String importBatchId;
	private LocalDateTime importedAt;

	public static GroupBuyRecordResponse from(GroupBuyRecord entity) {
		GroupBuyRecordResponse r = new GroupBuyRecordResponse();
		r.id = entity.getId();
		r.productId = entity.getProductId();
		r.productTypeId = entity.getProductTypeId();
		r.externalProductName = entity.getExternalProductName();
		r.supplierName = entity.getSupplierName();
		r.campaignStartDate = entity.getCampaignStartDate();
		r.campaignEndDate = entity.getCampaignEndDate();
		r.moqAtTime = entity.getMoqAtTime();
		r.salePriceAtTime = entity.getSalePriceAtTime();
		r.costPriceAtTime = entity.getCostPriceAtTime();
		r.marketPriceAtTime = entity.getMarketPriceAtTime();
		r.targetQuantity = entity.getTargetQuantity();
		r.actualQuantity = entity.getActualQuantity();
		r.participantCount = entity.getParticipantCount();
		r.result = entity.getResult();
		r.complaintCount = entity.getComplaintCount();
		r.returnCount = entity.getReturnCount();
		r.isSimulated = entity.getIsSimulated();
		r.importBatchId = entity.getImportBatchId();
		r.importedAt = entity.getImportedAt();
		return r;
	}

	public Long getId() { return id; }
	public Long getProductId() { return productId; }
	public Long getProductTypeId() { return productTypeId; }
	public String getExternalProductName() { return externalProductName; }
	public String getSupplierName() { return supplierName; }
	public LocalDate getCampaignStartDate() { return campaignStartDate; }
	public LocalDate getCampaignEndDate() { return campaignEndDate; }
	public Integer getMoqAtTime() { return moqAtTime; }
	public BigDecimal getSalePriceAtTime() { return salePriceAtTime; }
	public BigDecimal getCostPriceAtTime() { return costPriceAtTime; }
	public BigDecimal getMarketPriceAtTime() { return marketPriceAtTime; }
	public Integer getTargetQuantity() { return targetQuantity; }
	public Integer getActualQuantity() { return actualQuantity; }
	public Integer getParticipantCount() { return participantCount; }
	public String getResult() { return result; }
	public Integer getComplaintCount() { return complaintCount; }
	public Integer getReturnCount() { return returnCount; }
	public Boolean getIsSimulated() { return isSimulated; }
	public String getImportBatchId() { return importBatchId; }
	public LocalDateTime getImportedAt() { return importedAt; }
}
