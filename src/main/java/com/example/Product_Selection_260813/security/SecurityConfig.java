package com.example.Product_Selection_260813.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * 對應 六-4 安全性設計（RBAC落實／JWT）與 十二-9 統一錯誤回應格式的認證/授權骨架。
 *
 * @EnableMethodSecurity 讓後續各Controller可以用 @PreAuthorize("hasRole('MANAGER')")
 * 逐支落實RBAC（例如 POST /api/reviews、所有 /api/settings/*），這裡先開啟，
 * 避免之後每個新Controller都要各自加這個註解才發現漏掉。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			// CSRF：Token放在httpOnly Cookie，理論上仍有CSRF曝險，但Cookie本身已設定
			// SameSite=Strict（見AuthController），瀏覽器在跨站請求時完全不會帶上這個Cookie，
			// 等同於在瀏覽器層就擋掉了CSRF攻擊的前提條件；純REST API（無Session、無表單登入）
			// 也用不到Spring Security內建的CSRF Token機制，因此關閉，而非漏未設定。
			.csrf(AbstractHttpConfigurer::disable)
			// 無狀態：不使用HttpSession保存登入狀態，每個請求都靠JwtAuthenticationFilter
			// 從Cookie重新驗證身份，對應六-4「JWT無狀態設計」決議。
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/auth/login").permitAll()
				.anyRequest().authenticated()
			)
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
