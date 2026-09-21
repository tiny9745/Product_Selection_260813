package com.example.Product_Selection_260813.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.CustomFieldDefinition;

public interface CustomFieldDefinitionRepository extends JpaRepository<CustomFieldDefinition, Long> {

	List<CustomFieldDefinition> findByIsActiveTrue();

	Optional<CustomFieldDefinition> findByFieldCode(String fieldCode);

	boolean existsByFieldCode(String fieldCode);
}
