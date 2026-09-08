package com.example.Product_Selection_260813.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * PUT /api/settings/risk-options/{id} 的 Request Body：重新命名／調整既有風險選項。
 *
 * 與ProductTypeUpdateRequest同一套設計原則：只開放name／description／
 * alertKeywords，isSystemDefault不可修改（建立時就固定的身分），isActive
 * 不透過這支端點修改（維持由disableRiskOption()/enableRiskOption()專責管理，
 * 避免同一狀態欄位有兩個修改入口）。
 *
 * alertKeywords允許改成空白：代表管理層決定讓這個風險類型退出自動示警比對
 * （見DashboardService.getRiskAlerts()），只留作審核頁的人工複選項目，這是
 * 合理的設定變更，不需要另外用null/空字串區分「未設定」與「清空」。
 */
public class RiskOptionUpdateRequest {

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
