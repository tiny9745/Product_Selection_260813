package com.example.Product_Selection_260813.dto.request;

import com.example.Product_Selection_260813.constants.ValidationMessage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/auth/me/password 的 Request Body：使用者修改自己的密碼。
 *
 * 需帶入currentPassword由Service層驗證：防止裝置未鎖定時被他人直接
 * 把密碼改掉、鎖死原本使用者。newPassword沿用UserCreateRequest同一套
 * 密碼強度規則（僅長度下限8碼，理由見UserCreateRequest類別註解），
 * 兩處刻意保持一致，避免「建立帳號」與「改密碼」對密碼強度的認定不同步。
 */
public class ChangePasswordRequest {

	@NotBlank(message = ValidationMessage.USER_CURRENT_PASSWORD_BLANK)
	private String currentPassword;

	@NotBlank(message = ValidationMessage.USER_PASSWORD_BLANK)
	@Size(min = 8, message = ValidationMessage.USER_PASSWORD_TOO_SHORT)
	private String newPassword;

	public String getCurrentPassword() {
		return currentPassword;
	}

	public void setCurrentPassword(String currentPassword) {
		this.currentPassword = currentPassword;
	}

	public String getNewPassword() {
		return newPassword;
	}

	public void setNewPassword(String newPassword) {
		this.newPassword = newPassword;
	}
}