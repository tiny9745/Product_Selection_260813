package com.example.Product_Selection_260813.service.weather;

import java.util.List;
import java.util.Map;

/**
 * 天氣訊號監控的區域劃分＋各區代表城市經緯度。
 *
 * 沿用 WeatherCampaignSyncService.WEATHER_TAG_MAPPING 的既有慣例：業務設定
 * 先用靜態常數，不預先蓋一張設定表——如果之後要開放主管自行調整監控區域／
 * 代表城市（例如加入離島獨立分區），才需要升級成資料庫表，屆時只需要改這裡
 * 的查詢來源，不影響 OpenMeteoWeatherSignalProvider 的其餘邏輯。
 *
 * <b>「多個代表城市」的做法：</b>同一區域內的城市，每日各項氣象數值先取平均
 * （見 OpenMeteoWeatherSignalProvider.averageByDate()），再拿平均值去分類天氣
 * 訊號類型（WeatherNormalizer.classifyDay()），不是對「分類後的訊號類型」做
 * 多數決——氣象數值是連續量，先平均再分類，比先分類再投票更不容易因城市間
 * 些微數值差異，讓同一天的訊號忽有忽無。
 *
 * ⚠️ 代表城市與經緯度是初版預設值（4大區、每區2~3個縣市），實際監控範圍
 * 若與企劃不同，直接調整這裡的 Map 內容即可，不影響其他類別。
 */
public final class WeatherRegionConfig {

	private WeatherRegionConfig() {
	}

	/** 代表城市：名稱僅供log與campaign名稱使用，實際查詢只依賴經緯度。 */
	public record City(String name, double latitude, double longitude) {
	}

	/**
	 * 區域代碼的中文名稱，與前端 WEATHER_REGION_LABEL 一致。2026-09-24 新增：天氣檔期名稱原本直接
	 * 拼區域代碼（「NORTH大雨（系統自動）」），改用中文名稱。
	 */
	public static final Map<String, String> REGION_LABELS = Map.of(
			"NORTH", "北部",
			"CENTRAL", "中部",
			"SOUTH", "南部",
			"EAST", "東部");

	/** 取區域中文名稱；未知代碼原樣回傳，不讓同步因名稱失敗。 */
	public static String regionLabel(String region) {
		return REGION_LABELS.getOrDefault(region, region);
	}

	public static final Map<String, List<City>> REGION_CITIES = Map.of(
			"NORTH", List.of(
					new City("台北", 25.0330, 121.5654),
					new City("桃園", 24.9936, 121.3010),
					new City("新竹", 24.8039, 120.9647)),
			"CENTRAL", List.of(
					new City("台中", 24.1477, 120.6736),
					new City("彰化", 24.0518, 120.5161),
					new City("南投", 23.9157, 120.6869)),
			"SOUTH", List.of(
					new City("台南", 22.9997, 120.2270),
					new City("高雄", 22.6273, 120.3014),
					new City("屏東", 22.6813, 120.4818)),
			"EAST", List.of(
					new City("花蓮", 23.9871, 121.6015),
					new City("台東", 22.7583, 121.1444)));
}
