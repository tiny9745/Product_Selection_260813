package com.example.Product_Selection_260813.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.RiskOption;

public interface RiskOptionRepository extends JpaRepository<RiskOption, Long> {

    // 審核頁「人工風險評估（複選）」選項清單，僅顯示目前可選擇的風險類型
    List<RiskOption> findByIsActiveTrue();
}
