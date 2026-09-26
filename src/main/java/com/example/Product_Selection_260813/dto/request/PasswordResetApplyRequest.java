package com.example.Product_Selection_260813.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/auth/password-reset-requests（不需登入）：使用者申請重設密碼。
 * 只帶帳號；回應不透露帳號是否存在（見 PasswordResetRequestService.apply()）。
 */
public class PasswordResetApplyRequest {

	@NotBlank(message = "請輸入登入帳號")
	@Size(max = 50, message = "登入帳號長度不可超過 50 字")
	private String username;

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}
}
