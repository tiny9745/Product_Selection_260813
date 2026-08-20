package com.example.Product_Selection_260813.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.AiAnalysis;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    // 「目前有效版本」查詢規則：不新增is_current欄位，
    // 統一規則為該product_id底下generated_at最新一筆
    Optional<AiAnalysis> findFirstByProductIdOrderByGeneratedAtDesc(Long productId);
}
