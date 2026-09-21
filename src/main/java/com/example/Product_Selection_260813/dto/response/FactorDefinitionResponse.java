package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;
import java.util.Map;

import com.example.Product_Selection_260813.entity.FactorDefinition;

/** GET／POST／PUT /api/settings/factor-definitions 的回應格式。 */
public class FactorDefinitionResponse {

	private Long id;
	private String factorCode;
	private String factorName;
	private String category;
	private String strategyCode;
	private String dataSourceCode;
	/** 綁定的自訂商品屬性題目 id，跟 dataSourceCode 二選一，見 FactorDefinition 類別註解。 */
	private Long customFieldDefinitionId;
	private Map<String, BigDecimal> strategyParams;
	private Boolean isActive;
	/** V14新增：編輯產生新版本時，指向被取代的舊版本 id；null代表這是最初版本。 */
	private Long previousVersionId;
	/**
	 * V14新增：這一列是否已經被另一個版本取代（也就是有別的列的previousVersionId
	 * 指向這一列）。畫面應該依此隱藏「啟用」按鈕——已被取代的舊版本不可重新啟用，
	 * 見 SettingsService.enableFactorDefinition()。
	 */
	private Boolean isSuperseded;

	public static FactorDefinitionResponse from(FactorDefinition definition, boolean isSuperseded) {
		FactorDefinitionResponse dto = new FactorDefinitionResponse();
		dto.id = definition.getId();
		dto.factorCode = definition.getFactorCode();
		dto.factorName = definition.getFactorName();
		dto.category = definition.getCategory();
		dto.strategyCode = definition.getStrategyCode() == null ? null : definition.getStrategyCode().name();
		dto.dataSourceCode = definition.getDataSourceCode() == null ? null : definition.getDataSourceCode().name();
		dto.customFieldDefinitionId = definition.getCustomFieldDefinitionId();
		dto.strategyParams = definition.getStrategyParams();
		dto.isActive = definition.getIsActive();
		dto.previousVersionId = definition.getPreviousVersionId();
		dto.isSuperseded = isSuperseded;
		return dto;
	}

	/** 單筆情境（新增/編輯/停用/啟用剛完成，當下不可能已被取代）使用這個簡化版本。 */
	public static FactorDefinitionResponse from(FactorDefinition definition) {
		return from(definition, false);
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getFactorCode() {
		return factorCode;
	}

	public void setFactorCode(String factorCode) {
		this.factorCode = factorCode;
	}

	public String getFactorName() {
		return factorName;
	}

	public void setFactorName(String factorName) {
		this.factorName = factorName;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getStrategyCode() {
		return strategyCode;
	}

	public void setStrategyCode(String strategyCode) {
		this.strategyCode = strategyCode;
	}

	public String getDataSourceCode() {
		return dataSourceCode;
	}

	public void setDataSourceCode(String dataSourceCode) {
		this.dataSourceCode = dataSourceCode;
	}

	public Long getCustomFieldDefinitionId() {
		return customFieldDefinitionId;
	}

	public void setCustomFieldDefinitionId(Long customFieldDefinitionId) {
		this.customFieldDefinitionId = customFieldDefinitionId;
	}

	public Map<String, BigDecimal> getStrategyParams() {
		return strategyParams;
	}

	public void setStrategyParams(Map<String, BigDecimal> strategyParams) {
		this.strategyParams = strategyParams;
	}

	public Boolean getIsActive() {
		return isActive;
	}

	public void setIsActive(Boolean isActive) {
		this.isActive = isActive;
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
