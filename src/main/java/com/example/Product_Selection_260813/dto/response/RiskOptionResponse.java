package com.example.Product_Selection_260813.dto.response;

import com.example.Product_Selection_260813.entity.RiskOption;

/**
 * 審核頁「人工風險評估（複選）」的可選清單項目。
 *
 * 不回傳alert_keywords／created_by／created_at：這些是AI Prompt引導詞與稽核欄位，
 * 對管理層勾選當下的畫面渲染沒有意義，避免API回應夾帶用不到的內部細節。
 */
public class RiskOptionResponse {

	private Long id;
	private String name;
	private String description;
	private Boolean isSystemDefault;

	public static RiskOptionResponse from(RiskOption option) {
		RiskOptionResponse dto = new RiskOptionResponse();
		dto.id = option.getId();
		dto.name = option.getName();
		dto.description = option.getDescription();
		dto.isSystemDefault = option.getIsSystemDefault();
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
}
