package com.example.Product_Selection_260813.dto.response;

import java.util.List;
import java.util.Map;

import com.example.Product_Selection_260813.entity.CustomFieldDefinition;

/** GET／POST／PUT /api/settings/custom-field-definitions 的回應格式。 */
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
	/** V14新增：僅fieldType=SCALE_1_5時可能有值，key為1~5分數、value為文字說明。 */
	private Map<Integer, String> scaleLabels;
	/** V14新增：編輯產生新版本時，指向被取代的舊版本 id；null代表這是最初版本。 */
	private Long previousVersionId;
	/**
	 * V14新增：這一列是否已經被另一個版本取代。畫面應該依此隱藏「啟用」按鈕，
	 * 理由同 FactorDefinitionResponse.isSuperseded。
	 */
	private Boolean isSuperseded;

	public static CustomFieldDefinitionResponse from(CustomFieldDefinition definition,
			List<Long> applicableRootProductTypeIds, boolean isSuperseded) {
		CustomFieldDefinitionResponse dto = new CustomFieldDefinitionResponse();
		dto.id = definition.getId();
		dto.fieldCode = definition.getFieldCode();
		dto.fieldName = definition.getFieldName();
		dto.helpText = definition.getHelpText();
		dto.fieldType = definition.getFieldType() == null ? null : definition.getFieldType().name();
		dto.isRequired = definition.getIsRequired();
		dto.isActive = definition.getIsActive();
		dto.applicableRootProductTypeIds = applicableRootProductTypeIds;
		dto.scaleLabels = definition.getScaleLabels();
		dto.previousVersionId = definition.getPreviousVersionId();
		dto.isSuperseded = isSuperseded;
		return dto;
	}

	/** 單筆情境（新增/編輯/停用/啟用剛完成，當下不可能已被取代）使用這個簡化版本。 */
	public static CustomFieldDefinitionResponse from(CustomFieldDefinition definition,
			List<Long> applicableRootProductTypeIds) {
		return from(definition, applicableRootProductTypeIds, false);
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

	public Map<Integer, String> getScaleLabels() {
		return scaleLabels;
	}

	public void setScaleLabels(Map<Integer, String> scaleLabels) {
		this.scaleLabels = scaleLabels;
	}

	public Long getPreviousVersionId() {
		return previousVersionId;
	}

	public void setPreviousVersionId(Long previousVersionId) {
		this.previousVersionId = previousVersionId;
	}

	public Boolean getIsSuperseded() {
		return isSuperseded;
	}

	public void setIsSuperseded(Boolean isSuperseded) {
		this.isSuperseded = isSuperseded;
	}
}
