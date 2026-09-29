package com.example.Product_Selection_260813.service.discovery;

import com.example.Product_Selection_260813.common.exception.LlmAnalysisException;

/**
 * 探索用的 AI 服務（Groq）暫時無法使用（503、429 頻率限制、5xx、連線逾時），而且已經重試到上限仍失敗。
 *
 * 獨立成型別是讓 DiscoveryService 判斷「這次執行剩下的批次也不必再試」：服務過載通常會持續幾分鐘，
 * 繼續送後面的批次只會每批再等一輪重試、白白消耗時間與額度。
 * 相對地，一般的 LlmAnalysisException（例如某一批回應格式錯誤）只影響那一批，後面照常送。
 */
public class DiscoveryAiUnavailableException extends LlmAnalysisException {

	private static final long serialVersionUID = 1L;

	public DiscoveryAiUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
