package com.example.Product_Selection_260813.service.weather;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.constants.BusinessTimeZone;
import com.example.Product_Selection_260813.dto.response.WeatherDataStatusResponse;
import com.example.Product_Selection_260813.dto.response.WeatherSyncResponse;
import com.example.Product_Selection_260813.entity.DailyWeatherRecord;
import com.example.Product_Selection_260813.repository.DailyWeatherRecordRepository;
import com.example.Product_Selection_260813.service.campaign.FestiveCampaignsChangedEvent;

/**
 * 每日天氣資料同步（V26，取代原本的 WeatherCampaignSyncService）。
 *
 * 天氣不再產生檔期，只把四區每日氣象數值 upsert 進 daily_weather_metrics，
 * 由 WeatherBoostService 依這些資料計算天氣加成；同步完成後發布加成重算事件。
 *
 * <b>抓取範圍（規格 1.2 A）：</b>
 * <ul>
 * <li>冷啟動：某區在過去 historyDays 天內累積的歷史天數少於門檻（預設 27 天，保留緩衝）時，
 * 用 past_days=historyDays 一次補齊。</li>
 * <li>平常：past_days=2（昨天與前天，涵蓋排程偶爾漏跑一天）＋完整預報天數。</li>
 * </ul>
 * 四區各自判斷冷啟動，一區補資料不會拖累其他區只抓少量。
 *
 * <b>容錯：</b>沿用原本的策略——單一代表城市失敗只記警告並排除，改用同區其他城市平均；
 * 整區所有城市都失敗時跳過該區，既有資料保留，下次排程自然重試。
 */
@Service
public class WeatherDataSyncService {

	private static final Logger log = LoggerFactory.getLogger(WeatherDataSyncService.class);
	/** 平常每日同步回補的過去天數。 */
	private static final int ROUTINE_PAST_DAYS = 2;
	/** 資料保留期限：超過「歷史窗口＋此緩衝天數」的舊列在同步時清除。 */
	private static final int RETENTION_BUFFER_DAYS = 30;

	private final WeatherClient weatherClient;
	private final DailyWeatherRecordRepository dailyWeatherRecordRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Value("${weather.history-days:30}")
	private int historyDays = 30;

	@Value("${weather.forecast-days:14}")
	private int forecastDays = 14;

	/** 歷史天數少於此值視為冷啟動（規格範例值 27，可設定）。 */
	@Value("${weather.history-cold-start-threshold-days:27}")
	private int coldStartThresholdDays = 27;

	public WeatherDataSyncService(WeatherClient weatherClient, DailyWeatherRecordRepository dailyWeatherRecordRepository,
			ApplicationEventPublisher eventPublisher) {
		this.weatherClient = weatherClient;
		this.dailyWeatherRecordRepository = dailyWeatherRecordRepository;
		this.eventPublisher = eventPublisher;
	}

	/**
	 * 每天 05:00（台灣時間）同步；POST /api/settings/weather/sync 手動觸發走同一個方法。
	 * 同步完成後發布 FestiveCampaignsChangedEvent（加成重算事件，天氣與節慶加成一起重算）。
	 */
	@Scheduled(cron = "0 0 5 * * *", zone = "Asia/Taipei")
	@Transactional
	public WeatherSyncResponse syncWeatherData() {
		LocalDate today = LocalDate.now(BusinessTimeZone.TAIPEI);
		LocalDateTime now = LocalDateTime.now(BusinessTimeZone.TAIPEI);
		List<String> syncedRegions = new ArrayList<>();
		List<String> failedRegions = new ArrayList<>();
		List<String> coldStartRegions = new ArrayList<>();
		int upsertedDays = 0;

		for (Map.Entry<String, List<WeatherRegionConfig.City>> entry : WeatherRegionConfig.REGION_CITIES.entrySet()) {
			String region = entry.getKey();
			boolean coldStart = dailyWeatherRecordRepository.countHistoryDays(region, today.minusDays(historyDays),
					today) < coldStartThresholdDays;
			int pastDays = coldStart ? historyDays : ROUTINE_PAST_DAYS;

			List<DailyWeatherMetrics> perCityDays = fetchRegionCities(region, entry.getValue(), pastDays);
			if (perCityDays.isEmpty()) {
				log.warn("天氣區域{}的所有代表城市都取得失敗，本次跳過此區域，既有資料保留，等下次排程重試", region);
				failedRegions.add(region);
				continue;
			}
			upsertedDays += upsert(region, averageByDate(perCityDays), now);
			syncedRegions.add(region);
			if (coldStart) {
				coldStartRegions.add(region);
			}
		}

		int purged = dailyWeatherRecordRepository.deleteOlderThan(today.minusDays(historyDays + RETENTION_BUFFER_DAYS));
		if (!syncedRegions.isEmpty()) {
			eventPublisher.publishEvent(new FestiveCampaignsChangedEvent("天氣資料同步"));
		}
		log.info("天氣資料同步完成：成功 {}，失敗 {}，冷啟動 {}，寫入 {} 天，清除過期 {} 列",
				syncedRegions, failedRegions, coldStartRegions, upsertedDays, purged);
		return new WeatherSyncResponse(syncedRegions, failedRegions, coldStartRegions, upsertedDays, now);
	}

	/** GET /api/settings/weather/status：各區資料涵蓋天數與最近同步時間（畫面「資料更新時間」）。 */
	@Transactional(readOnly = true)
	public WeatherDataStatusResponse getStatus() {
		LocalDate today = LocalDate.now(BusinessTimeZone.TAIPEI);
		LocalDate historyFrom = today.minusDays(historyDays);
		LocalDate forecastTo = today.plusDays(forecastDays - 1L);
		Map<String, List<DailyWeatherRecord>> byRegion = dailyWeatherRecordRepository
				.findByWeatherDateBetween(historyFrom, forecastTo).stream()
				.collect(Collectors.groupingBy(DailyWeatherRecord::getRegion));

		List<WeatherDataStatusResponse.RegionStatus> regions = new ArrayList<>();
		LocalDateTime latest = null;
		for (String region : WeatherRegionConfig.REGION_CITIES.keySet().stream().sorted().toList()) {
			List<DailyWeatherRecord> records = byRegion.getOrDefault(region, List.of());
			int historyCount = (int) records.stream().filter(r -> r.getWeatherDate().isBefore(today)).count();
			int forecastCount = records.size() - historyCount;
			LocalDateTime fetchedAt = records.stream().map(DailyWeatherRecord::getFetchedAt)
					.max(LocalDateTime::compareTo).orElse(null);
			if (fetchedAt != null && (latest == null || fetchedAt.isAfter(latest))) {
				latest = fetchedAt;
			}
			regions.add(new WeatherDataStatusResponse.RegionStatus(region, WeatherRegionConfig.regionLabel(region),
					historyCount, forecastCount, fetchedAt));
		}
		return new WeatherDataStatusResponse(historyDays, forecastDays, coldStartThresholdDays, latest, regions);
	}

	private int upsert(String region, Map<LocalDate, DailyWeatherMetrics> byDate, LocalDateTime fetchedAt) {
		if (byDate.isEmpty()) {
			return 0;
		}
		LocalDate from = byDate.keySet().stream().min(LocalDate::compareTo).orElseThrow();
		LocalDate to = byDate.keySet().stream().max(LocalDate::compareTo).orElseThrow();
		Map<LocalDate, DailyWeatherRecord> existing = dailyWeatherRecordRepository
				.findByRegionAndWeatherDateBetween(region, from, to).stream()
				.collect(Collectors.toMap(DailyWeatherRecord::getWeatherDate, Function.identity()));

		List<DailyWeatherRecord> toSave = new ArrayList<>();
		for (DailyWeatherMetrics metrics : byDate.values()) {
			DailyWeatherRecord record = existing.get(metrics.date());
			if (record == null) {
				record = new DailyWeatherRecord();
				record.setRegion(region);
				record.setWeatherDate(metrics.date());
			}
			record.applyMetrics(metrics.apparentTemperatureMax(), metrics.apparentTemperatureMin(),
					metrics.humidityMean(), metrics.precipitationSum(), metrics.precipitationProbabilityMax(),
					metrics.windSpeedMax(), fetchedAt);
			toSave.add(record);
		}
		dailyWeatherRecordRepository.saveAll(toSave);
		return toSave.size();
	}

	/** 依序呼叫某區域所有代表城市；單一城市失敗只記錄警告並跳過，不中斷其他城市。 */
	private List<DailyWeatherMetrics> fetchRegionCities(String region, List<WeatherRegionConfig.City> cities,
			int pastDays) {
		List<DailyWeatherMetrics> result = new ArrayList<>();
		for (WeatherRegionConfig.City city : cities) {
			try {
				result.addAll(weatherClient.fetchDaily(city.latitude(), city.longitude(), pastDays, forecastDays)
						.values());
			} catch (Exception e) {
				log.warn("取得天氣區域{}代表城市{}的資料失敗，排除此城市改用其餘代表城市的平均值", region, city.name(), e);
			}
		}
		return result;
	}

	/** 同區域多個城市、同一天的數值逐欄位平均（邏輯沿用原 OpenMeteoWeatherSignalProvider）。 */
	static Map<LocalDate, DailyWeatherMetrics> averageByDate(Collection<DailyWeatherMetrics> allCityDays) {
		Map<LocalDate, List<DailyWeatherMetrics>> byDate = new TreeMap<>();
		for (DailyWeatherMetrics metrics : allCityDays) {
			byDate.computeIfAbsent(metrics.date(), d -> new ArrayList<>()).add(metrics);
		}
		Map<LocalDate, DailyWeatherMetrics> result = new LinkedHashMap<>();
		byDate.forEach((date, list) -> result.put(date, new DailyWeatherMetrics(date,
				avg(list, DailyWeatherMetrics::apparentTemperatureMax),
				avg(list, DailyWeatherMetrics::apparentTemperatureMin),
				avg(list, DailyWeatherMetrics::humidityMean),
				avg(list, DailyWeatherMetrics::precipitationSum),
				avg(list, DailyWeatherMetrics::precipitationProbabilityMax),
				avg(list, DailyWeatherMetrics::windSpeedMax))));
		return result;
	}

	/** 逐欄位忽略 null 後取平均；該欄位在所有城市都是 null 時，結果也是 null（不假裝有資料）。 */
	private static Double avg(List<DailyWeatherMetrics> list, Function<DailyWeatherMetrics, Double> getter) {
		List<Double> values = list.stream().map(getter).filter(Objects::nonNull).toList();
		if (values.isEmpty()) {
			return null;
		}
		return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
	}
}
