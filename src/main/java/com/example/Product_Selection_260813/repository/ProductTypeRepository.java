package com.example.Product_Selection_260813.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.ProductType;

public interface ProductTypeRepository extends JpaRepository<ProductType, Long> {

    // 商品類型設定頁：is_active=TRUE的類型供新增/編輯品項時選擇（系統預設＋自訂）
    List<ProductType> findByIsActiveTrue();

    // 兩層階層查詢。用 level 欄位而非遞迴查 parent——兩層固定深度下，
    // 一次查詢就能取得整層，不需要 recursive CTE。
    List<ProductType> findByParentIdOrderBySortOrderAsc(Long parentId);

    List<ProductType> findByLevelAndIsActiveTrueOrderBySortOrderAsc(Integer level);

    List<ProductType> findByParentIdAndIsActiveTrueOrderBySortOrderAsc(Long parentId);

    /**
     * 依名稱查小類（level=2）。供歷史開團紀錄 CSV 匯入使用——匯入格式這次
     * 改成填品類「名稱」而非數字 id（見 GroupBuyRecordService 類別註解），
     * 商品只能掛在小類，所以這裡限定 level=2，避免大類名稱被誤當成合法值。
     * 回傳 List 而非 Optional：資料庫沒有名稱唯一約束，理論上可能有同名
     * 小類（不同大類底下），Service 層依實際回傳筆數決定要不要視為錯誤。
     */
    List<ProductType> findByNameAndLevel(String name, Integer level);

    /**
     * 刪除大類（level=1）前用來檢查底下是否還有小類——parent_id 目前只有
     * DB 層的 FK 約束（V2 migration 的 fk_product_type_parent），沒有
     * ON DELETE CASCADE／SET NULL，直接刪除會被 FK 擋下丟出
     * DataIntegrityViolationException，最終被 GlobalExceptionHandler
     * 的保底 handler 包裝成 500「伺服器發生錯誤」，使用者完全看不出
     * 真正原因（其實是子類還在，不是查無資料或商品在用）。
     * 在 Service 層先用這個方法主動檢查，丟出語意正確的 IllegalStateException
     * （對應 409），把「大類底下還有小類」這個看得懂的原因回報給前端，
     * 不要讓它撞到 FK 約束才發現。
     */
    boolean existsByParentId(Long parentId);
}
