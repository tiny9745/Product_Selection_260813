package com.example.Product_Selection_260813.common.exception;

/**
 * LLM（目前為Gemini）呼叫失敗：包含網路逾時／連線失敗、API回傳非2xx狀態、
 * 回應內容格式不符預期（無法解析出summary/recommendation/reasons）等情況。
 *
 * <b>為什麼不沿用SystemConfigurationException：</b>
 * SystemConfigurationException代表「我方系統設定本身壞掉，使用者不管怎麼重試
 * 都不可能成功」；但LLM呼叫失敗通常是外部服務暫時性問題（逾時、對方伺服器
 * 忙碌、額度超過），使用者稍後重新點擊「生成AI分析」很可能就會成功，語意上
 * 不該跟「系統設定壞掉」混為一談。
 *
 * <b>為什麼回傳502而非500：</b>本例外代表「我方伺服器嘗試呼叫上游服務失敗」，
 * 語意上是Bad Gateway；沿用500反而讓監控系統無法區分「我方程式bug」與
 * 「外部LLM服務不穩定」這兩種完全不同、需要不同應對方式的問題。
 */
public class LlmAnalysisException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public LlmAnalysisException(String message) {
		super(message);
	}

	public LlmAnalysisException(String message, Throwable cause) {
		super(message, cause);
	}
}
