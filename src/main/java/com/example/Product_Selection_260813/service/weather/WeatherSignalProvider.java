package com.example.Product_Selection_260813.service.weather;

import java.util.List;

import com.example.Product_Selection_260813.dto.weather.WeatherSignal;

/**
 * 天氣訊號來源的抽象介面——WeatherCampaignSyncService只依賴這個介面，
 * 不知道訊號實際上是呼叫Open-Meteo、中央氣象署，還是StubWeatherSignalProvider
 * 的空清單（規劃文件第23節的分層原則）。
 *
 * 目前有兩個實作：{@link OpenMeteoWeatherSignalProvider}（標
 * {@code @Primary}，串接Open-Meteo Forecast API，Spring預設注入這一個）與
 * {@link StubWeatherSignalProvider}（一律回傳空清單，切換方式見該類別
 * 說明）——WeatherCampaignSyncService／calculateUrgencyFactor()的天氣分支
 * 本身完全不需要知道現在是哪一個實作在跑，之後要換資料來源（例如中央氣象署
 * 開放資料平台），只需要新增一個{@code @Primary}的實作類別，不用改動
 * WeatherCampaignSyncService任何一行。
 */
public interface WeatherSignalProvider {

	/**
	 * 取得目前所有「值得建立或更新天氣檔期」的訊號快照。
	 *
	 * 回傳範圍由實作決定要包含到哪個信心層級——規劃文件的建議是：
	 * LOW（15天以上的長期趨勢）先不落地成檔期，只在這裡回傳
	 * HIGH／MEDIUM（14天以內）的訊號，等距離縮短、信心度提升後
	 * 才會出現在回傳結果裡，交由WeatherCampaignSyncService建檔期。
	 * {@link OpenMeteoWeatherSignalProvider}目前只跟Open-Meteo要0~14天的
	 * 資料（見{@code weather.forecast-days}設定，預設14），因此實際上不會
	 * 產生LOW信心層級的訊號；日後若把該設定調高到Open-Meteo上限16天，
	 * 第15、16天會自然落入LOW，不需要修改任何程式碼。
	 */
	List<WeatherSignal> getActiveSignals();
}
