package com.example.Product_Selection_260813.service.weather;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 呼叫 Open-Meteo Forecast API（https://open-meteo.com/en/docs），把回應轉成
 * 每日氣象數值（DailyWeatherMetrics）。只負責「打API＋把JSON攤平成內部
 * 結構」，不做任何天氣訊號分類判斷——分類門檻在WeatherNormalizer（規劃
 * 文件第23節：External API → Client → DTO → Normalizer → Internal Model
 * 分層，此類別對應「Client」這一層）。
 *
 * <b>免API Key：</b>Open-Meteo為免費公開API，非商業用途不需要金鑰，因此本
 * 類別沒有像GeminiAnalysisServiceImpl那樣的{@code @Value("${xxx.api-key}")}
 * 設定，也不需要SystemConfigurationException那條「金鑰未設定」的檢查。
 *
 * <b>&amp;timezone=Asia/Taipei：</b>讓Open-Meteo回傳的每日資料以台灣當地
 * 日期為界（而不是UTC日界線），跟資料庫連線字串的
 * {@code serverTimezone=Asia/Taipei}一致，避免時區造成的off-by-one日期
 * 問題——這點對這支服務特別重要，因為WeatherSignal.windowStart／windowEnd
 * 之後會直接對到FestiveCampaign的startDate／endDate（LocalDate，無時區
 * 資訊），日期本身就是唯一的時間依據。
 *
 * <b>手動解析JSON的理由：</b>比照GeminiAnalysisServiceImpl的既有慣例（見
 * 該類別註解），Spring Boot 4環境下RestClient的HttpMessageConverter可能
 * 選到不相容的Jackson版本，因此response一律先用String接住，再用本類別
 * 自己的ObjectMapper解析，繞開自動轉換器的選擇問題。
 *
 * <b>失敗時的行為：</b>本類別不吞任何例外——RestClientException（連線／
 * 逾時／HTTP錯誤狀態碼）與JSON解析失敗都直接往外拋，由呼叫端
 * （OpenMeteoWeatherSignalProvider）決定「單一代表城市失敗」該怎麼處理
 * （目前是記錄警告後排除該城市，見該類別說明），這支類別本身只管「打API
 * 成功就回傳資料，失敗就丟例外」，職責單純。
 */
@Component
public class WeatherClient {

	private static final String BASE_URL = "https://api.open-meteo.com/v1/forecast";

	/**
	 * 要跟Open-Meteo要的每日聚合欄位。只挑WeatherNormalizer分類邏輯實際會用到
	 * 的六項，不多要——多要的欄位只會讓回應變大、卻沒有任何呼叫端在用。
	 */
	private static final String DAILY_VARIABLES = String.join(",",
			"apparent_temperature_max",
			"apparent_temperature_min",
			"relative_humidity_2m_mean",
			"precipitation_sum",
			"precipitation_probability_max",
			"wind_speed_10m_max");

	@Value("${weather.timeout-seconds:15}")
	private int timeoutSeconds;

	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * 取得指定座標未來{@code forecastDays}天（含今天）的每日氣象預報。
	 *
	 * @param latitude     緯度
	 * @param longitude    經度
	 * @param forecastDays 要取得的天數；Open-Meteo免費版forecast_days上限16天，
	 *                     超過會被API回400錯誤（見open-meteo.com/en/docs的
	 *                     forecast_days參數說明：Integer (0-16)）
	 * @return 以日期為key的每日氣象數值；理論上會有forecastDays筆，但若
	 *         Open-Meteo回應中缺漏某天資料，該日就不會出現在回傳的Map裡，
	 *         呼叫端需自行處理「某天完全沒資料」的情境，不能假設一定連續
	 * @throws org.springframework.web.client.RestClientException 連線失敗、
	 *                                                              逾時或HTTP錯誤狀態碼
	 * @throws IllegalStateException                                回應內容無法解析為預期的JSON結構
	 */
	public Map<LocalDate, DailyWeatherMetrics> fetchDaily(double latitude, double longitude, int forecastDays) {
		return fetchDaily(latitude, longitude, 0, forecastDays);
	}

	/**
	 * V26：同一次呼叫取得「過去 {@code pastDays} 天＋未來 {@code forecastDays} 天」的每日數值。
	 *
	 * 規格決議不另外整合 Historical Weather API（archive-api），直接用 Forecast API 的
	 * past_days 參數（0~92）。已知取捨：past_days 來自高解析度局部模型，可能隨模型版本更新
	 * 對同一天的值微調，不像 ERA5 再分析是穩定的最終值——使用者已接受，畫面只顯示資料更新時間。
	 * 過去日期的 precipitation_probability_max 常為 null，分類時由 WeatherNormalizer 特別處理。
	 */
	public Map<LocalDate, DailyWeatherMetrics> fetchDaily(double latitude, double longitude, int pastDays,
			int forecastDays) {
		URI uri = UriComponentsBuilder.fromUriString(BASE_URL)
				.queryParam("latitude", latitude)
				.queryParam("longitude", longitude)
				.queryParam("timezone", "Asia/Taipei")
				.queryParam("past_days", pastDays)
				.queryParam("forecast_days", forecastDays)
				.queryParam("daily", DAILY_VARIABLES)
				.build()
				.toUri();

		String responseJson = buildRestClient().get()
				.uri(uri)
				.retrieve()
				.body(String.class);

		JsonNode root;
		try {
			root = objectMapper.readTree(responseJson);
		} catch (Exception e) {
			throw new IllegalStateException("Open-Meteo回應內容無法解析為JSON", e);
		}

		return parseDaily(root.path("daily"));
	}

	private Map<LocalDate, DailyWeatherMetrics> parseDaily(JsonNode daily) {
		JsonNode timeArray = daily.path("time");
		if (!timeArray.isArray()) {
			// daily.time缺漏代表回應格式跟預期完全不符（例如API改版、或latitude/longitude
			// 參數有誤導致Open-Meteo回傳錯誤物件），沒有日期就無法對齊任何一筆數值，
			// 直接視為失敗，不要硬湊出一個空Map假裝成功。
			throw new IllegalStateException("Open-Meteo回應缺少daily.time陣列，無法對齊每日資料");
		}

		Map<LocalDate, DailyWeatherMetrics> result = new LinkedHashMap<>();
		for (int i = 0; i < timeArray.size(); i++) {
			LocalDate date = LocalDate.parse(timeArray.get(i).asText());
			result.put(date, new DailyWeatherMetrics(
					date,
					readDouble(daily, "apparent_temperature_max", i),
					readDouble(daily, "apparent_temperature_min", i),
					readDouble(daily, "relative_humidity_2m_mean", i),
					readDouble(daily, "precipitation_sum", i),
					readDouble(daily, "precipitation_probability_max", i),
					readDouble(daily, "wind_speed_10m_max", i)));
		}
		return result;
	}

	private Double readDouble(JsonNode daily, String field, int index) {
		JsonNode array = daily.path(field);
		if (!array.isArray() || index >= array.size()) {
			return null;
		}
		JsonNode value = array.get(index);
		return (value == null || value.isNull()) ? null : value.asDouble();
	}

	private RestClient buildRestClient() {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

		return RestClient.builder()
				.requestFactory(requestFactory)
				.build();
	}
}
