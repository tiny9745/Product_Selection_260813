package com.example.Product_Selection_260813.service.weather;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.dto.weather.WeatherSignal;

/**
 * 佔位／備援實作：一律回傳空清單，讓WeatherCampaignSyncService的天氣檔期
 * 同步不會建立任何WEATHER類別的festive_campaigns。
 *
 * <b>現況（2026-09-21更新，取代舊版TODO註解）：</b>目前有兩個實作並存：本類別
 * （模擬資料，不呼叫外部API）與{@link OpenMeteoWeatherSignalProvider}（正式
 * 串接Open-Meteo Forecast API，標註{@code @Primary}，Spring預設會注入這
 * 一個）——與LlmAnalysisService／GeminiAnalysisServiceImpl／
 * MockLlmAnalysisService的既有慣例完全一致。
 *
 * 切換方式：拿掉OpenMeteoWeatherSignalProvider的{@code @Primary}（或改用
 * {@code @Qualifier}指定注入本類別），即可暫時停用天氣功能（例如Open-Meteo
 * 發生大規模異常、除錯、demo前想先確認天氣功能不會誤動到既有檔期），
 * WeatherCampaignSyncService不需要任何更動。
 */
@Component
public class StubWeatherSignalProvider implements WeatherSignalProvider {

	@Override
	public List<WeatherSignal> getActiveSignals() {
		return List.of();
	}
}
