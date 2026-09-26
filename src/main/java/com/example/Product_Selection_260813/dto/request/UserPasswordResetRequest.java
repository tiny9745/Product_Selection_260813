package com.example.Product_Selection_260813.dto.request;

import com.example.Product_Selection_260813.constants.ValidationMessage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/users/{id}/reset-password 的 Request Body：管理者代重設密碼。
 *
 * 臨時密碼由管理者輸入（不由系統亂數產生）：管理者需要把密碼轉交給使用者，
 * 系統產生的亂數密碼只會多一個「怎麼把它安全地顯示給管理者」的問題。
 * 使用者下次登入時會被強制改掉（must_change_password），管理者不會長期知道
 * 使用者正在使用的密碼。
 *
 * 密碼強度規則與 UserCreateRequest／ChangePasswordRequest 一致（僅長度下限 8 碼），
 * 三處刻意保持相同，避免「建立帳號」「代重設」「自行修改」對密碼強度的認定不同步。
 */
public class UserPasswordResetRequest {

	@NotBlank(message = ValidationMessage.USER_PASSWORD_BLANK)
	@Size(min = 8, message = ValidationMessage.USER_PASSWORD_TOO_SHORT)
	private String newPassword;

	public String getNewPassword() {
		return newPassword;
	}

	public void setNewPassword(String newPassword) {
		this.newPassword = newPassword;
	}
}
