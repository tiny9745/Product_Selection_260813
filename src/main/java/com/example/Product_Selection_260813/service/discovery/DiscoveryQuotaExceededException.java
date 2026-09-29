package com.example.Product_Selection_260813.service.discovery;

import com.example.Product_Selection_260813.common.exception.LlmAnalysisException;

/**
 * PTT 新品探索的 AI 額度已用完：本系統的每月上限，或 Groq 免費方案要求等待過久（通常是每日 token 上限）。繼承 LlmAnalysisException（對外語意相同：AI 服務暫時不能用），
 * 另外獨立成型別只是讓 DiscoveryService 能判斷「後面的批次也不必再試」，不用比對錯誤訊息文字。
 */
public class DiscoveryQuotaExceededException extends LlmAnalysisException {

	private static final long serialVersionUID = 1L;

	public DiscoveryQuotaExceededException(String message) {
		super(message);
	}
}
