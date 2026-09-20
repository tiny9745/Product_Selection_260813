package com.example.Product_Selection_260813.enums;

/**
 * 正規化後的天氣訊號類型（規劃文件第10、11節）。
 *
 * 這是 WeatherNormalizer（尚未實作，見 WeatherSignalProvider 的說明）產出的結果，
 * 不是原始氣象數值——分類門檻（例如體感溫度幾度算 HOT）由 WeatherSignalProvider
 * 的實作決定，可能隨地區、資料來源調整，因此不寫死在這個 enum 裡。
 */
public enum WeatherSignalType {
	HOT("炎熱"),
	HUMID_HOT("悶熱"),
	HUMID("潮濕"),
	RAINY("降雨"),
	HEAVY_RAIN("大雨"),
	STRONG_WIND("強風"),
	COOL("涼爽"),
	COLD("寒冷"),
	DRY_COOL("乾冷"),
	NORMAL("一般");

	private final String label;

	private WeatherSignalType(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
