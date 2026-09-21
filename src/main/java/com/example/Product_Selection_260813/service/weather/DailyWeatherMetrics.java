package com.example.Product_Selection_260813.service.weather;

import java.time.LocalDate;

/**
 * WeatherClient 對外回傳的單日氣象數值（單一城市），已經是每日聚合值
 * （Open-Meteo daily 端點的輸出），還不是分類後的 WeatherSignalType——分類
 * 邏輯在 WeatherNormalizer，這裡只負責「跟外部API的原始JSON脫鉤」（規劃
 * 文件第23節 External API → Client → DTO → Normalizer → Internal Model
 * 分層裡，Client 輸出的中介資料，還不是 dto/weather/WeatherSignal 那個
 * 真正對外的DTO）。
 *
 * 刻意不放進 dto 套件：這是 WeatherClient／WeatherNormalizer／
 * OpenMeteoWeatherSignalProvider 三者之間的內部資料交換格式，外部呼叫端
 * （WeatherCampaignSyncService）永遠只看得到 WeatherSignal，不需要知道
 * 這個中介結構存在。
 *
 * 任何欄位都可能是 null：Open-Meteo 對應日期若該欄位沒有值（例如資料來源
 * 缺漏），呼叫端（WeatherNormalizer）必須自行處理 null，不能假設一定有值；
 * OpenMeteoWeatherSignalProvider.average() 做跨城市平均時，也是逐欄位
 * 忽略null，不是整筆記錄因為一個欄位是null就整天放棄。
 */
record DailyWeatherMetrics(
		LocalDate date,
		Double apparentTemperatureMax,
		Double apparentTemperatureMin,
		Double humidityMean,
		Double precipitationSum,
		Double precipitationProbabilityMax,
		Double windSpeedMax) {
}
