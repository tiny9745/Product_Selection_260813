package com.example.Product_Selection_260813.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 更新單一 system_settings 的請求。
 *
 * value 統一收字串——這是資料庫欄位本身的型別，型別／範圍驗證交給
 * SettingsService 對照 SystemSettingRegistry 的中繼資料做，不在這裡用
 * Jakarta 註解，因為每個 key 的合法範圍都不一樣，DTO 層級的靜態註解
 * 表達不了「這個欄位的驗證規則取決於路徑參數 key 是哪一個」。
 */
public class SystemSettingUpdateRequest {

	@NotBlank(message = "設定值不可為空")
	private String value;

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
}
