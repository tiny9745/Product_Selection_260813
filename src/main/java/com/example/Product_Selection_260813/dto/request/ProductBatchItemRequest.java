package com.example.Product_Selection_260813.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/products/batch 裡陣列的單一元素。
 *
 * 刻意用「包一層」而不是讓 ProductBatchCreateRequest 直接裝
 * List&lt;ProductCreateRequest&gt;，原因：
 * 1. rowNumber——前端是從 CSV/Excel 匯入，一批可能有幾十列，驗證失敗或
 *    後端逐列建立失敗時，要能對回使用者原始檔案的第幾列，不能只回「第 3
 *    個」這種跟原始檔案列號對不上的序位。
 * 2. imageFileName——批次圖片走「CSV 一欄檔名 + 另外夾帶的圖片檔案
 *    清單」比對，不是每列各打一次 uploadImage，所以圖片檔名要跟這一列
 *    的商品資料放在一起送，而不是塞進 ProductCreateRequest 本體（那支
 *    DTO 是單筆新增既有的公開介面，不為了批次這個新場景改動它的欄位）。
 *
 * product 欄位本身沿用 ProductCreateRequest 既有的全部驗證規則
 * （@Valid 會連帶觸發），批次新增與單筆新增的欄位規則必須完全一致，
 * 不能因為是批次就放寬——避免使用者用批次匯入繞過單筆新增擋不下的資料。
 */
public class ProductBatchItemRequest {

	/**
	 * 對回原始 CSV/Excel 的列號（建議前端從資料列開始算，不含標題列），
	 * 純粹用於結果回報，不參與任何業務判斷。允許為 null——理論上不影響
	 * 建立商品本身，只是那一列的結果會用陣列序位顯示。
	 */
	private Integer rowNumber;

	/**
	 * 對應這一列商品要套用的圖片檔名，需與 multipart 裡另外夾帶的圖片
	 * 檔案的原始檔名（originalFilename）完全一致（大小寫需相符）才會配對成功。
	 * 可留空——代表這一列商品這次不上傳圖片，不影響商品本身的建立。
	 * 若填了檔名但夾帶清單裡找不到對應檔案，商品仍會建立成功，只是
	 * 結果會多一句警告文字，不視為整列失敗（見 ProductBatchItemResult）。
	 */
	private String imageFileName;

	@NotNull(message = "商品資料不可為空")
	@Valid
	private ProductCreateRequest product;

	public Integer getRowNumber() {
		return rowNumber;
	}

	public void setRowNumber(Integer rowNumber) {
		this.rowNumber = rowNumber;
	}

	public String getImageFileName() {
		return imageFileName;
	}

	public void setImageFileName(String imageFileName) {
		this.imageFileName = imageFileName;
	}

	public ProductCreateRequest getProduct() {
		return product;
	}

	public void setProduct(ProductCreateRequest product) {
		this.product = product;
	}
}
