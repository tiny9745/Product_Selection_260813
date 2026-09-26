package com.example.Product_Selection_260813.entity;

import java.time.LocalDateTime;

import com.example.Product_Selection_260813.enums.PasswordResetRequestStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 重設密碼申請（V27，password_reset_requests）。
 *
 * 管理者只能對「有 PENDING 申請」的帳號代重設密碼；重設或駁回後結案，紀錄保留供稽核。
 * requestedAt／handledAt 由 Service 明確指定（不用 @CreationTimestamp），結案時間必須與
 * 同一筆交易內的重設動作一致。
 */
@Entity
@Table(name = "password_reset_requests")
public class PasswordResetRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private PasswordResetRequestStatus status = PasswordResetRequestStatus.PENDING;

	@Column(name = "requested_at", nullable = false, updatable = false)
	private LocalDateTime requestedAt;

	@Column(name = "handled_at")
	private LocalDateTime handledAt;

	@Column(name = "handled_by")
	private Long handledBy;

	protected PasswordResetRequest() {
	}

	public PasswordResetRequest(Long userId, LocalDateTime requestedAt) {
		this.userId = userId;
		this.requestedAt = requestedAt;
	}

	/**
	 * 結案：管理者重設完成（COMPLETED）、駁回（REJECTED），或使用者本人登入成功自動取消
	 * （CANCELLED，managerId 為 null）。只允許從 PENDING 結案。
	 */
	public void close(PasswordResetRequestStatus result, Long managerId, LocalDateTime at) {
		if (status != PasswordResetRequestStatus.PENDING || result == PasswordResetRequestStatus.PENDING) {
			throw new IllegalStateException("此重設密碼申請已結案");
		}
		this.status = result;
		this.handledBy = managerId;
		this.handledAt = at;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public PasswordResetRequestStatus getStatus() {
		return status;
	}

	public LocalDateTime getRequestedAt() {
		return requestedAt;
	}

	public LocalDateTime getHandledAt() {
		return handledAt;
	}

	public Long getHandledBy() {
		return handledBy;
	}
}
