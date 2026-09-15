package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.Product_Selection_260813.entity.GroupBuyRecord;

/**
 * 認領歷史紀錄的候選結果。
 *
 * 比對邏輯與 SimilarProductCandidateResponse／ProductSimilarityService 相同
 * （品類必要條件＋名稱／供應商相似度排序），這裡不特地抽共用工具類別，
 * 兩邊各自獨立——比對的對象一邊是 Product、一邊是 GroupBuyRecord，欄位
 * 形狀不同，硬抽共用只會多一層間接，可讀性不會變好，見團隊一貫做法。
 */
public class GroupBuyRecordClaimCandidateResponse {

	private Long id;
	private String externalProductName;
	private String supplierName;
	private LocalDate campaignStartDate;
	private LocalDate campaignEndDate;
	private Integer actualQuantity;
	private String result;

	private BigDecimal nameSimilarity;
	private BigDecimal supplierSimilarity;
	private BigDecimal combinedScore;

	public static GroupBuyRecordClaimCandidateResponse of(GroupBuyRecord record, BigDecimal nameSimilarity,
			BigDecimal supplierSimilarity, BigDecimal combinedScore) {
		GroupBuyRecordClaimCandidateResponse r = new GroupBuyRecordClaimCandidateResponse();
		r.id = record.getId();
		r.externalProductName = record.getExternalProductName();
		r.supplierName = record.getSupplierName();
		r.campaignStartDate = record.getCampaignStartDate();
		r.campaignEndDate = record.getCampaignEndDate();
		r.actualQuantity = record.getActualQuantity();
		r.result = record.getResult();
		r.nameSimilarity = nameSimilarity;
		r.supplierSimilarity = supplierSimilarity;
		r.combinedScore = combinedScore;
		return r;
	}

	public Long getId() { return id; }
	public String getExternalProductName() { return externalProductName; }
	public String getSupplierName() { return supplierName; }
	public LocalDate getCampaignStartDate() { return campaignStartDate; }
	public LocalDate getCampaignEndDate() { return campaignEndDate; }
	public Integer getActualQuantity() { return actualQuantity; }
	public String getResult() { return result; }
	public BigDecimal getNameSimilarity() { return nameSimilarity; }
	public BigDecimal getSupplierSimilarity() { return supplierSimilarity; }
	public BigDecimal getCombinedScore() { return combinedScore; }
}
