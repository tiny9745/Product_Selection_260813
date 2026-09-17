package com.example.Product_Selection_260813.repository;

import java.util.List;

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
     *
     * updatedFrom／updatedTo：依「商品修改時間」篩選（Product.updatedAt），
     * 兩者皆為閉區間、皆選填、可只帶一邊。選用 updatedAt 而非
     * createdAt——使用者要找的通常是「最近異動過的商品」，新增當下
     * updatedAt 與 createdAt 相同，之後每次編輯都會更新 updatedAt，
     * 這樣篩選出來的清單才會反映「最近有變化」而不是「最早建立」。
     */
    @Query("""
            SELECT p FROM Product p
            WHERE (:reviewStatus IS NULL OR p.reviewStatus = :reviewStatus)
              AND (:itemStatus IS NULL OR p.itemStatus = :itemStatus)
              AND (:candidateStatus IS NULL OR p.candidateStatus = :candidateStatus)
              AND (:productTypeId IS NULL OR p.productTypeId = :productTypeId)
              AND (:keyword IS NULL OR p.name LIKE CONCAT('%', :keyword, '%'))
              AND (:updatedFrom IS NULL OR p.updatedAt >= :updatedFrom)
              AND (:updatedTo IS NULL OR p.updatedAt <= :updatedTo)
            """)
    Page<Product> search(
            @Param("reviewStatus") ProductReviewStatus reviewStatus,
            @Param("itemStatus") ProductItemStatus itemStatus,
            @Param("candidateStatus") ProductCandidateStatus candidateStatus,
            @Param("productTypeId") Long productTypeId,
            @Param("keyword") String keyword,
            @Param("updatedFrom") java.time.LocalDateTime updatedFrom,
            @Param("updatedTo") java.time.LocalDateTime updatedTo,
            Pageable pageable);

    /**
     * AI建議清單（GET /api/products/ai-suggested）：
     * candidate_status=AI_SUGGESTED的商品，操作人員可「加入候選」轉為CANDIDATE。
     */
    Page<Product> findByCandidateStatus(ProductCandidateStatus candidateStatus, Pageable pageable);

    /**
     * 選品審核待審清單（GET /api/reviews/pending）：預設「未審核＋使用中」。
     *
     * ⚠️ 2026-09-16修正（原設計漏洞）：這支原本沒有candidateStatus條件，
     * 只要review_status=PENDING、item_status=ACTIVE就會出現在待審清單，
     * 即使該商品candidate_status還是AI_SUGGESTED（尚未經人工「加入候選」
     * 轉正）。實際後果：管理者可以直接審核一筆從未被轉正候選的AI建議商品，
     * 跳過「AI建議→人工轉正候選→人工審核」中間那一步，這與AiSuggestions
     * 頁面文件明確寫的規則矛盾（「只有人工轉為CANDIDATE後才可進入...審核；
     * AI不會自行核准商品」）。改用下面這支多一個candidateStatus條件的版本，
     * 舊的findByReviewStatusAndItemStatus()保留給其他仍需要「不分候選狀態」
     * 語意的呼叫端（目前沒有其他呼叫端，保留是避免不必要的連鎖修改）。
     */
    Page<Product> findByReviewStatusAndItemStatus(
            ProductReviewStatus reviewStatus, ProductItemStatus itemStatus, Pageable pageable);

    /**
     * 選品審核待審清單（GET /api/reviews/pending）實際採用的版本：
     * 多加candidateStatus=CANDIDATE，堵住上面說明的漏洞。
     */
    Page<Product> findByReviewStatusAndItemStatusAndCandidateStatus(
            ProductReviewStatus reviewStatus, ProductItemStatus itemStatus,
            ProductCandidateStatus candidateStatus, Pageable pageable);

    /**
     * 選品轉換率分母：submission_count>0（曾送審過）的不重複商品數。
     */
    long countBySubmissionCountGreaterThan(int submissionCount);

    /**
     * 選品轉換率分子：目前review_status=APPROVED的不重複商品數。
     */
    long countByReviewStatus(ProductReviewStatus reviewStatus);

    /**
     * Dashboard「待人工審核」／「AI建議待確認」統計（GET /api/dashboard/statistics）
     * 共用的衍生查詢：candidate_status=X 且 review_status=Y 的商品數。
     *
     * 2026-09-16修正：DashboardService.getStatistics() 現在用這支方法算
     * pendingCount（candidateStatus=CANDIDATE）跟aiSuggestedPendingCount
     * （candidateStatus=AI_SUGGESTED）——兩者互斥，不再是子集關係，
     * AI建議尚未轉正候選的商品不會被算進「待人工審核」。
     */
    long countByCandidateStatusAndReviewStatus(
            ProductCandidateStatus candidateStatus, ProductReviewStatus reviewStatus);

    /**
     * 依品類分組計數，供設定頁「使用品項」欄位使用。
     *
     * 原本這個統計恆為 null（前端註解明確寫著「後端沒有這個統計」），
     * 導致畫面上這一欄永遠顯示「—」，看起來像是資料缺漏，這次補上。
     *
     * 用 GROUP BY 一次查全部，不要在 Service 層對每個品類各自呼叫一次
     * count 查詢——品類數量不多（目前 39 筆），但沒有理由把 N+1 的
     * 查詢模式當成預設寫法，一次撈完再用 Map 對照即可。
     *
     * 回傳 Object[]，index 0 = productTypeId，index 1 = count。
     */
    @Query("SELECT p.productTypeId, COUNT(p) FROM Product p WHERE p.productTypeId IS NOT NULL GROUP BY p.productTypeId")
    List<Object[]> countGroupedByProductType();

    /**
     * AI推薦Top10（GET /api/dashboard/recommendations）：
     * 依企劃書QA4「只有CANDIDATE狀態商品才會出現在...推薦清單裡」，
     * 只在review_status=PENDING（不代表系統自動核准，仍待審核）、
     * candidate_status=CANDIDATE、item_status=ACTIVE範圍內，
     * 依product_evaluations.final_score排序取前N筆。
     *
     * 用implicit join（FROM Product p, ProductEvaluation pe）而非在Product entity
     * 上額外建立JPA關聯：這個專案的既定慣例是所有跨表關聯都用plain Long id欄位
     * 手動比對，不引入JPA關聯物件圖（見FestiveCampaignTag.java等處的相同慣例）。
     * JPQL的FROM子句可直接以entity名稱參照ProductEvaluation，不需要在本檔案
     * import該類別（JPQL依persistence unit註冊的entity名稱解析，非Java型別引用）。
     *
     * ⚠️ 2026-09-17修正：join 條件原本只要求「有 ProductEvaluation 那筆列存在」
     * （p.id = pe.productId），沒有排除 pe.finalScore 本身是 null 的情況。
     * 資料完整度未達 60% 門檻的商品（見 ScoringService.DATA_COMPLETENESS_THRESHOLD）
     * 不會進入固定模式計分，final_score 欄位會是 null，但仍然會有一筆
     * ProductEvaluation（dataCompleteness 有值），一樣滿足這個 join 條件、
     * 一樣符合 PENDING/CANDIDATE/ACTIVE 篩選，因此有機會被排進「AI 推薦
     * Top 10」——而 MySQL 對 NULL 在 DESC 排序視為最小值，會被排到最後面，
     * 在候選不足 10 筆時墊底湊數。結果就是「AI 推薦」清單裡出現一個根本
     * 沒被完整評分過的商品，畫面上最終分數顯示「—」。這在語意上就是錯的：
     * 一個資料完整度不夠、連分數都算不出來的商品，不該被 AI 推薦。
     * 加上 pe.finalScore IS NOT NULL，從源頭排除這種商品，而不是讓前端
     * 用「—」把這個問題蓋過去。
     */
    @Query("""
            SELECT p FROM Product p, ProductEvaluation pe
             WHERE p.id = pe.productId
               AND pe.finalScore IS NOT NULL
               AND p.reviewStatus = :reviewStatus
               AND p.candidateStatus = :candidateStatus
               AND p.itemStatus = :itemStatus
             ORDER BY pe.finalScore DESC
            """)
    List<Product> findTopRecommendations(
            @Param("reviewStatus") ProductReviewStatus reviewStatus,
            @Param("candidateStatus") ProductCandidateStatus candidateStatus,
            @Param("itemStatus") ProductItemStatus itemStatus,
            Pageable pageable);

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

    /**
     * RESALE 商品搜尋相似候選參考商品的候選池：同品類（小類）、未封存的商品。
     *
     * 只縮小到「同品類」這一個結構化條件——不在 SQL 層做名稱比對，名稱相似度
     * 交給 Java 端算（見 ProductSimilarityService），因為 Jaro-Winkler 這類
     * 演算法沒有對應的 SQL 語法，勢必要把候選撈出來後在應用層計算。
     *
     * 排除 ARCHIVED：已封存的商品不該被當成新商品要參考的對象，那通常代表
     * 這個品項已經停止經營，繼續引用它的歷史沒有意義。
     *
     * 不排除呼叫端自己（selfId 為 null 時代表新增情境，本來就沒有自己可排除）
     * 的篩選交給呼叫端在 Java 層處理，這裡只負責基本的候選池查詢。
     */
    @Query("SELECT p FROM Product p WHERE p.productTypeId = :productTypeId AND p.itemStatus <> 'ARCHIVED'")
    List<Product> findCandidatesByProductType(@Param("productTypeId") Long productTypeId);

    /**
     * RESALE 逐層過濾參考商品：第一層（商品分類已固定）之後，列出該分類下
     * 有供貨紀錄的供應商，供第二層下拉選單使用。
     *
     * DISTINCT 且排除空白值——supplierName 在 Entity 層是選填欄位，舊資料
     * 或人工建立時可能沒填，這種商品不該在下拉選單裡變成一個空白選項。
     */
    @Query("""
            SELECT DISTINCT p.supplierName FROM Product p
             WHERE p.productTypeId = :productTypeId
               AND p.itemStatus <> 'ARCHIVED'
               AND p.supplierName IS NOT NULL
               AND p.supplierName <> ''
             ORDER BY p.supplierName
            """)
    List<String> findDistinctSupplierNamesByProductType(@Param("productTypeId") Long productTypeId);

    /**
     * RESALE 逐層過濾參考商品：第二層，選定分類＋供應商之後列出可選的商品，
     * 供第三層下拉選單使用。篩選邏輯與 findCandidatesByProductType() 一致
     * （排除 ARCHIVED），差別只在多一個供應商精準比對條件。
     */
    @Query("""
            SELECT p FROM Product p
             WHERE p.productTypeId = :productTypeId
               AND p.supplierName = :supplierName
               AND p.itemStatus <> 'ARCHIVED'
             ORDER BY p.name
            """)
    List<Product> findCandidatesByProductTypeAndSupplier(
            @Param("productTypeId") Long productTypeId,
            @Param("supplierName") String supplierName);
}