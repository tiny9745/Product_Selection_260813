package com.example.Product_Selection_260813.dto.request;

import com.example.Product_Selection_260813.constants.ValidationMessage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/auth/me 的 Request Body：使用者修改自己的顯示名稱。
 *
 * 只開放修改name，不含username／role：username是登入帳號，改動涉及
 * AppUserRepository.existsByUsername唯一性檢查與「使用者可能忘記自己
 * 改過的登入帳號」等問題，本次需求未提及；role屬於權限層級，
 * 只能由管理層透過帳號管理異動，不應該讓使用者自行調整（否則PURCHASER
 * 能把自己升級成MANAGER）。
 */
public class UpdateProfileRequest {

	@NotBlank(message = ValidationMessage.USER_NAME_BLANK)
	@Size(max = 50, message = ValidationMessage.USER_NAME_TOO_LONG)
	private String name;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}