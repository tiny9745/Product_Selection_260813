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

	/**
	 * 是否允許調整權重。前端據此決定要不要顯示「編輯」按鈕——
	 * 沒有這個欄位的話，前端只能寫死 id 判斷，而 id 是流水號，
	 * 換環境或重新匯入資料就會判斷錯。
	 */
	private Boolean isEditable;

	public static EvaluationModeResponse from(EvaluationMode mode) {
		EvaluationModeResponse dto = new EvaluationModeResponse();
		dto.id = mode.getId();
		dto.modeCode = mode.getModeCode();
		dto.modeName = mode.getModeName();
		dto.version = mode.getVersion();
		dto.description = mode.getDescription();
		dto.isActive = mode.getIsActive();
		dto.isEditable = mode.getIsEditable();
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

	public Boolean getIsEditable() {
		return isEditable;
	}

	public void setIsEditable(Boolean isEditable) {
		this.isEditable = isEditable;
	}
}
