package com.example.Product_Selection_260813.dto.response;

import java.util.List;

/**
 * 系統設定「Google 趨勢參考」控制面板的狀態（GET /api/settings/google-trends）。
 *
 * @param enabled        來源是否啟用（預設停用）
 * @param keyConfigured  後端是否已設定 SerpApi 金鑰；只回布林值
 * @param usedThisMonth  本月已用次數（含查無資料，SerpApi 同樣計費）
 * @param monthlyLimit   本月上限（system_settings.serpapi_monthly_limit，預設 200）
 * @param running        目前是否有批次查詢正在執行
 * @param processedCount 執行中時已處理的商品數；未執行時為 null
 * @param totalCount     執行中時本次要處理的商品總數；未執行時為 null
 * @param batchSize      每次批次最多查詢的商品數（PTT 熱度前 N 名）
 * @param schedule       排程時間說明
 * @param recentRuns     最近 10 次執行紀錄，新到舊
 */
public record GoogleTrendStatusResponse(
		boolean enabled,
		boolean keyConfigured,
		int usedThisMonth,
		int monthlyLimit,
		boolean running,
		Integer processedCount,
		Integer totalCount,
		int batchSize,
		String schedule,
		List<GoogleTrendRunResponse> recentRuns) {
}
