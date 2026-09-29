package com.example.Product_Selection_260813.service.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.example.Product_Selection_260813.common.exception.LlmAnalysisException;
import com.example.Product_Selection_260813.repository.SystemSettingRepository;
import com.example.Product_Selection_260813.service.discovery.DiscoveryFitRules.FitCandidate;
import com.example.Product_Selection_260813.service.discovery.DiscoveryFitRules.FitResult;
import com.example.Product_Selection_260813.service.discovery.DiscoveryVerifier.ExtractedItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Groq 探索 client：回應解析、prompt／請求組裝、限流等待與重試（不發網路請求——HTTP 以 Transport 替身取代）。
 */
class GroqDiscoveryClientTest {

	private final ObjectMapper mapper = new ObjectMapper();

	/** 模擬 Groq Chat Completions 的回應外殼，content 是模型輸出的 JSON 字串。 */
	private static String response(String innerJson) {
		return response(innerJson, "stop");
	}

	private static String response(String innerJson, String finishReason) {
		String escaped = innerJson.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
		return "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"" + escaped
				+ "\"},\"finish_reason\":\"" + finishReason + "\"}],"
				+ "\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":50,\"total_tokens\":150}}";
	}

	// ========================= 回應解析 =========================

	@Test
	void 解析商品清單_欄位缺漏的單筆略過() {
		String inner = """
				{"items":[
				  {"canonicalName":"義美小泡芙","mentionText":"義美小泡芙","categoryHint":"食品/零食","titleIds":["t1","t2"]},
				  {"canonicalName":"Dyson V15","mentionText":"Dyson V15","categoryHint":null,"titleIds":["t3"]},
				  {"canonicalName":"缺 mention","titleIds":["t4"]}
				]}""";
		List<ExtractedItem> items = GroqDiscoveryClient.parseItems(mapper, response(inner));

		assertThat(items).containsExactly(
				new ExtractedItem("義美小泡芙", "義美小泡芙", "食品/零食", List.of("t1", "t2")),
				new ExtractedItem("Dyson V15", "Dyson V15", null, List.of("t3")));
	}

	@Test
	void 沒有結果或被內容過濾_丟例外() {
		assertThatThrownBy(() -> GroqDiscoveryClient.parseItems(mapper, "{\"choices\":[]}"))
				.isInstanceOf(LlmAnalysisException.class);
		assertThatThrownBy(() -> GroqDiscoveryClient.parseItems(mapper, response("{\"items\":[]}", "content_filter")))
				.isInstanceOf(LlmAnalysisException.class);
		assertThatThrownBy(() -> GroqDiscoveryClient.parseItems(mapper, "not json"))
				.isInstanceOf(LlmAnalysisException.class);
	}

	@Test
	void 回應被截斷_丟例外而不是回傳半套結果() {
		assertThatThrownBy(() -> GroqDiscoveryClient.parseItems(mapper, response("{\"items\":[", "length")))
				.isInstanceOf(LlmAnalysisException.class)
				.hasMessageContaining("截斷");
	}

	@Test
	void 解析適配評分_分數不是數字的那筆略過() {
		String inner = """
				{"items":[
				  {"id":"d7","score":82,"reason":"適合家庭","concerns":["單價未知"]},
				  {"id":"d8","score":"很高","reason":"x","concerns":[]},
				  {"id":"d9","score":40,"reason":"普通","concerns":[]}
				]}""";
		List<FitResult> results = GroqDiscoveryClient.parseFitResults(mapper, response(inner));
		assertThat(results).containsExactly(
				new FitResult("d7", 82, "適合家庭", List.of("單價未知")),
				new FitResult("d9", 40, "普通", List.of()));
	}

	// ========================= Prompt 與請求 =========================

	@Test
	void prompt包含標題編號與可選品類() {
		Map<String, String> titles = new LinkedHashMap<>();
		titles.put("t1", "[情報] 義美小泡芙");
		String prompt = GroqDiscoveryClient.buildPrompt(titles, List.of("食品/零食"), List.of());
		assertThat(prompt).contains("t1｜[情報] 義美小泡芙");
		assertThat(prompt).contains("- 食品/零食");
		assertThat(prompt).contains("逐字");
		// 沒有略過反例時不出現第 8 條規則
		assertThat(prompt).doesNotContain("不是實體商品」，同類型");
	}

	@Test
	void 抽取prompt帶入略過為不是實體商品的反例() {
		Map<String, String> titles = new LinkedHashMap<>();
		titles.put("t1", "[情報] 街口支付 回饋");
		String prompt = GroqDiscoveryClient.buildPrompt(titles, List.of(), List.of("街口支付", "全聯福利卡"));
		assertThat(prompt).contains("街口支付、全聯福利卡");
	}

	@Test
	void 適配評分prompt包含客群背景_項目與略過反例() {
		String prompt = GroqDiscoveryClient.buildFitPrompt(
				List.of(new FitCandidate("d7", "義美小泡芙", "食品/零食", List.of("[情報] 全聯 義美小泡芙 買一送一"))),
				"【核心客群】雙薪家庭\n", List.of("義美泡芙禮盒（不適合團購）"));
		assertThat(prompt).contains("d7｜義美小泡芙｜食品/零食｜[情報] 全聯 義美小泡芙 買一送一");
		assertThat(prompt).contains("【核心客群】雙薪家庭");
		assertThat(prompt).contains("義美泡芙禮盒（不適合團購）");
		assertThat(prompt).contains("不要假設價格");
	}

	@Test
	void 請求使用strict_json_schema_每個欄位都必填且不允許額外欄位() {
		ObjectNode body = GroqDiscoveryClient.buildRequestBody(mapper, "openai/gpt-oss-120b", "prompt",
				"discovered_products", GroqDiscoveryClient.extractionSchema(mapper), 3000, "low");

		assertThat(body.path("model").asText()).isEqualTo("openai/gpt-oss-120b");
		assertThat(body.path("messages").get(0).path("content").asText()).isEqualTo("prompt");
		assertThat(body.path("max_completion_tokens").asInt()).isEqualTo(3000);
		assertThat(body.path("reasoning_effort").asText()).isEqualTo("low");
		assertThat(body.path("include_reasoning").asBoolean(true)).isFalse();
		JsonNode format = body.path("response_format");
		assertThat(format.path("type").asText()).isEqualTo("json_schema");
		assertThat(format.path("json_schema").path("strict").asBoolean()).isTrue();

		JsonNode root = format.path("json_schema").path("schema");
		assertThat(root.path("additionalProperties").asBoolean(true)).isFalse();
		JsonNode item = root.path("properties").path("items").path("items");
		assertThat(item.path("additionalProperties").asBoolean(true)).isFalse();
		List<String> required = new ArrayList<>();
		item.path("required").forEach(node -> required.add(node.asText()));
		assertThat(required).containsExactly("canonicalName", "mentionText", "categoryHint", "titleIds");
		// strict 模式的可空欄位用 ["string","null"]，不是 Gemini 的 nullable:true
		assertThat(item.path("properties").path("categoryHint").path("type").toString())
				.isEqualTo("[\"string\",\"null\"]");
	}

	@Test
	void 推理強度留空_不送reasoning參數() {
		ObjectNode body = GroqDiscoveryClient.buildRequestBody(mapper, "llama-3.3-70b-versatile", "prompt", "fit_scores",
				GroqDiscoveryClient.fitSchema(mapper), 2000, "");
		assertThat(body.has("reasoning_effort")).isFalse();
		assertThat(body.has("include_reasoning")).isFalse();
	}

	// ========================= 限流與重試（純函式） =========================

	@Test
	void 解析Groq的時間格式() {
		assertThat(GroqDiscoveryClient.parseDurationMillis("7.66s")).isEqualTo(7660);
		assertThat(GroqDiscoveryClient.parseDurationMillis("2m59.56s")).isEqualTo(179_560);
		assertThat(GroqDiscoveryClient.parseDurationMillis("1h2m3s")).isEqualTo(3_723_000);
		assertThat(GroqDiscoveryClient.parseDurationMillis("120ms")).isEqualTo(120);
		assertThat(GroqDiscoveryClient.parseDurationMillis("0s")).isZero();
		assertThat(GroqDiscoveryClient.parseDurationMillis(null)).isEqualTo(-1);
		assertThat(GroqDiscoveryClient.parseDurationMillis("abc")).isEqualTo(-1);
		assertThat(GroqDiscoveryClient.parseDurationMillis("7.66s later")).isEqualTo(-1);
	}

	@Test
	void token額度不足才等待_等到恢復時間() {
		long now = 1_000_000;
		// 還不知道額度（第一次呼叫）：不等
		assertThat(GroqDiscoveryClient.rateLimitWaitMillis(-1, 0, 5000, now)).isZero();
		// 額度夠：不等
		assertThat(GroqDiscoveryClient.rateLimitWaitMillis(6000, now + 30_000, 5000, now)).isZero();
		// 額度不夠：等到恢復時間＋緩衝
		assertThat(GroqDiscoveryClient.rateLimitWaitMillis(1000, now + 30_000, 5000, now))
				.isEqualTo(30_000 + GroqDiscoveryClient.RATE_LIMIT_MARGIN_MS);
		// 記錄的恢復時間已經過了（例如隔天的排程）：不等
		assertThat(GroqDiscoveryClient.rateLimitWaitMillis(1000, now - 1, 5000, now)).isZero();
	}

	@Test
	void 預估token包含prompt_固定開銷與輸出上限() {
		assertThat(GroqDiscoveryClient.estimateRequestTokens("一二三", 3000))
				.isEqualTo(3 + GroqDiscoveryClient.REQUEST_OVERHEAD_TOKENS + 3000);
	}

	@Test
	void 只有暫時性錯誤才重試_413請求過大不重試() {
		assertThat(GroqDiscoveryClient.isRetryable(httpError(503, ""))).isTrue();
		assertThat(GroqDiscoveryClient.isRetryable(httpError(500, ""))).isTrue();
		assertThat(GroqDiscoveryClient.isRetryable(httpError(429, ""))).isTrue();
		assertThat(GroqDiscoveryClient.isRetryable(new ResourceAccessException("Read timed out"))).isTrue();
		// 金鑰錯、模型不存在、請求格式錯、單批超過 TPM：重試也不會好
		assertThat(GroqDiscoveryClient.isRetryable(httpError(400, ""))).isFalse();
		assertThat(GroqDiscoveryClient.isRetryable(httpError(401, ""))).isFalse();
		assertThat(GroqDiscoveryClient.isRetryable(httpError(404, ""))).isFalse();
		assertThat(GroqDiscoveryClient.isRetryable(httpError(413, ""))).isFalse();
		assertThat(GroqDiscoveryClient.isRetryable(new RestClientException("其他"))).isFalse();
	}

	@Test
	void 重試等待時間逐次乘三_上限六十秒() {
		assertThat(GroqDiscoveryClient.backoffDelayMs(5000, 1)).isEqualTo(5000);
		assertThat(GroqDiscoveryClient.backoffDelayMs(5000, 2)).isEqualTo(15000);
		assertThat(GroqDiscoveryClient.backoffDelayMs(5000, 3)).isEqualTo(45000);
		assertThat(GroqDiscoveryClient.backoffDelayMs(5000, 4)).isEqualTo(60000);
		assertThat(GroqDiscoveryClient.backoffDelayMs(-1, 2)).isZero();
	}

	@Test
	void 失敗原因只留狀態與錯誤訊息_429不帶組織ID原文() {
		String rateLimited = "{\"error\":{\"message\":\"Rate limit reached for model `openai/gpt-oss-120b` in organization `org_123` on tokens per minute (TPM)\",\"type\":\"tokens\",\"code\":\"rate_limit_exceeded\"}}";
		assertThat(GroqDiscoveryClient.describeFailure(mapper, httpError(429, rateLimited)))
				.isEqualTo("Groq 呼叫頻率或 token 額度已達上限（429）");
		assertThat(GroqDiscoveryClient.describeFailure(mapper, httpError(503, "{}")))
				.isEqualTo("Groq 服務暫時無法使用（503）");
		assertThat(GroqDiscoveryClient.describeFailure(mapper, httpError(413, "{}")))
				.contains("discovery.titles-per-ai-call");
		assertThat(GroqDiscoveryClient.describeFailure(mapper,
				httpError(400, "{\"error\":{\"message\":\"json_schema is invalid\"}}")))
				.isEqualTo("Groq 回應錯誤（400）：json_schema is invalid");
		assertThat(GroqDiscoveryClient.describeFailure(mapper, httpError(404, "not json")))
				.isEqualTo("Groq 模型名稱不存在（404），請檢查 groq.model 設定");
		assertThat(GroqDiscoveryClient.describeFailure(mapper, new ResourceAccessException("Read timed out")))
				.isEqualTo("連線 Groq 逾時或失敗");
	}

	// ========================= 呼叫流程（Transport 替身） =========================

	private GroqDiscoveryClient client;
	private final List<Long> sleeps = new ArrayList<>();
	private final List<String> sentBodies = new ArrayList<>();
	private long now;

	@BeforeEach
	void setUpClient() {
		client = new GroqDiscoveryClient();
		SystemSettingRepository settings = mock(SystemSettingRepository.class);
		when(settings.findById(anyString())).thenReturn(Optional.empty());
		ReflectionTestUtils.setField(client, "systemSettingRepository", settings);
		ReflectionTestUtils.setField(client, "apiKey", "gsk_test");
		ReflectionTestUtils.setField(client, "baseUrl", "https://api.groq.com/openai/v1/");
		ReflectionTestUtils.setField(client, "model", "openai/gpt-oss-120b");
		ReflectionTestUtils.setField(client, "maxCompletionTokens", 3000);
		ReflectionTestUtils.setField(client, "reasoningEffort", "low");
		ReflectionTestUtils.setField(client, "maxAttempts", 4);
		ReflectionTestUtils.setField(client, "retryBackoffMs", 5000L);
		ReflectionTestUtils.setField(client, "maxRateLimitWaitSeconds", 90L);
		now = 1_000_000;
		client.nowMillis = () -> now;
		client.sleeper = millis -> {
			sleeps.add(millis);
			now += millis;
		};
	}

	/** 依序回傳 replies；元素是 Reply 就成功，是 RestClientException 就丟出。 */
	private void givenReplies(Object... replies) {
		List<Object> queue = new ArrayList<>(List.of(replies));
		client.transport = (url, key, body) -> {
			assertThat(url).isEqualTo("https://api.groq.com/openai/v1/chat/completions");
			assertThat(key).isEqualTo("gsk_test");
			sentBodies.add(body);
			Object next = queue.remove(0);
			if (next instanceof RestClientException e) {
				throw e;
			}
			return (GroqDiscoveryClient.Reply) next;
		};
	}

	private static GroqDiscoveryClient.Reply ok(String body, Map<String, String> headers) {
		UnaryOperator<String> lookup = name -> headers.get(name);
		return new GroqDiscoveryClient.Reply(body, lookup);
	}

	private static final String EMPTY_ITEMS = response("{\"items\":[]}");

	private static Map<String, String> titles(int count) {
		Map<String, String> titles = new LinkedHashMap<>();
		for (int i = 1; i <= count; i++) {
			titles.put("t" + i, "[情報] 義美小泡芙 第" + i + "篇");
		}
		return titles;
	}

	@Test
	void 成功呼叫_送出strict_schema請求並解析結果() {
		givenReplies(ok(response("""
				{"items":[{"canonicalName":"義美小泡芙","mentionText":"義美小泡芙","categoryHint":null,"titleIds":["t1"]}]}"""),
				Map.of()));

		List<ExtractedItem> items = client.extract(titles(1), List.of(), List.of());

		assertThat(items).containsExactly(new ExtractedItem("義美小泡芙", "義美小泡芙", null, List.of("t1")));
		assertThat(sentBodies.get(0)).contains("\"response_format\"").contains("\"strict\":true");
		assertThat(sleeps).isEmpty();
	}

	@Test
	void 上次回應顯示token不足_下一批先等額度恢復再送() {
		givenReplies(
				ok(EMPTY_ITEMS, Map.of("x-ratelimit-remaining-tokens", "1200", "x-ratelimit-reset-tokens", "42.5s")),
				ok(EMPTY_ITEMS, Map.of()));

		client.extract(titles(1), List.of(), List.of());
		client.extract(titles(1), List.of(), List.of());

		assertThat(sleeps).containsExactly(42_500 + GroqDiscoveryClient.RATE_LIMIT_MARGIN_MS);
		assertThat(sentBodies).hasSize(2);
	}

	@Test
	void 收到429_依retry_after等待後重試成功() {
		givenReplies(rateLimited("7"), ok(EMPTY_ITEMS, Map.of()));

		client.extract(titles(1), List.of(), List.of());

		assertThat(sleeps).containsExactly(7000L);
		assertThat(sentBodies).hasSize(2);
	}

	@Test
	void 收到429且要等很久_視為每日額度用完_不重試() {
		givenReplies(rateLimited("1800"));

		assertThatThrownBy(() -> client.extract(titles(1), List.of(), List.of()))
				.isInstanceOf(DiscoveryQuotaExceededException.class)
				.hasMessageContaining("30 分鐘");
		assertThat(sentBodies).hasSize(1);
		assertThat(sleeps).isEmpty();
	}

	@Test
	void 服務持續503_重試到上限後拋暫時無法使用() {
		givenReplies(httpError(503, "{}"), httpError(503, "{}"), httpError(503, "{}"), httpError(503, "{}"));

		assertThatThrownBy(() -> client.extract(titles(1), List.of(), List.of()))
				.isInstanceOf(DiscoveryAiUnavailableException.class)
				.hasMessageContaining("已嘗試 4 次");
		assertThat(sleeps).containsExactly(5000L, 15000L, 45000L);
	}

	@Test
	void 單批超過每分鐘token上限413_不重試直接失敗() {
		givenReplies(httpError(413, "{}"));

		assertThatThrownBy(() -> client.extract(titles(1), List.of(), List.of()))
				.isInstanceOf(LlmAnalysisException.class)
				.isNotInstanceOf(DiscoveryAiUnavailableException.class)
				.hasMessageContaining("413");
		assertThat(sentBodies).hasSize(1);
	}

	@Test
	void 沒有金鑰_不送請求() {
		ReflectionTestUtils.setField(client, "apiKey", " ");
		givenReplies();
		assertThat(client.isConfigured()).isFalse();
		assertThatThrownBy(() -> client.extract(titles(1), List.of(), List.of()))
				.hasMessageContaining("GROQ_API_KEY");
		assertThat(sentBodies).isEmpty();
	}

	// ========================= 工具 =========================

	private static RestClientResponseException httpError(int status, String body) {
		return httpError(status, body, null);
	}

	private static RestClientResponseException httpError(int status, String body, HttpHeaders headers) {
		return new RestClientResponseException(status + " error", HttpStatusCode.valueOf(status), "", headers,
				body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
	}

	private static RestClientResponseException rateLimited(String retryAfterSeconds) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("retry-after", retryAfterSeconds);
		return httpError(429, "{\"error\":{\"message\":\"Rate limit reached\"}}", headers);
	}
}
