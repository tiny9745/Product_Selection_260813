package com.example.Product_Selection_260813.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.Product_Selection_260813.entity.TrendSignal;

public interface TrendSignalRepository extends JpaRepository<TrendSignal, Long> {

    // 品項詳情頁趨勢明細顯示
    List<TrendSignal> findByProductIdOrderByCollectedAtDesc(Long productId);

    // 取得該商品最新一筆趨勢資料（顯示「最後同步時間」用）
    Optional<TrendSignal> findFirstByProductIdOrderByCollectedAtDesc(Long productId);

    // AI主動選品批次規則（規格書七）：取得所有曾經有趨勢資料的商品ID，
    // 作為批次要逐一檢查的範圍。
    @Query("SELECT DISTINCT t.productId FROM TrendSignal t")
    List<Long> findDistinctProductIds();

    // AI主動選品批次規則：取得單一商品最近3筆趨勢資料（依日期新到舊），
    // 用於「連續3天呈上升趨勢」判斷。若該商品記錄不足3筆，回傳的List會小於3筆，
    // 由呼叫端（AiSuggestionBatchService）自行判斷筆數不足時此條件不成立。
    List<TrendSignal> findTop3ByProductIdOrderByCollectedAtDesc(Long productId);
}
