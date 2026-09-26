package com.example.Product_Selection_260813.service.weather;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.example.Product_Selection_260813.enums.WeatherSignalType;

/**
 * WeatherNormalizer是天氣訊號分類的業務規則核心，這支測試把classifyDay()的邊界行為釘死
 * （V26 起窗口分組與預報可信度隨天氣檔期移除），
 * 避免之後調整門檻常數時，悄悄改變了分類或分組的結果（比照
 * ScoringAlgorithmsTest之於ScoringAlgorithms的既有慣例）。
 */
class WeatherNormalizerTest {

	private static DailyWeatherMetrics metrics(LocalDate date, Double tempMax, Double tempMin, Double humidity,
			Double precipSum, Double precipProb, Double windMax) {
		return new DailyWeatherMetrics(date, tempMax, tempMin, humidity, precipSum, precipProb, windMax);
	}

	@Nested
	class ClassifyDay {

		@Test
		void 體感溫度達35度以上判定為HOT() {
			Set<WeatherSignalType> types = WeatherNormalizer
					.classifyDay(metrics(LocalDate.now(), 35.0, 28.0, 50.0, null, null, null));
			assertThat(types).containsExactly(WeatherSignalType.HOT);
		}

		@Test
		void 未達HOT但溫度濕度都高判定為HUMID_HOT() {
			Set<WeatherSignalType> types = WeatherNormalizer
					.classifyDay(metrics(LocalDate.now(), 34.0, 28.0, 80.0, null, null, null));
			assertThat(types).containsExactly(WeatherSignalType.HUMID_HOT);
		}

		@Test
		void 溫度不極端但濕度達80以上判定為HUMID() {
			Set<WeatherSignalType> types = WeatherNormalizer
					.classifyDay(metrics(LocalDate.now(), 28.0, 24.0, 85.0, null, null, null));
			assertThat(types).containsExactly(WeatherSignalType.HUMID);
		}

		@Test
		void 日均體感溫度低於等於14度判定為COLD() {
			Set<WeatherSignalType> types = WeatherNormalizer
					.classifyDay(metrics(LocalDate.now(), 15.0, 11.0, 70.0, null, null, null));
			assertThat(types).containsExactly(WeatherSignalType.COLD);
		}

		@Test
		void 涼爽區間濕度低於60判定為DRY_COOL() {
			Set<WeatherSignalType> types = WeatherNormalizer
					.classifyDay(metrics(LocalDate.now(), 19.0, 16.0, 45.0, null, null, null));
			assertThat(types).containsExactly(WeatherSignalType.DRY_COOL);
		}

		@Test
		void 涼爽區間濕度達60以上判定為COOL() {
			Set<WeatherSignalType> types = WeatherNormalizer
					.classifyDay(metrics(LocalDate.now(), 19.0, 16.0, 70.0, null, null, null));
			assertThat(types).containsExactly(WeatherSignalType.COOL);
		}

		@Test
		void 溫度濕度都落在中性區間時不產生任何溫濕度訊號() {
			Set<WeatherSignalType> types = WeatherNormalizer
					.classifyDay(metrics(LocalDate.now(), 26.0, 22.0, 65.0, null, null, null));
			assertThat(types).isEmpty();
		}

		@Test
		void 缺最高溫資料時不猜測直接略過溫濕度分類() {
			Set<WeatherSignalType> types = WeatherNormalizer
					.classifyDay(metrics(LocalDate.now(), null, null, 90.0, null, null, null));
			assertThat(types).isEmpty();
		}

		@Test
		void 降雨機率與雨量都達門檻判定為HEAVY_RAIN() {
			Set<WeatherSignalType> types = WeatherNormalizer
					.classifyDay(metrics(LocalDate.now(), 26.0, 22.0, 65.0, 45.0, 75.0, null));
			assertThat(types).containsExactly(WeatherSignalType.HEAVY_RAIN);
		}

		@Test
		void 降雨機率雨量達一般門檻未達HEAVY_RAIN判定為RAINY() {
			Set<WeatherSignalType> types = WeatherNormalizer
					.classifyDay(metrics(LocalDate.now(), 26.0, 22.0, 65.0, 10.0, 65.0, null));
			assertThat(types).containsExactly(WeatherSignalType.RAINY);
		}

		@Test
		void 風速達50以上判定為STRONG_WIND_且可與COLD同時成立() {
			Set<WeatherSignalType> types = WeatherNormalizer
					.classifyDay(metrics(LocalDate.now(), 15.0, 11.0, 70.0, null, null,55.0));
			assertThat(types).containsExactlyInAnyOrder(WeatherSignalType.COLD, WeatherSignalType.STRONG_WIND);
		}
	}

	/** V26：歷史日（observed）的降雨判斷——過去日期常沒有降雨機率，只看實際雨量。 */
	@Nested
	class ObservedDay {

		@Test
		void 歷史日沒有降雨機率時只依雨量判定() {
			DailyWeatherMetrics rainy = metrics(LocalDate.now().minusDays(1), 26.0, 22.0, 65.0, 10.0, null, null);
			assertThat(WeatherNormalizer.classifyDay(rainy, true)).containsExactly(WeatherSignalType.RAINY);
			// 預報日沒有機率時維持原規則：不猜測，不產生降雨訊號。
			assertThat(WeatherNormalizer.classifyDay(rainy, false)).isEmpty();
		}

		@Test
		void 歷史日雨量達大雨門檻判定為HEAVY_RAIN() {
			DailyWeatherMetrics heavy = metrics(LocalDate.now().minusDays(1), 26.0, 22.0, 65.0, 45.0, null, null);
			assertThat(WeatherNormalizer.classifyDay(heavy, true)).containsExactly(WeatherSignalType.HEAVY_RAIN);
		}

		@Test
		void 歷史日若有降雨機率仍依原規則() {
			DailyWeatherMetrics lowProbability = metrics(LocalDate.now().minusDays(1), 26.0, 22.0, 65.0, 10.0, 30.0,
					null);
			assertThat(WeatherNormalizer.classifyDay(lowProbability, true)).isEmpty();
		}
	}
}
