package com.example.Product_Selection_260813.service.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.example.Product_Selection_260813.dto.request.WeatherBoostSettingsUpdateRequest;
import com.example.Product_Selection_260813.entity.WeatherBoostSetting;
import com.example.Product_Selection_260813.enums.WeatherSignalType;
import com.example.Product_Selection_260813.json.WeatherBoostSnapshot;
import com.example.Product_Selection_260813.repository.DailyWeatherRecordRepository;
import com.example.Product_Selection_260813.repository.RegionWeightRepository;
import com.example.Product_Selection_260813.repository.WeatherBoostSettingRepository;
import com.example.Product_Selection_260813.repository.WeatherSignalTagMappingRepository;
import com.example.Product_Selection_260813.service.campaign.FestiveCampaignsChangedEvent;

/**
 * V26 天氣加成：逐日命中取最高權重、窗口平均（缺資料的日子不計）、四區依占比加權、
 * 歷史／預測依比重混合、再換算成加成分數。evaluate() 是純函式，直接以 Context 驗證。
 */
@ExtendWith(MockitoExtension.class)
class WeatherBoostServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);
	private static final Map<WeatherSignalType, Map<String, BigDecimal>> MAPPINGS = Map.of(
			WeatherSignalType.HOT, Map.of("涼感", new BigDecimal("1.0"), "飲料", new BigDecimal("0.6")),
			WeatherSignalType.HUMID, Map.of("除濕", new BigDecimal("0.6"), "涼感", new BigDecimal("0.3")));

	@Mock
	private DailyWeatherRecordRepository dailyWeatherRecordRepository;
	@Mock
	private WeatherSignalTagMappingRepository tagMappingRepository;
	@Mock
	private RegionWeightRepository regionWeightRepository;
	@Mock
	private WeatherBoostSettingRepository settingRepository;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private WeatherBoostService weatherBoostService;

	private static WeatherBoostService.Context context(Map<String, Map<LocalDate, Set<WeatherSignalType>>> daily,
			Map<String, BigDecimal> regionWeights) {
		return new WeatherBoostService.Context(TODAY, TODAY.minusDays(30), TODAY.plusDays(13), daily, MAPPINGS,
				regionWeights, new BigDecimal("60"), new BigDecimal("40"), new BigDecimal("5"), null);
	}

	private static Map<LocalDate, Set<WeatherSignalType>> days(Object... dateAndTypes) {
		java.util.HashMap<LocalDate, Set<WeatherSignalType>> map = new java.util.HashMap<>();
		for (int i = 0; i < dateAndTypes.length; i += 2) {
			@SuppressWarnings("unchecked")
			Set<WeatherSignalType> types = (Set<WeatherSignalType>) dateAndTypes[i + 1];
			map.put((LocalDate) dateAndTypes[i], types);
		}
		return map;
	}

	@Test
	void 沒有任何天氣資料時加成為零_分數為null() {
		WeatherBoostSnapshot result = WeatherBoostService.evaluate(context(Map.of(), Map.of()), List.of("涼感"));

		assertThat(result.getHistoryScore()).isNull();
		assertThat(result.getForecastScore()).isNull();
		assertThat(result.getCombinedScore()).isNull();
		assertThat(result.getWeatherBoost()).isEqualByComparingTo("0");
	}

	@Test
	void 歷史與預測依比重混合後換算加成() {
		// 北區：昨天 HOT（歷史 100 分），今天沒有訊號（預測 0 分）→ 100×60% ＋ 0×40% = 60 → 60/100×5 = 3.00
		Map<String, Map<LocalDate, Set<WeatherSignalType>>> daily = Map.of("NORTH",
				days(TODAY.minusDays(1), Set.of(WeatherSignalType.HOT), TODAY, Set.of()));
		WeatherBoostSnapshot result = WeatherBoostService.evaluate(context(daily, Map.of("NORTH", new BigDecimal("25"))),
				List.of("涼感"));

		assertThat(result.getHistoryScore()).isEqualByComparingTo("100");
		assertThat(result.getForecastScore()).isEqualByComparingTo("0");
		assertThat(result.getCombinedScore()).isEqualByComparingTo("60");
		assertThat(result.getWeatherBoost()).isEqualByComparingTo("3.00");
		assertThat(result.getMatchedTags()).containsExactly("涼感");
		assertThat(result.getHistoryDays()).isEqualTo(1);
		assertThat(result.getForecastDays()).isEqualTo(1);
	}

	@Test
	void 缺資料的日子不計入分母() {
		// 30 天歷史窗口只有 2 天有資料，其中 1 天命中 → 50 分（不是 1/30）
		Map<String, Map<LocalDate, Set<WeatherSignalType>>> daily = Map.of("NORTH",
				days(TODAY.minusDays(1), Set.of(WeatherSignalType.HOT), TODAY.minusDays(2), Set.of()));
		WeatherBoostSnapshot result = WeatherBoostService.evaluate(context(daily, Map.of("NORTH", new BigDecimal("25"))),
				List.of("涼感"));

		assertThat(result.getHistoryScore()).isEqualByComparingTo("50");
		// 沒有預報資料：直接採用歷史分
		assertThat(result.getCombinedScore()).isEqualByComparingTo("50");
		assertThat(result.getWeatherBoost()).isEqualByComparingTo("2.50");
	}

	@Test
	void 四區依業務占比加權_沒有資料的區不計入() {
		// 北區 100 分（占比 30）、南區 0 分（占比 10）、東區無資料（占比 60，不計入）→ (100×30 + 0×10) / 40 = 75
		Map<String, Map<LocalDate, Set<WeatherSignalType>>> daily = Map.of(
				"NORTH", days(TODAY.minusDays(1), Set.of(WeatherSignalType.HOT)),
				"SOUTH", days(TODAY.minusDays(1), Set.of()));
		WeatherBoostSnapshot result = WeatherBoostService.evaluate(
				context(daily, Map.of("NORTH", new BigDecimal("30"), "SOUTH", new BigDecimal("10"), "EAST",
						new BigDecimal("60"))),
				List.of("涼感"));

		assertThat(result.getHistoryScore()).isEqualByComparingTo("75");
	}

	@Test
	void 同一天多個訊號只取最高權重_不疊加() {
		// HOT→涼感 1.0、HUMID→涼感 0.3：同一天取 1.0，不是 1.3
		Map<String, Map<LocalDate, Set<WeatherSignalType>>> daily = Map.of("NORTH",
				days(TODAY.minusDays(1), Set.of(WeatherSignalType.HOT, WeatherSignalType.HUMID)));
		WeatherBoostSnapshot result = WeatherBoostService.evaluate(context(daily, Map.of("NORTH", new BigDecimal("25"))),
				List.of("涼感", "除濕"));

		assertThat(result.getHistoryScore()).isEqualByComparingTo("100");
		assertThat(result.getMatchedTags()).containsExactly("涼感", "除濕");
	}

	@Test
	void 商品沒有對應標籤時加成為零() {
		Map<String, Map<LocalDate, Set<WeatherSignalType>>> daily = Map.of("NORTH",
				days(TODAY.minusDays(1), Set.of(WeatherSignalType.HOT)));
		WeatherBoostSnapshot result = WeatherBoostService.evaluate(context(daily, Map.of("NORTH", new BigDecimal("25"))),
				List.of("文具"));

		assertThat(result.getHistoryScore()).isEqualByComparingTo("0");
		assertThat(result.getWeatherBoost()).isEqualByComparingTo("0");
		assertThat(result.getMatchedTags()).isEmpty();
	}

	@Test
	void 只有一邊有分數時直接採用() {
		assertThat(WeatherBoostService.combine(null, new BigDecimal("40"), new BigDecimal("60"), new BigDecimal("40")))
				.isEqualByComparingTo("40");
		assertThat(WeatherBoostService.combine(null, null, new BigDecimal("60"), new BigDecimal("40"))).isNull();
	}

	private static WeatherBoostSettingsUpdateRequest request(String history, String forecast, String cap) {
		WeatherBoostSettingsUpdateRequest request = new WeatherBoostSettingsUpdateRequest();
		request.setHistoryWeightPercentage(new BigDecimal(history));
		request.setForecastWeightPercentage(new BigDecimal(forecast));
		request.setBoostCap(new BigDecimal(cap));
		return request;
	}

	@Test
	void 更新設定_比重加總必須為100且上限不超過10() {
		assertThatThrownBy(() -> weatherBoostService.updateSettings(request("60", "30", "5"), 1L))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> weatherBoostService.updateSettings(request("60", "40", "11"), 1L))
				.isInstanceOf(IllegalArgumentException.class);
		verify(settingRepository, never()).saveAndFlush(any());
	}

	@Test
	void 更新設定成功後觸發加成重算() {
		WeatherBoostSetting setting = new WeatherBoostSetting();
		setting.setId(WeatherBoostSetting.SINGLETON_ID);
		when(settingRepository.findById(WeatherBoostSetting.SINGLETON_ID)).thenReturn(Optional.of(setting));
		when(settingRepository.saveAndFlush(setting)).thenReturn(setting);

		weatherBoostService.updateSettings(request("70", "30", "4"), 1L);

		assertThat(setting.getHistoryWeightPercentage()).isEqualByComparingTo("70");
		assertThat(setting.getBoostCap()).isEqualByComparingTo("4");
		verify(eventPublisher).publishEvent(any(FestiveCampaignsChangedEvent.class));
	}
}
