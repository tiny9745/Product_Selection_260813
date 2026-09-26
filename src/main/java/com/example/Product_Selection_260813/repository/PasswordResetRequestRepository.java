package com.example.Product_Selection_260813.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.PasswordResetRequest;
import com.example.Product_Selection_260813.enums.PasswordResetRequestStatus;

public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, Long> {

	/**
	 * 某帳號的待處理申請。正常情況最多一筆（apply() 會先檢查）；極端併發下兩個請求同時通過
	 * 檢查可能產生兩筆，所以回傳 List，結案時一併處理，不會留下孤兒 PENDING。
	 */
	List<PasswordResetRequest> findByUserIdAndStatus(Long userId, PasswordResetRequestStatus status);

	/** 帳號管理清單：一次取出所有待處理申請，避免逐帳號查詢（N+1）。 */
	List<PasswordResetRequest> findByStatus(PasswordResetRequestStatus status);
}
