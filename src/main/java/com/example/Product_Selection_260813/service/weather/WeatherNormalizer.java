package com.example.Product_Selection_260813.service.weather;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import com.example.Product_Selection_260813.enums.WeatherSignalType;

/**
 * 把（已跨代表城市平均過的）每日氣象數值，分類成WeatherSignalType——對應規劃文件
 * 第23節分層裡的「Normalizer」這一層。
 *
 * V26（2026-09-25）：天氣檔期移除後，原本「把連續天數分組成訊號窗口、推算預報可信度」
 * 的部分一併移除；分類結果改由 WeatherBoostService 逐日對照商品標籤、計算天氣加成。
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
 * 天氣加成逐日取「所有訊號對應標籤中的最高權重」，多個訊號同時成立不會衝突。
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
	 * 依單日（已跨城市平均）氣象數值，判斷當天符合哪些天氣訊號類型（預報日）。
	 * 某個判斷軸線缺資料（例如濕度為null）時，該軸線直接不產生訊號，
	 * 不用其他欄位去猜——寧可漏判，也不要用不完整的資料誤判。
	 *
	 * @return 當天符合的訊號類型；完全不符合任何門檻時回傳空集合（等同 NORMAL，
	 *         一般天氣不該命中任何商品）。
	 */
	static Set<WeatherSignalType> classifyDay(DailyWeatherMetrics metrics) {
		return classifyDay(metrics, false);
	}

	/**
	 * @param observed true＝已經過去的日期（歷史）。Open-Meteo 以 past_days 回傳的過去日期，
	 *                 降雨機率常為 null（雨已經下完，沒有「機率」可言），此時只看實際雨量判斷
	 *                 降雨訊號；預報日則維持「機率與雨量都達門檻」的原規則。
	 */
	static Set<WeatherSignalType> classifyDay(DailyWeatherMetrics metrics, boolean observed) {
		Set<WeatherSignalType> types = new LinkedHashSet<>();
		classifyTemperatureHumidity(metrics).ifPresent(types::add);
		classifyRain(metrics, observed).ifPresent(types::add);
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

	private static Optional<WeatherSignalType> classifyRain(DailyWeatherMetrics m, boolean observed) {
		Double amount = m.precipitationSum();
		// 歷史日期沒有降雨機率時視為 100%（雨量是實際觀測值），只依雨量判斷。
		Double probability = m.precipitationProbabilityMax() == null && observed ? Double.valueOf(100)
				: m.precipitationProbabilityMax();
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
}
