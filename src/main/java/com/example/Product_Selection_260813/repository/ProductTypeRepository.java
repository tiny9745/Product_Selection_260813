package com.example.Product_Selection_260813.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.ProductType;

public interface ProductTypeRepository extends JpaRepository<ProductType, Long> {

    // 商品類型設定頁：is_active=TRUE的類型供新增/編輯品項時選擇（系統預設＋自訂）
    List<ProductType> findByIsActiveTrue();
}
