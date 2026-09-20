package com.example.Product_Selection_260813.service.weather;

import java.util.List;

import com.example.Product_Selection_260813.dto.weather.WeatherSignal;

/**
 * 天氣訊號來源的抽象介面——WeatherCampaignSyncService 只依賴這個介面，
 * 不知道訊號實際上是呼叫 Open-Meteo、中央氣象署，還是任何其他來源
 * （規劃文件第23節的分層原則）。
 *
 * 目前只有 StubWeatherSignalProvider 這個先占位的實作，真正的 Open-Meteo
 * 整合（WeatherClient 呼叫外部 API + WeatherNormalizer 轉換門檻規則）
 * 屬於下一步要做的事，不在這次「骨架」範圍內——這支介面存在的目的，
 * 就是讓 WeatherCampaignSyncService／calculateUrgencyFactor() 的天氣分支
 * 可以先寫、先測，之後接上真正的資料來源時，只需要新增一個
 * @Primary 的實作類別替換掉 StubWeatherSignalProvider，不用改動
 * WeatherCampaignSyncService 任何一行。
 */
public interface WeatherSignalProvider {

	/**
	 * 取得目前所有「值得建立或更新天氣檔期」的訊號快照。
	 *
	 * 回傳範圍由實作決定要包含到哪個信心層級——規劃文件的建議是：
	 * LOW（15天以上的長期趨勢）先不落地成檔期，只在這裡回傳
	 * HIGH／MEDIUM（14天以內）的訊號，等距離縮短、信心度提升後
	 * 才會出現在回傳結果裡，交由 WeatherCampaignSyncService 建檔期。
	 */
	List<WeatherSignal> getActiveSignals();
}
