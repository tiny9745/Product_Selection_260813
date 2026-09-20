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

	/** 批次查詢，供 ScoringService.buildWeightSnapshot() 一次撈完避免 N+1。 */
	List<FactorDefinition> findByFactorCodeIn(Collection<String> factorCodes);
}
