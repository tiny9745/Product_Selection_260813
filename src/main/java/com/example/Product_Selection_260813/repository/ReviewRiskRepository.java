package com.example.Product_Selection_260813.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.ReviewRisk;
import com.example.Product_Selection_260813.entity.ReviewRiskId;

public interface ReviewRiskRepository extends JpaRepository<ReviewRisk, ReviewRiskId> {

    // 查詢某次審核勾選了哪些人工風險；By Id_ReviewId對應@EmbeddedId欄位
    // id.reviewId的巢狀路徑導覽（底線用來消除命名解析歧義）
    List<ReviewRisk> findById_ReviewId(Long reviewId);

    // 決策紀錄匯出：一次查回多筆審核紀錄的風險評估，避免逐筆查詢（N+1）
    List<ReviewRisk> findById_ReviewIdIn(java.util.Collection<Long> reviewIds);
}
