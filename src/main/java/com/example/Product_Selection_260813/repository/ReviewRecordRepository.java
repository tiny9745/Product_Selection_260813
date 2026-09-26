package com.example.Product_Selection_260813.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.Product_Selection_260813.entity.ReviewRecord;

public interface ReviewRecordRepository extends JpaRepository<ReviewRecord, Long> {

    // 單一商品審核歷史（GET /api/products/{id}/reviews）
    List<ReviewRecord> findByProductIdOrderByReviewedAtDesc(Long productId);

    // 多件商品的審核紀錄一次查回（CSV 匯出取每件商品最新一筆快照，避免逐筆查詢 N+1）
    List<ReviewRecord> findByProductIdIn(java.util.Collection<Long> productIds);

    /**
     * 決策紀錄列表（GET /api/reviews/decision-records，跨商品彙總查詢）。
     *
     * 2026-09-24改寫：原本是 findAllByOrderByReviewedAtDesc／
     * findByReviewStatusOrderByReviewedAtDesc 兩支衍生查詢，排序寫死在方法名稱裡，
     * 前端傳來的 sort 只會被接在 reviewed_at 之後、實際上無效；搜尋、日期篩選也
     * 只能在前端對「當頁 20 筆」做。現在篩選、關鍵字、排序全部交給資料庫，
     * 跨分頁結果才會一致。
     *
     * 為什麼用 native query：
     * - 商品名稱刻意取 product_snapshot.name（審核當下的名稱，見
     *   ReviewRecordResponse 類別註解的 Snapshot 精神），這是 JSON 欄位，
     *   需要 JSON_EXTRACT，JPQL 無法直接表達。
     * - ORDER BY 由 :sortKey／:sortDir 以 CASE 組出（白名單在
     *   ReviewService.resolveDecisionRecordSort() 驗證），傳入的 Pageable 必須是
     *   unsorted，避免 Spring Data 再自行附加 ORDER BY。
     *
     * 參數語意：
     * - reviewStatus：'APPROVED'／'REJECTED'，null＝不篩選。
     * - reviewedFrom（含）／reviewedToExclusive（不含）：半開區間，
     *   由 Service 把「迄日」轉成「迄日隔天 00:00」，選同一天也能涵蓋整天。
     * - keyword：已轉小寫並跳脫 LIKE 萬用字元（\ % _），null＝不篩選。
     * - 最終分數為 null 的紀錄不論升降冪一律排在最後，與前端既有排序語意一致。
     * - 最後一律以 reviewed_at、id 當次要排序鍵，確保排序結果穩定，
     *   翻頁時不會因為同值而重複或遺漏。
     */
    @Query(value = """
            SELECT r.* FROM review_records r
            WHERE (:reviewStatus IS NULL OR r.review_status = :reviewStatus)
              AND (:reviewedFrom IS NULL OR r.reviewed_at >= :reviewedFrom)
              AND (:reviewedToExclusive IS NULL OR r.reviewed_at < :reviewedToExclusive)
              AND (:keyword IS NULL OR LOWER(JSON_UNQUOTE(JSON_EXTRACT(r.product_snapshot, '$.name')))
                   LIKE CONCAT('%', :keyword, '%'))
            ORDER BY
              CASE WHEN :sortKey = 'submissionCount' AND :sortDir = 'ASC' THEN r.submission_count END ASC,
              CASE WHEN :sortKey = 'submissionCount' AND :sortDir = 'DESC' THEN r.submission_count END DESC,
              CASE WHEN :sortKey = 'finalScore' THEN (r.final_score_snapshot IS NULL) ELSE 0 END ASC,
              CASE WHEN :sortKey = 'finalScore' AND :sortDir = 'ASC' THEN r.final_score_snapshot END ASC,
              CASE WHEN :sortKey = 'finalScore' AND :sortDir = 'DESC' THEN r.final_score_snapshot END DESC,
              CASE WHEN :sortKey = 'reviewedAt' AND :sortDir = 'ASC' THEN r.reviewed_at END ASC,
              CASE WHEN :sortKey = 'reviewedAt' AND :sortDir = 'ASC' THEN r.id END ASC,
              r.reviewed_at DESC,
              r.id DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM review_records r
            WHERE (:reviewStatus IS NULL OR r.review_status = :reviewStatus)
              AND (:reviewedFrom IS NULL OR r.reviewed_at >= :reviewedFrom)
              AND (:reviewedToExclusive IS NULL OR r.reviewed_at < :reviewedToExclusive)
              AND (:keyword IS NULL OR LOWER(JSON_UNQUOTE(JSON_EXTRACT(r.product_snapshot, '$.name')))
                   LIKE CONCAT('%', :keyword, '%'))
            """,
            nativeQuery = true)
    Page<ReviewRecord> searchDecisionRecords(
            @Param("reviewStatus") String reviewStatus,
            @Param("reviewedFrom") LocalDateTime reviewedFrom,
            @Param("reviewedToExclusive") LocalDateTime reviewedToExclusive,
            @Param("keyword") String keyword,
            @Param("sortKey") String sortKey,
            @Param("sortDir") String sortDir,
            Pageable pageable);

    // APPROVED商品Final Score凍結快照讀取：核准後不會再重新送審，
    // 故該商品最新一筆審核紀錄即為凍結快照來源
    Optional<ReviewRecord> findFirstByProductIdOrderByReviewedAtDesc(Long productId);
}
