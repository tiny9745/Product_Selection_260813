package com.example.Product_Selection_260813.service.weather;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.dto.weather.WeatherSignal;
import com.example.Product_Selection_260813.enums.WeatherSignalType;

/**
 * {@link WeatherSignalProvider}正式實作：呼叫Open-Meteo Forecast API
 * （WeatherClient）取得各代表城市的每日氣象預報，依區域平均後交給
 * WeatherNormalizer分類、組成天氣訊號窗口。取代StubWeatherSignalProvider
 * ——切換方式與GeminiAnalysisServiceImpl取代MockLlmAnalysisService完全
 * 相同（見該類別的Javadoc）：這裡標{@code @Primary}，Stub保留不刪，之後
 * 要暫時停用天氣功能（除錯、Open-Meteo發生大規模異常時應急）只需要拿掉
 * 這裡的{@code @Primary}，WeatherCampaignSyncService不用改動任何一行。
 *
 * <b>容錯策略：</b>單一代表城市呼叫失敗（例如逾時），只記錄警告並排除該
 * 城市，改用同區域其餘代表城市的平均值——整支服務是背景每日排程
 * （WeatherCampaignSyncService的{@code @Scheduled(cron = "0 0 5 * * *")}），
 * 沒有使用者在等待這次呼叫的結果，寧可用「少一個城市的平均值」把當天同步
 * 完成，也不要讓單一城市的暫時性問題讓整個天氣同步（含北中南東四區）全部
 * 失敗。只有當某區域「所有代表城市都失敗」時，才會整區跳過（回傳的訊號
 * 清單不含該區，既有的天氣檔期也不會被這次同步動到——WeatherCampaignSyncService.
 * expireStaleWeatherCampaigns()只會讓「這次有同步到、但訊號消失」的檔期
 * 過期，一整區完全沒同步到不影響既有檔期），下次排程（每天05:00）會自然
 * 重試，不需要額外的重試機制。
 */
@Component
@Primary
public class OpenMeteoWeatherSignalProvider implements WeatherSignalProvider {

	private static final Logger log = LoggerFactory.getLogger(OpenMeteoWeatherSignalProvider.class);

	@Autowired
	private WeatherClient weatherClient;

	/** Open-Meteo免費版forecast_days上限16天；預設14天＝WeatherForecastConfidence的HIGH+MEDIUM涵蓋範圍。 */
	@Value("${weather.forecast-days:14}")
	private int forecastDays;

	@Override
	public List<WeatherSignal> getActiveSignals() {
		LocalDate today = LocalDate.now();
		List<WeatherSignal> allSignals = new ArrayList<>();

		for (Map.Entry<String, List<WeatherRegionConfig.City>> entry : WeatherRegionConfig.REGION_CITIES.entrySet()) {
			String region = entry.getKey();
			List<DailyWeatherMetrics> perCityAllDays = fetchRegionCities(region, entry.getValue());

			if (perCityAllDays.isEmpty()) {
				log.warn("天氣區域{}的所有代表城市都取得失敗，本次排程跳過此區域，等下次排程（每天05:00）重試", region);
				continue;
			}

			Map<LocalDate, DailyWeatherMetrics> averaged = averageByDate(perCityAllDays);
			Map<LocalDate, Set<WeatherSignalType>> dailyTypes = new TreeMap<>();
			averaged.forEach((date, metrics) -> dailyTypes.put(date, WeatherNormalizer.classifyDay(metrics)));

			allSignals.addAll(WeatherNormalizer.buildSignals(region, dailyTypes, today));
		}

		return allSignals;
	}

	/** 依序呼叫某區域所有代表城市；單一城市失敗只記錄警告並跳過，不中斷其他城市。 */
	private List<DailyWeatherMetrics> fetchRegionCities(String region, List<WeatherRegionConfig.City> cities) {
		List<DailyWeatherMetrics> result = new ArrayList<>();
		for (WeatherRegionConfig.City city : cities) {
			try {
				result.addAll(weatherClient.fetchDaily(city.latitude(), city.longitude(), forecastDays).values());
			} catch (Exception e) {
				log.warn("取得天氣區域{}代表城市{}的預報失敗，排除此城市改用其餘代表城市的平均值", region, city.name(), e);
			}
		}
		return result;
	}

	/** 把同區域多個城市、同一天的數值，逐欄位取平均（見WeatherRegionConfig的說明）。 */
	private Map<LocalDate, DailyWeatherMetrics> averageByDate(List<DailyWeatherMetrics> allCityDays) {
		Map<LocalDate, List<DailyWeatherMetrics>> byDate = new TreeMap<>();
		for (DailyWeatherMetrics metrics : allCityDays) {
			byDate.computeIfAbsent(metrics.date(), d -> new ArrayList<>()).add(metrics);
		}

		Map<LocalDate, DailyWeatherMetrics> result = new LinkedHashMap<>();
		byDate.forEach((date, metricsList) -> result.put(date, average(date, metricsList)));
		return result;
	}

	private DailyWeatherMetrics average(LocalDate date, List<DailyWeatherMetrics> list) {
		return new DailyWeatherMetrics(
				date,
				avg(list, DailyWeatherMetrics::apparentTemperatureMax),
				avg(list, DailyWeatherMetrics::apparentTemperatureMin),
				avg(list, DailyWeatherMetrics::humidityMean),
				avg(list, DailyWeatherMetrics::precipitationSum),
				avg(list, DailyWeatherMetrics::precipitationProbabilityMax),
				avg(list, DailyWeatherMetrics::windSpeedMax));
	}

	/** 逐欄位忽略null後取平均；該欄位在所有城市都是null時，結果也是null（不假裝有資料）。 */
	private Double avg(List<DailyWeatherMetrics> list, Function<DailyWeatherMetrics, Double> getter) {
		List<Double> values = list.stream().map(getter).filter(Objects::nonNull).toList();
		if (values.isEmpty()) {
			return null;
		}
		return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
	}
}
