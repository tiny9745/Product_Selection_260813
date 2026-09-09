package com.example.Product_Selection_260813.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.Product_Selection_260813.entity.ProductTypeScoreBand;

public interface ProductTypeScoreBandRepository extends JpaRepository<ProductTypeScoreBand, Long> {

	/** 指定大類的生效區間。同一組合可能有多個版本，取版本號最大的那筆。 */
	@Query("SELECT b FROM ProductTypeScoreBand b "
			+ "WHERE b.productTypeId = :productTypeId AND b.factorCode = :factorCode AND b.isActive = true "
			+ "ORDER BY b.version DESC LIMIT 1")
	Optional<ProductTypeScoreBand> findActiveByTypeAndFactor(@Param("productTypeId") Long productTypeId,
			@Param("factorCode") String factorCode);

	/** 全域預設區間（product_type_id IS NULL）。品類沒有專屬設定時的 fallback。 */
	@Query("SELECT b FROM ProductTypeScoreBand b "
			+ "WHERE b.productTypeId IS NULL AND b.factorCode = :factorCode AND b.isActive = true "
			+ "ORDER BY b.version DESC LIMIT 1")
	Optional<ProductTypeScoreBand> findActiveGlobalByFactor(@Param("factorCode") String factorCode);
}
