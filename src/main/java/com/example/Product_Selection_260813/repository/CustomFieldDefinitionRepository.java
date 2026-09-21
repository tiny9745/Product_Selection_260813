package com.example.Product_Selection_260813.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.CustomFieldDefinition;

public interface CustomFieldDefinitionRepository extends JpaRepository<CustomFieldDefinition, Long> {

	List<CustomFieldDefinition> findByIsActiveTrue();

	Optional<CustomFieldDefinition> findByFieldCode(String fieldCode);

	boolean existsByFieldCode(String fieldCode);

	/**
	 * V14新增：建立/編輯時的重複代碼檢查改用這個方法，理由同
	 * FactorDefinitionRepository.existsByFactorCodeAndIsActiveTrue()。
	 */
	boolean existsByFieldCodeAndIsActiveTrue(String fieldCode);

	/**
	 * V14新增：擋下「重新啟用一個已被編輯取代的舊題目」，理由同
	 * FactorDefinitionRepository.existsByPreviousVersionId()。
	 */
	boolean existsByPreviousVersionId(Long previousVersionId);
}
