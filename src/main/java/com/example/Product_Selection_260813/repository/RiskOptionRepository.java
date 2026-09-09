package com.example.Product_Selection_260813.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.RiskOption;

public interface RiskOptionRepository extends JpaRepository<RiskOption, Long> {

    // 審核頁「人工風險評估（複選）」選項清單，僅顯示目前可選擇的風險類型
    List<RiskOption> findByIsActiveTrue();

    // Gate 判定不通過時，依 auto_trigger_code 找出要預先勾選的風險選項。
    // 回傳 List 而非 Optional：同一個 Gate 對應多個風險選項是合理的設定。
    List<RiskOption> findByAutoTriggerCode(String autoTriggerCode);
}
