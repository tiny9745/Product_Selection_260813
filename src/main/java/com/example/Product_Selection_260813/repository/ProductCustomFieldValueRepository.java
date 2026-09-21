package com.example.Product_Selection_260813.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.ProductCustomFieldValue;

public interface ProductCustomFieldValueRepository extends JpaRepository<ProductCustomFieldValue, Long> {

	List<ProductCustomFieldValue> findByProductId(Long productId);

	/** 供商品列表頁批次查詢多筆商品的自訂屬性答案，避免逐筆各查一次（N+1）。 */
	List<ProductCustomFieldValue> findByProductIdIn(Collection<Long> productIds);

	/** 編輯商品時整份覆蓋語意：先刪光這個商品的舊答案，再依送來的內容重新寫入。 */
	void deleteByProductId(Long productId);
}
