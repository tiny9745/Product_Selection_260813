package com.example.Product_Selection_260813.dto.request;

import java.math.BigDecimal;
import java.util.Map;

import com.example.Product_Selection_260813.enums.FactorDataSource;
import com.example.Product_Selection_260813.enums.FactorStrategyCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/settings/factor-definitions/{id}：編輯自訂計分因子。
 *
 * 刻意不是就地更新既有列，而是「新增一列＋把被取代的舊列停用」，理由見
 * V14 migration 類別註解與 SettingsService.updateFactorDefinition()。
 *
 * 沒有 factorCode 欄位：代碼是 evaluation_factors.factor_code、
 * product_type_score_bands.factor_code 用來對照因子的穩定文字鍵，編輯
 * 不開放修改——只要代碼不變，編輯產生的新版本會自動被既有模式權重配置、
 * 目標區間設定認得，不需要额外搬移任何資料。如果真的要換代碼，語意上是
 * 「刪除這個因子、另外新增一個全新的因子」，請改用刪除（停用）＋新增。
 *
 * strategyCode／dataSourceCode／customFieldDefinitionId／strategyParams
 * 驗證規則與 FactorDefinitionCreateRequest 完全相同（資料源二選一、
 * 策略是否已實作、資料源與策略是否相容），SettingsService內部共用同一段
 * 驗證邏輯，不重複寫一次。
 */
public class FactorDefinitionUpdateRequest {

	@NotBlank(message = "因子名稱不可為空")
	@Size(max = 100, message = "因子名稱長度不可超過100")
	private String factorName;

	private String category;

	@NotNull(message = "必須指定運算邏輯")
	private FactorStrategyCode strategyCode;

	/** 跟 customFieldDefinitionId 二選一，見 FactorDefinitionCreateRequest 類別註解。 */
	private FactorDataSource dataSourceCode;

	/** 跟 dataSourceCode 二選一，見 FactorDefinitionCreateRequest 類別註解。 */
	private Long customFieldDefinitionId;

	private Map<String, BigDecimal> strategyParams;

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

	public FactorStrategyCode getStrategyCode() {
		return strategyCode;
	}

	public void setStrategyCode(FactorStrategyCode strategyCode) {
		this.strategyCode = strategyCode;
	}

	public FactorDataSource getDataSourceCode() {
		return dataSourceCode;
	}

	public void setDataSourceCode(FactorDataSource dataSourceCode) {
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
}
