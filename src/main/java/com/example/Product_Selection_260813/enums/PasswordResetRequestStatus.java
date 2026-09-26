package com.example.Product_Selection_260813.enums;

/** 重設密碼申請狀態（V27）。 */
public enum PasswordResetRequestStatus {
	/** 使用者已申請，等待管理者處理。 */
	PENDING,
	/** 管理者已重設密碼。 */
	COMPLETED,
	/** 管理者駁回（例如無法確認是本人申請）。 */
	REJECTED,
	/** 使用者在管理者處理前，就用原本的密碼成功登入：已不需要重設，自動取消（V28）。 */
	CANCELLED
}
