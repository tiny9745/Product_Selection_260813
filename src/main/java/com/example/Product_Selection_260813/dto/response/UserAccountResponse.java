package com.example.Product_Selection_260813.dto.response;

import java.time.LocalDateTime;

import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.enums.UserRole;

/**
 * 帳號管理（GET／POST /api/users、PUT /api/users/{id}/disable）的回應格式。
 *
 * <b>為什麼不共用既有的UserResponse：</b>UserResponse是登入／`/me`用的「自身身分」
 * 格式，只含id／username／name／role；帳號管理是管理視角，需要額外顯示
 * enabled（啟用狀態，這正是停用功能的操作對象）與createdAt（判斷帳號新舊）。
 * 若為此改動UserResponse，會連帶改變login與/me兩支既有API的回應格式，
 * 影響範圍超出帳號管理本身——兩者用途不同，各自維護反而邊界清楚。
 *
 * <b>password絕不出現在此DTO</b>：無論明文或BCrypt雜湊值都不回傳。雜湊值外洩
 * 雖不等於密碼外洩，但等於免費送給攻擊者離線暴力破解的素材，沒有任何
 * 畫面需要它。
 */
public class UserAccountResponse {

	private Long id;
	private String username;
	private String name;
	private UserRole role;
	private Boolean enabled;
	private LocalDateTime createdAt;

	public static UserAccountResponse from(AppUser user) {
		UserAccountResponse dto = new UserAccountResponse();
		dto.id = user.getId();
		dto.username = user.getUsername();
		dto.name = user.getName();
		dto.role = user.getRole();
		dto.enabled = user.getEnabled();
		dto.createdAt = user.getCreatedAt();
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

	public Boolean getEnabled() {
		return enabled;
	}

	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
