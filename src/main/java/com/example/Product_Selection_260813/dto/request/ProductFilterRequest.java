package com.example.Product_Selection_260813.dto.request;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.Product_Selection_260813.enums.ProductCandidateStatus;
import com.example.Product_Selection_260813.enums.ProductItemStatus;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;

/**
 * 品項清單篩選條件（2026-09 CSV 匯出）。
 *
 * 兩處共用同一個類別：
 * <ul>
 * <li>GET /api/products：由 Controller 從 query 參數組出（既有參數名稱不變）。</li>
 * <li>POST /api/products/export：直接作為 JSON body。前端把品項管理頁「目前的篩選條件」
 * 原樣送來，後端固定改成 reviewStatus=APPROVED 後匯出全部符合筆數（不分頁）。</li>
 * </ul>
 * 所有欄位選填，null＝不篩選。
 */
public class ProductFilterRequest {

	/** 匯出時會被忽略（固定 APPROVED）。 */
	private ProductReviewStatus reviewStatus;
	private ProductItemStatus itemStatus;
	/** 不帶時 Service 預設 CANDIDATE（見 ProductService.toCriteria()）。 */
	private ProductCandidateStatus candidateStatus;
	private Long productTypeId;
	private String keyword;
	/** 商品修改時間，閉區間（沿用既有語意）。 */
	private LocalDateTime updatedFrom;
	private LocalDateTime updatedTo;
	/** 送審批次：GET /api/products/submission-batches 回傳的 batchId，或 "NONE"。 */
	private String submissionBatch;
	/** 審核日期（最新一筆審核紀錄），閉區間、以日為單位。 */
	private LocalDate reviewedFrom;
	private LocalDate reviewedTo;
	/** true＝只要從未匯出過的商品；false／不帶＝不篩。 */
	private Boolean neverExported;

	public ProductReviewStatus getReviewStatus() { return reviewStatus; }
	public void setReviewStatus(ProductReviewStatus reviewStatus) { this.reviewStatus = reviewStatus; }
	public ProductItemStatus getItemStatus() { return itemStatus; }
	public void setItemStatus(ProductItemStatus itemStatus) { this.itemStatus = itemStatus; }
	public ProductCandidateStatus getCandidateStatus() { return candidateStatus; }
	public void setCandidateStatus(ProductCandidateStatus candidateStatus) { this.candidateStatus = candidateStatus; }
	public Long getProductTypeId() { return productTypeId; }
	public void setProductTypeId(Long productTypeId) { this.productTypeId = productTypeId; }
	public String getKeyword() { return keyword; }
	public void setKeyword(String keyword) { this.keyword = keyword; }
	public LocalDateTime getUpdatedFrom() { return updatedFrom; }
	public void setUpdatedFrom(LocalDateTime updatedFrom) { this.updatedFrom = updatedFrom; }
	public LocalDateTime getUpdatedTo() { return updatedTo; }
	public void setUpdatedTo(LocalDateTime updatedTo) { this.updatedTo = updatedTo; }
	public String getSubmissionBatch() { return submissionBatch; }
	public void setSubmissionBatch(String submissionBatch) { this.submissionBatch = submissionBatch; }
	public LocalDate getReviewedFrom() { return reviewedFrom; }
	public void setReviewedFrom(LocalDate reviewedFrom) { this.reviewedFrom = reviewedFrom; }
	public LocalDate getReviewedTo() { return reviewedTo; }
	public void setReviewedTo(LocalDate reviewedTo) { this.reviewedTo = reviewedTo; }
	public Boolean getNeverExported() { return neverExported; }
	public void setNeverExported(Boolean neverExported) { this.neverExported = neverExported; }
}
