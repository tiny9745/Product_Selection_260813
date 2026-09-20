package com.example.Product_Selection_260813.service.weather;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.dto.weather.WeatherSignal;

/**
 * 佔位實作：回傳空清單，讓 WeatherCampaignSyncService 可以先完整寫出、
 * 先部署，天氣檔期同步邏輯先不會做任何事（不會建立任何 WEATHER 類別的
 * festive_campaigns），直到真正接上 Open-Meteo（或其他來源）的實作
 * 出現並取代這個 Bean 為止。
 *
 * TODO：實作真正的 WeatherClient（呼叫 Open-Meteo Forecast API）＋
 * WeatherNormalizer（依規劃文件第11節的門檻規則，把 Temperature／
 * Humidity／Precipitation Probability 等原始數值轉成 WeatherSignalType），
 * 再依規劃文件第15節做區域聚合（例如南部＝台南＋高雄＋屏東取多數決
 * 或平均），最後才組成這裡要回傳的 List&lt;WeatherSignal&gt;。
 * 完成後把這個類別的 @Component 拿掉（或改標 @Component("stub") 並
 * 讓真正的實作標 @Primary），不用改動任何呼叫端。
 */
@Component
public class StubWeatherSignalProvider implements WeatherSignalProvider {

	@Override
	public List<WeatherSignal> getActiveSignals() {
		return List.of();
	}
}
