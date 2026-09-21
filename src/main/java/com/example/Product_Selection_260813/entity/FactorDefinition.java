package com.example.Product_Selection_260813.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.example.Product_Selection_260813.enums.FactorDataSource;
import com.example.Product_Selection_260813.enums.FactorStrategyCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 自訂計分因子的全域定義：這個因子叫什麼、用哪種運算邏輯、讀商品的哪個既有欄位。
 *
 * 與 evaluation_factors 的分工：這張表回答「這個因子怎麼算」（全域、跟評估模式
 * 無關的商業規則），evaluation_factors 回答「哪個模式要不要用它、權重多少」
 * （每個模式各自的選擇）。既有七個因子刻意不遷移進這張表——它們的運算邏輯
 * （尤其 HISTORY_FULFILLMENT 的貝氏收縮、TREND_HEAT 的指數衰減）目前無法通用化，
 * 繼續維持 ProductFactorScorer 裡寫死的七個方法，這張表只承接新增的自訂因子，
 * 兩者在 ProductFactorScorer.scoreAll() 裡並列計分，互不影響。
 */
@Entity
@Table(name = "factor_definitions")
public class FactorDefinition {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "factor_code", nullable = false, length = 50, unique = true)
	private String factorCode;

	@Column(name = "factor_name", nullable = false, length = 100)
	private String factorName;

	/** 畫面分組顯示用，語意同 EvaluationFactor.category，不參與計算。 */
	@Column(name = "category", length = 20)
	private String category;

	@Enumerated(EnumType.STRING)
	@Column(name = "strategy_code", nullable = false, length = 30)
	private FactorStrategyCode strategyCode;

	/**
	 * 綁定的既有 Product 欄位，跟 customFieldDefinitionId 二選一——這兩個
	 * 欄位不會同時有值，也不會同時是 null，由 SettingsService.
	 * createFactorDefinition() 驗證。改成可為 null（V13 migration 之前是
	 * NOT NULL）：本來假設每個自訂因子都一定綁定一個寫死的 Product 欄位，
	 * 但自訂商品屬性（動態問卷）上線後，因子也可能改綁一個動態問卷題目，
	 * 那種情況這裡就是 null，見 customFieldDefinitionId 的說明。
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "data_source_code", length = 50)
	private FactorDataSource dataSourceCode;

	/**
	 * 綁定的自訂商品屬性題目（custom_field_definitions.id），跟
	 * dataSourceCode 二選一。2026-09-20新增，「開新計分因子資料源」最後
	 * 一階段——讓因子除了讀 Product entity 的固定欄位，也能讀管理層自己
	 * 在設定頁新增的動態問卷答案（product_custom_field_values），不需要
	 * 工程師每次都要盤點既有欄位、改 FactorDataSource enum。
	 *
	 * 這裡故意存 id 而非 fieldCode：id 是穩定的資料庫外鍵，fieldCode
	 * 理論上不可變（目前沒有重新命名的 API），但存 id 讓關聯更明確、
	 * 也讓之後如果真的開放改名時不用煩惱這裡的參照要不要跟著更新。
	 */
	@Column(name = "custom_field_definition_id")
	private Long customFieldDefinitionId;

	/**
	 * 該策略自己的參數，例如 MANUAL_SCALE／MANUAL_PERCENT 的 "scale" 倍率。
	 * 用 Map 而非固定欄位的 POJO：不同策略需要的參數名稱不同，且策略清單
	 * 之後還會增加，比照 WeightSnapshot.scoreBands 用動態 key 的既有做法。
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "strategy_params")
	private Map<String, BigDecimal> strategyParams;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;

	/** 既有七個因子固定為 false（它們根本不在這張表）；這張表目前每一筆都是自訂因子。 */
	@Column(name = "is_system_default", nullable = false)
	private Boolean isSystemDefault = false;

	/**
	 * 編輯產生新版本時，指向被取代的舊版本 id；null 代表這是最初版本，或這一列
	 * 從未被編輯取代過（單純停用/啟用）。V14 新增，見 migration 類別註解。
	 *
	 * 用途只有一個：擋下「重新啟用一個已經被新版本取代的舊列」——
	 * SettingsService.enableFactorDefinition() 會查「有沒有其他列的
	 * previousVersionId 指向這一列」，有的話代表這一列已被取代，拒絕啟用。
	 */
	@Column(name = "previous_version_id")
	private Long previousVersionId;

	@Column(name = "created_by")
	private Long createdBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@Column(name = "updated_by")
	private Long updatedBy;

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

	public Boolean getIsActive() {
		return isActive;
	}

	public void setIsActive(Boolean isActive) {
		this.isActive = isActive;
	}

	public Boolean getIsSystemDefault() {
		return isSystemDefault;
	}

	public void setIsSystemDefault(Boolean isSystemDefault) {
		this.isSystemDefault = isSystemDefault;
	}

	public Long getPreviousVersionId() {
		return previousVersionId;
	}

	public void setPreviousVersionId(Long previousVersionId) {
		this.previousVersionId = previousVersionId;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(Long createdBy) {
		this.createdBy = createdBy;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Long getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(Long updatedBy) {
		this.updatedBy = updatedBy;
	}
}
