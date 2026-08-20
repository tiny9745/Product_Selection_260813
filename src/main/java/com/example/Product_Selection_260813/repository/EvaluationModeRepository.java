package com.example.Product_Selection_260813.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.EvaluationMode;

public interface EvaluationModeRepository extends JpaRepository<EvaluationMode, Long> {

    // 設定頁模式列表（GET /api/settings/evaluation-modes）：僅列出可用版本
    List<EvaluationMode> findByIsActiveTrue();

    // 同一mode_code下最新版本（新增權重版本時查最大version號，用於version+1判斷）
    Optional<EvaluationMode> findTopByModeCodeOrderByVersionDesc(String modeCode);

    Optional<EvaluationMode> findByModeCodeAndVersion(String modeCode, Integer version);

    // 「目前生效模式」已改採方案B（見SystemSettingRepository），
    // 流程為：先查SystemSettingRepository取得id，
    // 再用本Repository繼承而來的findById(id)取得完整EvaluationMode，
    // 因此這裡不再需要（也不應該再猜測性地）提供「目前生效」查詢方法。
}
