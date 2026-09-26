package com.example.Product_Selection_260813.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Product_Selection_260813.common.ApiResponse;
import com.example.Product_Selection_260813.dto.request.UserCreateRequest;
import com.example.Product_Selection_260813.dto.request.UserPasswordResetRequest;
import com.example.Product_Selection_260813.dto.response.UserAccountResponse;
import com.example.Product_Selection_260813.service.UserService;

import jakarta.validation.Valid;

/**
 * 對應 四、API總表「1-2. 帳號管理」，四支端點皆為[僅管理]。
 *
 * 與AuthController分開的原因見七-5與UserService類別註解：AuthController
 * 處理「自身身分」（登入／me／登出，[操作+管理]），本Controller處理
 * 「管理別人的帳號」（[僅管理]），權限範圍與職責性質都不同。
 *
 * 不提供DELETE端點：帳號只停用不刪除（七-5決議），實體刪除會使
 * review_records等歷史稽核紀錄失去對應人員資料；被停用的帳號改用
 * PUT /api/users/{id}/enable復用。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

	@Autowired
	private UserService userService;

	/**
	 * GET /api/users：列出所有帳號（含角色、啟用狀態），不回傳密碼雜湊值。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@GetMapping
	public ResponseEntity<ApiResponse<List<UserAccountResponse>>> getUsers() {
		List<UserAccountResponse> result = userService.getAllUsers();
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	/**
	 * POST /api/users：新增帳號，密碼以BCrypt雜湊後存入，不以明文保存或回傳。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@PostMapping
	public ResponseEntity<ApiResponse<UserAccountResponse>> createUser(
			@Valid @RequestBody UserCreateRequest request) {
		UserAccountResponse result = userService.createUser(request);
		return ResponseEntity.ok(ApiResponse.success("帳號建立成功", result));
	}

	/**
	 * PUT /api/users/{id}/disable：停用帳號，停用後該帳號無法登入。
	 *
	 * 需帶入目前登入者username，供Service層擋下「停用自己」的誤操作
	 * （理由見UserService.disableUser()）。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/{id}/disable")
	public ResponseEntity<ApiResponse<UserAccountResponse>> disableUser(@PathVariable("id") Long id,
			@AuthenticationPrincipal String username) {
		UserAccountResponse result = userService.disableUser(id, username);
		return ResponseEntity.ok(ApiResponse.success("帳號已停用", result));
	}

	/**
	 * PUT /api/users/{id}/enable：復用（重新啟用）帳號，啟用後該帳號恢復可登入。
	 *
	 * 不需要像disableUser()一樣帶入目前登入者username：啟用不存在「操作自己」
	 * 需要擋下的風險（理由見UserService.enableUser()）。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/{id}/enable")
	public ResponseEntity<ApiResponse<UserAccountResponse>> enableUser(@PathVariable("id") Long id) {
		UserAccountResponse result = userService.enableUser(id);
		return ResponseEntity.ok(ApiResponse.success("帳號已復用", result));
	}

	/**
	 * PUT /api/users/{id}/reset-password：管理者代重設密碼（V24）。
	 *
	 * 重設後該帳號現有登入立即失效，使用者以新密碼登入後會被強制先修改密碼。
	 * V27：只能重設「本人已在登入頁申請」的帳號，沒有待處理申請回 409。
	 * 需帶入目前登入者 username，供 Service 層擋下「重設自己」（理由見 UserService.resetPassword()）。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/{id}/reset-password")
	public ResponseEntity<ApiResponse<UserAccountResponse>> resetPassword(@PathVariable("id") Long id,
			@Valid @RequestBody UserPasswordResetRequest request, @AuthenticationPrincipal String username) {
		UserAccountResponse result = userService.resetPassword(id, request.getNewPassword(), username);
		return ResponseEntity.ok(ApiResponse.success("密碼已重設，該使用者下次登入時需先修改密碼", result));
	}

	/**
	 * PUT /api/users/{id}/password-reset-request/reject：駁回重設密碼申請（V27）。
	 * 重設密碼（上方）現在必須先有本人申請；無法確認是本人時用這支駁回。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/{id}/password-reset-request/reject")
	public ResponseEntity<ApiResponse<UserAccountResponse>> rejectPasswordResetRequest(@PathVariable("id") Long id,
			@AuthenticationPrincipal String username) {
		UserAccountResponse result = userService.rejectPasswordResetRequest(id, username);
		return ResponseEntity.ok(ApiResponse.success("已駁回重設密碼申請", result));
	}
}
