package com.example.Product_Selection_260813.dto.response;

import java.time.LocalDateTime;

import com.example.Product_Selection_260813.entity.GoogleTrendRun;
import com.example.Product_Selection_260813.enums.TrendSyncRunStatus;
import com.example.Product_Selection_260813.enums.TrendSyncTrigger;

/**
 * 一次 Google 趨勢批次查詢的執行紀錄（GET /api/settings/google-trends 的 recentRuns）。
 *
 * @param triggeredByName 手動觸發的管理者名稱；排程觸發為 null
 */
public record GoogleTrendRunResponse(
		Long id,
		TrendSyncTrigger triggerType,
		TrendSyncRunStatus status,
		LocalDateTime startedAt,
		LocalDateTime finishedAt,
		int totalCount,
		int okCount,
		int noDataCount,
		int failedCount,
		String message,
		String triggeredByName) {

	public static GoogleTrendRunResponse from(GoogleTrendRun run, String triggeredByName) {
		return new GoogleTrendRunResponse(run.getId(), run.getTriggerType(), run.getStatus(), run.getStartedAt(),
				run.getFinishedAt(), run.getTotalCount(), run.getOkCount(), run.getNoDataCount(),
				run.getFailedCount(), run.getMessage(), triggeredByName);
	}
}
