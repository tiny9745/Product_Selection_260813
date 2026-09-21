package com.example.Product_Selection_260813.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.FactorDefinition;

public interface FactorDefinitionRepository extends JpaRepository<FactorDefinition, Long> {

	List<FactorDefinition> findByIsActiveTrue();

	Optional<FactorDefinition> findByFactorCode(String factorCode);

	boolean existsByFactorCode(String factorCode);

	/**
	 * V14新增：建立/編輯時的重複代碼檢查改用這個方法，而不是existsByFactorCode()。
	 * 版本鏈設計下，一個代碼可能同時存在多列（舊版isActive=false＋新版
	 * isActive=true）——只有「目前生效中」的那一列才算真的佔用這個代碼，
	 * 已經被取代或已刪除的舊版本不應該擋住代碼被重新使用。
	 */
	boolean existsByFactorCodeAndIsActiveTrue(String factorCode);

	/**
	 * V14新增：擋下「重新啟用一個已被編輯取代的舊版本」。見 previousVersionId
	 * 欄位註解——有其他列的previousVersionId指向這個id，代表這個id已經被取代。
	 */
	boolean existsByPreviousVersionId(Long previousVersionId);

	/** 批次查詢，供 ScoringService.buildWeightSnapshot() 一次撈完避免 N+1。 */
	List<FactorDefinition> findByFactorCodeIn(Collection<String> factorCodes);

	/**
	 * V14新增：SettingsService.updateCustomFieldDefinition() 編輯自訂商品屬性時，
	 * 用這個方法找出所有目前綁定該題目、且生效中的因子，逐一改綁到新版本的id，
	 * 避免題目被編輯（id變動）後，這些因子安靜地讀不到新商品填的答案。
	 * 只查生效中的：已停用/已被取代的因子不需要跟著搬移。
	 */
	List<FactorDefinition> findByCustomFieldDefinitionIdAndIsActiveTrue(Long customFieldDefinitionId);

	/**
	 * V14新增：buildWeightSnapshot() 改用這個方法取代findByFactorCodeIn()。
	 * 版本鏈設計下同一代碼可能有多列，若沿用findByFactorCodeIn()再用
	 * Collectors.toMap(FactorDefinition::getFactorCode, ...)組裝，一旦
	 * 舊版本尚未被真正清空（歷史列還在），會因為同一個key出現兩次而丟出
	 * IllegalStateException（Duplicate key）。只查目前生效中的那一列，
	 * 天生保證每個代碼最多一列，才能安全組Map。
	 */
	List<FactorDefinition> findByFactorCodeInAndIsActiveTrue(Collection<String> factorCodes);
}
