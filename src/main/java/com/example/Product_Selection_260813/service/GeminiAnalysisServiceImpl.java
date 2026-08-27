package com.example.Product_Selection_260813.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.YearMonth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.Product_Selection_260813.common.exception.LlmAnalysisException;
import com.example.Product_Selection_260813.common.exception.SystemConfigurationException;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductEvaluation;
import com.example.Product_Selection_260813.entity.SystemSetting;
import com.example.Product_Selection_260813.repository.SystemSettingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * {@link LlmAnalysisService}的正式實作：呼叫Google Gemini API（generateContent端點）
 * 產生AI摘要／推薦原因／風險提示，取代{@link MockLlmAnalysisService}的模擬資料。
 *
 * <b>切換方式：</b>本類別標註{@link Primary}，Spring在有多個LlmAnalysisService實作時
 * 會優先注入這一個；MockLlmAnalysisService本身不需要移除或改動，未來若要暫時切回
 * 模擬資料（例如額度耗盡、除錯），只需拿掉這裡的@Primary（或加上@Qualifier指定），
 * 呼叫端AiSelectionService完全不受影響——與LlmAnalysisService介面類別註解裡
 * 記載的TODO設計一致。
 *
 * <b>輸出格式：</b>透過generationConfig.responseMimeType=application/json強制Gemini
 * 回傳結構化JSON（含summary／recommendation／reasons三個欄位），避免用字串比對
 * 或正則從自由文字裡「猜」欄位邊界。
 *
 * <b>額度保護：</b>企劃書提及的月用量保護機制依賴system_settings表，目前
 * SettingsService尚未建立對應邏輯（獨立任務，見AiSelectionService類別註解），
 * 本類別暫不處理，只負責單次呼叫是否成功。
 */
@Service
@Primary
public class GeminiAnalysisServiceImpl implements LlmAnalysisService {

	private static final Logger log = LoggerFactory.getLogger(GeminiAnalysisServiceImpl.class);

	private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

	@Value("${gemini.api-key:}")
	private String apiKey;

	@Value("${gemini.model:gemini-2.5-flash}")
	private String model;

	@Value("${gemini.timeout-seconds:30}")
	private int timeoutSeconds;

	@Autowired
	private SystemSettingRepository systemSettingRepository;

	// 月額度保護：MVP版本，key依月份自動輪替（如gemini_calls_2026-08），
	// 不需要額外的排程去重置計數——換月自然就是全新的一筆。上限可在
	// system_settings手動調整"gemini_monthly_limit"這個key，沒設定時
	// 用DEFAULT_MONTHLY_LIMIT。這不是原子操作（沒加鎖），高併發下有機會
	// 誤差幾次，但足以擋住失控的重複呼叫；未來若流量變大，建議改用
	// Redis計數器或資料庫層級的原子UPDATE。
	private static final String QUOTA_LIMIT_KEY = "gemini_monthly_limit";
	private static final int DEFAULT_MONTHLY_LIMIT = 500;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public LlmAnalysisResult generate(Product product, ProductEvaluation evaluation) {
		if (apiKey == null || apiKey.isBlank()) {
			// 我方設定缺漏（未設定GEMINI_API_KEY環境變數），不管怎麼重試都不會成功，
			// 屬於SystemConfigurationException的語意（見該類別說明），不是
			// LlmAnalysisException（那個代表「外部服務暫時性問題」）。
			throw new SystemConfigurationException("尚未設定Gemini API金鑰（GEMINI_API_KEY），請聯絡維運人員設定");
		}

		checkAndIncrementQuota();

		String prompt = buildPrompt(product, evaluation);
		String requestJson;
		try {
			requestJson = objectMapper.writeValueAsString(buildRequestBody(prompt));
		} catch (Exception e) {
			// 我方組出來的request body本身序列化失敗，屬於程式問題而非外部服務問題
			throw new LlmAnalysisException("組裝Gemini請求內容失敗", e);
		}

		String responseJson;
		try {
			URI uri = URI.create(BASE_URL + model + ":generateContent");
			// 注意：body/response都刻意用String手動處理，不交給Spring的HttpMessageConverter
			// 自動轉換。原因：Spring Boot 4環境下同時存在新版Jackson3與舊版Jackson2（本類別
			// 用的ObjectNode/JsonNode屬於舊版），RestClient可能選到不認得舊版ObjectNode的
			// 轉換器，退化成用反射抓getter方法序列化，產生isArray／getNodeType這類方法名稱
			// 誤當成JSON欄位送出去的錯誤內容。改用String可以完全繞過這個轉換器選擇問題。
			responseJson = buildRestClient().post()
					.uri(uri)
					.header("x-goog-api-key", apiKey)
					.contentType(MediaType.APPLICATION_JSON)
					.body(requestJson)
					.retrieve()
					.body(String.class);
		} catch (RestClientException e) {
			log.error("呼叫Gemini API失敗（商品ID={}）", product.getId(), e);
			throw new LlmAnalysisException("呼叫AI分析服務失敗，請稍後再試", e);
		}

		JsonNode responseBody;
		try {
			responseBody = objectMapper.readTree(responseJson);
		} catch (Exception e) {
			throw new LlmAnalysisException("Gemini回應內容無法解析為JSON", e);
		}

		return parseResponse(responseBody);
	}

	// ========================= 月額度保護 =========================

	private void checkAndIncrementQuota() {
		int limit = systemSettingRepository.findById(QUOTA_LIMIT_KEY)
				.map(SystemSetting::getSettingValue)
				.map(value -> {
					try {
						return Integer.parseInt(value);
					} catch (NumberFormatException e) {
						log.warn("system_settings裡{}的值無法解析為整數，改用預設值{}", QUOTA_LIMIT_KEY, DEFAULT_MONTHLY_LIMIT);
						return DEFAULT_MONTHLY_LIMIT;
					}
				})
				.orElse(DEFAULT_MONTHLY_LIMIT);

		String monthKey = "gemini_calls_" + YearMonth.now();
		int used = systemSettingRepository.findById(monthKey)
				.map(SystemSetting::getSettingValue)
				.map(Integer::parseInt)
				.orElse(0);

		if (used >= limit) {
			throw new LlmAnalysisException(
					"本月AI分析呼叫次數已達上限（" + limit + "次），請聯絡管理員調整system_settings裡的" + QUOTA_LIMIT_KEY + "，或等待下月自動重置");
		}

		// 原子UPSERT遞增，不走JPA的load-modify-save（見repository方法上的說明）
		systemSettingRepository.incrementCounter(monthKey);
	}

	// ========================= Prompt 組裝 =========================

	private String buildPrompt(Product product, ProductEvaluation evaluation) {
		StringBuilder sb = new StringBuilder();
		sb.append("你是電商選品的AI分析助理，請根據以下商品資料與評估分數，產生繁體中文分析。\n\n");

		sb.append("【商品資料】\n");
		sb.append("名稱：").append(product.getName()).append('\n');
		appendIfPresent(sb, "商品描述", product.getDescription());
		appendIfPresent(sb, "供應商", product.getSupplierName());
		appendIfPresent(sb, "成本價", product.getCostPrice());
		appendIfPresent(sb, "售價", product.getSalePrice());
		appendIfPresent(sb, "市場行情價", product.getMarketPrice());
		appendIfPresent(sb, "最低訂購量", product.getMoq());
		appendIfPresent(sb, "目標客群描述", product.getTargetCustomerDescription());
		appendIfPresent(sb, "預估購買率", product.getEstimatedPurchaseRate());

		sb.append("\n【評估分數】（滿分未定，僅供參考相對高低）\n");
		if (evaluation == null || evaluation.getTotalScore() == null) {
			sb.append("此商品目前尚無完整評估分數。\n");
		} else {
			sb.append("商業條件：").append(nullSafe(evaluation.getBusinessScore())).append('\n');
			sb.append("核心客群：").append(nullSafe(evaluation.getAudienceScore())).append('\n');
			sb.append("歷史銷售：").append(nullSafe(evaluation.getHistoricalScore())).append('\n');
			sb.append("預估購買：").append(nullSafe(evaluation.getPurchaseScore())).append('\n');
			sb.append("市場趨勢：").append(nullSafe(evaluation.getTrendScore())).append('\n');
			sb.append("預測人氣：").append(nullSafe(evaluation.getForecastScore())).append('\n');
			sb.append("綜合加權總分：").append(nullSafe(evaluation.getTotalScore())).append('\n');
			sb.append("資料完整度：").append(nullSafe(evaluation.getDataCompleteness())).append("%\n");
		}

		sb.append("\n請以JSON格式回傳，包含三個欄位：\n");
		sb.append("- summary：2到3句話的整體摘要\n");
		sb.append("- recommendation：一句話的明確建議（例如「建議優先上架」「建議先核准，上架時機另議」「建議暫緩或拒絕」）\n");
		sb.append("- reasons：條列式的理由與風險提示（若資料不足，需明確指出資料不足，不得虛構未提供的資訊）\n");

		return sb.toString();
	}

	private void appendIfPresent(StringBuilder sb, String label, Object value) {
		if (value == null) {
			return;
		}
		sb.append(label).append('：').append(value).append('\n');
	}

	private String nullSafe(Object value) {
		return value != null ? value.toString() : "未知";
	}

	// ========================= Gemini Request/Response =========================

	private ObjectNode buildRequestBody(String prompt) {
		ObjectNode root = objectMapper.createObjectNode();

		ObjectNode part = objectMapper.createObjectNode();
		part.put("text", prompt);
		ObjectNode content = objectMapper.createObjectNode();
		content.set("parts", objectMapper.createArrayNode().add(part));
		root.set("contents", objectMapper.createArrayNode().add(content));

		ObjectNode responseSchema = objectMapper.createObjectNode();
		responseSchema.put("type", "OBJECT");
		ObjectNode properties = objectMapper.createObjectNode();
		properties.set("summary", objectMapper.createObjectNode().put("type", "STRING"));
		properties.set("recommendation", objectMapper.createObjectNode().put("type", "STRING"));
		properties.set("reasons", objectMapper.createObjectNode().put("type", "STRING"));
		responseSchema.set("properties", properties);
		responseSchema.set("required",
				objectMapper.createArrayNode().add("summary").add("recommendation").add("reasons"));

		ObjectNode generationConfig = objectMapper.createObjectNode();
		generationConfig.put("responseMimeType", "application/json");
		generationConfig.set("responseSchema", responseSchema);
		root.set("generationConfig", generationConfig);

		return root;
	}

	private LlmAnalysisResult parseResponse(JsonNode responseBody) {
		if (responseBody == null) {
			throw new LlmAnalysisException("Gemini API回傳空白內容");
		}

		JsonNode blockReason = responseBody.path("promptFeedback").path("blockReason");
		if (!blockReason.isMissingNode() && !blockReason.isNull()) {
			throw new LlmAnalysisException("內容被Gemini安全機制擋下：" + blockReason.asText());
		}

		JsonNode candidates = responseBody.path("candidates");
		if (!candidates.isArray() || candidates.isEmpty()) {
			throw new LlmAnalysisException("Gemini API未回傳任何候選結果");
		}

		JsonNode firstCandidate = candidates.get(0);
		String finishReason = firstCandidate.path("finishReason").asText("");
		if (!finishReason.isEmpty() && !"STOP".equals(finishReason)) {
			throw new LlmAnalysisException("Gemini未正常完成回應，finishReason=" + finishReason);
		}

		String rawText = firstCandidate.path("content").path("parts").path(0).path("text").asText(null);
		if (rawText == null || rawText.isBlank()) {
			throw new LlmAnalysisException("Gemini回應內容格式不符預期，無法解析出分析文字");
		}

		JsonNode parsed;
		try {
			parsed = objectMapper.readTree(rawText);
		} catch (Exception e) {
			throw new LlmAnalysisException("Gemini回應內容無法解析為JSON", e);
		}

		LlmAnalysisResult result = new LlmAnalysisResult();
		result.setSummary(parsed.path("summary").asText(null));
		result.setRecommendation(parsed.path("recommendation").asText(null));
		result.setReasons(parsed.path("reasons").asText(null));
		result.setModelName(model);
		return result;
	}

	// ========================= HTTP Client =========================

	private RestClient buildRestClient() {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

		return RestClient.builder()
				.baseUrl(BASE_URL)
				.requestFactory(requestFactory)
				.build();
	}
}