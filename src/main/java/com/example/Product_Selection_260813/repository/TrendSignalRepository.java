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

    /**
     * 2026-09-29：最新一筆「非指定來源」的趨勢資料。評分用（ProductFactorScorer.scoreTrendHeat()），
     * 傳入 SIMULATED 以排除舊版 PTT 抓不到時寫入的模擬資料——隨機值不該影響評分。
     */
    Optional<TrendSignal> findFirstByProductIdAndSourceNotOrderByCollectedAtDesc(Long productId, String source);

    /** 指定來源最新一筆（Google 趨勢批次涵蓋說明用，見 GoogleTrendService.getBatchCoverage()）。 */
    Optional<TrendSignal> findFirstByProductIdAndSourceOrderByCollectedAtDesc(Long productId, String source);

    /**
     * 2026-09-29：一次取出多個商品「各自最近 N 筆」趨勢資料（品項管理清單、熱度排行榜的「連續上升」標記用，
     * 見 RecentTrendService）。
     *
     * 取代原本熱度規則選品批次逐商品呼叫的 findTop3ByProductIdOrderByCollectedAtDesc()——清單一頁 20 筆
     * 若逐筆查就是 N+1。每組取前 N 筆是 greatest-n-per-group，用 MySQL 8 的 ROW_NUMBER() 視窗函數；
     * 外層只選 trend_signals 本身的欄位，讓 Hibernate 能直接對應成 TrendSignal。
     * 結果依 product_id、collected_at 新到舊排序；同一時間戳以 id 大者為新。
     * productIds 不可為空集合（IN () 是 SQL 語法錯誤），由呼叫端先判斷。
     */
    @Query(value = """
            SELECT ts.* FROM trend_signals ts
              JOIN (SELECT id,
                           ROW_NUMBER() OVER (PARTITION BY product_id ORDER BY collected_at DESC, id DESC) AS rn
                      FROM trend_signals
                     WHERE product_id IN (:productIds)) ranked ON ranked.id = ts.id
             WHERE ranked.rn <= :perProduct
             ORDER BY ts.product_id, ts.collected_at DESC, ts.id DESC
            """, nativeQuery = true)
    List<TrendSignal> findRecentByProductIds(@Param("productIds") java.util.Collection<Long> productIds,
            @Param("perProduct") int perProduct);

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
     *
     * 2026-09-30：
     * - 只列「最新一筆熱度 &gt; 0」的商品。熱度 0＝PTT 近 90 天完全搜不到討論，原本會拿來補滿 10 個名額，
     *   排名沒有意義；有討論的商品不足 10 個時就只回傳那幾個，由前端說明其餘商品沒有討論。
     *   條件套在「最新一筆」上：最新一筆是 0 的商品不會退回去用舊的非 0 資料。
     * - 同分時依 product_id 排序，避免同分商品每次重新整理順序都不同。
     */
    @Query(value = """
            SELECT t.* FROM trend_signals t
             INNER JOIN products p ON p.id = t.product_id
             WHERE p.item_status = 'ACTIVE'
               AND t.collected_at = (
                     SELECT MAX(t2.collected_at) FROM trend_signals t2
                      WHERE t2.product_id = t.product_id
                   )
               AND t.popularity_score > 0
             ORDER BY t.popularity_score DESC, t.product_id ASC
             LIMIT :limit
            """, nativeQuery = true)
    List<TrendSignal> findLatestSignalsRankedByScore(@Param("limit") int limit);

    /**
     * Google 趨勢每週批次的「PTT 熱度補位」名單（2026-09-30，見 GoogleTrendService.findCandidates()）。
     *
     * 與上面的排行榜查詢不同，條件全部在 SQL 內完成，LIMIT 才不會被不合格的資料佔掉名額
     * （原本先取前 N 筆、再在 Java 端濾掉 SIMULATED／熱度 0，實際查詢數可能遠少於 N）：
     * - 只看每個商品「最新一筆 PTT 真實資料」（source='PTT'），與評分熱度因子的取法一致；
     * - 熱度 &gt; 0：PTT 完全沒人討論的品名，Google 多半也查無資料，不值得花額度；
     * - 使用中商品；
     * - :recheckSince 之後已查過 Google 趨勢的商品略過（Google 一次回傳 3 個月序列，一週查一次就夠）。
     */
    @Query(value = """
            SELECT t.* FROM trend_signals t
             INNER JOIN products p ON p.id = t.product_id
             WHERE p.item_status = 'ACTIVE'
               AND t.source = 'PTT'
               AND t.popularity_score > 0
               AND t.collected_at = (
                     SELECT MAX(t2.collected_at) FROM trend_signals t2
                      WHERE t2.product_id = t.product_id AND t2.source = 'PTT'
                   )
               AND NOT EXISTS (
                     SELECT 1 FROM google_trend_signals g
                      WHERE g.product_id = t.product_id AND g.collected_at >= :recheckSince
                   )
             ORDER BY t.popularity_score DESC, t.product_id ASC
             LIMIT :limit
            """, nativeQuery = true)
    List<TrendSignal> findPttRankedForGoogleTrend(@Param("recheckSince") java.time.LocalDateTime recheckSince,
            @Param("limit") int limit);

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