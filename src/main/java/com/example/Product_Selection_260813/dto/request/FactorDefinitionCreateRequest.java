package com.example.Product_Selection_260813.dto.request;

import java.math.BigDecimal;
import java.util.Map;

import com.example.Product_Selection_260813.enums.FactorDataSource;
import com.example.Product_Selection_260813.enums.FactorStrategyCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/settings/factor-definitions 的 Request Body：新增自訂計分因子。
 *
 * 新增後這個因子只是「存在」，不代表任何評估模式會用到它——要讓某個自訂模式
 * 開始採計，管理層還需要另外呼叫既有的
 * PUT /api/settings/evaluation-modes/{id}/factors，把這個 factorCode 加進去
 * 並分配權重（其餘因子的權重要跟著調整，讓整份加總仍為 100）。這是刻意
 * 分成兩支端點的設計：這支端點回答「這個因子怎麼算」（全域），另一支端點
 * 回答「哪個模式要不要用、用多少」（各模式各自決定），語意跟既有
 * evaluation_factors／factor_definitions 的分工一致。
 *
 * isActive／isSystemDefault 不開放外部指定，比照 RiskOptionCreateRequest 的
 * 既有原則：新增的一律是自訂、啟用中的因子。
 *
 * <b>2026-09-20新增：資料源二選一</b>——dataSourceCode（綁定既有 Product
 * 固定欄位）跟 customFieldDefinitionId（綁定自訂商品屬性動態問卷題目）
 * 恰好擇一，兩者都不送或都送會被 SettingsService.createFactorDefinition()
 * 拒絕。這裡都不加 @NotNull：靜態的 Bean Validation 沒辦法表達「兩者恰好
 * 一個有值」這種條件關係，驗證放在 Service 層。
 */
public class FactorDefinitionCreateRequest {

	@NotBlank(message = "因子代碼不可為空")
	@Size(max = 50, message = "因子代碼長度不可超過50")
	private String factorCode;

	@NotBlank(message = "因子名稱不可為空")
	@Size(max = 100, message = "因子名稱長度不可超過100")
	private String factorName;

	/** 畫面分組顯示用，可為空——不強制歸類到既有四組分組。 */
	private String category;

	@NotNull(message = "必須指定運算邏輯")
	private FactorStrategyCode strategyCode;

	/** 跟 customFieldDefinitionId 二選一，見類別註解。 */
	private FactorDataSource dataSourceCode;

	/** 跟 dataSourceCode 二選一，見類別註解。綁定 custom_field_definitions.id。 */
	private Long customFieldDefinitionId;

	/**
	 * 該策略自己的參數，例如 MANUAL_SCALE／MANUAL_PERCENT 的 "scale"。
	 * 可省略——省略時各策略會用自己的預設值（見各 FactorCalculationStrategy 實作）。
	 */
	private Map<String, BigDecimal> strategyParams;

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
