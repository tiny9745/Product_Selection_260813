package com.example.Product_Selection_260813.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/settings/product-types 的 Request Body：新增自訂商品分類。
 *
 * isSystemDefault／isActive／createdBy不開放外部指定：新增的分類一律視為
 * 自訂分類（isSystemDefault=false），createdBy由Service層依登入者解析，
 * 不該由前端傳入。
 */
public class ProductTypeCreateRequest {

	@NotBlank(message = "商品類型名稱不可為空")
	private String name;

	private String description;

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
}
