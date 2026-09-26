package com.example.Product_Selection_260813.security;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.Product_Selection_260813.repository.AppUserRepository;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * V24：帶 mustChangePassword claim 的 token 只能呼叫 /api/auth/**，其餘 API 由 filter 直接回 403，
 * 不進入後續 filter chain。
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterMustChangePasswordTest {

	@Mock
	private JwtTokenProvider jwtTokenProvider;
	@Mock
	private AppUserRepository appUserRepository;
	@Mock
	private RestSecurityHandlers restSecurityHandlers;
	@Mock
	private HttpServletRequest request;
	@Mock
	private HttpServletResponse response;
	@Mock
	private FilterChain filterChain;
	@Mock
	private Claims claims;

	private JwtAuthenticationFilter filter;

	@BeforeEach
	void setUp() {
		filter = new JwtAuthenticationFilter(jwtTokenProvider, appUserRepository, restSecurityHandlers);
		when(request.getCookies()).thenReturn(new Cookie[] { new Cookie("access_token", "token") });
		when(jwtTokenProvider.parseClaims("token")).thenReturn(Optional.of(claims));
		when(jwtTokenProvider.getUsername(claims)).thenReturn("buyer01");
		when(jwtTokenProvider.getRole(claims)).thenReturn("PURCHASER");
		when(jwtTokenProvider.getSessionVersion(claims)).thenReturn(4);
		when(appUserRepository.findActiveSessionVersionByUsername("buyer01")).thenReturn(Optional.of(4));
		when(jwtTokenProvider.isPasswordChangeRequired(claims)).thenReturn(true);
		when(request.getContextPath()).thenReturn("");
	}

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void 必須改密碼時_一般API直接回403且不往下傳() throws Exception {
		when(request.getRequestURI()).thenReturn("/api/products");

		filter.doFilterInternal(request, response, filterChain);

		verify(restSecurityHandlers).writePasswordChangeRequired(response);
		verify(filterChain, never()).doFilter(request, response);
	}

	@Test
	void 必須改密碼時_仍可呼叫auth底下的API() throws Exception {
		when(request.getRequestURI()).thenReturn("/api/auth/me/password");

		filter.doFilterInternal(request, response, filterChain);

		verify(restSecurityHandlers, never()).writePasswordChangeRequired(response);
		verify(filterChain).doFilter(request, response);
	}
}
