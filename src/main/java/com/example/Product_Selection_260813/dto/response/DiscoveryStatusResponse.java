package com.example.Product_Selection_260813.dto.response;

import java.util.List;

/**
 * 系統設定「PTT 新品探索」排程面板的狀態（GET /api/settings/discovery）。
 *
 * @param pttEnabled       PTT 來源是否啟用（與熱度同步共用同一個開關；停用時探索也不執行）
 * @param aiConfigured     是否已設定探索用的 AI 金鑰（GROQ_API_KEY）；未設定時探索一定失敗，畫面要提示
 *                         （2026-09-29 由 geminiConfigured 改名：探索改用 Groq）
 * @param running          目前是否正在執行
 * @param schedule         排程時間說明
 * @param aiCallsThisMonth 本月探索已使用的 AI 呼叫次數
 * @param aiMonthlyLimit   探索專用的每月上限（system_settings.discovery_ai_monthly_limit）
 * @param recentRuns       最近 10 次執行紀錄，新到舊
 */
public record DiscoveryStatusResponse(
		boolean pttEnabled,
		boolean aiConfigured,
		boolean running,
		String schedule,
		int aiCallsThisMonth,
		int aiMonthlyLimit,
		List<DiscoveryRunResponse> recentRuns) {
}
