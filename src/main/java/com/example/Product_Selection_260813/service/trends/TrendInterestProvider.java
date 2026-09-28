package com.example.Product_Selection_260813.service.trends;

/**
 * 「搜尋興趣的方向與成長率」來源。目前唯一實作是 SerpApiTrendInterestProvider（非官方代抓）；
 * Google 官方 Trends API（申請制 alpha）核准後，只要換一個實作，呼叫端 GoogleTrendService 不用改。
 *
 * 刻意沒有「抓不到就回模擬資料」的備援：跟 PTT 停用時不退回模擬是同一個理由——
 * 隨機數字會讓方向隨機成立，被當成真的市場訊號。抓不到就丟例外，由呼叫端記為失敗。
 */
public interface TrendInterestProvider {

	/**
	 * @return 取得序列時 status=OK；Google 查無結果時 status=NO_DATA（這種情況 SerpApi 也會計入額度）
	 * @throws TrendInterestUnavailableException 網路、逾時、金鑰錯誤、額度用盡等任何呼叫失敗
	 */
	TrendInterest fetch(String keyword);

	/** 來源是否已完成設定（例如金鑰）；給狀態畫面顯示，不回傳金鑰本身。 */
	boolean isConfigured();
}
