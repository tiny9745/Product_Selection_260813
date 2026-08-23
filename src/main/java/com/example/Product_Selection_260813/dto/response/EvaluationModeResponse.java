package com.example.Product_Selection_260813.dto.response;

import com.example.Product_Selection_260813.entity.EvaluationMode;

/**
 * 評估模式基本資料，共用於：
 * GET /api/settings/evaluation-modes（清單）／
 * GET、PUT /api/settings/evaluation-mode/current（目前生效模式）。
 */
public class EvaluationModeResponse {

	private Long id;
	private String modeCode;
	private String modeName;
	private Integer version;
	private String description;
	private Boolean isActive;

	public static EvaluationModeResponse from(EvaluationMode mode) {
		EvaluationModeResponse dto = new EvaluationModeResponse();
		dto.id = mode.getId();
		dto.modeCode = mode.getModeCode();
		dto.modeName = mode.getModeName();
		dto.version = mode.getVersion();
		dto.description = mode.getDescription();
		dto.isActive = mode.getIsActive();
		return dto;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getModeCode() {
		return modeCode;
	}

	public void setModeCode(String modeCode) {
		this.modeCode = modeCode;
	}

	public String getModeName() {
		return modeName;
	}

	public void setModeName(String modeName) {
		this.modeName = modeName;
	}

	public Integer getVersion() {
		return version;
	}

	public void setVersion(Integer version) {
		this.version = version;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Boolean getIsActive() {
		return isActive;
	}

	public void setIsActive(Boolean isActive) {
		this.isActive = isActive;
	}
}
