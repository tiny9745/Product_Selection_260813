package com.example.Product_Selection_260813.dto.response;

import com.example.Product_Selection_260813.entity.Product;

/**
 * RESALE 逐層過濾參考商品的第三層（商品名稱）選項。
 *
 * 刻意只給 id／name 兩個欄位：這一層只負責「列出可選項」，選定之後前端
 * 另外呼叫既有的 GET /api/products/{id} 取得完整資料做預填（見
 * ProductService.getProduct()），不在這裡重複塞入 description／
 * campaignTags 等欄位——那些欄位只有「使用者真的選了這一筆」才用得到，
 * 列出全部候選時就先撈一輪完整資料是不必要的查詢成本。
 */
public class ResaleReferenceOptionResponse {

	private Long productId;
	private String name;

	public static ResaleReferenceOptionResponse of(Product product) {
		ResaleReferenceOptionResponse r = new ResaleReferenceOptionResponse();
		r.productId = product.getId();
		r.name = product.getName();
		return r;
	}

	public Long getProductId() {
		return productId;
	}

	public String getName() {
		return name;
	}
}
