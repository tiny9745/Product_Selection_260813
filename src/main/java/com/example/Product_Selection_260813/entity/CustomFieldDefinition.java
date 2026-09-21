package com.example.Product_Selection_260813.entity;

import java.time.LocalDateTime;
import java.util.Map;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.example.Product_Selection_260813.enums.CustomFieldType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 自訂商品屬性（動態問卷）的題目定義。
 *
 * 這是「開新計分因子資料源」需求的第一階段：先讓管理層能自己在設定頁定義
 * 新的商品屬性題目，不需要改資料庫欄位、不需要工程師新增 migration。
 * 商品建立/編輯表單依這裡的定義動態渲染輸入元件，答案存進
 * product_custom_field_values（每個商品各自一列，稀疏表）。
 *
 * 品類範圍（哪些大類的商品表單會出現這一題）另外存在
 * custom_field_applicable_types，這裡不直接持有集合欄位——沿用本專案既有
 * 慣例（見 FestiveCampaignTag 類別註解）：關聯只在 DB 層用 FK 約束落實，
 * Entity 層不引入 JPA 關聯物件圖，避免 N+1 與 lazy-loading 的額外複雜度。
 *
 * 為什麼不比照 factor_definitions 叫「因子」：這張表定義的是「商品的一個
 * 屬性」，不是「計分邏輯」——同一個屬性可能之後被多個計分因子引用，也可能
 * 純粹只是給人看的商品資訊（field_type=TEXT 就是這種），兩個概念故意分開，
 * 對應「先有資料，才能談怎麼計分」的分層。
 */
@Entity
@Table(name = "custom_field_definitions")
public class CustomFieldDefinition {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "field_code", nullable = false, length = 50, unique = true)
	private String fieldCode;

	@Column(name = "field_name", nullable = false, length = 100)
	private String fieldName;

	/** 商品表單上這一題底下的補充說明，可為 null。 */
	@Column(name = "help_text", length = 255)
	private String helpText;

	@Enumerated(EnumType.STRING)
	@Column(name = "field_type", nullable = false, length = 20)
	private CustomFieldType fieldType;

	@Column(name = "is_required", nullable = false)
	private Boolean isRequired = false;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;

	/**
	 * 編輯產生新版本時，指向被取代的舊版本 id；null 代表這是最初版本，或這一列
	 * 從未被編輯取代過（單純停用/啟用）。V14 新增，理由與用途同
	 * FactorDefinition.previousVersionId——SettingsService.
	 * enableCustomFieldDefinition() 用同一套邏輯擋下「重新啟用已被取代的舊題目」。
	 */
	@Column(name = "previous_version_id")
	private Long previousVersionId;

	/**
	 * 僅 fieldType=SCALE_1_5 時可能有值：1~5 每個分數代表的文字說明，
	 * 例如「供應穩定性」的 5 代表「非常穩定」。key 為分數（1~5），value 為
	 * 說明文字。其餘型態這裡恆為 null，SettingsService 會在建立/編輯時驗證
	 * （見 validateScaleLabels()），不允許非 SCALE_1_5 型態夾帶這個欄位，
	 * 避免出現「有資料但永遠不會被讀取」的死資料。
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "scale_labels")
	private Map<Integer, String> scaleLabels;

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

	public Map<Integer, String> getScaleLabels() {
		return scaleLabels;
	}

	public void setScaleLabels(Map<Integer, String> scaleLabels) {
		this.scaleLabels = scaleLabels;
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
