package com.example.Product_Selection_260813.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.SystemSetting;

/**
 * PK即為setting_key（String型別），因此「依key查值」直接用
 * 繼承來的findById(String key)即可，不需要額外多寫一支
 * findBySettingKey——那會是重複、多餘的方法。
 *
 * 使用範例（Service層）：
 *   Long modeId = systemSettingRepository.findById("current_evaluation_mode_id")
 *       .map(s -> Long.valueOf(s.getSettingValue()))
 *       .orElseThrow(() -> new IllegalStateException("尚未設定目前生效模式"));
 *   EvaluationMode current = evaluationModeRepository.findById(modeId)
 *       .orElseThrow(...);
 */
public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
	
}