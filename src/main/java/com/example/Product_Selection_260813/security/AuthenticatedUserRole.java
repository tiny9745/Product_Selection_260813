package com.example.Product_Selection_260813.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import com.example.Product_Selection_260813.enums.UserRole;

/**
 * 從目前請求的 Authentication 取出呼叫者角色，供「同一支 API 依角色回傳不同欄位」的端點使用
 * （例如 GET /api/group-buy-records 的成本資訊揭露範圍）。
 *
 * 角色來源是 JwtAuthenticationFilter 依 JWT role claim 寫入的 ROLE_ 權限，與 @PreAuthorize
 * 判斷用的是同一份資料，不另外查資料庫；JWT 的有效性（含 activeSessionVersion 比對）
 * 已在 filter 驗證過。
 *
 * 無法辨識時一律回傳權限較低的 PURCHASER（fail-safe）：寧可少給管理資訊，也不要因為
 * 判斷失敗而把成本價外露給操作層。
 */
public final class AuthenticatedUserRole {

	private static final String MANAGER_AUTHORITY = "ROLE_" + UserRole.MANAGER.name();

	private AuthenticatedUserRole() {
	}

	public static UserRole of(Authentication authentication) {
		if (authentication == null || authentication.getAuthorities() == null) {
			return UserRole.PURCHASER;
		}
		for (GrantedAuthority authority : authentication.getAuthorities()) {
			if (MANAGER_AUTHORITY.equals(authority.getAuthority())) {
				return UserRole.MANAGER;
			}
		}
		return UserRole.PURCHASER;
	}
}
