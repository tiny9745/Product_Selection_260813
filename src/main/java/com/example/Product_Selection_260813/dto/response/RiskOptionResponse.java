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

	/** 五大風險面向，供前端把選項歸到正確的分類顯示。 */
	private String category;

	/**
	 * 觸發 AI 風險提示用的關鍵字。原本這支 DTO 完全沒有暴露這個欄位——
	 * CREATE／UPDATE 請求都能寫入，GET 卻從來讀不回來，等於「能設定但
	 * 看不到目前設定的是什麼」，管理層想確認或修改既有關鍵字時無從得知
	 * 目前存了什麼，只能用「猜」或整個重新輸入一次去覆蓋。
	 */
	private String alertKeywords;

	public String getAlertKeywords() {
		return alertKeywords;
	}

	public void setAlertKeywords(String alertKeywords) {
		this.alertKeywords = alertKeywords;
	}

	/**
	 * 此選項是否由 Gate 判定自動帶入（預先勾選）。
	 *
	 * 前端據此顯示「系統判定」徽章。主管可以取消勾選，但取消後仍要把該項 id
	 * 留在 systemSuggestedRiskOptionIds 一起送出——否則後端無法區分
	 * 「系統沒建議」和「系統建議了但被推翻」。
	 */
	private Boolean autoTriggered;

	/** 自動帶入時的判定原因，來自 Gate 的 reason。 */
	private String triggerReason;


	public static RiskOptionResponse from(RiskOption option) {
		RiskOptionResponse dto = new RiskOptionResponse();
		dto.id = option.getId();
		dto.name = option.getName();
		dto.description = option.getDescription();
		dto.isSystemDefault = option.getIsSystemDefault();
		dto.category = option.getCategory();
		dto.alertKeywords = option.getAlertKeywords();
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

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public Boolean getAutoTriggered() {
		return autoTriggered;
	}

	public void setAutoTriggered(Boolean autoTriggered) {
		this.autoTriggered = autoTriggered;
	}

	public String getTriggerReason() {
		return triggerReason;
	}

	public void setTriggerReason(String triggerReason) {
		this.triggerReason = triggerReason;
	}
}
