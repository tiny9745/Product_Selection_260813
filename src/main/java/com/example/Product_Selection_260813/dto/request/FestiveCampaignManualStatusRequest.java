package com.example.Product_Selection_260813.dto.request;

import jakarta.validation.constraints.NotNull;

import com.example.Product_Selection_260813.enums.FestiveCampaignStatus;

/**
 * POST /api/settings/festive-campaigns/{id}/manual-status 的 Request Body。
 *
 * status對應campaign_status切換目標值，overrideEnabled對應is_manual_override
 * 開關，兩者分開表達「切換狀態」與「開關手動覆蓋旗標」兩件事（企劃書API總表
 * 原文備註），確保熔斷清單②有明確的復原路徑——例如可以只開啟is_manual_override
 * 而不切換狀態，或切換狀態的同時決定要不要讓Daily Cron之後繼續接管。
 */
public class FestiveCampaignManualStatusRequest {

	@NotNull(message = "檔期狀態不可為空")
	private FestiveCampaignStatus status;

	@NotNull(message = "是否啟用手動覆蓋不可為空")
	private Boolean overrideEnabled;

	public FestiveCampaignStatus getStatus() {
		return status;
	}

	public void setStatus(FestiveCampaignStatus status) {
		this.status = status;
	}

	public Boolean getOverrideEnabled() {
		return overrideEnabled;
	}

	public void setOverrideEnabled(Boolean overrideEnabled) {
		this.overrideEnabled = overrideEnabled;
	}
}
