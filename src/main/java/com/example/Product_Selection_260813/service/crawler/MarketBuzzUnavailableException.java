package com.example.Product_Selection_260813.service.crawler;

/**
 * 市場熱度資料來源整體無法取得（例如 PTT 所有看板都逾時）。TrendService 接到後
 * 會改用 StubMarketBuzzProvider，不讓整個同步流程失敗。
 */
public class MarketBuzzUnavailableException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public MarketBuzzUnavailableException(String message) {
		super(message);
	}
}
