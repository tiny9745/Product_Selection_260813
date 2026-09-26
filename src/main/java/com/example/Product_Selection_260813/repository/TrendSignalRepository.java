package com.example.Product_Selection_260813.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * ⚠️ 2026-09-25 新增：儀表板「熱度排行榜」用。
     *
     * 跟 AI 推薦 Top 10（依「綜合加權總分」排序，七大因子混在一起）不同，
     * 這支刻意「只看趨勢單一因子」——一個商品可能因為毛利率差被拉低總分
     * 而沒進 AI 推薦榜，但熱度其實在飆升，這支查詢能讓這種商品被看見，
     * 不是重複做一次 Top 10。
     *
     * 「每個商品最新一筆」是典型的 greatest-n-per-group 問題，JPQL 沒有
     * 好的寫法，這裡用原生 SQL 的相關子查詢（correlated subquery）取得
     * 每個 product_id 的最新 collected_at，再照 popularity_score 排序。
     * 只納入 item_status='ACTIVE' 的商品（跟既有的 findTopRecommendations
     * 篩選精神一致，封存商品不該出現在任何排行榜上）。
     */
    @Query(value = """
            SELECT t.* FROM trend_signals t
             INNER JOIN products p ON p.id = t.product_id
             WHERE p.item_status = 'ACTIVE'
               AND t.collected_at = (
                     SELECT MAX(t2.collected_at) FROM trend_signals t2
                      WHERE t2.product_id = t.product_id
                   )
             ORDER BY t.popularity_score DESC
             LIMIT :limit
            """, nativeQuery = true)
    List<TrendSignal> findLatestSignalsRankedByScore(@Param("limit") int limit);

    /**
     * ⚠️ 2026-09-25 新增：品項詳情頁「熱度趨勢圖」用。
     *
     * findByProductIdOrderByCollectedAtDesc() 已經回傳完整歷史（不限筆數），
     * 這支刻意加上時間範圍限制——trend_signals 每天 02:00 排程 + 手動
     * 同步都會新增一筆，長期下來一個商品可能累積上百筆，畫成折線圖不需要
     * 全部歷史，只需要「最近 N 天」。用 collected_at >= :since 這種相對
     * 時間範圍查詢，不用另外做分頁。
     */
    List<TrendSignal> findByProductIdAndCollectedAtAfterOrderByCollectedAtAsc(
            Long productId, java.time.LocalDateTime since);

    /**
     * 刪除商品前的預防性清理用。
     *
     * ⚠️ 已依 schema sql260902.sql 核對確認：fk_trend_signals_product
     * FOREIGN KEY (product_id) REFERENCES products (id)，沒有 ON DELETE
     * CASCADE。直接刪 products 會撞到這個外鍵，見 ProductService.
     * deleteProduct() 的完整刪除順序說明。
     */
    void deleteByProductId(Long productId);
}