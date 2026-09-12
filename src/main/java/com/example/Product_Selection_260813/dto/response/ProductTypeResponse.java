package com.example.Product_Selection_260813.dto.response;

import com.example.Product_Selection_260813.entity.ProductType;

public class ProductTypeResponse {

	private Long id;
	private String name;
	private String description;
	private Boolean isSystemDefault;
	private Boolean isActive;
	/**
	 * 兩層階層資訊——先前這支 DTO 完全沒有暴露這兩欄，導致從 Entity
	 * 到前端全程都不知道品類其實分大類（level=1）／小類（level=2）
	 * 兩層，畫面上只能顯示扁平清單，無法依大類分組顯示小類。
	 */
	private Long parentId;
	private Integer level;
	/**
	 * 使用這個品類的商品數量。由 SettingsService.getAllProductTypes() 從
	 * ProductRepository.countGroupedByProductType() 的結果填入，不是這個
	 * Entity 本身的欄位——ProductType 不該知道有多少商品在用它，這是
	 * 跨表統計，屬於 Service 層的職責，from() 這個方法只轉換 ProductType
	 * 自己的欄位，usedCount 由呼叫端另外設定。
	 */
	private Long usedCount;

	public Long getUsedCount() {
		return usedCount;
	}

	public void setUsedCount(Long usedCount) {
		this.usedCount = usedCount;
	}

	public static ProductTypeResponse from(ProductType type) {
		ProductTypeResponse dto = new ProductTypeResponse();
		dto.id = type.getId();
		dto.name = type.getName();
		dto.description = type.getDescription();
		dto.isSystemDefault = type.getIsSystemDefault();
		dto.isActive = type.getIsActive();
		dto.parentId = type.getParentId();
		dto.level = type.getLevel();
		return dto;
	}

	public Long getParentId() {
		return parentId;
	}

	public void setParentId(Long parentId) {
		this.parentId = parentId;
	}

	public Integer getLevel() {
		return level;
	}

	public void setLevel(Integer level) {
		this.level = level;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
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
}
