package com.example.Product_Selection_260813.dto.response;

import java.util.List;

/**
 * 系統設定「爬蟲排程控制」區塊的狀態（GET /api/settings/trend-crawler）。
 *
 * @param enabled        PTT 熱度來源是否啟用
 * @param running        目前是否有全商品同步正在執行
 * @param processedCount 執行中時已處理的商品數；未執行時為 null
 * @param totalCount     執行中時本次要處理的商品總數；未執行時為 null
 * @param schedule       排程時間說明（固定值，程式碼中的 cron 設定）
 * @param recentRuns     最近 10 次執行紀錄，新到舊
 */
public record TrendCrawlerStatusResponse(
		boolean enabled,
		boolean running,
		Integer processedCount,
		Integer totalCount,
		String schedule,
		List<TrendSyncRunResponse> recentRuns) {
}
