package com.example.Product_Selection_260813.service.discovery;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.example.Product_Selection_260813.common.exception.LlmAnalysisException;
import com.example.Product_Selection_260813.common.exception.SystemConfigurationException;
import com.example.Product_Selection_260813.entity.SystemSetting;
import com.example.Product_Selection_260813.repository.SystemSettingRepository;
import com.example.Product_Selection_260813.service.discovery.DiscoveryFitRules.FitCandidate;
import com.example.Product_Selection_260813.service.discovery.DiscoveryFitRules.FitResult;
import com.example.Product_Selection_260813.service.discovery.DiscoveryVerifier.ExtractedItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * PTT 新品探索的兩種 AI 呼叫，改用 Groq（2026-09-29，取代 GeminiDiscoveryClient）：
 * <ol>
 * <li>{@link #extract}：從一批文章標題抽出「可以採購販售的具體商品」。</li>
 * <li>{@link #evaluateFit}：依核心客群與團購通路條件，替探索到的商品打 0~100 的適配分。</li>
 * </ol>
 *
 * <b>為什麼換掉 Gemini：</b>Gemini 的 503 是 Google 端模型過載（與送出的資料量無關），重試與備援模型都擋不住，
 * 每天排程常常整批失敗。Groq 免費方案不需信用卡、表示不以 API 資料訓練模型（2026-09 查證），走 OpenAI 相容的 Chat Completions 格式。
 * 決議不做跨供應商備援：重試耗盡就記錄失敗，未處理的標題下次排程自動補跑（見 DiscoveryService 2'.）。
 * 單一商品的 AI 分析（GeminiAnalysisServiceImpl）不受影響，仍使用 Gemini。
 *
 * <b>Groq 免費方案的真正瓶頸是「每分鐘 token 數」（TPM，openai/gpt-oss-120b 為 8K），不是請求數：</b>
 * <ul>
 * <li>單一請求的「輸入 + max_completion_tokens」超過 TPM 會直接被拒（413），所以每批標題數必須比 Gemini 時期小
 * （discovery.titles-per-ai-call 預設 80、items-per-fit-call 預設 10）。</li>
 * <li>送出前依上一次回應的 x-ratelimit-remaining-tokens／x-ratelimit-reset-tokens 預估 token 是否足夠，不足就先等到
 * 額度恢復（{@link #rateLimitWaitMillis}），避免每批都先吃一次 429。</li>
 * <li>仍然收到 429 時依 retry-after 等待後重試；等待時間超過 discovery.ai.max-rate-limit-wait-seconds（通常是
 * 每日 token 額度 TPD 用完）就拋 {@link DiscoveryQuotaExceededException}，讓這次執行停止後續 AI 呼叫。</li>
 * </ul>
 * 所以一次探索的 AI 階段大約每分鐘處理一批，首次執行（沒有已處理標題）可能需要 20～30 分鐘，屬正常現象。
 *
 * <b>額度獨立：</b>另外用 {@value #QUOTA_LIMIT_KEY}（預設 {@value #DEFAULT_MONTHLY_LIMIT} 次／月）與
 * {@value #QUOTA_COUNTER_PREFIX}YYYY-MM 計數，抽取與評分共用，當作「失控保護」而不是費用控管（Groq 免費方案不計費）。
 * 扣額度時機與分析服務相同（呼叫前扣），理由見 GeminiAnalysisServiceImpl.checkAndIncrementQuota()。
 *
 * <b>輸出不直接採信：</b>雖然使用 strict JSON schema（受限解碼，格式一定符合），內容仍經 {@link DiscoveryVerifier}、
 * {@link DiscoveryFitRules#verifyFit} 驗證後才使用。
 */
@Component
public class GroqDiscoveryClient {

	private static final Logger log = LoggerFactory.getLogger(GroqDiscoveryClient.class);

	public static final String QUOTA_LIMIT_KEY = "discovery_ai_monthly_limit";
	/** 失控保護：每天最多約 30 批抽取＋4 批評分，一個月約 1,000 次。 */
	public static final int DEFAULT_MONTHLY_LIMIT = 1000;
	static final String QUOTA_COUNTER_PREFIX = "discovery_ai_calls_";

	/** prompt 裡最多放幾個略過反例，避免 prompt 無限變長。 */
	static final int MAX_NEGATIVE_EXAMPLES = 30;

	/** 預估 token 時，prompt 以外的固定開銷（訊息格式與 JSON schema）。 */
	static final int REQUEST_OVERHEAD_TOKENS = 400;

	/** 依 x-ratelimit-reset-tokens 等待時多等的緩衝，避免剛好卡在恢復的瞬間。 */
	static final long RATE_LIMIT_MARGIN_MS = 500;

	@Value("${groq.api-key:}")
	private String apiKey;

	@Value("${groq.base-url:https://api.groq.com/openai/v1}")
	private String baseUrl;

	/** 必須是支援 strict structured outputs 的模型（2026-09：openai/gpt-oss-120b、openai/gpt-oss-20b）。 */
	@Value("${groq.model:openai/gpt-oss-120b}")
	private String model;

	@Value("${groq.timeout-seconds:60}")
	private int timeoutSeconds;

	/** 單次輸出上限（gpt-oss 的推理 token 也算在內）。與輸入相加不可超過 TPM，否則 Groq 回 413。 */
	@Value("${groq.max-completion-tokens:3000}")
	private int maxCompletionTokens;

	/** gpt-oss 系列的推理強度（low／medium／high）；換成不支援的模型時留空，就不送這個參數。 */
	@Value("${groq.reasoning-effort:low}")
	private String reasoningEffort;

	/** 暫時性錯誤（429、5xx、逾時）的最多嘗試次數（含第一次）。重試不另外計入月額度。 */
	@Value("${discovery.ai.max-attempts:4}")
	private int maxAttempts;

	/** 5xx／逾時第一次重試前等待的毫秒數，之後每次乘 3（預設 5 秒、15 秒、45 秒）。429 改用 retry-after。 */
	@Value("${discovery.ai.retry-backoff-ms:5000}")
	private long retryBackoffMs;

	/** 為了等 token 額度恢復最多願意等幾秒；超過代表每日額度用完，停止這次執行的後續 AI 呼叫。 */
	@Value("${discovery.ai.max-rate-limit-wait-seconds:90}")
	private long maxRateLimitWaitSeconds;

	/** 可替換的等待方式，測試時換成不真的睡。 */
	interface Sleeper {
		void sleep(long millis) throws InterruptedException;
	}

	/** 一次 HTTP 回應：內容與回應標頭（標頭名稱不分大小寫）。 */
	record Reply(String body, UnaryOperator<String> header) {
	}

	/** 可替換的 HTTP 傳輸，測試時換成假的回應；錯誤狀態以 RestClientException 丟出（與 RestClient 相同）。 */
	interface Transport {
		Reply post(String url, String apiKey, String requestJson);
	}

	Sleeper sleeper = Thread::sleep;
	LongSupplier nowMillis = System::currentTimeMillis;
	Transport transport = this::postWithRestClient;

	/** 最近一次回應的 token 額度狀態；-1＝還不知道（尚未呼叫過）。只在 synchronized 的 call() 內讀寫。 */
	private long tokensRemaining = -1;
	private long tokensResetAtMillis = 0;

	@Autowired
	private SystemSettingRepository systemSettingRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	/** 寫進 discovered_items.model_name／fit_model 供追蹤。 */
	public String modelName() {
		return model;
	}

	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}

	/**
	 * 抽取一批標題裡的商品。
	 *
	 * @param titlesById          標題編號 → 標題（編號由呼叫端產生，例如 t1、t2）
	 * @param categoryNames       可選的品類（「大類/小類」），AI 只能從這份清單挑 categoryHint
	 * @param notProductExamples  操作人員略過為「不是實體商品」的名稱（第二階段回饋），可為空
	 * @throws SystemConfigurationException 沒有設定 API 金鑰
	 * @throws LlmAnalysisException         額度用完（DiscoveryQuotaExceededException）、暫時無法使用
	 *                                      （DiscoveryAiUnavailableException）、呼叫失敗、回應格式錯誤
	 */
	public List<ExtractedItem> extract(Map<String, String> titlesById, List<String> categoryNames,
			List<String> notProductExamples) {
		String prompt = buildPrompt(titlesById, categoryNames, notProductExamples);
		return parseItems(objectMapper,
				call(prompt, "discovered_products", extractionSchema(objectMapper), titlesById.size() + " 則標題"));
	}

	/**
	 * 替一批探索項目打適配分。
	 *
	 * @param candidates        待評分的項目（id 由呼叫端產生，例如 d12）
	 * @param audienceContext   核心客群與通路條件的文字描述（DiscoveryFitRules.describeContext）
	 * @param negativeExamples  操作人員略過的項目「名稱（原因）」，當作反例；可為空
	 */
	public List<FitResult> evaluateFit(List<FitCandidate> candidates, String audienceContext,
			List<String> negativeExamples) {
		String prompt = buildFitPrompt(candidates, audienceContext, negativeExamples);
		return parseFitResults(objectMapper,
				call(prompt, "fit_scores", fitSchema(objectMapper), candidates.size() + " 個項目評分"));
	}

	/** 本月已使用／上限，供系統設定畫面顯示。 */
	public int[] quotaUsage() {
		return new int[] { readInt(QUOTA_COUNTER_PREFIX + YearMonth.now(), 0), monthlyLimit() };
	}

	/** 本月還剩幾次。 */
	public int remainingThisMonth() {
		int[] usage = quotaUsage();
		return Math.max(0, usage[1] - usage[0]);
	}

	// ========================= 共用呼叫 =========================

	/**
	 * synchronized：token 額度是整個帳號共用的，同一時間只送一個請求，等待計算才會準。
	 * （DiscoveryRunService 本來就保證同一時間只有一次探索在跑，這裡是雙重保險。）
	 */
	private synchronized String call(String prompt, String schemaName, ObjectNode schema, String what) {
		if (!isConfigured()) {
			throw new SystemConfigurationException("尚未設定 Groq API 金鑰（GROQ_API_KEY），PTT 新品探索無法執行");
		}
		checkAndIncrementQuota();

		String requestJson;
		try {
			requestJson = objectMapper.writeValueAsString(
					buildRequestBody(objectMapper, model, prompt, schemaName, schema, maxCompletionTokens, reasoningEffort));
		} catch (Exception e) {
			throw new LlmAnalysisException("組裝 Groq 請求內容失敗", e);
		}
		long neededTokens = estimateRequestTokens(prompt, maxCompletionTokens);
		return callWithRetry(requestJson, neededTokens, what);
	}

	/**
	 * 送出請求；暫時性錯誤依等待時間重試。重試耗盡拋 DiscoveryAiUnavailableException，需要等太久（每日額度用完）
	 * 拋 DiscoveryQuotaExceededException，其他錯誤（金鑰、模型名稱、請求過大）直接拋 LlmAnalysisException。
	 */
	private String callWithRetry(String requestJson, long neededTokens, String what) {
		int attempts = Math.max(1, maxAttempts);
		long maxWaitMs = Math.max(0, maxRateLimitWaitSeconds) * 1000;
		String url = trimTrailingSlash(baseUrl) + "/chat/completions";
		for (int attempt = 1;; attempt++) {
			// 先確認 token 額度夠不夠這一批；不夠就等到恢復（TPM 最多等一分鐘左右）
			long paceMs = Math.min(rateLimitWaitMillis(tokensRemaining, tokensResetAtMillis, neededTokens,
					nowMillis.getAsLong()), maxWaitMs);
			if (paceMs > 0) {
				log.info("PTT 新品探索：Groq token 額度剩 {}，這批預估需要 {}，等待 {} 秒後送出（{}）", tokensRemaining,
						neededTokens, (paceMs + 999) / 1000, what);
				sleepOrThrow(paceMs);
			}
			try {
				Reply reply = transport.post(url, apiKey, requestJson);
				rememberRateLimit(reply.header());
				logUsage(reply.body(), what);
				return reply.body();
			} catch (RestClientException e) {
				UnaryOperator<String> headers = headersOf(e);
				rememberRateLimit(headers);
				String reason = describeFailure(objectMapper, e);
				if (!isRetryable(e)) {
					// 400／401／403／404／413：金鑰、模型名稱或請求內容有問題，重試也不會好
					log.error("PTT 新品探索呼叫 Groq 失敗（{}，模型 {}）：{}", what, model, reason, e);
					throw new LlmAnalysisException("呼叫AI服務失敗：" + reason, e);
				}
				long delay = isRateLimited(e) ? retryAfterMillis(headers) : -1;
				if (delay < 0) {
					delay = backoffDelayMs(retryBackoffMs, attempt);
				}
				if (isRateLimited(e) && delay > maxWaitMs) {
					log.warn("PTT 新品探索：Groq 要求等待 {} 秒以上才能再呼叫（{}），停止本次後續 AI 呼叫", delay / 1000, what);
					throw new DiscoveryQuotaExceededException("Groq 免費額度暫時用完（需等待約 " + formatWait(delay)
							+ "，通常是每日 token 上限），本次先停止，未處理的標題下次排程會自動補跑");
				}
				if (attempt >= attempts) {
					log.error("PTT 新品探索呼叫 Groq 失敗（{}，模型 {}），已嘗試 {} 次：{}", what, model, attempt, reason);
					throw new DiscoveryAiUnavailableException(
							"AI 服務暫時無法使用：" + reason + "（模型 " + model + "，已嘗試 " + attempt + " 次）", e);
				}
				log.warn("PTT 新品探索呼叫 Groq 第 {} 次失敗（{}）：{}，{} 秒後重試", attempt, what, reason,
						(delay + 999) / 1000);
				sleepOrThrow(delay);
			}
		}
	}

	private void sleepOrThrow(long millis) {
		try {
			sleeper.sleep(millis);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new LlmAnalysisException("等待 AI 服務時被中斷", interrupted);
		}
	}

	private void rememberRateLimit(UnaryOperator<String> header) {
		if (header == null) {
			return;
		}
		long remaining = parseLongOrMinusOne(header.apply("x-ratelimit-remaining-tokens"));
		long resetMs = parseDurationMillis(header.apply("x-ratelimit-reset-tokens"));
		if (remaining >= 0 && resetMs >= 0) {
			tokensRemaining = remaining;
			tokensResetAtMillis = nowMillis.getAsLong() + resetMs;
		}
	}

	private void logUsage(String body, String what) {
		try {
			JsonNode usage = objectMapper.readTree(body).path("usage");
			if (usage.has("total_tokens")) {
				log.info("PTT 新品探索：Groq {} 使用 {} tokens（輸入 {}、輸出 {}）", what, usage.path("total_tokens").asLong(),
						usage.path("prompt_tokens").asLong(), usage.path("completion_tokens").asLong());
			}
		} catch (Exception ignored) {
			// 只是紀錄用；回應格式問題交給 extractItemsNode 處理
		}
	}

	// ========================= 限流與重試（純函式，見 GroqDiscoveryClientTest） =========================

	/** 送出前預估的 token：prompt 以「一個字元≈一個 token」保守估計（中文標題大致如此），加上固定開銷與輸出上限。 */
	static long estimateRequestTokens(String prompt, int maxCompletionTokens) {
		return (long) prompt.length() + REQUEST_OVERHEAD_TOKENS + Math.max(0, maxCompletionTokens);
	}

	/**
	 * 送出前要先等多久。還不知道額度（-1）、額度足夠、或上次記錄的恢復時間已經過了，都不必等。
	 */
	static long rateLimitWaitMillis(long remainingTokens, long resetAtMillis, long neededTokens, long nowMillis) {
		if (remainingTokens < 0 || remainingTokens >= neededTokens || resetAtMillis <= nowMillis) {
			return 0;
		}
		return resetAtMillis - nowMillis + RATE_LIMIT_MARGIN_MS;
	}

	private static final Pattern DURATION_PART = Pattern.compile("(\\d+(?:\\.\\d+)?)(ms|h|m|s)");

	/**
	 * 解析 Groq 的時間格式（例如 7.66s、2m59.56s、1h2m3s、120ms）為毫秒；無法解析回傳 -1。
	 */
	static long parseDurationMillis(String value) {
		if (value == null || value.isBlank()) {
			return -1;
		}
		String text = value.trim();
		Matcher matcher = DURATION_PART.matcher(text);
		double millis = 0;
		int consumed = 0;
		while (matcher.find()) {
			if (matcher.start() != consumed) {
				return -1;
			}
			double amount = Double.parseDouble(matcher.group(1));
			millis += switch (matcher.group(2)) {
				case "h" -> amount * 3_600_000;
				case "m" -> amount * 60_000;
				case "s" -> amount * 1000;
				default -> amount; // ms
			};
			consumed = matcher.end();
		}
		return consumed == text.length() && consumed > 0 ? Math.round(millis) : -1;
	}

	/** 429 的 retry-after（秒，可能有小數）轉毫秒；沒有或無法解析回傳 -1。 */
	static long retryAfterMillis(UnaryOperator<String> header) {
		if (header == null) {
			return -1;
		}
		String value = header.apply("retry-after");
		if (value == null || value.isBlank()) {
			return -1;
		}
		try {
			double seconds = Double.parseDouble(value.trim());
			return seconds < 0 ? -1 : Math.round(seconds * 1000);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/**
	 * 值得重試的暫時性錯誤：5xx、429 頻率限制、連線或讀取逾時。
	 * 其餘 4xx（金鑰錯、模型不存在、請求格式錯、413 請求超過 TPM）重試也不會好，直接失敗。
	 */
	static boolean isRetryable(RestClientException e) {
		if (e instanceof ResourceAccessException) {
			return true;
		}
		if (e instanceof RestClientResponseException response && response.getStatusCode() != null) {
			int status = response.getStatusCode().value();
			return status == 429 || status >= 500;
		}
		return false;
	}

	private static boolean isRateLimited(RestClientException e) {
		return e instanceof RestClientResponseException response && response.getStatusCode() != null
				&& response.getStatusCode().value() == 429;
	}

	/** 第 attempt 次失敗後要等多久：base、base×3、base×9…，上限 60 秒。 */
	static long backoffDelayMs(long baseMs, int attempt) {
		long delay = Math.max(0, baseMs);
		for (int i = 1; i < attempt && delay < 60_000; i++) {
			delay *= 3;
		}
		return Math.min(delay, 60_000);
	}

	/**
	 * 給人看的失敗原因（寫進執行紀錄）：只取狀態碼與 error.message。
	 * 429 不附原文——Groq 的原文會帶組織 ID，不必出現在畫面上。
	 */
	static String describeFailure(ObjectMapper mapper, RestClientException e) {
		if (e instanceof ResourceAccessException) {
			return "連線 Groq 逾時或失敗";
		}
		if (e instanceof RestClientResponseException response && response.getStatusCode() != null) {
			int status = response.getStatusCode().value();
			String label = switch (status) {
				case 503 -> "Groq 服務暫時無法使用（503）";
				case 429 -> "Groq 呼叫頻率或 token 額度已達上限（429）";
				case 413 -> "單批請求超過 Groq 每分鐘 token 上限（413），請調低 discovery.titles-per-ai-call 或 groq.max-completion-tokens";
				case 401, 403 -> "Groq 金鑰無效或沒有權限（" + status + "）";
				case 404 -> "Groq 模型名稱不存在（404），請檢查 groq.model 設定";
				default -> "Groq 回應錯誤（" + status + "）";
			};
			if (status == 503 || status == 429 || status == 413) {
				return label;
			}
			String upstream = upstreamMessage(mapper, response.getResponseBodyAsString());
			return upstream == null ? label : label + "：" + upstream;
		}
		String message = e.getMessage();
		return message == null ? e.getClass().getSimpleName() : truncateMessage(message);
	}

	private static UnaryOperator<String> headersOf(RestClientException e) {
		if (e instanceof RestClientResponseException response) {
			HttpHeaders headers = response.getResponseHeaders();
			if (headers != null) {
				return headers::getFirst;
			}
		}
		return null;
	}

	private static String upstreamMessage(ObjectMapper mapper, String body) {
		if (body == null || body.isBlank()) {
			return null;
		}
		try {
			String message = mapper.readTree(body).path("error").path("message").asText("");
			return message.isBlank() ? null : truncateMessage(message);
		} catch (Exception parseFailure) {
			return null;
		}
	}

	private static String truncateMessage(String message) {
		String singleLine = message.replaceAll("\\s+", " ").trim();
		return singleLine.length() > 120 ? singleLine.substring(0, 120) + "…" : singleLine;
	}

	private static long parseLongOrMinusOne(String value) {
		if (value == null || value.isBlank()) {
			return -1;
		}
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static String formatWait(long millis) {
		long minutes = Math.round(millis / 60_000.0);
		return minutes >= 1 ? minutes + " 分鐘" : Math.max(1, millis / 1000) + " 秒";
	}

	private static String trimTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	// ========================= 額度 =========================

	private void checkAndIncrementQuota() {
		int limit = monthlyLimit();
		String monthKey = QUOTA_COUNTER_PREFIX + YearMonth.now();
		int used = readInt(monthKey, 0);
		if (used >= limit) {
			throw new DiscoveryQuotaExceededException("本月 AI 商品雷達的呼叫次數已達上限（" + limit
					+ " 次），請調整 system_settings 的 " + QUOTA_LIMIT_KEY + "，或等待下月自動重置");
		}
		systemSettingRepository.incrementCounter(monthKey);
	}

	private int monthlyLimit() {
		return readInt(QUOTA_LIMIT_KEY, DEFAULT_MONTHLY_LIMIT);
	}

	private int readInt(String key, int fallback) {
		return systemSettingRepository.findById(key)
				.map(SystemSetting::getSettingValue)
				.map(value -> {
					try {
						return Integer.parseInt(value.trim());
					} catch (NumberFormatException e) {
						log.warn("system_settings 裡 {} 的值「{}」無法解析為整數，改用 {}", key, value, fallback);
						return fallback;
					}
				})
				.orElse(fallback);
	}

	// ========================= Prompt（內容沿用 Gemini 時期，沒有針對 gpt-oss 另外調整） =========================

	static String buildPrompt(Map<String, String> titlesById, List<String> categoryNames,
			List<String> notProductExamples) {
		StringBuilder sb = new StringBuilder();
		sb.append("你是台灣團購零售業的選品助理。以下是 PTT 看板近 7 天的文章標題，每行格式為「編號｜標題」。\n")
				.append("請找出標題裡提到的「可以向供應商採購、再賣給消費者的具體商品」。\n\n")
				.append("規則：\n")
				.append("1. 只列實體消費商品（食品、日用品、家電、3C、美妝保養、家居用品等），要具體到品牌或品項，例如「Dyson V15 吸塵器」「義美小泡芙」。\n")
				.append("2. 不要列：信用卡、電子支付、銀行、股票、保險、電信方案、App、遊戲點數、服務、餐廳、店家、通路、旅遊、活動、優惠券本身，也不要列太籠統的類別（例如「零食」「衣服」）。\n")
				.append("3. mentionText 必須「逐字」取自標題原文（可以只取其中一段），不可改寫、翻譯或補字。\n")
				.append("4. canonicalName 是這個商品的通用名稱（品牌＋品項），不要包含價格、數量、規格（如 500g、x3入）、優惠字樣。\n")
				.append("5. 同一個商品在多則標題出現時只列一次，把所有相關標題編號都放進 titleIds；titleIds 只能使用下方出現的編號。\n")
				.append("6. categoryHint 只能從「可選品類」清單中原樣挑一個；都不適合就填 null。\n")
				.append("7. 標題沒有提到具體商品就不要列，寧可少列也不要猜測。\n");
		List<String> examples = limit(notProductExamples);
		if (!examples.isEmpty()) {
			sb.append("8. 以下名稱曾被採購人員判定「不是實體商品」，同類型的東西不要列出：")
					.append(String.join("、", examples)).append('\n');
		}
		sb.append("\n可選品類：\n");
		if (categoryNames.isEmpty()) {
			sb.append("（無，categoryHint 一律填 null）\n");
		} else {
			categoryNames.forEach(name -> sb.append("- ").append(name).append('\n'));
		}
		sb.append("\n標題：\n");
		titlesById.forEach((id, title) -> sb.append(id).append('｜').append(title).append('\n'));
		return sb.toString();
	}

	static String buildFitPrompt(List<FitCandidate> candidates, String audienceContext, List<String> negativeExamples) {
		StringBuilder sb = new StringBuilder();
		sb.append("你是台灣團購零售業的選品顧問。請評估下列從 PTT 討論中發現的商品，是否適合放進我們的團購選品。\n\n")
				.append(audienceContext).append('\n')
				.append("評分規則：\n")
				.append("1. score 為 0~100 的整數：80 以上＝非常適合核心客群且適合團購；50 左右＝普通；30 以下＝不適合。\n")
				.append("2. 考量：是否符合核心客群的年齡、偏好與價格敏感度；是否適合團購（集單、可預期出貨、單價與客單合理、不需專業安裝或售後）；溫層與保存是否容易處理。\n")
				.append("3. 只依下方提供的資訊判斷，不要假設價格、供應商或銷量等未提供的數字；資訊不足時給中間分數並在 concerns 寫明。\n")
				.append("4. reason 用一到兩句繁體中文說明主要理由（60 字內）；concerns 列出最多 3 個簡短疑慮（每個 12 字內），沒有就給空陣列。\n")
				.append("5. 每個 id 都要回傳一筆，id 必須原樣使用下方提供的編號。\n");
		List<String> examples = limit(negativeExamples);
		if (!examples.isEmpty()) {
			sb.append("6. 採購人員曾略過下列項目（括號內為原因），與它們相似的商品請相應降低分數：")
					.append(String.join("、", examples)).append('\n');
		}
		sb.append("\n待評估商品（每行格式為「編號｜名稱｜品類｜PTT 標題摘錄」）：\n");
		for (FitCandidate candidate : candidates) {
			sb.append(candidate.id()).append('｜').append(candidate.name()).append('｜')
					.append(candidate.categoryHint() == null ? "品類未判定" : candidate.categoryHint()).append('｜')
					.append(String.join(" ／ ", candidate.sampleTitles())).append('\n');
		}
		return sb.toString();
	}

	private static List<String> limit(List<String> examples) {
		if (examples == null) {
			return List.of();
		}
		return examples.stream().filter(e -> e != null && !e.isBlank()).limit(MAX_NEGATIVE_EXAMPLES).toList();
	}

	// ========================= Request =========================

	/**
	 * OpenAI 相容的 Chat Completions 請求。response_format 用 strict json_schema（受限解碼）；
	 * reasoningEffort 空白時不送 reasoning 參數（非 gpt-oss 模型不接受）。
	 */
	static ObjectNode buildRequestBody(ObjectMapper mapper, String model, String prompt, String schemaName,
			ObjectNode schema, int maxCompletionTokens, String reasoningEffort) {
		ObjectNode root = mapper.createObjectNode();
		root.put("model", model);
		ObjectNode message = mapper.createObjectNode().put("role", "user").put("content", prompt);
		root.set("messages", mapper.createArrayNode().add(message));
		// 抽取與評分要的是穩定、可重現，不需要創意。
		root.put("temperature", 0);
		root.put("max_completion_tokens", maxCompletionTokens);
		if (reasoningEffort != null && !reasoningEffort.isBlank()) {
			root.put("reasoning_effort", reasoningEffort.trim());
			// 推理內容不回傳（仍會計入 token），回應只留結果 JSON
			root.put("include_reasoning", false);
		}
		ObjectNode jsonSchema = mapper.createObjectNode();
		jsonSchema.put("name", schemaName);
		jsonSchema.put("strict", true);
		jsonSchema.set("schema", schema);
		ObjectNode responseFormat = mapper.createObjectNode().put("type", "json_schema");
		responseFormat.set("json_schema", jsonSchema);
		root.set("response_format", responseFormat);
		return root;
	}

	/**
	 * { items: [ { canonicalName, mentionText, categoryHint|null, titleIds[] } ] }
	 * strict 模式規定每個欄位都要列在 required、每個物件都要 additionalProperties=false，可空欄位用 ["string","null"]。
	 */
	static ObjectNode extractionSchema(ObjectMapper mapper) {
		ObjectNode properties = mapper.createObjectNode();
		properties.set("canonicalName", type(mapper, "string"));
		properties.set("mentionText", type(mapper, "string"));
		ObjectNode nullableString = mapper.createObjectNode();
		nullableString.set("type", mapper.createArrayNode().add("string").add("null"));
		properties.set("categoryHint", nullableString);
		properties.set("titleIds", stringArray(mapper));
		return wrapItems(mapper, properties);
	}

	/** { items: [ { id, score, reason, concerns[] } ] } */
	static ObjectNode fitSchema(ObjectMapper mapper) {
		ObjectNode properties = mapper.createObjectNode();
		properties.set("id", type(mapper, "string"));
		properties.set("score", type(mapper, "integer"));
		properties.set("reason", type(mapper, "string"));
		properties.set("concerns", stringArray(mapper));
		return wrapItems(mapper, properties);
	}

	private static ObjectNode type(ObjectMapper mapper, String type) {
		return mapper.createObjectNode().put("type", type);
	}

	private static ObjectNode stringArray(ObjectMapper mapper) {
		ObjectNode array = type(mapper, "array");
		array.set("items", type(mapper, "string"));
		return array;
	}

	/** 把每個屬性都列為 required（strict 模式要求）後包成 { items: [...] }。 */
	private static ObjectNode wrapItems(ObjectMapper mapper, ObjectNode itemProperties) {
		ObjectNode itemSchema = strictObject(mapper, itemProperties);
		ObjectNode items = type(mapper, "array");
		items.set("items", itemSchema);
		ObjectNode rootProperties = mapper.createObjectNode();
		rootProperties.set("items", items);
		return strictObject(mapper, rootProperties);
	}

	private static ObjectNode strictObject(ObjectMapper mapper, ObjectNode properties) {
		ObjectNode object = type(mapper, "object");
		object.set("properties", properties);
		ArrayNode required = mapper.createArrayNode();
		properties.fieldNames().forEachRemaining(required::add);
		object.set("required", required);
		object.put("additionalProperties", false);
		return object;
	}

	// ========================= Response =========================

	/**
	 * 取出 Chat Completions 回應裡模型輸出的 items 陣列（套件層級，供單元測試直接餵 JSON 字串）。
	 * 被截斷（finish_reason=length）、被內容過濾、格式不符都丟例外，不回傳半套結果。
	 */
	static JsonNode extractItemsNode(ObjectMapper mapper, String responseJson) {
		JsonNode body;
		try {
			body = mapper.readTree(responseJson);
		} catch (Exception e) {
			throw new LlmAnalysisException("Groq 回應內容無法解析為JSON", e);
		}
		if (body == null) {
			throw new LlmAnalysisException("Groq API 回傳空白內容");
		}
		JsonNode choices = body.path("choices");
		if (!choices.isArray() || choices.isEmpty()) {
			throw new LlmAnalysisException("Groq API 未回傳任何結果");
		}
		JsonNode first = choices.get(0);
		String finishReason = first.path("finish_reason").asText("");
		if ("length".equals(finishReason)) {
			throw new LlmAnalysisException("Groq 回應超過輸出上限被截斷，請調低每批數量或調高 groq.max-completion-tokens");
		}
		if (!finishReason.isEmpty() && !"stop".equals(finishReason)) {
			throw new LlmAnalysisException("Groq 未正常完成回應，finish_reason=" + finishReason);
		}
		String rawText = first.path("message").path("content").asText(null);
		if (rawText == null || rawText.isBlank()) {
			throw new LlmAnalysisException("Groq 回應內容格式不符預期");
		}
		JsonNode parsed;
		try {
			parsed = mapper.readTree(rawText);
		} catch (Exception e) {
			throw new LlmAnalysisException("Groq 回應的結果無法解析為JSON", e);
		}
		JsonNode itemsNode = parsed.path("items");
		if (!itemsNode.isArray()) {
			throw new LlmAnalysisException("Groq 回應缺少 items 陣列");
		}
		return itemsNode;
	}

	/** 抽取結果。單筆欄位缺漏只略過那一筆。 */
	static List<ExtractedItem> parseItems(ObjectMapper mapper, String responseJson) {
		List<ExtractedItem> result = new ArrayList<>();
		for (JsonNode node : extractItemsNode(mapper, responseJson)) {
			String canonicalName = textOrNull(node.path("canonicalName"));
			String mentionText = textOrNull(node.path("mentionText"));
			JsonNode ids = node.path("titleIds");
			if (canonicalName == null || mentionText == null || !ids.isArray()) {
				continue;
			}
			result.add(new ExtractedItem(canonicalName, mentionText, textOrNull(node.path("categoryHint")),
					textList(ids)));
		}
		return result;
	}

	/** 評分結果（未驗證；範圍、id 由 DiscoveryFitRules.verifyFit 檢查）。分數不是數字的那筆略過。 */
	static List<FitResult> parseFitResults(ObjectMapper mapper, String responseJson) {
		List<FitResult> result = new ArrayList<>();
		for (JsonNode node : extractItemsNode(mapper, responseJson)) {
			String id = textOrNull(node.path("id"));
			JsonNode score = node.path("score");
			if (id == null || !score.isNumber()) {
				continue;
			}
			JsonNode concerns = node.path("concerns");
			result.add(new FitResult(id, score.asInt(), textOrNull(node.path("reason")),
					concerns.isArray() ? textList(concerns) : List.of()));
		}
		return result;
	}

	private static List<String> textList(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach(node -> {
			String value = textOrNull(node);
			if (value != null) {
				values.add(value);
			}
		});
		return values;
	}

	private static String textOrNull(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}
		String text = node.asText("").trim();
		return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
	}

	// ========================= HTTP =========================

	/**
	 * 實際的 HTTP 呼叫。寫法比照 GeminiAnalysisServiceImpl（String body、JdkClientHttpRequestFactory）——
	 * Spring Boot 4 下 Jackson 2／3 並存的轉換器問題見該類別註解。改用 toEntity 是為了拿到限流回應標頭。
	 */
	private Reply postWithRestClient(String url, String key, String requestJson) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
		ResponseEntity<String> response = RestClient.builder().requestFactory(requestFactory).build()
				.post()
				.uri(URI.create(url))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
				.contentType(MediaType.APPLICATION_JSON)
				.body(requestJson)
				.retrieve()
				.toEntity(String.class);
		HttpHeaders headers = response.getHeaders();
		return new Reply(response.getBody(), headers::getFirst);
	}
}
