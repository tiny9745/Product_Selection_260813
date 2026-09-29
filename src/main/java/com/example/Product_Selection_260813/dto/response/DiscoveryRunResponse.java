package com.example.Product_Selection_260813.dto.response;

import java.time.LocalDateTime;

import com.example.Product_Selection_260813.entity.DiscoveryRun;
import com.example.Product_Selection_260813.enums.TrendSyncRunStatus;
import com.example.Product_Selection_260813.enums.TrendSyncTrigger;

/**
 * 一次 PTT 新品探索的執行紀錄（GET /api/settings/discovery 的 recentRuns）。
 *
 * @param triggeredByName 手動觸發的管理者名稱；排程觸發為 null
 */
public record DiscoveryRunResponse(
		Long id,
		TrendSyncTrigger triggerType,
		TrendSyncRunStatus status,
		LocalDateTime startedAt,
		LocalDateTime finishedAt,
		int postCount,
		int titleCount,
		int aiCallCount,
		int extractedCount,
		int rejectedCount,
		int matchedExistingCount,
		int similarDismissedCount,
		int newCount,
		int updatedCount,
		int fitCount,
		int googleCount,
		String message,
		String triggeredByName) {

	public static DiscoveryRunResponse from(DiscoveryRun run, String triggeredByName) {
		return new DiscoveryRunResponse(run.getId(), run.getTriggerType(), run.getStatus(), run.getStartedAt(),
				run.getFinishedAt(), run.getPostCount(), run.getTitleCount(), run.getAiCallCount(),
				run.getExtractedCount(), run.getRejectedCount(), run.getMatchedExistingCount(),
				run.getSimilarDismissedCount(), run.getNewCount(), run.getUpdatedCount(), run.getFitCount(),
				run.getGoogleCount(), run.getMessage(), triggeredByName);
	}
}
