package com.example.Product_Selection_260813.dto.response;

import java.time.LocalDateTime;

import com.example.Product_Selection_260813.entity.TrendSyncRun;
import com.example.Product_Selection_260813.enums.TrendSyncRunStatus;
import com.example.Product_Selection_260813.enums.TrendSyncTrigger;

/**
 * 一次 PTT 熱度全商品同步的執行紀錄（GET /api/settings/trend-crawler 的 recentRuns）。
 *
 * @param triggeredByName 手動觸發的管理者名稱；排程觸發為 null
 */
public record TrendSyncRunResponse(
		Long id,
		TrendSyncTrigger triggerType,
		TrendSyncRunStatus status,
		LocalDateTime startedAt,
		LocalDateTime finishedAt,
		int totalCount,
		int realCount,
		int fallbackCount,
		int failedCount,
		String message,
		String triggeredByName) {

	public static TrendSyncRunResponse from(TrendSyncRun run, String triggeredByName) {
		return new TrendSyncRunResponse(run.getId(), run.getTriggerType(), run.getStatus(), run.getStartedAt(),
				run.getFinishedAt(), run.getTotalCount(), run.getRealCount(), run.getFallbackCount(),
				run.getFailedCount(), run.getMessage(), triggeredByName);
	}
}
