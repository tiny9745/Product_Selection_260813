package com.example.Product_Selection_260813.dto.request;

import com.example.Product_Selection_260813.constants.ValidationMessage;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/auth/login 的 Request Body。
 */
public class LoginRequest {

	@NotBlank(message = ValidationMessage.AUTH_USER_NAME)
	private String username;

	@NotBlank(message = ValidationMessage.AUTH_PASSWORD)
	private String password;

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
}
