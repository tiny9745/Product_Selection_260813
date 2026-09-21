package com.example.Product_Selection_260813.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 自訂商品屬性題目適用的品類（大類）。
 *
 * 一個題目底下完全沒有列＝適用全部品類，不需要另外一個 is_global 旗標——
 * 「有沒有任何限制列」本身就是判斷依據，比多一個容易跟資料互相矛盾的
 * 布林欄位更不容易出錯（例如 is_global=true 但底下卻掛了限制列這種
 * 不該存在卻可能發生的狀態）。
 *
 * root_product_type_id 沿用既有 ScoreBandResolver 的既定作法：一律以「大類」
 * （product_types.level=1）為準，不是商品實際掛的小類，理由是大類數量少、
 * 適合做成勾選清單，且管理層已經在「目標區間」頁面熟悉這套「依大類設定」
 * 的心智模型（見 SettingsService.createCustomFieldDefinition() 的驗證邏輯）。
 *
 * 不使用 @ManyToOne 關聯：沿用本專案既有慣例（見 FestiveCampaignTag／
 * Product.productTypeId 等），FK 只在 DB 層以約束落實，Entity 層維持
 * plain Long id 欄位，不引入 JPA 關聯物件圖。
 */
@Entity
@Table(
		name = "custom_field_applicable_types",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_custom_field_applicable_types_field_type",
				columnNames = {"field_definition_id", "root_product_type_id"}
		)
)
public class CustomFieldApplicableType {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "field_definition_id", nullable = false)
	private Long fieldDefinitionId;

	@Column(name = "root_product_type_id", nullable = false)
	private Long rootProductTypeId;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getFieldDefinitionId() {
		return fieldDefinitionId;
	}

	public void setFieldDefinitionId(Long fieldDefinitionId) {
		this.fieldDefinitionId = fieldDefinitionId;
	}

	public Long getRootProductTypeId() {
		return rootProductTypeId;
	}

	public void setRootProductTypeId(Long rootProductTypeId) {
		this.rootProductTypeId = rootProductTypeId;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
