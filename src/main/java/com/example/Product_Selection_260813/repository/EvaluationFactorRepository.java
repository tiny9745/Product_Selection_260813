package com.example.Product_Selection_260813.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.EvaluationFactor;

public interface EvaluationFactorRepository extends JpaRepository<EvaluationFactor, Long> {

    // 品項詳情頁／審核頁「固定加權唯讀展示」資料來源
    List<EvaluationFactor> findByEvaluationModeIdOrderBySortOrderAsc(Long evaluationModeId);

    /**
     * 停用自訂因子時，找出所有模式裡引用這個因子代碼的列，供
     * SettingsService.disableFactorDefinition() 一併把權重歸零。
     */
    List<EvaluationFactor> findByFactorCode(String factorCode);
}
