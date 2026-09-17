package com.example.Product_Selection_260813.dto.request;

import com.example.Product_Selection_260813.constants.ValidationMessage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/settings/product-types 的 Request Body：新增自訂商品分類。
 *
 * isSystemDefault／isActive／createdBy不開放外部指定：新增的分類一律視為
 * 自訂分類（isSystemDefault=false），createdBy由Service層依登入者解析，
 * 不該由前端傳入。
 *
 * ⚠️ 2026-09-17補上parentId：這個欄位原本完全不存在，createProductType()
 * 因此從來沒有機會設定level／parentId，新建立的自訂分類一律是level=null、
 * parentId=null的「無層級」孤兒分類——既不是正確的大類（level=1），也不是
 * 小類（level=2），跟系統既有的兩層分類體系（見ProductType.java的level／
 * parentId欄位註解）完全脫節。
 *
 * parentId為null：新增大類（level=1），直接生成，不需要額外資訊。
 * parentId有值：新增小類（level=2），Service層會驗證這個id存在、
 * 而且本身必須是大類（level=1）——不允許小類底下再掛小類，只有兩層。
 */
public class ProductTypeCreateRequest {

	@NotBlank(message = "商品類型名稱不可為空")
	@Size(max = 50, message = ValidationMessage.PRODUCT_TYPE_NAME_TOO_LONG)
	private String name;

	@Size(max = 255, message = ValidationMessage.PRODUCT_TYPE_DESCRIPTION_TOO_LONG)
	private String description;

	/** null＝新增大類；有值＝新增小類，掛在這個id指定的大類底下。 */
	private Long parentId;

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

	public Long getParentId() {
		return parentId;
	}

	public void setParentId(Long parentId) {
		this.parentId = parentId;
	}
}
