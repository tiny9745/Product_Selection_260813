package com.example.Product_Selection_260813.dto.response;

import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.enums.UserRole;

/**
 * 使用者基本資料（不含password），/api/auth/login 與 /api/auth/me 共用同一格式，
 * 避免兩支API各自維護一份幾乎相同的欄位定義造成日後不同步。
 *
 * 注意：role這裡只回傳enum本身（序列化為"PURCHASER"／"MANAGER"字串），
 * 沒有呼叫UserRole既有的getUserRole()帶出中文顯示名稱（"操作層"／"管理層"）。
 * 這是刻意的：資料表設計文件（四-1 app_users備註）決議「顯示名稱由前端自行轉譯」，
 * 但目前UserRole.java已經把顯示名稱寫死在enum建構子裡，兩者有出入，
 * 這個矛盾還沒有確認要以哪一邊為準（見前一輪回覆），在確認前，這支DTO先照文件決議走，
 * 不把後端寫死的中文顯示名稱外露到API回應，避免之後改變決議時要多改一個地方。
 */
public class UserResponse {

	private Long id;
	private String username;
	private String name;
	private UserRole role;

	public static UserResponse from(AppUser user) {
		UserResponse dto = new UserResponse();
		dto.id = user.getId();
		dto.username = user.getUsername();
		dto.name = user.getName();
		dto.role = user.getRole();
		return dto;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public UserRole getRole() {
		return role;
	}

	public void setRole(UserRole role) {
		this.role = role;
	}
}
