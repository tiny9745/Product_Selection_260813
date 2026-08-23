package com.example.Product_Selection_260813.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * PUT /api/settings/evaluation-mode/current 的 Request Body。
 *
 * 只能切換成既有3套模式其中之一，不開放直接修改權重（見企劃書四-14設計取捨）——
 * 這支API只負責「指到哪個既有模式」，不負責「這個模式的內容是什麼」。
 */
public class SwitchEvaluationModeRequest {

	@NotNull(message = "評估模式編號不可為空")
	private Long evaluationModeId;

	public Long getEvaluationModeId() {
		return evaluationModeId;
	}

	public void setEvaluationModeId(Long evaluationModeId) {
		this.evaluationModeId = evaluationModeId;
	}
}
