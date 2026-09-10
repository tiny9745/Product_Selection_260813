package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;

import com.example.Product_Selection_260813.entity.Product;

/**
 * 相似商品候選結果。
 *
 * 附上分項相似度（nameSimilarity／supplierSimilarity）而不是只回傳
 * 一個綜合分數，是為了讓畫面能顯示「品類相同、供應商相同、名稱85%相似」
 * 這種可解釋的理由——只給一個 72 分的綜合分數，使用者沒有辦法判斷這個
 * 分數合不合理，附上分項才能讓人核對系統的判斷依據，這是人工確認這一步
 * 真正能發揮作用的前提。
 */
public class SimilarProductCandidateResponse {

	private Long productId;
	private String name;
	private String supplierName;
	private String pricingType;

	/** 名稱相似度，Jaro-Winkler 演算法算出，範圍 0~1。 */
	private BigDecimal nameSimilarity;

	/** 供應商名稱相似度，範圍 0~1；任一邊供應商名稱為空時為 null（無法比較，不是 0）。 */
	private BigDecimal supplierSimilarity;

	/** 綜合分數（見 ProductSimilarityService 的加權方式），排序依據。 */
	private BigDecimal combinedScore;

	public static SimilarProductCandidateResponse of(Product product, BigDecimal nameSimilarity,
			BigDecimal supplierSimilarity, BigDecimal combinedScore) {
		SimilarProductCandidateResponse r = new SimilarProductCandidateResponse();
		r.productId = product.getId();
		r.name = product.getName();
		r.supplierName = product.getSupplierName();
		r.pricingType = product.getPricingType() != null ? product.getPricingType().name() : null;
		r.nameSimilarity = nameSimilarity;
		r.supplierSimilarity = supplierSimilarity;
		r.combinedScore = combinedScore;
		return r;
	}

	public Long getProductId() { return productId; }
	public String getName() { return name; }
	public String getSupplierName() { return supplierName; }
	public String getPricingType() { return pricingType; }
	public BigDecimal getNameSimilarity() { return nameSimilarity; }
	public BigDecimal getSupplierSimilarity() { return supplierSimilarity; }
	public BigDecimal getCombinedScore() { return combinedScore; }
}
