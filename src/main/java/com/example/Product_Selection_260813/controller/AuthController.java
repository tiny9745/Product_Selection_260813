package com.example.Product_Selection_260813.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Product_Selection_260813.common.ApiResponse;
import com.example.Product_Selection_260813.dto.request.LoginRequest;
import com.example.Product_Selection_260813.dto.response.LoginResult;
import com.example.Product_Selection_260813.dto.response.UserResponse;
import com.example.Product_Selection_260813.service.AuthService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * 對應 API總表 一、登入／權限。
 *
 * Controller只負責HTTP層的事：解析Request、設定/清除Cookie、決定HTTP狀態碼。
 * 所有認證邏輯都在AuthService，Controller不直接碰密碼比對或JWT內容解析，
 * 這樣AuthService可以完全不依賴Servlet API，單元測試不需要啟動Web環境。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private static final String TOKEN_COOKIE_NAME = "access_token";

	@Autowired
	private AuthService authService;

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<UserResponse>> login(
			@Valid @RequestBody LoginRequest request,
			HttpServletResponse response
	) {
		LoginResult result = authService.login(request.getUsername(), request.getPassword());

		ResponseCookie cookie = ResponseCookie.from(TOKEN_COOKIE_NAME, result.getToken())
				.httpOnly(true)      // 防XSS竊取，六-4決議：不使用localStorage/sessionStorage
				.secure(true)        // 僅限HTTPS傳輸；本機開發若無HTTPS需另外調整
				.sameSite("Strict")  // 防CSRF：僅同站請求才會帶上此Cookie
				.path("/")
				.maxAge(result.getExpiresInSeconds())
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

		// Token本身不放進JSON Body：httpOnly Cookie前端JS本來就讀不到，
		// 放進Body等於多開一個管道洩漏它，回應只需要使用者資訊供前端顯示。
		return ResponseEntity.ok(ApiResponse.success("登入成功", result.getUser()));
	}

	@GetMapping("/me")
	public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal String username) {
		UserResponse user = authService.getCurrentUser(username);
		return ResponseEntity.ok(ApiResponse.success("查詢成功", user));
	}

	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Void>> logout(
			@AuthenticationPrincipal String username,
			HttpServletResponse response
	) {
		authService.logout(username);

		ResponseCookie expiredCookie = ResponseCookie.from(TOKEN_COOKIE_NAME, "")
				.httpOnly(true)
				.secure(true)
				.sameSite("Strict")
				.path("/")
				.maxAge(0) // maxAge=0 讓瀏覽器立即刪除這個Cookie
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());

		return ResponseEntity.ok(ApiResponse.success("登出成功"));
	}
}
