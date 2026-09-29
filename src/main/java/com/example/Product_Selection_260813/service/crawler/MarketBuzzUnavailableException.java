package com.example.Product_Selection_260813.service.crawler;

/**
 * 市場熱度資料來源整體無法取得（例如 PTT 所有看板都逾時）。
 *
 * 2026-09-29 起 TrendService 接到後<b>不再改用模擬資料</b>：保留上一筆真實資料、照常重算評分
 * （讓熱度時效衰減繼續進行），再把這個例外往外拋——全商品同步計為「失敗」，單一商品手動同步回 502。
 * 訊息會直接顯示給使用者，不可包含網址等內部細節。
 */
public class MarketBuzzUnavailableException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public MarketBuzzUnavailableException(String message) {
		super(message);
	}

	public MarketBuzzUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
