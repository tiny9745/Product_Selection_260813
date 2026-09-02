package com.example.Product_Selection_260813.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.ProductEvaluation;

public interface ProductEvaluationRepository extends JpaRepository<ProductEvaluation, Long> {

    // 假設每個商品僅維護一筆即時評估結果，重算時UPDATE而非新增一筆
    // ⚠️若實際允許同一商品保留多筆歷史評估紀錄，需改為
    // findFirstByProductIdOrderByCalculatedAtDesc，請與團隊確認實際寫入策略
    Optional<ProductEvaluation> findByProductId(Long productId);

    /**
     * 批次查詢：GET /api/products 清單併帶 finalScore／dataCompleteness 用。
     * 一頁最多 size 筆商品只查這一次，不要在迴圈裡逐筆呼叫 findByProductId，
     * 否則會變成 N+1（見 ProductService.resolveEvaluations() 的說明）。
     */
    List<ProductEvaluation> findByProductIdIn(Collection<Long> productIds);
}