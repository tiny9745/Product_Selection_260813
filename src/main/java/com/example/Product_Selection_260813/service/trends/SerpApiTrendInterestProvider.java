package com.example.Product_Selection_260813.service.trends;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 透過 SerpApi（非官方代抓服務）取得 Google 趨勢「隨時間變化的興趣」序列。
 *
 * <b>查詢參數（2026-09-28 實測確認）：</b>engine=google_trends、data_type=TIMESERIES、
 * geo=TW、hl=zh-tw、date=today 3-m（近 3 個月、每日一點，約 90 點）、tz=-480（台灣時間；
 * 不帶的話 SerpApi 預設 420，也就是美西時間，日期會錯一天）。一次只查一個關鍵字：
 * 同一次請求的多個關鍵字共用縮放，熱門商品會把冷門商品壓到接近 0。
 *
 * <b>回應格式（實測）：</b>
 * <ul>
 * <li>有資料：interest_over_time.timeline_data[]，每點 values[0].extracted_value（整數）；
 * 當天那一點帶 partial_data=true（還沒結束，數字偏低），要排除</li>
 * <li>查無資料：HTTP 200、search_metadata.status=Success，但帶 error「Google Trends hasn't returned
 * any results for this query.」。⚠️ 這種情況 SerpApi 也計入每月額度（實測帳號用量 +1）</li>
 * </ul>
 *
 * <b>API Key：</b>從環境變數 SERPAPI_API_KEY 注入（比照 GEMINI_API_KEY），不進 Git。
 * SerpApi 只接受把金鑰放在網址參數，所以請求網址本身含金鑰——本類別的例外訊息與 log
 * 一律不帶網址或 RestClient 例外原文（那裡面有完整網址），只記狀態碼與 SerpApi 的 error 欄位。
 *
 * <b>逾時：</b>實測單次約 7 秒（SerpApi 要即時去抓 Google），所以讀取逾時設 30 秒，
 * 不是一般 API 的 3～5 秒；逾時只影響該商品，批次會繼續下一個。
 */
@Component
public class SerpApiTrendInterestProvider implements TrendInterestProvider {

	private static final Logger log = LoggerFactory.getLogger(SerpApiTrendInterestProvider.class);

	private static final String ENDPOINT = "https://serpapi.com/search.json";

	/** SerpApi 對「Google 查無結果」回的 error 文字（實測）。 */
	private static final String NO_RESULTS_MARKER = "hasn't returned any results";

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Value("${serpapi.api-key:}")
	private String apiKey;

	@Value("${serpapi.geo:TW}")
	private String geo;

	@Value("${serpapi.timeout-seconds:30}")
	private int timeoutSeconds;

	@Override
	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}

	@Override
	public TrendInterest fetch(String keyword) {
		if (!isConfigured()) {
			throw new TrendInterestUnavailableException("尚未設定 SerpApi 金鑰（環境變數 SERPAPI_API_KEY），請聯絡維運人員設定");
		}
		URI uri = UriComponentsBuilder.fromUriString(ENDPOINT)
				.queryParam("engine", "google_trends")
				.queryParam("data_type", "TIMESERIES")
				.queryParam("geo", geo)
				.queryParam("hl", "zh-tw")
				.queryParam("tz", "-480")
				.queryParam("date", "today 3-m")
				.queryParam("q", keyword)
				.queryParam("api_key", apiKey)
				.encode()
				.build()
				.toUri();

		String body;
		try {
			body = buildRestClient().get().uri(uri).retrieve().body(String.class);
		} catch (RestClientResponseException e) {
			// 4xx／5xx：SerpApi 會在 body 放 {"error": "..."}（例如金鑰錯誤、額度用盡），只取這個欄位
			String error = extractError(e.getResponseBodyAsString());
			log.warn("SerpApi 回應 HTTP {}（關鍵字「{}」）：{}", e.getStatusCode().value(), keyword, error);
			throw new TrendInterestUnavailableException(
					"Google 趨勢服務回應錯誤（HTTP " + e.getStatusCode().value() + "）" + (error.isEmpty() ? "" : "：" + error));
		} catch (RestClientException e) {
			// 連線失敗、逾時。e.getMessage() 含完整網址（有金鑰），不可以記錄或往外傳
			log.warn("呼叫 SerpApi 失敗（關鍵字「{}」）：{}", keyword, e.getClass().getSimpleName());
			throw new TrendInterestUnavailableException("無法連線到 Google 趨勢服務，請稍後再試");
		}
		return parse(body);
	}

	/** 解析 SerpApi 回應；抽成靜態方法方便用實測回應做單元測試。 */
	static TrendInterest parse(String body) {
		JsonNode root;
		try {
			root = OBJECT_MAPPER.readTree(body == null ? "" : body);
		} catch (Exception e) {
			throw new TrendInterestUnavailableException("Google 趨勢服務回應格式無法解析");
		}
		if (root == null || root.isMissingNode() || root.isNull()) {
			throw new TrendInterestUnavailableException("Google 趨勢服務回應空白內容");
		}

		String error = root.path("error").asText("");
		if (!error.isEmpty()) {
			if (error.contains(NO_RESULTS_MARKER)) {
				return TrendInterest.noData(0);
			}
			throw new TrendInterestUnavailableException("Google 趨勢服務回應錯誤：" + error);
		}
		String status = root.path("search_metadata").path("status").asText("");
		if (!"Success".equals(status)) {
			throw new TrendInterestUnavailableException("Google 趨勢服務尚未完成查詢（狀態：" + status + "）");
		}

		JsonNode timeline = root.path("interest_over_time").path("timeline_data");
		if (!timeline.isArray() || timeline.isEmpty()) {
			return TrendInterest.noData(0);
		}
		List<Integer> values = new ArrayList<>();
		for (JsonNode point : timeline) {
			if (point.path("partial_data").asBoolean(false)) {
				continue;
			}
			JsonNode value = point.path("values").path(0);
			JsonNode extracted = value.path("extracted_value");
			if (extracted.isNumber()) {
				values.add(extracted.asInt());
			} else {
				// 保險：Google 對極低量會顯示「<1」，視為 0
				values.add(parseLeadingInt(value.path("value").asText("0")));
			}
		}
		return TrendInterestCalculator.fromSeries(values);
	}

	private static int parseLeadingInt(String text) {
		String digits = text.replaceAll("[^0-9]", "");
		return text.startsWith("<") || digits.isEmpty() ? 0 : Integer.parseInt(digits);
	}

	private static String extractError(String body) {
		try {
			return OBJECT_MAPPER.readTree(body).path("error").asText("");
		} catch (Exception e) {
			return "";
		}
	}

	private RestClient buildRestClient() {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
		return RestClient.builder().requestFactory(requestFactory).build();
	}
}
