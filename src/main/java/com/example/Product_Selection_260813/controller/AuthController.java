package com.example.Product_Selection_260813.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Product_Selection_260813.common.ApiResponse;
import com.example.Product_Selection_260813.dto.request.ChangePasswordRequest;
import com.example.Product_Selection_260813.dto.request.LoginRequest;
import com.example.Product_Selection_260813.dto.request.PasswordResetApplyRequest;
import com.example.Product_Selection_260813.dto.request.UpdateProfileRequest;
import com.example.Product_Selection_260813.dto.response.LoginResult;
import com.example.Product_Selection_260813.dto.response.UserResponse;
import com.example.Product_Selection_260813.service.AuthService;
import com.example.Product_Selection_260813.service.PasswordResetRequestService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * 對應 API總表 一、登入／權限。
 *
 * Controller只負責HTTP層的事：解析Request、設定/清除Cookie、決定HTTP狀態碼。
 * 所有認證邏輯都在AuthService，Controller不直接碰密碼比對或JWT內容解析， 這樣AuthService可以完全不依賴Servlet
 * API，單元測試不需要啟動Web環境。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private static final String TOKEN_COOKIE_NAME = "access_token";

	@Autowired
	private AuthService authService;

	@Autowired
	private PasswordResetRequestService passwordResetRequestService;

	/**
	 * POST /api/auth/password-reset-requests：申請重設密碼（V27，不需登入）。
	 *
	 * 忘記密碼的人無法登入，所以這支是公開端點（SecurityConfig permitAll）。為了不被用來試出
	 * 系統有哪些帳號，不論帳號是否存在、是否停用、是否已申請過，一律回 202 與同一段訊息。
	 * 管理者處理後會透過其他管道（口頭、通訊軟體）把臨時密碼交給使用者。
	 */
	@PostMapping("/password-reset-requests")
	public ResponseEntity<ApiResponse<Void>> applyPasswordReset(@Valid @RequestBody PasswordResetApplyRequest request) {
		passwordResetRequestService.apply(request.getUsername());
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(ApiResponse.success(PASSWORD_RESET_APPLIED_MESSAGE));
	}

	/** 申請重設密碼的固定回應訊息（不透露帳號是否存在）；前端直接顯示這段文字。 */
	static final String PASSWORD_RESET_APPLIED_MESSAGE = "已送出申請。若帳號存在，管理者處理後會提供臨時密碼給您。";

	/**
	 * Cookie的Secure屬性，由設定檔決定而非寫死。
	 *
	 * Secure=true時瀏覽器只在HTTPS連線帶上此Cookie，正式環境必須為true；
	 * 但本機開發若跑在http://localhost，寫死true會導致瀏覽器/Postman完全收不到
	 * 也送不回這個Cookie，登入後呼叫其他API一律被擋401，開發階段等於測不到
	 * 「瀏覽器自動帶Cookie」這條真實路徑（只能手動複製Cookie到Header繞過，
	 * 與前端實際行為不同）。
	 *
	 * 預設值刻意設為true（安全預設）：設定檔漏掉這個key時走安全的那一邊，
	 * 而不是不小心以不安全的設定跑在正式環境。開發環境需在application.properties
	 * 明確寫cookie.secure=false，或以環境變數COOKIE_SECURE覆蓋。
	 */
	@Value("${cookie.secure}")
	private boolean cookieSecure;

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<UserResponse>> login(@Valid @RequestBody LoginRequest request,
			HttpServletResponse response) {
		LoginResult result = authService.login(request.getUsername(), request.getPassword());

		ResponseCookie cookie = ResponseCookie.from(TOKEN_COOKIE_NAME, result.getToken()).httpOnly(true) // 防XSS竊取，六-4決議：不使用localStorage/sessionStorage
				.secure(cookieSecure) // 僅限HTTPS傳輸，由cookie.secure設定控制（見欄位說明）
				.sameSite("Strict") // 防CSRF：僅同站請求才會帶上此Cookie
				.path("/").maxAge(result.getExpiresInSeconds()).build();
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
	public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal String username,
			HttpServletResponse response) {
		authService.logout(username);

		// 清除用的Cookie屬性必須與登入時設定的完全一致（含secure／sameSite／path），
		// 否則瀏覽器會視為不同的Cookie而不會覆蓋掉原本那個，登出將失效。
		ResponseCookie expiredCookie = ResponseCookie.from(TOKEN_COOKIE_NAME, "").httpOnly(true).secure(cookieSecure)
				.sameSite("Strict").path("/").maxAge(0) // maxAge=0 讓瀏覽器立即刪除這個Cookie
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());

		return ResponseEntity.ok(ApiResponse.success("登出成功"));
	}
	
	@PatchMapping("/me")
	public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
			@AuthenticationPrincipal String username,
			@Valid @RequestBody UpdateProfileRequest request) {
		UserResponse result = authService.updateProfile(username, request.getName());
		return ResponseEntity.ok(ApiResponse.success("個人資料修改成功", result));
	}

	@PatchMapping("/me/password")
	public ResponseEntity<ApiResponse<UserResponse>> changePassword(
			@AuthenticationPrincipal String username,
			@Valid @RequestBody ChangePasswordRequest request,
			HttpServletResponse response) {
		LoginResult result = authService.changePassword(username, request.getCurrentPassword(), request.getNewPassword());

		// 密碼修改成功後重發Cookie，屬性須與login()完全一致，理由同logout()註解：
		// 任何一個屬性（secure／sameSite／path）不一致，瀏覽器會視為不同Cookie。
		ResponseCookie cookie = ResponseCookie.from(TOKEN_COOKIE_NAME, result.getToken()).httpOnly(true)
				.secure(cookieSecure).sameSite("Strict").path("/").maxAge(result.getExpiresInSeconds()).build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

		return ResponseEntity.ok(ApiResponse.success("密碼修改成功", result.getUser()));
	}
}
