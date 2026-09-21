package com.example.Product_Selection_260813.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 商品對某個自訂商品屬性題目的答案（EAV 值表）。
 *
 * 天生是稀疏表：一個商品，對於「它建立之後才新增的題目」，本來就不會有
 * 對應的列，查詢自然回傳查無資料——這正是這個設計「舊資料保持不變」的
 * 由來，不需要另外寫批次程式去幫舊商品補值，也不需要幫還沒填的題目存一筆
 * 空值列。停用一個題目定義也不會刪除這裡已經存在的列，商品詳情頁仍可
 * 唯讀顯示（見 CustomFieldDefinition 類別註解／SettingsService.
 * disableCustomFieldDefinition()）。
 *
 * numeric_value／text_value 只會有一個有值，依 CustomFieldDefinition.
 * fieldType 決定用哪一欄——TEXT 用 text_value，其餘三種數值類用
 * numeric_value。分成兩欄而不是「值一律存字串再各自解析」，是為了讓數值類
 * 答案能維持型別安全（DECIMAL 欄位、可以直接做數值比較與之後接回計分系統時
 * 的四則運算），不需要每次讀取都做字串轉數字的容錯處理。
 *
 * 不使用 @ManyToOne 關聯：沿用本專案既有慣例（見 FestiveCampaignTag／
 * CustomFieldApplicableType 等），FK 只在 DB 層以約束落實。
 */
@Entity
@Table(
		name = "product_custom_field_values",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_product_custom_field_values_product_field",
				columnNames = {"product_id", "field_definition_id"}
		)
)
public class ProductCustomFieldValue {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "field_definition_id", nullable = false)
	private Long fieldDefinitionId;

	@Column(name = "numeric_value", precision = 12, scale = 4)
	private BigDecimal numericValue;

	@Column(name = "text_value", length = 500)
	private String textValue;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getProductId() {
		return productId;
	}

	public void setProductId(Long productId) {
		this.productId = productId;
	}

	public Long getFieldDefinitionId() {
		return fieldDefinitionId;
	}

	public void setFieldDefinitionId(Long fieldDefinitionId) {
		this.fieldDefinitionId = fieldDefinitionId;
	}

	public BigDecimal getNumericValue() {
		return numericValue;
	}

	public void setNumericValue(BigDecimal numericValue) {
		this.numericValue = numericValue;
	}

	public String getTextValue() {
		return textValue;
	}

	public void setTextValue(String textValue) {
		this.textValue = textValue;
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
