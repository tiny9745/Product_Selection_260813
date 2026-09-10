package com.example.Product_Selection_260813.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    // 登入流程（POST /api/auth/login）需依username查詢帳號驗證密碼
    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    /**
     * 只查 activeSessionVersion 這一個整數欄位，供 JwtAuthenticationFilter
     * 每個受保護請求都要呼叫的版本比對使用。用投影查詢而非
     * findByUsername().map(AppUser::getActiveSessionVersion())，
     * 是為了不讓 Hibernate 把整個 AppUser 實體（含密碼雜湊等）都撈出來——
     * 這支查詢的呼叫頻率等於整個系統的請求量，欄位選得越小越好。
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT u.activeSessionVersion FROM AppUser u WHERE u.username = :username")
    Optional<Integer> findActiveSessionVersionByUsername(@org.springframework.data.repository.query.Param("username") String username);
}
