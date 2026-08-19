package com.example.Product_Selection_260813.enums;

public enum UserRole {
	PURCHASER("操作層"),//
	MANAGER("管理層");

	private final String userRole;

	UserRole(String userRole) {
		this.userRole = userRole;
	}

	public String getUserRole() {
		return userRole;
	}

}
