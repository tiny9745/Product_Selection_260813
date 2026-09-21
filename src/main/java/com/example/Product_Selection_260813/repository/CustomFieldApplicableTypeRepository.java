package com.example.Product_Selection_260813.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.CustomFieldApplicableType;

public interface CustomFieldApplicableTypeRepository extends JpaRepository<CustomFieldApplicableType, Long> {

	List<CustomFieldApplicableType> findByFieldDefinitionId(Long fieldDefinitionId);

	/** 供商品表單渲染批次查詢多個題目的品類限制，避免逐題各查一次（N+1）。 */
	List<CustomFieldApplicableType> findByFieldDefinitionIdIn(Collection<Long> fieldDefinitionIds);

	/** 更新一個題目的品類範圍時，整份覆蓋語意：先刪光舊的，再插入新的。 */
	void deleteByFieldDefinitionId(Long fieldDefinitionId);
}
