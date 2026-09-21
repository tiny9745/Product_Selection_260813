package com.example.Product_Selection_260813.service.weather;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.example.Product_Selection_260813.dto.weather.WeatherSignal;
import com.example.Product_Selection_260813.enums.WeatherForecastConfidence;
import com.example.Product_Selection_260813.enums.WeatherSignalType;

/**
 * WeatherNormalizer是天氣訊號分類與窗口分組的業務規則核心，這支測試把
 * classifyDay()／buildSignals()／resolveConfidence()的邊界行為釘死，
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

	@Nested
	class BuildSignals {

		@Test
		void 連續天數同類型組成單一窗口() {
			LocalDate today = LocalDate.of(2026, 9, 21);
			Map<LocalDate, Set<WeatherSignalType>> dailyTypes = new TreeMap<>();
			dailyTypes.put(today, Set.of(WeatherSignalType.RAINY));
			dailyTypes.put(today.plusDays(1), Set.of(WeatherSignalType.RAINY));
			dailyTypes.put(today.plusDays(2), Set.of(WeatherSignalType.RAINY));

			List<WeatherSignal> signals = WeatherNormalizer.buildSignals("SOUTH", dailyTypes, today);

			assertThat(signals).hasSize(1);
			WeatherSignal signal = signals.get(0);
			assertThat(signal.getRegion()).isEqualTo("SOUTH");
			assertThat(signal.getType()).isEqualTo(WeatherSignalType.RAINY);
			assertThat(signal.getWindowStart()).isEqualTo(today);
			assertThat(signal.getWindowEnd()).isEqualTo(today.plusDays(2));
		}

		@Test
		void 不連續天數同類型組成兩個窗口() {
			LocalDate today = LocalDate.of(2026, 9, 21);
			Map<LocalDate, Set<WeatherSignalType>> dailyTypes = new TreeMap<>();
			dailyTypes.put(today, Set.of(WeatherSignalType.HOT));
			dailyTypes.put(today.plusDays(1), Set.of(WeatherSignalType.HOT));
			dailyTypes.put(today.plusDays(5), Set.of(WeatherSignalType.HOT));

			List<WeatherSignal> signals = WeatherNormalizer.buildSignals("SOUTH", dailyTypes, today);

			assertThat(signals).hasSize(2);
			assertThat(signals).extracting(WeatherSignal::getWindowStart)
					.containsExactlyInAnyOrder(today, today.plusDays(5));
		}

		@Test
		void 空集合的日期不會產生任何訊號() {
			LocalDate today = LocalDate.of(2026, 9, 21);
			Map<LocalDate, Set<WeatherSignalType>> dailyTypes = new TreeMap<>();
			dailyTypes.put(today, Set.of());

			List<WeatherSignal> signals = WeatherNormalizer.buildSignals("SOUTH", dailyTypes, today);

			assertThat(signals).isEmpty();
		}
	}

	@Nested
	class ResolveConfidence {

		private final LocalDate today = LocalDate.of(2026, 9, 21);

		@Test
		void 窗口起始日就是今天判定為HIGH() {
			assertThat(WeatherNormalizer.resolveConfidence(today, today)).isEqualTo(WeatherForecastConfidence.HIGH);
		}

		@Test
		void 窗口起始日距今第7天仍判定為HIGH() {
			assertThat(WeatherNormalizer.resolveConfidence(today.plusDays(7), today))
					.isEqualTo(WeatherForecastConfidence.HIGH);
		}

		@Test
		void 窗口起始日距今第8天判定為MEDIUM() {
			assertThat(WeatherNormalizer.resolveConfidence(today.plusDays(8), today))
					.isEqualTo(WeatherForecastConfidence.MEDIUM);
		}

		@Test
		void 窗口起始日距今第14天仍判定為MEDIUM() {
			assertThat(WeatherNormalizer.resolveConfidence(today.plusDays(14), today))
					.isEqualTo(WeatherForecastConfidence.MEDIUM);
		}

		@Test
		void 窗口起始日距今第15天判定為LOW() {
			assertThat(WeatherNormalizer.resolveConfidence(today.plusDays(15), today))
					.isEqualTo(WeatherForecastConfidence.LOW);
		}
	}
}
