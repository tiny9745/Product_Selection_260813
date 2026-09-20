package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;
import java.util.Map;

import com.example.Product_Selection_260813.entity.FactorDefinition;

/** GET／POST /api/settings/factor-definitions 的回應格式。 */
public class FactorDefinitionResponse {

	private Long id;
	private String factorCode;
	private String factorName;
	private String category;
	private String strategyCode;
	private String dataSourceCode;
	private Map<String, BigDecimal> strategyParams;
	private Boolean isActive;

	public static FactorDefinitionResponse from(FactorDefinition definition) {
		FactorDefinitionResponse dto = new FactorDefinitionResponse();
		dto.id = definition.getId();
		dto.factorCode = definition.getFactorCode();
		dto.factorName = definition.getFactorName();
		dto.category = definition.getCategory();
		dto.strategyCode = definition.getStrategyCode() == null ? null : definition.getStrategyCode().name();
		dto.dataSourceCode = definition.getDataSourceCode() == null ? null : definition.getDataSourceCode().name();
		dto.strategyParams = definition.getStrategyParams();
		dto.isActive = definition.getIsActive();
		return dto;
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
}
