package com.example.Product_Selection_260813.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.Product_Selection_260813.entity.GroupBuyRecord;

/**
 * 歷史開團紀錄查詢。
 *
 * 成團率的分母只計 FULFILLED + FAILED，<b>CANCELLED 不計入</b>——取消開團多半是
 * 營運面的決定（供應商臨時出不了貨、檔期調整），不代表客群不買單，把它算進
 * 分母會低估該品類的真實成團能力。
 */
public interface GroupBuyRecordRepository extends JpaRepository<GroupBuyRecord, Long> {

	// ---------------- 成團率（貝氏收縮的原始輸入）----------------

	/** 全域成團次數。 */
	@Query("SELECT COUNT(g) FROM GroupBuyRecord g WHERE g.result = 'FULFILLED'")
	long countGlobalFulfilled();

	/** 全域有效樣本數（排除 CANCELLED）。 */
	@Query("SELECT COUNT(g) FROM GroupBuyRecord g WHERE g.result IN ('FULFILLED', 'FAILED')")
	long countGlobalEffective();

	@Query("SELECT COUNT(g) FROM GroupBuyRecord g "
			+ "WHERE g.productTypeId = :productTypeId AND g.result = 'FULFILLED'")
	long countFulfilledByProductType(@Param("productTypeId") Long productTypeId);

	@Query("SELECT COUNT(g) FROM GroupBuyRecord g "
			+ "WHERE g.productTypeId = :productTypeId AND g.result IN ('FULFILLED', 'FAILED')")
	long countEffectiveByProductType(@Param("productTypeId") Long productTypeId);

	@Query("SELECT COUNT(g) FROM GroupBuyRecord g "
			+ "WHERE g.productId = :productId AND g.result = 'FULFILLED'")
	long countFulfilledByProduct(@Param("productId") Long productId);

	@Query("SELECT COUNT(g) FROM GroupBuyRecord g "
			+ "WHERE g.productId = :productId AND g.result IN ('FULFILLED', 'FAILED')")
	long countEffectiveByProduct(@Param("productId") Long productId);

	// ---------------- 集單量分位數（GATE_MOQ_FEASIBILITY）----------------

	/**
	 * 該品類「成團案例」的實際集單量清單，供 ScoringAlgorithms.percentile() 計算基準。
	 *
	 * 只取 FULFILLED：未成團的集單量代表「沒達到的量」，拿它當可達成基準會低估能力。
	 * 分位數在應用層計算而非用 SQL，是為了讓演算法保持可單元測試的純函式。
	 */
	@Query("SELECT g.actualQuantity FROM GroupBuyRecord g "
			+ "WHERE g.productTypeId = :productTypeId AND g.result = 'FULFILLED' "
			+ "ORDER BY g.actualQuantity ASC")
	List<Integer> findFulfilledQuantitiesByProductType(@Param("productTypeId") Long productTypeId);

	// ---------------- 模擬資料標記（Signal 顯示用）----------------

	@Query("SELECT COUNT(g) FROM GroupBuyRecord g "
			+ "WHERE g.productTypeId = :productTypeId AND g.isSimulated = true")
	long countSimulatedByProductType(@Param("productTypeId") Long productTypeId);

	// ---------------- 匯入管理 ----------------

	List<GroupBuyRecord> findByImportBatchId(String importBatchId);

	void deleteByImportBatchId(String importBatchId);

	List<GroupBuyRecord> findByProductTypeIdOrderByCampaignStartDateDesc(Long productTypeId);

	List<GroupBuyRecord> findByProductIdOrderByCampaignStartDateDesc(Long productId);

	// ---------------- 歷史毛利率／折扣深度區間計算 ----------------

	/**
	 * 該品類（大類）有效樣本的成本價、售價（供算毛利率用）。
	 *
	 * 只取 costPriceAtTime／salePriceAtTime 皆非 null 的紀錄——這兩欄是
	 * 後來才補上的，舊資料可能缺，缺了就不該猜測或補值，直接跳過那筆。
	 * result 限 FULFILLED／FAILED，理由與成團率計算一致：CANCELLED 多半是
	 * 營運面決定，不代表這個定價不合理，不該污染樣本。
	 *
	 * 回傳 Object[]，index 0 = costPriceAtTime，index 1 = salePriceAtTime，
	 * 在 Java 端計算毛利率而不是在 SQL 裡做除法——除以零的防護、BigDecimal
	 * 精度控制在 Java 端处理更安全，也跟 ScoringAlgorithms 的其餘計算方式一致。
	 */
	@Query("SELECT g.costPriceAtTime, g.salePriceAtTime FROM GroupBuyRecord g "
			+ "WHERE g.productTypeId = :productTypeId "
			+ "AND g.result IN ('FULFILLED', 'FAILED') "
			+ "AND g.costPriceAtTime IS NOT NULL AND g.salePriceAtTime IS NOT NULL "
			+ "AND g.salePriceAtTime > 0")
	List<Object[]> findMarginRateSamplesByProductType(@Param("productTypeId") Long productTypeId);

	/** 語意同上，供折扣深度使用：index 0 = marketPriceAtTime，index 1 = salePriceAtTime。 */
	@Query("SELECT g.marketPriceAtTime, g.salePriceAtTime FROM GroupBuyRecord g "
			+ "WHERE g.productTypeId = :productTypeId "
			+ "AND g.result IN ('FULFILLED', 'FAILED') "
			+ "AND g.marketPriceAtTime IS NOT NULL AND g.salePriceAtTime IS NOT NULL "
			+ "AND g.marketPriceAtTime > 0")
	List<Object[]> findDiscountDepthSamplesByProductType(@Param("productTypeId") Long productTypeId);

	/** 上述兩種樣本中，是否含模擬資料——供快照的 historyIncludesSimulated 使用。 */
	@Query("SELECT COUNT(g) > 0 FROM GroupBuyRecord g "
			+ "WHERE g.productTypeId = :productTypeId AND g.isSimulated = true "
			+ "AND g.costPriceAtTime IS NOT NULL AND g.salePriceAtTime IS NOT NULL")
	boolean marginRateSamplesIncludeSimulated(@Param("productTypeId") Long productTypeId);
}
