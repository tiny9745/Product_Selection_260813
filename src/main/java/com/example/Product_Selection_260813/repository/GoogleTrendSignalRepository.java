package com.example.Product_Selection_260813.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.GoogleTrendSignal;

public interface GoogleTrendSignalRepository extends JpaRepository<GoogleTrendSignal, Long> {

	/** 品項詳情頁「Google 趨勢參考」：該商品最新一筆。 */
	Optional<GoogleTrendSignal> findFirstByProductIdOrderByCollectedAtDesc(Long productId);

	/** 刪除商品前的預防性清理（外鍵沒有 ON DELETE CASCADE），見 ProductService.deleteProduct()。 */
	void deleteByProductId(Long productId);
}
