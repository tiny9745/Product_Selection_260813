package com.example.Product_Selection_260813.dto.response;

import com.example.Product_Selection_260813.entity.ProductType;

public class ProductTypeResponse {

	private Long id;
	private String name;
	private String description;
	private Boolean isSystemDefault;
	private Boolean isActive;

	public static ProductTypeResponse from(ProductType type) {
		ProductTypeResponse dto = new ProductTypeResponse();
		dto.id = type.getId();
		dto.name = type.getName();
		dto.description = type.getDescription();
		dto.isSystemDefault = type.getIsSystemDefault();
		dto.isActive = type.getIsActive();
		return dto;
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
