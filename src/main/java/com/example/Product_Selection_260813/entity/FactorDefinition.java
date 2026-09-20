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

	@Enumerated(EnumType.STRING)
	@Column(name = "data_source_code", nullable = false, length = 50)
	private FactorDataSource dataSourceCode;

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
