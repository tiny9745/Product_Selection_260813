package com.example.Product_Selection_260813.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.Product_Selection_260813.entity.GoogleTrendSignal;

public interface GoogleTrendSignalRepository extends JpaRepository<GoogleTrendSignal, Long> {

	/** 品項詳情頁「Google 趨勢參考」：該商品最新一筆。 */
	Optional<GoogleTrendSignal> findFirstByProductIdOrderByCollectedAtDesc(Long productId);

	/**
	 * 多個商品各自的最新一筆（AI 建議清單、儀表板熱度排行榜一次帶出，不逐筆查詢）。
	 * 同一商品同一秒有兩筆時兩筆都會回來，呼叫端以 id 較大者為準（見 GoogleTrendService.latestByProductIds）。
	 */
	@Query(value = """
			SELECT g.* FROM google_trend_signals g
			 WHERE g.product_id IN (:productIds)
			   AND g.collected_at = (
			         SELECT MAX(g2.collected_at) FROM google_trend_signals g2
			          WHERE g2.product_id = g.product_id
			       )
			""", nativeQuery = true)
	List<GoogleTrendSignal> findLatestByProductIds(@Param("productIds") Collection<Long> productIds);

	/** 刪除商品前的預防性清理（外鍵沒有 ON DELETE CASCADE），見 ProductService.deleteProduct()。 */
	void deleteByProductId(Long productId);
}
