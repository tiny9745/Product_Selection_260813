package com.example.Product_Selection_260813.dto.request;

import com.example.Product_Selection_260813.constants.ValidationMessage;
import com.example.Product_Selection_260813.enums.UserRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/users 的 Request Body：由管理層代辦建立帳號
 * （本系統不開放公開自我註冊，見七-5帳號建立與角色劃分決議）。
 *
 * <b>enabled不開放指定</b>：新帳號一律為啟用狀態（Entity預設值），
 * 「建立一個一開始就停用的帳號」沒有實際使用情境，多開一個欄位只會
 * 增加誤填風險。要停用改用PUT /api/users/{id}/disable。
 *
 * <b>密碼長度下限8碼</b>：這是本DTO唯一的密碼強度限制，刻意不加
 * 「須含大小寫、數字、符號」等組合規則——這類規則已被NIST SP 800-63B
 * 建議移除（會誘導使用者採用"Password1!"這種可預測的變形），長度才是
 * 有效的強度來源。上限不設限制：BCrypt本身有72 bytes截斷特性，
 * 超長密碼不會造成錯誤，且限制上限反而妨礙使用者用長密語(passphrase)。
 */
public class UserCreateRequest {

	@NotBlank(message = ValidationMessage.USER_USERNAME_BLANK)
	@Size(max = 50, message = ValidationMessage.USER_USERNAME_TOO_LONG)
	private String username;

	@NotBlank(message = ValidationMessage.USER_NAME_BLANK)
	@Size(max = 50, message = ValidationMessage.USER_NAME_TOO_LONG)
	private String name;

	@NotNull(message = ValidationMessage.USER_ROLE_NULL)
	private UserRole role;

	@NotBlank(message = ValidationMessage.USER_PASSWORD_BLANK)
	@Size(min = 8, message = ValidationMessage.USER_PASSWORD_TOO_SHORT)
	private String password;

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

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
}
