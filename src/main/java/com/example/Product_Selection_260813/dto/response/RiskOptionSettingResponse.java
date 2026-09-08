package com.example.Product_Selection_260813.dto.response;

import com.example.Product_Selection_260813.entity.RiskOption;

/**
 * 系統設定（GET／POST／PUT／disable／enable /api/settings/risk-options）
 * 的回應格式，管理視角專用。
 *
 * <b>為什麼不共用既有的RiskOptionResponse：</b>RiskOptionResponse是審核頁
 * 「人工風險評估（複選）」渲染用的格式（見該類別註解），刻意不含alertKeywords
 * ／isActive——審核當下不需要知道AI示警關鍵字，清單本身也已經是
 * findByIsActiveTrue()篩選過的結果，不需要isActive欄位。
 *
 * 但設定頁是管理視角：需要看到、也需要能編輯alertKeywords（見
 * RiskOptionUpdateRequest類別註解），也需要isActive來呈現目前的啟用/停用
 * 狀態（否則呼叫disable/enable後前端無從得知操作是否生效）。若為此改動
 * RiskOptionResponse，會連帶影響審核頁的回應格式與資料揭露範圍——兩者用途
 * 不同，各自維護反而邊界清楚（同UserAccountResponse不共用UserResponse的理由）。
 */
public class RiskOptionSettingResponse {

	private Long id;
	private String name;
	private String description;
	private String alertKeywords;
	private Boolean isSystemDefault;
	private Boolean isActive;

	public static RiskOptionSettingResponse from(RiskOption option) {
		RiskOptionSettingResponse dto = new RiskOptionSettingResponse();
		dto.id = option.getId();
		dto.name = option.getName();
		dto.description = option.getDescription();
		dto.alertKeywords = option.getAlertKeywords();
		dto.isSystemDefault = option.getIsSystemDefault();
		dto.isActive = option.getIsActive();
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

	public String getAlertKeywords() {
		return alertKeywords;
	}

	public void setAlertKeywords(String alertKeywords) {
		this.alertKeywords = alertKeywords;
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
