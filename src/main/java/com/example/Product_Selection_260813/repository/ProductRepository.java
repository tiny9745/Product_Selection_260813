package com.example.Product_Selection_260813.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.enums.ProductCandidateStatus;
import com.example.Product_Selection_260813.enums.ProductItemStatus;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 品項管理主清單搜尋／篩選（GET /api/products）。
     * 審核狀態／品項狀態／候選狀態三個欄位語意各自獨立，不可合併判斷；
     * 是否預設candidateStatus=CANDIDATE由Service層依畫面需求決定，
     * 本方法只負責「帶入什麼就篩什麼、不帶就不篩」。
     */
    @Query("""
            SELECT p FROM Product p
            WHERE (:reviewStatus IS NULL OR p.reviewStatus = :reviewStatus)
              AND (:itemStatus IS NULL OR p.itemStatus = :itemStatus)
              AND (:candidateStatus IS NULL OR p.candidateStatus = :candidateStatus)
              AND (:productTypeId IS NULL OR p.productTypeId = :productTypeId)
              AND (:keyword IS NULL OR p.name LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<Product> search(
            @Param("reviewStatus") ProductReviewStatus reviewStatus,
            @Param("itemStatus") ProductItemStatus itemStatus,
            @Param("candidateStatus") ProductCandidateStatus candidateStatus,
            @Param("productTypeId") Long productTypeId,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * AI建議清單（GET /api/products/ai-suggested）：
     * candidate_status=AI_SUGGESTED的商品，操作人員可「加入候選」轉為CANDIDATE。
     */
    Page<Product> findByCandidateStatus(ProductCandidateStatus candidateStatus, Pageable pageable);

    /**
     * 選品審核待審清單（GET /api/reviews/pending）：預設「未審核＋使用中」。
     */
    Page<Product> findByReviewStatusAndItemStatus(
            ProductReviewStatus reviewStatus, ProductItemStatus itemStatus, Pageable pageable);

    /**
     * 選品轉換率分母：submission_count>0（曾送審過）的不重複商品數。
     */
    long countBySubmissionCountGreaterThan(int submissionCount);

    /**
     * 選品轉換率分子：目前review_status=APPROVED的不重複商品數。
     */
    long countByReviewStatus(ProductReviewStatus reviewStatus);

    /**
     * 商品類型「條件式刪除」檢查（見團隊決議：已封存(ARCHIVED)商品若仍有引用，
     * 該類型一樣不可刪除，故此方法刻意不加item_status篩選，
     * 任何狀態的商品只要引用該product_type_id都視為「使用中」）：
     * DELETE /api/settings/product-types/{id}須拒絕（回409而非硬刪）。
     */
    boolean existsByProductTypeId(Long productTypeId);

    /**
     * 審核併發控制：條件式UPDATE，僅在目前review_status仍等於expectedStatus時才更新成功。
     * 回傳值為實際影響筆數——Service層依此判斷0（狀態已被他人改變，回409）或1（成功）。
     *
     * clearAutomatically = true：批次UPDATE語句繞過Persistence Context直接送SQL，
     * Hibernate不會知道記憶體裡舊的Product物件已經過期；加這個參數會在執行完後
     * 自動清空Persistence Context，強迫後續讀取重新從DB撈最新值，
     * 避免同一個@Transactional方法裡「呼叫完update卻讀到update前的舊物件」這種陷阱。
     *
     * 這支方法要生效，呼叫端的Service method必須加@Transactional，
     * 否則@Modifying查詢不會被真正送出執行。
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Product p
               SET p.reviewStatus = :newStatus
             WHERE p.id = :id
               AND p.reviewStatus = :expectedStatus
            """)
    int conditionalUpdateReviewStatus(
            @Param("id") Long id,
            @Param("expectedStatus") ProductReviewStatus expectedStatus,
            @Param("newStatus") ProductReviewStatus newStatus);
}
