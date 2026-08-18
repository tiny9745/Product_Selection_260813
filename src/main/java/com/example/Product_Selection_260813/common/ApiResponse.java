package com.example.Product_Selection_260813.common;

public class ApiResponse {
	private boolean success;
	private String message;

	public ApiResponse(boolean success, String message) {
		super();
		this.success = success;
		this.message = message;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public static ApiResponse success(String message) {
		return new ApiResponse(true, message);
	}

	public static ApiResponse failure(String message) {
		return new ApiResponse(false, message);
	}
}
