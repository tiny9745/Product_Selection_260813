package com.example.Product_Selection_260813.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.TrendSignal;

public interface TrendSignalRepository extends JpaRepository<TrendSignal, Long> {

    // 品項詳情頁趨勢明細顯示
    List<TrendSignal> findByProductIdOrderByCollectedAtDesc(Long productId);

    // 取得該商品最新一筆趨勢資料（顯示「最後同步時間」用）
    Optional<TrendSignal> findFirstByProductIdOrderByCollectedAtDesc(Long productId);
}
