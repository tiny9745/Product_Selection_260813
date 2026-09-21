package com.example.Product_Selection_260813.dto.request;

import java.util.List;

import com.example.Product_Selection_260813.enums.CustomFieldType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/settings/custom-field-definitions 的 Request Body：新增自訂商品屬性題目。
 *
 * applicableRootProductTypeIds 省略或傳空陣列＝適用全部品類；有傳值時，每個
 * id 都必須是「大類」（product_types.level=1），Service 層會驗證，不是大類
 * 一律拒絕——避免不小心選到小類，导致這一題在商品表單上永遠不會依「大類」
 * 判斷邏輯正確顯示。
 *
 * isActive／createdBy 不開放外部指定，比照 FactorDefinitionCreateRequest 的
 * 既有原則：新增的一律是啟用中的題目，操作者由後端從登入資訊解析。
 */
public class CustomFieldDefinitionCreateRequest {

	@NotBlank(message = "欄位代碼不可為空")
	@Size(max = 50, message = "欄位代碼長度不可超過50")
	private String fieldCode;

	@NotBlank(message = "欄位名稱不可為空")
	@Size(max = 100, message = "欄位名稱長度不可超過100")
	private String fieldName;

	@Size(max = 255, message = "補充說明長度不可超過255")
	private String helpText;

	@NotNull(message = "必須指定欄位型態")
	private CustomFieldType fieldType;

	private Boolean isRequired;

	/** 省略或空陣列＝適用全部品類。 */
	private List<Long> applicableRootProductTypeIds;

	public String getFieldCode() {
		return fieldCode;
	}

	public void setFieldCode(String fieldCode) {
		this.fieldCode = fieldCode;
	}

	public String getFieldName() {
		return fieldName;
	}

	public void setFieldName(String fieldName) {
		this.fieldName = fieldName;
	}

	public String getHelpText() {
		return helpText;
	}

	public void setHelpText(String helpText) {
		this.helpText = helpText;
	}

	public CustomFieldType getFieldType() {
		return fieldType;
	}

	public void setFieldType(CustomFieldType fieldType) {
		this.fieldType = fieldType;
	}

	public Boolean getIsRequired() {
		return isRequired;
	}

	public void setIsRequired(Boolean isRequired) {
		this.isRequired = isRequired;
	}

	public List<Long> getApplicableRootProductTypeIds() {
		return applicableRootProductTypeIds;
	}

	public void setApplicableRootProductTypeIds(List<Long> applicableRootProductTypeIds) {
		this.applicableRootProductTypeIds = applicableRootProductTypeIds;
	}
}
