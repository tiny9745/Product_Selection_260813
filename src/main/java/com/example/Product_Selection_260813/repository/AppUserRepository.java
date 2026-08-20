package com.example.Product_Selection_260813.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    // 登入流程（POST /api/auth/login）需依username查詢帳號驗證密碼
    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);
}
