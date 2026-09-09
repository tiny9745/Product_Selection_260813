package com.example.Product_Selection_260813.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name="product_types")
public class ProductType {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/**
	 * 父層品類；null 代表本身是大類。兩層固定深度，不支援更深的階層。
	 */
	@Column(name = "parent_id")
	private Long parentId;

	/**
	 * 1=大類 2=小類。刻意的反正規化——兩層固定深度，用 level 判斷比每次遞迴
	 * 查 parent 便宜得多，且商品只允許掛在小類（level=2）的驗證也靠這一欄。
	 *
	 * 資料庫實際型別是 TINYINT（值域小，刻意用 TINYINT 省空間），但 Java 端用
	 * Integer 存取比 Byte 好用（不用到處寫 (byte) 轉型）。Hibernate 對 Integer
	 * 的預設 JDBC 型別推斷是 INTEGER，跟資料庫的 TINYINT 對不上，在
	 * ddl-auto=validate 時會直接判定 schema 不合法而啟動失敗。用
	 * @JdbcTypeCode 明確告訴 Hibernate 這個欄位要當 TINYINT 驗證，
	 * Java 端型別維持 Integer 不受影響。
	 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "level", nullable = false)
	private Integer level = 1;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder = 0;

	@Column(name = "name", nullable = false, length = 50)
	private String name;

	// ---- 品類預設屬性：商品層對應欄位為 null 時繼承這裡的值 ----
	// 這層預設是讓勾選式設計可行的關鍵配套。採購建一件「有機小松菜」選好
	// 「蔬果」品類，溫層自動帶冷藏、效期自動帶 7 天內——九個欄位變成確認一眼。
	// 沒有這層預設，那些欄位會變成沒人填的空欄位，而沒人填的欄位價值是零。

	@Column(name = "default_temperature_zone", length = 20)
	private String defaultTemperatureZone;

	/** 此品類商品是否有效期概念；false 時效期欄位不列入資料完整度分母。 */
	@Column(name = "has_shelf_life")
	private Boolean hasShelfLife;

	@Column(name = "default_shelf_life_tier", length = 20)
	private String defaultShelfLifeTier;

	@Column(name = "return_policy", length = 30)
	private String returnPolicy;

	/** GATE_SHELF_LIFE 的門檻天數。解析順序：小類 → 大類 → system_settings。 */
	@Column(name = "shelf_life_threshold_days")
	private Integer shelfLifeThresholdDays;

	/** MOQ 三層解析的中間層。 */
	@Column(name = "default_moq")
	private Integer defaultMoq;

	@Column(name = "required_certification", length = 200)
	private String requiredCertification;

	/** 評估模式綁品類；僅在大類（level=1）設定有意義。 */
	@Column(name = "default_evaluation_mode_id")
	private Long defaultEvaluationModeId;

	@Column(name = "description", length = 255)
	private String description;

	@Column(name = "is_system_default", nullable = false)
	private Boolean isSystemDefault = false;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;

	@Column(name = "created_by")
	private Long createdBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}


	/** 父層品類 id；null 代表本身是大類 */
	public Long getParentId() {
		return parentId;
	}

	public void setParentId(Long parentId) {
		this.parentId = parentId;
	}

	/** 1=大類 2=小類 */
	public Integer getLevel() {
		return level;
	}

	public void setLevel(Integer level) {
		this.level = level;
	}

	public Integer getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(Integer sortOrder) {
		this.sortOrder = sortOrder;
	}

	public String getDefaultTemperatureZone() {
		return defaultTemperatureZone;
	}

	public void setDefaultTemperatureZone(String defaultTemperatureZone) {
		this.defaultTemperatureZone = defaultTemperatureZone;
	}

	public Boolean getHasShelfLife() {
		return hasShelfLife;
	}

	public void setHasShelfLife(Boolean hasShelfLife) {
		this.hasShelfLife = hasShelfLife;
	}

	public String getDefaultShelfLifeTier() {
		return defaultShelfLifeTier;
	}

	public void setDefaultShelfLifeTier(String defaultShelfLifeTier) {
		this.defaultShelfLifeTier = defaultShelfLifeTier;
	}

	public String getReturnPolicy() {
		return returnPolicy;
	}

	public void setReturnPolicy(String returnPolicy) {
		this.returnPolicy = returnPolicy;
	}

	public Integer getShelfLifeThresholdDays() {
		return shelfLifeThresholdDays;
	}

	public void setShelfLifeThresholdDays(Integer shelfLifeThresholdDays) {
		this.shelfLifeThresholdDays = shelfLifeThresholdDays;
	}

	public Integer getDefaultMoq() {
		return defaultMoq;
	}

	public void setDefaultMoq(Integer defaultMoq) {
		this.defaultMoq = defaultMoq;
	}

	public String getRequiredCertification() {
		return requiredCertification;
	}

	public void setRequiredCertification(String requiredCertification) {
		this.requiredCertification = requiredCertification;
	}

	public Long getDefaultEvaluationModeId() {
		return defaultEvaluationModeId;
	}

	public void setDefaultEvaluationModeId(Long defaultEvaluationModeId) {
		this.defaultEvaluationModeId = defaultEvaluationModeId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Boolean getIsSystemDefault() {
		return isSystemDefault;
	}

	public void setIsSystemDefault(Boolean isSystemDefault) {
		this.isSystemDefault = isSystemDefault;
	}

	public Boolean getIsActive() {
		return isActive;
	}

	public void setIsActive(Boolean isActive) {
		this.isActive = isActive;
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
}