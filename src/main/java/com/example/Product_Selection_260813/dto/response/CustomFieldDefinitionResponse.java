package com.example.Product_Selection_260813.dto.response;

import java.util.List;

import com.example.Product_Selection_260813.entity.CustomFieldDefinition;

/** GET／POST /api/settings/custom-field-definitions 的回應格式。 */
public class CustomFieldDefinitionResponse {

	private Long id;
	private String fieldCode;
	private String fieldName;
	private String helpText;
	private String fieldType;
	private Boolean isRequired;
	private Boolean isActive;
	/** 空陣列＝適用全部品類，見 CustomFieldDefinitionCreateRequest 的類別註解。 */
	private List<Long> applicableRootProductTypeIds;

	public static CustomFieldDefinitionResponse from(CustomFieldDefinition definition,
			List<Long> applicableRootProductTypeIds) {
		CustomFieldDefinitionResponse dto = new CustomFieldDefinitionResponse();
		dto.id = definition.getId();
		dto.fieldCode = definition.getFieldCode();
		dto.fieldName = definition.getFieldName();
		dto.helpText = definition.getHelpText();
		dto.fieldType = definition.getFieldType() == null ? null : definition.getFieldType().name();
		dto.isRequired = definition.getIsRequired();
		dto.isActive = definition.getIsActive();
		dto.applicableRootProductTypeIds = applicableRootProductTypeIds;
		return dto;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

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

	public String getFieldType() {
		return fieldType;
	}

	public void setFieldType(String fieldType) {
		this.fieldType = fieldType;
	}

	public Boolean getIsRequired() {
		return isRequired;
	}

	public void setIsRequired(Boolean isRequired) {
		this.isRequired = isRequired;
	}

	public Boolean getIsActive() {
		return isActive;
	}

	public void setIsActive(Boolean isActive) {
		this.isActive = isActive;
	}

	public List<Long> getApplicableRootProductTypeIds() {
		return applicableRootProductTypeIds;
	}

	public void setApplicableRootProductTypeIds(List<Long> applicableRootProductTypeIds) {
		this.applicableRootProductTypeIds = applicableRootProductTypeIds;
	}
}
