package com.example.Product_Selection_260813.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	/**
	 * 原子遞增一個「數字型」設定值（不存在就從1開始，存在就+1）。
	 * 用於像gemini_calls_2026-08這種計數器場景。
	 *
	 * 這裡刻意用INSERT...ON DUPLICATE KEY UPDATE的原生SQL，而不是
	 * 「findById讀出entity → setSettingValue → save」這種JPA慣用寫法：
	 * 後者在這張表沒有@Version欄位的情況下，Spring Data JPA對「ID已存在」
	 * 的物件會走entityManager.merge()，merge()在某些情境下（尤其重疊的
	 * session/交易時序）會誤判成StaleObjectStateException並整個丟出來，
	 * 即使實際上沒有真正的併發衝突。原子UPSERT完全繞開這個問題，而且
	 * 同時把「讀出來改完再寫回去」中間的競態條件也一併解決了。
	 */
	@Modifying
	@Query(value = "INSERT INTO system_settings (setting_key, setting_value, updated_at) "
			+ "VALUES (:key, '1', NOW()) "
			+ "ON DUPLICATE KEY UPDATE setting_value = CAST(setting_value AS UNSIGNED) + 1, updated_at = NOW()",
			nativeQuery = true)
	void incrementCounter(@Param("key") String key);
}