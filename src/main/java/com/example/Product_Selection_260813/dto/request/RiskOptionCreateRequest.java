package com.example.Product_Selection_260813.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/settings/risk-options 的 Request Body：新增自訂人工風險類型。
 *
 * alertKeywords是AI風險提示的關鍵字引導詞（逗號或頓號分隔），供儀表板
 * 「高風險示警」卡片比對ai_analyses的summary／reasons文字使用
 * （見DashboardService.getRiskAlerts()）。可不填——不填代表這個風險類型
 * 只作為審核時的人工複選項目，不參與自動示警比對。
 *
 * isSystemDefault／isActive不開放外部指定：新增的一律為自訂項目
 * （isSystemDefault=false）且預設啟用，符合「新增就是要用」的直覺。
 */
public class RiskOptionCreateRequest {

	@NotBlank(message = "風險選項名稱不可為空")
	private String name;

	private String description;

	private String alertKeywords;

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

	public String getAlertKeywords() {
		return alertKeywords;
	}

	public void setAlertKeywords(String alertKeywords) {
		this.alertKeywords = alertKeywords;
	}
}
