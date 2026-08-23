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
import com.example.Product_Selection_260813.dto.response.UserAccountResponse;
import com.example.Product_Selection_260813.service.UserService;

import jakarta.validation.Valid;

/**
 * 對應 四、API總表「1-2. 帳號管理」，三支端點皆為[僅管理]。
 *
 * 與AuthController分開的原因見七-5與UserService類別註解：AuthController
 * 處理「自身身分」（登入／me／登出，[操作+管理]），本Controller處理
 * 「管理別人的帳號」（[僅管理]），權限範圍與職責性質都不同。
 *
 * 不提供DELETE端點：帳號只停用不刪除（七-5決議），實體刪除會使
 * review_records等歷史稽核紀錄失去對應人員資料。
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
}
