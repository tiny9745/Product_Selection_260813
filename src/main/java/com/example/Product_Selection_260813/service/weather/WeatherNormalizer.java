package com.example.Product_Selection_260813.service.weather;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.example.Product_Selection_260813.dto.weather.WeatherSignal;
import com.example.Product_Selection_260813.enums.WeatherForecastConfidence;
import com.example.Product_Selection_260813.enums.WeatherSignalType;

/**
 * 把（已跨代表城市平均過的）每日氣象數值，分類成WeatherSignalType，再把
 * 連續天數分組成WeatherSignal窗口——對應規劃文件第23節分層裡的
 * 「Normalizer」這一層。
 *
 * 刻意寫成純靜態方法、不注入任何Spring Bean：分類門檻與分組演算法是這支
 * 服務裡「業務規則最集中」的地方，比照ScoringAlgorithms（見該類別的
 * WeatherNormalizerTest之於本類別，等同ScoringAlgorithmsTest之於
 * ScoringAlgorithms）的既有慣例，讓這部分不依賴Spring context就能單元
 * 測試，之後門檻調整時能直接跑測試驗證行為有沒有跑掉。
 *
 * <b>同一天可以同時符合多種訊號類型：</b>溫濕度（HOT／HUMID_HOT／HUMID／
 * COLD／COOL／DRY_COOL，互斥，一天最多一種）、降雨（RAINY／HEAVY_RAIN，
 * 互斥）、強風（STRONG_WIND）是三條獨立判斷軸線，例如「濕冷又颳大風」的
 * 一天會同時產生COLD與STRONG_WIND兩個訊號——這是刻意設計，不是漏寫if/else：
 * WeatherCampaignSyncService.buildCampaignCode()把type included進campaign
 * code，同一天的不同類型本來就會變成不同的festive_campaigns記錄，不衝突。
 */
final class WeatherNormalizer {

	private WeatherNormalizer() {
	}

	// ====================== 分類門檻 ======================
	// 以下皆為初版預設值，不是中央氣象署的正式特報標準（例如高溫燈號、
	// 大雨/豪雨特報門檻都是官方安全性警示，跟本服務「觸發商品標籤」的
	// 商業判斷目的不同，門檻可以也應該不一樣）。若規劃文件第11節有明確
	// 數字，請直接改這裡的常數，不用動下面的分類邏輯。

	/** 日最高體感溫度（°C）達此值 → HOT。 */
	private static final double HOT_THRESHOLD = 35.0;
	/** 日最高體感溫度（°C）達此值，且同時濕度達HUMID_HOT_HUMIDITY_THRESHOLD → HUMID_HOT（優先於單純HOT判斷）。 */
	private static final double HUMID_HOT_TEMP_THRESHOLD = 33.0;
	private static final double HUMID_HOT_HUMIDITY_THRESHOLD = 75.0;
	/** 溫度未達悶熱門檻，但平均濕度（%）達此值 → HUMID。 */
	private static final double HUMID_HUMIDITY_THRESHOLD = 80.0;
	/** 日均體感溫度（°C，取最高/最低平均）低於等於此值 → COLD。 */
	private static final double COLD_THRESHOLD = 14.0;
	/** 日均體感溫度（°C）低於等於此值（且高於COLD門檻） → COOL或DRY_COOL。 */
	private static final double COOL_THRESHOLD = 19.0;
	/** 落在COOL區間內，平均濕度（%）低於此值 → DRY_COOL，否則COOL。 */
	private static final double DRY_COOL_HUMIDITY_THRESHOLD = 60.0;
	/** 降雨機率（%）達此值，且日累積雨量（mm）達HEAVY_RAIN_AMOUNT_THRESHOLD → HEAVY_RAIN。 */
	private static final double HEAVY_RAIN_PROBABILITY_THRESHOLD = 70.0;
	private static final double HEAVY_RAIN_AMOUNT_THRESHOLD = 40.0;
	/** 降雨機率（%）達此值，且日累積雨量（mm）達RAINY_AMOUNT_THRESHOLD（未達HEAVY_RAIN） → RAINY。 */
	private static final double RAINY_PROBABILITY_THRESHOLD = 60.0;
	private static final double RAINY_AMOUNT_THRESHOLD = 5.0;
	/** 日最大陣風/風速（km/h）達此值 → STRONG_WIND。 */
	private static final double STRONG_WIND_THRESHOLD = 50.0;

	/**
	 * 依單日（已跨城市平均）氣象數值，判斷當天符合哪些天氣訊號類型。
	 * 某個判斷軸線缺資料（例如濕度為null）時，該軸線直接不產生訊號，
	 * 不用其他欄位去猜——寧可漏判，也不要用不完整的資料誤判。
	 *
	 * @return 當天符合的訊號類型；完全不符合任何門檻（含NORMAL情境）時回傳
	 *         空集合，呼叫端不需要特別處理NORMAL——NORMAL本來就不在
	 *         WeatherCampaignSyncService.WEATHER_TAG_MAPPING裡，回傳空集合
	 *         等同該類別註解說的「一般天氣不該命中任何商品」。
	 */
	static Set<WeatherSignalType> classifyDay(DailyWeatherMetrics metrics) {
		Set<WeatherSignalType> types = new LinkedHashSet<>();
		classifyTemperatureHumidity(metrics).ifPresent(types::add);
		classifyRain(metrics).ifPresent(types::add);
		if (metrics.windSpeedMax() != null && metrics.windSpeedMax() >= STRONG_WIND_THRESHOLD) {
			types.add(WeatherSignalType.STRONG_WIND);
		}
		return types;
	}

	private static Optional<WeatherSignalType> classifyTemperatureHumidity(DailyWeatherMetrics m) {
		Double tempMax = m.apparentTemperatureMax();
		Double tempMean = averageOf(m.apparentTemperatureMax(), m.apparentTemperatureMin());
		Double humidity = m.humidityMean();

		if (tempMax == null) {
			return Optional.empty();
		}
		if (tempMax >= HOT_THRESHOLD) {
			return Optional.of(WeatherSignalType.HOT);
		}
		if (tempMax >= HUMID_HOT_TEMP_THRESHOLD && humidity != null && humidity >= HUMID_HOT_HUMIDITY_THRESHOLD) {
			return Optional.of(WeatherSignalType.HUMID_HOT);
		}
		if (humidity != null && humidity >= HUMID_HUMIDITY_THRESHOLD) {
			return Optional.of(WeatherSignalType.HUMID);
		}
		if (tempMean != null && tempMean <= COLD_THRESHOLD) {
			return Optional.of(WeatherSignalType.COLD);
		}
		if (tempMean != null && tempMean <= COOL_THRESHOLD) {
			boolean dry = humidity != null && humidity < DRY_COOL_HUMIDITY_THRESHOLD;
			return Optional.of(dry ? WeatherSignalType.DRY_COOL : WeatherSignalType.COOL);
		}
		return Optional.empty();
	}

	private static Optional<WeatherSignalType> classifyRain(DailyWeatherMetrics m) {
		Double probability = m.precipitationProbabilityMax();
		Double amount = m.precipitationSum();
		if (probability == null || amount == null) {
			return Optional.empty();
		}
		if (probability >= HEAVY_RAIN_PROBABILITY_THRESHOLD && amount >= HEAVY_RAIN_AMOUNT_THRESHOLD) {
			return Optional.of(WeatherSignalType.HEAVY_RAIN);
		}
		if (probability >= RAINY_PROBABILITY_THRESHOLD && amount >= RAINY_AMOUNT_THRESHOLD) {
			return Optional.of(WeatherSignalType.RAINY);
		}
		return Optional.empty();
	}

	private static Double averageOf(Double a, Double b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		return (a + b) / 2.0;
	}

	/**
	 * 把「每日符合哪些類型」的結果，依類型分組成連續天數的窗口（WeatherSignal），
	 * 對應WeatherSignal.windowStart/windowEnd的語意：「這個訊號預期發生的
	 * 時間窗口起訖日」，不是逐日各開一筆——例如某區域連續5天都符合RAINY，
	 * 會組成1筆windowStart~windowEnd橫跨5天的WeatherSignal，而不是5筆
	 * 各1天的訊號（後者會讓WeatherCampaignSyncService.buildCampaignCode()
	 * 因為windowStart不同而每天都新開一筆festive_campaigns，不符合「檔期」
	 * 的語意）。
	 *
	 * @param dailyTypes 每日分類結果，key為日期，須已排序或呼叫端不關心順序
	 *                   （本方法內部會依日期排序後才分組）
	 * @param today      用來計算WeatherForecastConfidence的基準日，通常是
	 *                   LocalDate.now()；獨立傳入是為了讓測試可以固定基準日，
	 *                   不用依賴系統時鐘
	 */
	static List<WeatherSignal> buildSignals(String region, Map<LocalDate, Set<WeatherSignalType>> dailyTypes,
			LocalDate today) {
		List<WeatherSignal> signals = new ArrayList<>();
		for (WeatherSignalType type : WeatherSignalType.values()) {
			if (type == WeatherSignalType.NORMAL) {
				continue;
			}
			List<LocalDate> matchingDates = dailyTypes.entrySet().stream()
					.filter(entry -> entry.getValue().contains(type))
					.map(Map.Entry::getKey)
					.sorted()
					.toList();
			signals.addAll(groupIntoWindows(region, type, matchingDates, today));
		}
		return signals;
	}

	private static List<WeatherSignal> groupIntoWindows(String region, WeatherSignalType type,
			List<LocalDate> sortedDates, LocalDate today) {
		List<WeatherSignal> result = new ArrayList<>();
		LocalDate windowStart = null;
		LocalDate windowEnd = null;

		for (LocalDate date : sortedDates) {
			if (windowStart == null) {
				windowStart = date;
				windowEnd = date;
			} else if (date.equals(windowEnd.plusDays(1))) {
				windowEnd = date;
			} else {
				result.add(toSignal(region, type, windowStart, windowEnd, today));
				windowStart = date;
				windowEnd = date;
			}
		}
		if (windowStart != null) {
			result.add(toSignal(region, type, windowStart, windowEnd, today));
		}
		return result;
	}

	private static WeatherSignal toSignal(String region, WeatherSignalType type, LocalDate start, LocalDate end,
			LocalDate today) {
		WeatherSignal signal = new WeatherSignal();
		signal.setRegion(region);
		signal.setType(type);
		signal.setWindowStart(start);
		signal.setWindowEnd(end);
		signal.setConfidence(resolveConfidence(start, today));
		return signal;
	}

	/**
	 * 依窗口起始日距離今天的天數，決定信心層級（WeatherForecastConfidence
	 * enum本身刻意不寫死天數門檻，理由見該enum的Javadoc）。
	 *
	 * 0~7天＝HIGH、8~14天＝MEDIUM——這兩層是這次MVP範圍內唯一會用到的
	 * （OpenMeteoWeatherSignalProvider目前只跟Open-Meteo要0~14天的資料，
	 * 見weather.forecast-days設定），15天以上的LOW分支保留在這裡是因為
	 * enum本身已經定義了這個層級，日後若weather.forecast-days調高到
	 * Open-Meteo上限16天，第15、16天會自然落入這個分支，不需要再改
	 * 這支方法。
	 */
	static WeatherForecastConfidence resolveConfidence(LocalDate windowStart, LocalDate today) {
		long daysFromNow = ChronoUnit.DAYS.between(today, windowStart);
		if (daysFromNow <= 7) {
			return WeatherForecastConfidence.HIGH;
		}
		if (daysFromNow <= 14) {
			return WeatherForecastConfidence.MEDIUM;
		}
		return WeatherForecastConfidence.LOW;
	}
}
