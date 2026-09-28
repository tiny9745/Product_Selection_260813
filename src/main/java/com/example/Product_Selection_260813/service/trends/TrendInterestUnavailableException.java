package com.example.Product_Selection_260813.service.trends;

/**
 * 呼叫 Google 趨勢來源失敗（網路、逾時、SerpApi 回錯誤）。GlobalExceptionHandler 轉成 502：
 * 屬於上游服務的暫時性問題，跟 LlmAnalysisException 同一種語意。
 *
 * ⚠️ 訊息會回給前端，所以只放我方組好的文字與 SerpApi 回傳的 error 欄位；
 * 不可以把 RestClient 例外的 message 或 cause 帶進來——那裡面有完整請求網址，含 api_key。
 */
public class TrendInterestUnavailableException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public TrendInterestUnavailableException(String message) {
		super(message);
	}
}
