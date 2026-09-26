package com.example.Product_Selection_260813.service.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.example.Product_Selection_260813.dto.response.WeatherSyncResponse;
import com.example.Product_Selection_260813.repository.DailyWeatherRecordRepository;
import com.example.Product_Selection_260813.service.campaign.FestiveCampaignsChangedEvent;

/** V26 每日天氣資料同步：冷啟動補齊 30 天、平常只補 2 天；整區失敗時跳過、不觸發重算。 */
@ExtendWith(MockitoExtension.class)
class WeatherDataSyncServiceTest {

	@Mock
	private WeatherClient weatherClient;
	@Mock
	private DailyWeatherRecordRepository dailyWeatherRecordRepository;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private WeatherDataSyncService syncService;

	private static Map<LocalDate, DailyWeatherMetrics> oneDay() {
		LocalDate date = LocalDate.of(2026, 9, 25);
		return Map.of(date, new DailyWeatherMetrics(date, 34.0, 27.0, 70.0, 0.0, 10.0, 12.0));
	}

	@Test
	void 歷史天數不足門檻時以冷啟動補齊30天() {
		when(dailyWeatherRecordRepository.countHistoryDays(anyString(), any(), any())).thenReturn(3L);
		when(weatherClient.fetchDaily(anyDouble(), anyDouble(), eq(30), eq(14))).thenReturn(oneDay());

		WeatherSyncResponse result = syncService.syncWeatherData();

		assertThat(result.coldStartRegions()).hasSize(WeatherRegionConfig.REGION_CITIES.size());
		assertThat(result.failedRegions()).isEmpty();
		verify(weatherClient, never()).fetchDaily(anyDouble(), anyDouble(), eq(2), anyInt());
		verify(eventPublisher).publishEvent(any(FestiveCampaignsChangedEvent.class));
	}

	@Test
	void 歷史天數足夠時只補最近兩天() {
		when(dailyWeatherRecordRepository.countHistoryDays(anyString(), any(), any())).thenReturn(30L);
		when(weatherClient.fetchDaily(anyDouble(), anyDouble(), eq(2), eq(14))).thenReturn(oneDay());

		WeatherSyncResponse result = syncService.syncWeatherData();

		assertThat(result.coldStartRegions()).isEmpty();
		assertThat(result.syncedRegions()).hasSize(WeatherRegionConfig.REGION_CITIES.size());
	}

	@Test
	void 所有代表城市都失敗時跳過該區且不觸發重算() {
		when(dailyWeatherRecordRepository.countHistoryDays(anyString(), any(), any())).thenReturn(30L);
		when(weatherClient.fetchDaily(anyDouble(), anyDouble(), anyInt(), anyInt()))
				.thenThrow(new IllegalStateException("連線逾時"));

		WeatherSyncResponse result = syncService.syncWeatherData();

		assertThat(result.syncedRegions()).isEmpty();
		assertThat(result.failedRegions()).hasSize(WeatherRegionConfig.REGION_CITIES.size());
		verify(eventPublisher, never()).publishEvent(any());
		verify(dailyWeatherRecordRepository, never()).saveAll(any());
	}

	@Test
	void 同區多城市同一天逐欄位平均_缺值不假裝有資料() {
		LocalDate date = LocalDate.of(2026, 9, 25);
		Map<LocalDate, DailyWeatherMetrics> averaged = WeatherDataSyncService.averageByDate(List.of(
				new DailyWeatherMetrics(date, 30.0, 20.0, null, 10.0, null, 5.0),
				new DailyWeatherMetrics(date, 34.0, 24.0, null, 20.0, null, 15.0)));

		DailyWeatherMetrics result = averaged.get(date);
		assertThat(result.apparentTemperatureMax()).isEqualTo(32.0);
		assertThat(result.precipitationSum()).isEqualTo(15.0);
		assertThat(result.humidityMean()).isNull();
	}
}
