package com.example.Product_Selection_260813.dto.request;

import com.example.Product_Selection_260813.constants.ValidationMessage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/settings/product-types/{id} 的 Request Body：重新命名既有商品分類。
 *
 * 只開放name／description：isSystemDefault不可修改（系統預設與自訂分類的身分
 * 一旦建立就固定，改名不改變這個身分）；isActive不透過這支端點修改，維持由
 * 專屬的enable/disable端點承接，避免同一個狀態欄位有兩個修改入口互相打架
 * （見SettingsService.updateProductType()類別註解）。
 *
 * 系統預設分類（isSystemDefault=true）本輪確認開放改名：程式碼中沒有任何地方
 * 寫死比對這9類分類的名稱字串（不像FestiveCategory是獨立enum），改名不影響
 * 其他功能運作。
 */
public class ProductTypeUpdateRequest {

	@NotBlank(message = "商品類型名稱不可為空")
	@Size(max = 50, message = ValidationMessage.PRODUCT_TYPE_NAME_TOO_LONG)
	private String name;

	@Size(max = 255, message = ValidationMessage.PRODUCT_TYPE_DESCRIPTION_TOO_LONG)
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
