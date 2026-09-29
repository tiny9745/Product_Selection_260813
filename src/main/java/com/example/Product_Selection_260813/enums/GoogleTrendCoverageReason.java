package com.example.Product_Selection_260813.enums;

/**
 * 商品是否在 Google 趨勢每週批次範圍內、以及原因（GET /api/products/{id}/google-trend/coverage）。
 * 判斷順序與 GoogleTrendService.getBatchCoverage() 相同。
 */
public enum GoogleTrendCoverageReason {
	/** 來源停用：批次不會執行。 */
	SOURCE_DISABLED,
	/** 未設定 SerpApi 金鑰：批次不會執行。 */
	NOT_CONFIGURED,
	/** 已封存商品不在批次範圍。 */
	ARCHIVED,
	/** 重查間隔內已查過，下次批次略過。 */
	RECENTLY_QUERIED,
	/** 待審商品，下次批次優先查詢。 */
	PENDING_PRIORITY,
	/** PTT 熱度在補位名額內，下次批次會查詢。 */
	PTT_RANKED,
	/** 在名單內，但排名超過本月剩餘額度，本月批次查不到。 */
	QUOTA_SHORT,
	/** 待審商品超過每次上限，這次排不進名額。 */
	PENDING_OVER_LIMIT,
	/** 非待審，且沒有任何 PTT 真實熱度資料。 */
	NO_PTT_DATA,
	/** 非待審，且最新一筆 PTT 熱度為 0。 */
	PTT_ZERO,
	/** 非待審，PTT 熱度 &gt; 0 但排不進補位名額。 */
	RANKED_OUT
}
