package com.example.Product_Selection_260813.dto.request;

import java.util.List;
import java.util.Map;

import com.example.Product_Selection_260813.enums.CustomFieldType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/settings/custom-field-definitions/{id}：編輯自訂商品屬性題目。
 *
 * 刻意不是就地更新既有列，而是「新增一列（內容是修改後的版本）＋把被取代的
 * 舊列停用」，理由見 V14 migration 類別註解與 SettingsService.
 * updateCustomFieldDefinition()。
 *
 * 沒有 fieldCode 欄位：代碼是跨表（product_custom_field_values、
 * factor_definitions.custom_field_definition_id其實是存id不是存code，但
 * WeightFactorSnapshot.customFieldCode是凍結字串）用來識別「這是同一題」
 * 的穩定鍵，編輯不開放修改。如果真的要換代碼，語意上是「刪除這題、另外
 * 新增一題全新的」，不是「編輯」，請改用刪除（停用）＋新增兩個既有端點。
 *
 * 其餘欄位都是完整覆蓋語意（比照 CustomFieldDefinitionCreateRequest）：
 * applicableRootProductTypeIds省略或空陣列＝適用全部品類；scaleLabels
 * 省略＝不設定文字說明。
 */
public class CustomFieldDefinitionUpdateRequest {

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

	/** 僅fieldType=SCALE_1_5時可以提供，見CustomFieldDefinitionCreateRequest類別註解。 */
	private Map<Integer, String> scaleLabels;

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

	public Map<Integer, String> getScaleLabels() {
		return scaleLabels;
	}

	public void setScaleLabels(Map<Integer, String> scaleLabels) {
		this.scaleLabels = scaleLabels;
	}
}
