package com.example.Product_Selection_260813.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.ProductEvaluation;

public interface ProductEvaluationRepository extends JpaRepository<ProductEvaluation, Long> {

    // 假設每個商品僅維護一筆即時評估結果，重算時UPDATE而非新增一筆
    // ⚠️若實際允許同一商品保留多筆歷史評估紀錄，需改為
    // findFirstByProductIdOrderByCalculatedAtDesc，請與團隊確認實際寫入策略
    Optional<ProductEvaluation> findByProductId(Long productId);
}
