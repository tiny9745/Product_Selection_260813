package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.Product_Selection_260813.entity.GroupBuyRecord;
import com.example.Product_Selection_260813.enums.UserRole;

/**
 * 開團紀錄的唯讀查詢回應。
 *
 * <b>依檢視者角色決定成本資訊的揭露範圍</b>（2026-09 歷史銷售紀錄職責分層）：
 * 同一支 GET /api/group-buy-records，不另開兩套 DTO／兩支 API。
 * <ul>
 * <li>PURCHASER：costPriceAtTime、marginRate 一律為 null。成本價屬管理層資訊，
 * 操作層拿到 cost 與 sale 兩個原始數字就能自行反推毛利率。</li>
 * <li>MANAGER：額外取得 costPriceAtTime 與後端算好的 marginRate。</li>
 * </ul>
 * 刻意只在這裡（唯一的組裝點）判斷角色：之後新增欄位時只要看這個方法就知道
 * 哪些欄位受角色限制，不會在 Service／Controller 各處散落 if 判斷。
 */
public class GroupBuyRecordResponse {

	/** 毛利率（%）顯示精度，與商品端 marginRate 的前端呈現一致（小數兩位）。 */
	private static final int MARGIN_RATE_SCALE = 2;
	private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

	private Long id;
	private Long productId;
	private Long productTypeId;
	private String externalProductName;
	private String supplierName;
	private LocalDate campaignStartDate;
	private LocalDate campaignEndDate;
	private Integer moqAtTime;
	private BigDecimal salePriceAtTime;
	/** 僅 MANAGER 有值；PURCHASER 固定為 null。 */
	private BigDecimal costPriceAtTime;
	/**
	 * 毛利率（%）＝ (salePriceAtTime − costPriceAtTime) ÷ salePriceAtTime × 100，小數兩位。
	 * 僅 MANAGER 有值；售價或成本缺漏、售價為 0 時為 null（無法計算，不是 0%）。
	 */
	private BigDecimal marginRate;
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

	public static GroupBuyRecordResponse from(GroupBuyRecord entity, UserRole viewerRole) {
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
		if (viewerRole == UserRole.MANAGER) {
			r.costPriceAtTime = entity.getCostPriceAtTime();
			r.marginRate = calculateMarginRate(entity.getSalePriceAtTime(), entity.getCostPriceAtTime());
		}
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

	static BigDecimal calculateMarginRate(BigDecimal salePrice, BigDecimal costPrice) {
		if (salePrice == null || costPrice == null || salePrice.signum() == 0) {
			return null;
		}
		return salePrice.subtract(costPrice)
				.multiply(ONE_HUNDRED)
				.divide(salePrice, MARGIN_RATE_SCALE, RoundingMode.HALF_UP);
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
	public BigDecimal getMarginRate() { return marginRate; }
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
