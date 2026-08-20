package com.example.Product_Selection_260813.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 每個請求進來時，嘗試從httpOnly Cookie取出JWT並驗證，驗證成功則把使用者身份
 * 寫進SecurityContext，讓後續的@AuthenticationPrincipal、@PreAuthorize能取用。
 *
 * 設計取捨：這裡直接信任JWT裡的claims（username／role），不會為了每個請求
 * 都額外查一次資料庫確認enabled狀態——若真的要做到帳號停用立即生效，
 * 每個受保護的API都要多一次DB查詢，對一個雛型系統而言成本大於效益。
 * 目前只有AuthService.getCurrentUser()（對應/api/auth/me）刻意重查資料庫，
 * 其餘API的「帳號停用生效時間」則依循六-4決議：最長等到8小時token過期為止，
 * 這是文件裡「不做Refresh Token/黑名單機制」的必然結果，不是這支filter獨自的取捨。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String TOKEN_COOKIE_NAME = "access_token";

	private final JwtTokenProvider jwtTokenProvider;

	public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
		this.jwtTokenProvider = jwtTokenProvider;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		extractTokenFromCookie(request).flatMap(jwtTokenProvider::parseClaims).ifPresent(claims -> {
			String username = jwtTokenProvider.getUsername(claims);
			String role = jwtTokenProvider.getRole(claims);

			// 統一加上"ROLE_"前綴：Spring Security的hasRole("MANAGER")底層比對的
			// 就是"ROLE_MANAGER"這個Authority字串，這是框架慣例，不是本專案自創的規則。
			var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

			var authentication = new UsernamePasswordAuthenticationToken(username, null, authorities);
			SecurityContextHolder.getContext().setAuthentication(authentication);
		});

		filterChain.doFilter(request, response);
	}

	private Optional<String> extractTokenFromCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		for (Cookie cookie : cookies) {
			if (TOKEN_COOKIE_NAME.equals(cookie.getName())) {
				return Optional.ofNullable(cookie.getValue());
			}
		}
		return Optional.empty();
	}
}
