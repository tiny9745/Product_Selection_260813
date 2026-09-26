package com.example.Product_Selection_260813.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * PUT /api/settings/weather/boost-settings：天氣加成的歷史／預測比重與加成上限（V26）。
 * 「比重加總=100」屬於跨欄位規則，由 WeatherBoostService.updateSettings() 驗證（比照 region-weights）。
 */
public class WeatherBoostSettingsUpdateRequest {

	@NotNull(message = "歷史比重為必填")
	@DecimalMin(value = "0", message = "歷史比重不可為負數")
	@DecimalMax(value = "100", message = "歷史比重不可超過 100")
	private BigDecimal historyWeightPercentage;

	@NotNull(message = "預測比重為必填")
	@DecimalMin(value = "0", message = "預測比重不可為負數")
	@DecimalMax(value = "100", message = "預測比重不可超過 100")
	private BigDecimal forecastWeightPercentage;

	@NotNull(message = "加成上限為必填")
	@DecimalMin(value = "0", message = "加成上限不可為負數")
	@DecimalMax(value = "10", message = "加成上限不可超過 10 分")
	private BigDecimal boostCap;

	public BigDecimal getHistoryWeightPercentage() { return historyWeightPercentage; }
	public void setHistoryWeightPercentage(BigDecimal historyWeightPercentage) { this.historyWeightPercentage = historyWeightPercentage; }
	public BigDecimal getForecastWeightPercentage() { return forecastWeightPercentage; }
	public void setForecastWeightPercentage(BigDecimal forecastWeightPercentage) { this.forecastWeightPercentage = forecastWeightPercentage; }
	public BigDecimal getBoostCap() { return boostCap; }
	public void setBoostCap(BigDecimal boostCap) { this.boostCap = boostCap; }
}
