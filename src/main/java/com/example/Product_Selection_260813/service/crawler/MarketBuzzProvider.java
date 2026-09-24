package com.example.Product_Selection_260813.service.crawler;

import com.example.Product_Selection_260813.entity.TrendSignal;

/**
 * 市場熱度資料來源的抽象介面——TrendService 只依賴這個介面，不知道資料實際上是
 * 爬 PTT 還是 StubMarketBuzzProvider 的模擬值（比照 service/weather/ 的
 * WeatherSignalProvider 分層方式）。
 *
 * 目前有兩個實作：{@link PttMarketBuzzProvider}（標 {@code @Primary}，Spring
 * 預設注入這一個）與 {@link StubMarketBuzzProvider}（原本 TrendService 的
 * Random 模擬邏輯，保留當備援）。之後要加 Google Trends 等來源，新增一個實作
 * 類別即可，TrendService 不用改。
 */
public interface MarketBuzzProvider {

	/**
	 * 取得指定關鍵字目前的市場熱度。
	 *
	 * @param keyword  搜尋關鍵字
	 * @param previous 該商品上一筆趨勢資料，沒有則為 null；模擬實作以它為基準做隨機波動
	 * @throws MarketBuzzUnavailableException 資料來源整體無法取得（例如所有看板都連不上）。
	 *         「查到了、但討論量是 0」是正常結果，不會拋這個例外
	 */
	MarketBuzzSignal fetch(String keyword, TrendSignal previous);
}
