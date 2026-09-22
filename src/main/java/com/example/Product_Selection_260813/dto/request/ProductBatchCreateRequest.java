package com.example.Product_Selection_260813.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * POST /api/products/batch 的 items part（application/json）解析出來的物件。
 *
 * 端點本身是 multipart/form-data（要跟另一個 part 一起送圖片檔案），
 * 這支 DTO 只對應其中 "items" 這個 part 的內容，不是整個 multipart request。
 *
 * 上限 200 筆：批次新增走「逐列各自 @Transactional」（見 ProductService.
 * createProductsBatch() 類別註解），單一 HTTP request 內要跑完全部列的
 * 建立＋計分＋（選填）圖片寫檔，筆數太多會讓單次請求時間拉得很長、
 * 也讓「一半列成功一半列失敗」這種部分失敗結果的可讀性變差；使用者要
 * 匯入更多筆時應分批送出，不在這裡放寬上限。
 */
public class ProductBatchCreateRequest {

	@NotEmpty(message = "批次新增至少要有一筆商品資料")
	@Size(max = 200, message = "單次批次新增最多 200 筆，請分批匯入")
	@Valid
	private List<ProductBatchItemRequest> items;

	public List<ProductBatchItemRequest> getItems() {
		return items;
	}

	public void setItems(List<ProductBatchItemRequest> items) {
		this.items = items;
	}
}
