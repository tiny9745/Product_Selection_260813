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
}
