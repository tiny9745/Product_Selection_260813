package com.example.Product_Selection_260813.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 四區每日氣象數值（V26，daily_weather_metrics）：天氣加成的資料來源。
 *
 * 同一區同一天只有一列（UNIQUE region+weather_date），WeatherDataSyncService 每次同步 upsert。
 * 早於今天的列是「歷史」、今天起是「預報」——同一張表，依日期區分，不另存旗標
 * （日期一過，昨天的預報自然變成歷史，下一次同步再以實際值覆蓋）。
 *
 * 欄位形狀沿用 service.weather.DailyWeatherMetrics record（WeatherNormalizer 的輸入），
 * 以 toMetrics() 轉換，不另外設計一套分類輸入。
 */
@Entity
@Table(name = "daily_weather_metrics")
public class DailyWeatherRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "region", nullable = false, length = 10)
	private String region;

	@Column(name = "weather_date", nullable = false)
	private LocalDate weatherDate;

	@Column(name = "apparent_temperature_max", precision = 5, scale = 2)
	private BigDecimal apparentTemperatureMax;

	@Column(name = "apparent_temperature_min", precision = 5, scale = 2)
	private BigDecimal apparentTemperatureMin;

	@Column(name = "humidity_mean", precision = 5, scale = 2)
	private BigDecimal humidityMean;

	@Column(name = "precipitation_sum", precision = 6, scale = 2)
	private BigDecimal precipitationSum;

	@Column(name = "precipitation_probability_max", precision = 5, scale = 2)
	private BigDecimal precipitationProbabilityMax;

	@Column(name = "wind_speed_max", precision = 5, scale = 2)
	private BigDecimal windSpeedMax;

	/** 最近一次從 Open-Meteo 取得此列的時間；由同步服務明確指定（同一次同步的列相同）。 */
	@Column(name = "fetched_at", nullable = false)
	private LocalDateTime fetchedAt;

	public Long getId() {
		return id;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public LocalDate getWeatherDate() {
		return weatherDate;
	}

	public void setWeatherDate(LocalDate weatherDate) {
		this.weatherDate = weatherDate;
	}

	public LocalDateTime getFetchedAt() {
		return fetchedAt;
	}

	/** 以一次 API 取得的數值整列覆蓋（缺值就存 null，不保留舊值——以最新一次回應為準）。 */
	public void applyMetrics(Double temperatureMax, Double temperatureMin, Double humidity, Double precipitation,
			Double precipitationProbability, Double windSpeed, LocalDateTime fetchedAt) {
		this.apparentTemperatureMax = toDecimal(temperatureMax);
		this.apparentTemperatureMin = toDecimal(temperatureMin);
		this.humidityMean = toDecimal(humidity);
		this.precipitationSum = toDecimal(precipitation);
		this.precipitationProbabilityMax = toDecimal(precipitationProbability);
		this.windSpeedMax = toDecimal(windSpeed);
		this.fetchedAt = fetchedAt;
	}

	public BigDecimal getApparentTemperatureMax() {
		return apparentTemperatureMax;
	}

	public BigDecimal getApparentTemperatureMin() {
		return apparentTemperatureMin;
	}

	public BigDecimal getHumidityMean() {
		return humidityMean;
	}

	public BigDecimal getPrecipitationSum() {
		return precipitationSum;
	}

	public BigDecimal getPrecipitationProbabilityMax() {
		return precipitationProbabilityMax;
	}

	public BigDecimal getWindSpeedMax() {
		return windSpeedMax;
	}

	private static BigDecimal toDecimal(Double value) {
		return value == null ? null : BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP);
	}
}
