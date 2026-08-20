package com.example.Product_Selection_260813.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.AudienceProfile;

public interface AudienceProfileRepository extends JpaRepository<AudienceProfile, Long> {

    // version/is_active本階段僅預留欄位，不實作版本切換邏輯，先提供基本查詢
    List<AudienceProfile> findByIsActiveTrue();
}
