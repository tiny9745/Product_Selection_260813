package com.example.Product_Selection_260813.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.AiAnalysis;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    // 「目前有效版本」查詢規則：不新增is_current欄位，
    // 統一規則為該product_id底下generated_at最新一筆。
    // 次要排序鍵IdDesc：generated_at是DATETIME(僅精確到秒)，短時間內連續產生
    // 多筆分析時容易撞秒(例如demo/測試情境下seed資料與手動觸發生成剛好同一秒)，
    // 沒有次要排序鍵時MySQL對同值排序結果不保證順序，加上id DESC（auto increment，
    // 值越大代表寫入越晚）確保「最新一筆」的判定是決定性的，不受時間戳精度影響。
    Optional<AiAnalysis> findFirstByProductIdOrderByGeneratedAtDescIdDesc(Long productId);
}
