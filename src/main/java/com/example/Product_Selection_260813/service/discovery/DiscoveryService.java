package com.example.Product_Selection_260813.service.discovery;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.Product_Selection_260813.common.exception.LlmAnalysisException;
import com.example.Product_Selection_260813.common.exception.SystemConfigurationException;
import com.example.Product_Selection_260813.entity.DiscoveredItem;
import com.example.Product_Selection_260813.entity.DiscoveredItemEvidence;
import com.example.Product_Selection_260813.entity.DiscoveryProcessedTitle;
import com.example.Product_Selection_260813.entity.ProductType;
import com.example.Product_Selection_260813.entity.AudienceProfile;
import com.example.Product_Selection_260813.enums.DiscoveredItemStatus;
import com.example.Product_Selection_260813.enums.DiscoveryDismissReason;
import com.example.Product_Selection_260813.enums.GoogleTrendStatus;
import com.example.Product_Selection_260813.repository.AudienceProfileRepository;
import com.example.Product_Selection_260813.repository.DiscoveredItemEvidenceRepository;
import com.example.Product_Selection_260813.repository.DiscoveredItemRepository;
import com.example.Product_Selection_260813.repository.DiscoveryProcessedTitleRepository;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.ProductTypeRepository;
import com.example.Product_Selection_260813.service.crawler.MarketBuzzSignal;
import com.example.Product_Selection_260813.service.crawler.PttBoardIndexParser;
import com.example.Product_Selection_260813.service.crawler.PttBoardIndexParser.PttIndexPage;
import com.example.Product_Selection_260813.service.crawler.PttBoardIndexParser.PttIndexPost;
import com.example.Product_Selection_260813.service.crawler.PttClient;
import com.example.Product_Selection_260813.service.crawler.PttMarketBuzzProvider;
import com.example.Product_Selection_260813.service.crawler.PttSelectors;
import com.example.Product_Selection_260813.service.discovery.DiscoveryFitRules.FitCandidate;
import com.example.Product_Selection_260813.service.discovery.DiscoveryFitRules.FitResult;
import com.example.Product_Selection_260813.service.discovery.DiscoveryFitRules.VerifiedFit;
import com.example.Product_Selection_260813.service.discovery.DiscoveryVerifier.ExtractedItem;
import com.example.Product_Selection_260813.service.resolver.AlgorithmSettings;
import com.example.Product_Selection_260813.service.resolver.ProductTypeAttributeResolver;
import com.example.Product_Selection_260813.service.trends.GoogleTrendSettings;
import com.example.Product_Selection_260813.service.trends.TrendInterest;
import com.example.Product_Selection_260813.service.trends.TrendInterestProvider;
import com.example.Product_Selection_260813.service.discovery.DiscoveryVerifier.VerifiedItem;

/**
 * PTT 新品探索的處理流程（第一階段，2026-09-29）。排程、執行紀錄、防止重複執行在
 * DiscoveryRunService；這裡只負責「跑一次」：
 *
 * <pre>
 * 1. 爬文章列表：現有 ptt.boards 每個看板，從最新一頁往舊翻，收集近 N 天（預設 7）的文章標題
 * 2. 規則過濾：去掉 Re:／Fw: 前綴並合併同標題、排除公告板務等分類（DiscoveryText）
 *    2'. 只送新標題：已成功處理過的標題（discovery_processed_titles）不再送 AI；既有項目的提及篇數、
 *        推文量、最後出現時間改由佐證文章重算（refreshWindowStats），不呼叫 AI
 * 3. AI 抽取：每批最多 80 則標題，抽出具體商品（GroqDiscoveryClient；2026-09-29 由 Gemini 改為 Groq）
 * 4. Java 驗證：名稱必須真的出現在引用的標題裡，否則丟棄（DiscoveryVerifier）
 * 5. 彙總：同一正規化名稱合併，累計提及篇數與推文量
 * 6. 排除既有商品：名稱與 products 相符者略過，交給既有熱度同步（ExistingProductMatcher）
 * 7. 寫入 discovered_items／discovered_item_evidence（DISMISSED、CONVERTED 維持原狀態，只更新數字）
 * 8. 查熱度：本次提及最多的前 N 個待處理項目（預設 10），用既有 PttMarketBuzzProvider 查 90 天
 *    討論量，換算方式與商品熱度完全相同，畫面上的分數可以直接比較
 * ── 第二階段（2026-09-29）──
 * 6'. 排除與「已略過」項目名稱非常相似的新結果（同名的仍更新原項目數字，狀態不變）
 * 3'. 略過原因為「不是實體商品」的名稱，當作 AI 抽取的反例
 * 7'. 溫層判定：依品類預設溫層對照通路支援溫層（DiscoveryFitRules.temperatureGate）
 * 9. 適配評分：從沒評過、或客群設定改過的待處理項目，每批 10 個交給 AI 打 0~100 分（附理由、疑慮），
 *    其他略過原因的項目當作反例；AI 分數只當參考，不與任何規則分數加權合成
 * 10. Google 趨勢交叉驗證：適配分最高、7 天內沒查過的前 N 個（預設 5），在 Google 趨勢來源已啟用
 *    且本月額度足夠時，用既有 SerpApi 來源查詢方向與成長率（額度與商品的 Google 趨勢共用）
 * </pre>
 *
 * <b>容錯：</b>單一看板失敗只略過該看板；某一批 AI 呼叫失敗只略過該批（其他批照常處理）；
 * 額度用完就停止後續 AI 呼叫，已抽到的結果照樣寫入。所有略過都寫進回傳的 warnings，
 * 由 DiscoveryRunService 記在執行紀錄上，不會無聲失敗。
 *
 * <b>對 PTT 的請求量：</b>7 個看板 × 最多 {@code discovery.max-index-pages-per-board} 頁，
 * 加上查熱度的 N 個關鍵字（每個最多 7 看板 × 5 頁），全部共用 PttClient 的 1 秒間隔與
 * synchronized，與熱度同步同時執行也不會加倍請求速度。
 */
@Service
public class DiscoveryService {

	private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);

	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final int KEYWORD_MAX_LENGTH = 100;
	private static final int NAME_MAX_LENGTH = 120;
	private static final int TITLE_MAX_LENGTH = 255;

	/**
	 * 一次執行的統計；warnings 為空代表每一步都成功。
	 *
	 * @param extractionFailed 有標題要送 AI，但沒有任何一批抽取成功（例如 AI 服務過載）。
	 *                         這時「抽出 0 筆」不代表 PTT 上沒有新品，執行紀錄要標成失敗，不能顯示「已完成」。
	 */
	public record RunResult(int postCount, int titleCount, int aiCallCount, int extractedCount, int rejectedCount,
			int matchedExistingCount, int similarDismissedCount, int newCount, int updatedCount, int fitCount,
			int googleCount, List<String> warnings, boolean extractionFailed) {
	}

	/** 一篇文章（爬蟲與佐證共用）。 */
	record CollectedPost(String board, String postPath, String title, int pushVolume, long postedAtEpochSeconds) {
	}

	/** 同一個正規化名稱在這次執行中的彙總。 */
	static final class Aggregate {
		final String normalizedName;
		final String canonicalName;
		final Map<String, Integer> mentionTexts = new HashMap<>();
		final Map<String, Integer> categoryHints = new HashMap<>();
		final Map<String, CollectedPost> posts = new LinkedHashMap<>();
		/** 寫入後的實體，供第 8 步查熱度使用。 */
		DiscoveredItem savedItem;

		Aggregate(String normalizedName, String canonicalName) {
			this.normalizedName = normalizedName;
			this.canonicalName = canonicalName;
		}

		int pushVolume() {
			return posts.values().stream().mapToInt(CollectedPost::pushVolume).sum();
		}
	}

	@Autowired
	private PttClient pttClient;

	@Autowired
	private PttMarketBuzzProvider pttMarketBuzzProvider;

	@Autowired
	private GroqDiscoveryClient groqDiscoveryClient;

	@Autowired
	private DiscoveredItemRepository discoveredItemRepository;

	@Autowired
	private DiscoveredItemEvidenceRepository discoveredItemEvidenceRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private DiscoveryProcessedTitleRepository processedTitleRepository;

	@Autowired
	private ProductTypeRepository productTypeRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	// ---------------- 第二階段 ----------------

	@Autowired
	private AudienceProfileRepository audienceProfileRepository;

	@Autowired
	private ProductTypeAttributeResolver productTypeAttributeResolver;

	@Autowired
	private AlgorithmSettings algorithmSettings;

	@Autowired
	private GoogleTrendSettings googleTrendSettings;

	@Autowired
	private TrendInterestProvider trendInterestProvider;

	@Value("${ptt.boards}")
	private List<String> boards;

	@Value("${discovery.recent-days:7}")
	private int recentDays;

	@Value("${discovery.max-index-pages-per-board:30}")
	private int maxIndexPagesPerBoard;

	@Value("${discovery.titles-per-ai-call:80}")
	private int titlesPerAiCall;

	/** 已處理標題紀錄保留天數；實際使用時至少是 recent-days 的 2 倍，避免視窗內的標題被當成新標題重送。 */
	@Value("${discovery.processed-title-retention-days:30}")
	private int processedTitleRetentionDays;

	/** findByTitleKeyIn 每次查詢的 key 數量上限。 */
	private static final int TITLE_KEY_QUERY_CHUNK = 500;

	@Value("${discovery.max-ai-calls-per-run:30}")
	private int maxAiCallsPerRun;

	@Value("${discovery.max-buzz-checks-per-run:10}")
	private int maxBuzzChecksPerRun;

	@Value("${discovery.max-fit-evaluations-per-run:40}")
	private int maxFitEvaluationsPerRun;

	@Value("${discovery.items-per-fit-call:10}")
	private int itemsPerFitCall;

	@Value("${discovery.max-google-checks-per-run:5}")
	private int maxGoogleChecksPerRun;

	/** 與 GoogleTrendService 共用同一個 SerpApi 請求間隔設定。 */
	@Value("${google-trends.request-delay-ms:2000}")
	private long googleRequestDelayMs;

	/** 同一項目多久內不重查 Google 趨勢（週資料，天天查沒有意義）。 */
	static final int GOOGLE_RECHECK_DAYS = 7;

	private final Clock clock = Clock.systemDefaultZone();

	/**
	 * 執行一次完整探索。
	 *
	 * @param shouldContinue 每個耗時步驟前詢問；回傳 false（例如 PTT 來源被停用）就提早結束，已完成的部分照樣寫入
	 */
	public RunResult run(BooleanSupplier shouldContinue) {
		// 沒有 API 金鑰就不必先花幾分鐘爬 PTT，直接讓這次執行失敗並記錄原因。
		if (!groqDiscoveryClient.isConfigured()) {
			throw new SystemConfigurationException("尚未設定 Groq API 金鑰（GROQ_API_KEY），PTT 新品探索無法執行");
		}
		List<String> warnings = new ArrayList<>();

		// 1. 爬文章列表
		List<CollectedPost> posts = crawlRecentPosts(shouldContinue, warnings);

		// 2. 規則過濾與同標題合併：key＝去掉 Re:/Fw: 之後的標題
		Map<String, List<CollectedPost>> postsByTitle = new LinkedHashMap<>();
		for (CollectedPost post : posts) {
			String cleaned = DiscoveryText.cleanTitle(post.title());
			if (DiscoveryText.isExcludedTitle(cleaned)) {
				continue;
			}
			postsByTitle.computeIfAbsent(cleaned, key -> new ArrayList<>()).add(post);
		}
		List<String> allTitles = new ArrayList<>(postsByTitle.keySet());

		// 2'. 只送新標題：先前已成功送過 AI 且結果已寫入的標題不再送（失敗的批次沒有被標記，下次會自然補跑）
		Set<String> processedKeys = findProcessedTitleKeys(allTitles);
		List<String> titles = allTitles.stream().filter(title -> !processedKeys.contains(titleKey(title))).toList();
		if (!processedKeys.isEmpty()) {
			log.info("PTT 新品探索：{} 則標題中 {} 則先前已處理過，本次只送 {} 則給 AI", allTitles.size(),
					allTitles.size() - titles.size(), titles.size());
		}

		// 略過回饋（第二階段）：已略過項目的名稱用來排除相似結果，原因用來當 AI 反例。
		List<DiscoveredItem> dismissed = discoveredItemRepository.findByStatusOrderByHandledAtDesc(
				DiscoveredItemStatus.DISMISSED);
		List<String> notProductExamples = dismissed.stream()
				.filter(item -> item.getDismissReasonCode() == DiscoveryDismissReason.NOT_A_PRODUCT)
				.map(DiscoveredItem::getDisplayName).toList();

		// 3～5. AI 抽取、驗證、彙總
		Map<String, Long> productTypeIdByCategory = loadCategories();
		List<String> categoryNames = new ArrayList<>(productTypeIdByCategory.keySet());
		Map<String, Aggregate> aggregates = new LinkedHashMap<>();
		int aiCalls = 0;
		int succeededBatches = 0;
		// 抽取成功的批次的標題；寫入成功後才標記為已處理（見下方 7 步）
		List<String> succeededTitles = new ArrayList<>();
		int extracted = 0;
		int rejected = 0;
		for (int start = 0; start < titles.size(); start += titlesPerAiCall) {
			if (!shouldContinue.getAsBoolean()) {
				warnings.add("PTT 來源在執行途中被停用，後續標題未處理");
				break;
			}
			if (aiCalls >= maxAiCallsPerRun) {
				warnings.add(String.format("已達單次 AI 呼叫上限 %d 次，剩餘 %d 則標題未處理", maxAiCallsPerRun,
						titles.size() - start));
				break;
			}
			Map<String, String> batch = new LinkedHashMap<>();
			for (int i = start; i < Math.min(start + titlesPerAiCall, titles.size()); i++) {
				batch.put("t" + (i + 1), titles.get(i));
			}
			List<ExtractedItem> items;
			try {
				aiCalls++;
				items = groqDiscoveryClient.extract(batch, categoryNames, notProductExamples);
			} catch (LlmAnalysisException e) {
				warnings.add("AI 抽取失敗（第 " + aiCalls + " 批）：" + e.getMessage());
				log.warn("PTT 新品探索第 {} 批 AI 抽取失敗，略過此批：{}", aiCalls, e.getMessage());
				if (e instanceof DiscoveryQuotaExceededException) {
					break; // 額度用完，後面每一批都會失敗
				}
				if (e instanceof DiscoveryAiUnavailableException) {
					// 已經重試到上限仍過載：後面的批次大概也會失敗，不再每批空等一輪重試
					int remaining = titles.size() - Math.min(start + titlesPerAiCall, titles.size());
					if (remaining > 0) {
						warnings.add("AI 服務暫時無法使用，剩餘 " + remaining + " 則標題未處理，請稍後重新執行");
					}
					break;
				}
				continue;
			}
			succeededBatches++;
			succeededTitles.addAll(batch.values());
			extracted += items.size();
			for (ExtractedItem item : items) {
				VerifiedItem verified = DiscoveryVerifier.verify(item, batch);
				if (verified == null) {
					rejected++;
					continue;
				}
				Aggregate aggregate = aggregates.computeIfAbsent(verified.normalizedName(),
						key -> new Aggregate(key, verified.canonicalName()));
				aggregate.mentionTexts.merge(verified.mentionText(), 1, Integer::sum);
				if (verified.categoryHint() != null && productTypeIdByCategory.containsKey(verified.categoryHint())) {
					aggregate.categoryHints.merge(verified.categoryHint(), 1, Integer::sum);
				}
				for (String titleId : verified.titleIds()) {
					for (CollectedPost post : postsByTitle.getOrDefault(batch.get(titleId), List.of())) {
						aggregate.posts.putIfAbsent(post.postPath(), post);
					}
				}
			}
		}

		// 6. 排除既有商品
		List<String> existingNames = productRepository.findAllNames().stream()
				.map(DiscoveryText::normalize).filter(name -> !name.isEmpty()).toList();
		int matchedExisting = 0;
		int similarDismissed = 0;
		Set<String> dismissedNames = dismissed.stream().map(DiscoveredItem::getNormalizedName)
				.collect(Collectors.toSet());
		List<String> dismissedNameList = new ArrayList<>(dismissedNames);
		List<Aggregate> candidates = new ArrayList<>();
		for (Aggregate aggregate : aggregates.values()) {
			if (ExistingProductMatcher.matchesAny(aggregate.normalizedName, existingNames)) {
				matchedExisting++;
			} else if (!dismissedNames.contains(aggregate.normalizedName)
					&& ExistingProductMatcher.matchesAny(aggregate.normalizedName, dismissedNameList)) {
				// 6'. 與已略過項目「相似但不同名」→ 排除。完全同名的照常進入寫入步驟，只更新原項目數字。
				similarDismissed++;
			} else {
				candidates.add(aggregate);
			}
		}

		// 7. 寫入
		// 探索項目與「已處理標題」在同一個交易：項目寫入失敗時標題不會被標成已處理，下次會重送。
		int[] counts = new TransactionTemplate(transactionManager).execute(status -> {
			int[] written = candidates.isEmpty() ? new int[] { 0, 0 } : persist(candidates, productTypeIdByCategory);
			markTitlesProcessed(succeededTitles);
			return written;
		});

		// 7'. 已處理標題不會再過 AI，所以既有項目的提及篇數／推文量／最後出現時間改由佐證文章重算
		refreshWindowStats(posts, existingNames, warnings);

		// 8. 查熱度
		checkBuzz(candidates, shouldContinue, warnings);

		// 9. 適配評分
		int[] fit = evaluateFit(candidates, dismissed, shouldContinue, warnings);

		// 10. Google 趨勢交叉驗證
		int googleChecked = checkGoogleTrends(candidates, shouldContinue, warnings);

		int retentionDays = Math.max(processedTitleRetentionDays, recentDays * 2);
		processedTitleRepository.deleteProcessedBefore(LocalDateTime.now(clock).minusDays(retentionDays));

		boolean extractionFailed = aiCalls > 0 && succeededBatches == 0;
		RunResult result = new RunResult(posts.size(), titles.size(), aiCalls + fit[1], extracted, rejected,
				matchedExisting, similarDismissed, counts[0], counts[1], fit[0], googleChecked, warnings,
				extractionFailed);
		log.info("PTT 新品探索完成：{} 篇文章、{} 則標題、AI {} 次、抽出 {} 筆（驗證丟棄 {}、既有商品 {}、與已略過相似 {}）、"
				+ "新增 {}、更新 {}、適配評分 {}、Google 趨勢 {}",
				result.postCount(), result.titleCount(), result.aiCallCount(), result.extractedCount(),
				result.rejectedCount(), result.matchedExistingCount(), result.similarDismissedCount(),
				result.newCount(), result.updatedCount(), result.fitCount(), result.googleCount());
		return result;
	}

	// ========================= 已處理標題 =========================

	/** SHA-256(normalize(標題))。標題傳入的是 cleanTitle 之後的結果（postsByTitle 的 key）。 */
	static String titleKey(String cleanedTitle) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(DiscoveryText.normalize(cleanedTitle).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 不可用", e);
		}
	}

	private Set<String> findProcessedTitleKeys(List<String> cleanedTitles) {
		List<String> keys = cleanedTitles.stream().map(DiscoveryService::titleKey).distinct().toList();
		Set<String> found = new HashSet<>();
		for (int i = 0; i < keys.size(); i += TITLE_KEY_QUERY_CHUNK) {
			List<String> chunk = keys.subList(i, Math.min(i + TITLE_KEY_QUERY_CHUNK, keys.size()));
			processedTitleRepository.findByTitleKeyIn(chunk)
					.forEach(processed -> found.add(processed.getTitleKey()));
		}
		return found;
	}

	/** 必須在交易內呼叫（與項目寫入同一個交易）。 */
	private void markTitlesProcessed(List<String> cleanedTitles) {
		if (cleanedTitles.isEmpty()) {
			return;
		}
		LocalDateTime now = LocalDateTime.now(clock);
		processedTitleRepository.saveAll(cleanedTitles.stream().map(DiscoveryService::titleKey).distinct()
				.map(key -> new DiscoveryProcessedTitle(key, now)).toList());
	}

	/**
	 * 不呼叫 AI，重算探索視窗內既有項目的統計，並把已處理標題的新文章（例如新的 Re: 回文）掛回原項目。
	 *
	 * <p>
	 * 為什麼需要：persist() 的提及篇數與推文量原本由「本次送 AI 的標題」彙總而來；標題被跳過後，
	 * 沒有這一步的話數字會掉成只剩新標題的部分。這裡改以視窗內的佐證文章為準：
	 * <ul>
	 * <li>提及篇數＝視窗內的佐證文章數（含剛掛上的新文章）。</li>
	 * <li>推文量＝各文章的最新推文量（本次爬到的用新值，沒爬到的用佐證上次記錄的值）。</li>
	 * <li>最後出現時間：視窗內有文章仍在本次爬取結果中，就更新為現在。</li>
	 * <li>與 persist() 相同，已建立商品（CONVERTED）與名稱相符既有商品者不更新（交給既有熱度同步）。</li>
	 * </ul>
	 * 失敗只記警告，不讓整次探索失敗：下次執行會再算一次。
	 */
	private void refreshWindowStats(List<CollectedPost> crawledPosts, List<String> existingNames,
			List<String> warnings) {
		try {
			new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
				LocalDateTime now = LocalDateTime.now(clock);
				LocalDateTime since = now.minusDays(recentDays);
				Map<String, CollectedPost> crawledByPath = new HashMap<>();
				crawledPosts.forEach(post -> crawledByPath.putIfAbsent(post.postPath(), post));

				List<DiscoveredItemEvidence> windowEvidence = discoveredItemEvidenceRepository
						.findByPostedAtGreaterThanEqual(since);
				if (windowEvidence.isEmpty()) {
					return;
				}

				// (a) 同標題的新文章掛回原項目。key：清理後標題 → 項目 id
				Map<String, Set<Long>> itemIdsByTitleKey = new HashMap<>();
				Set<String> knownItemPost = new HashSet<>();
				for (DiscoveredItemEvidence evidence : windowEvidence) {
					itemIdsByTitleKey.computeIfAbsent(titleKey(DiscoveryText.cleanTitle(evidence.getTitle())),
							key -> new HashSet<>()).add(evidence.getItemId());
					knownItemPost.add(evidence.getItemId() + "|" + evidence.getPostPath());
				}
				List<DiscoveredItemEvidence> linked = new ArrayList<>();
				for (CollectedPost post : crawledByPath.values()) {
					Set<Long> itemIds = itemIdsByTitleKey.get(titleKey(DiscoveryText.cleanTitle(post.title())));
					if (itemIds == null) {
						continue;
					}
					for (Long itemId : itemIds) {
						if (knownItemPost.add(itemId + "|" + post.postPath())) {
							DiscoveredItemEvidence evidence = new DiscoveredItemEvidence();
							evidence.setItemId(itemId);
							evidence.setBoard(truncate(post.board(), 40));
							evidence.setPostPath(truncate(post.postPath(), 120));
							evidence.setTitle(truncate(post.title(), TITLE_MAX_LENGTH));
							evidence.setPushVolume(post.pushVolume());
							evidence.setPostedAt(LocalDateTime.ofInstant(
									Instant.ofEpochSecond(post.postedAtEpochSeconds()), ZONE));
							evidence.setCollectedAt(now);
							linked.add(evidence);
						}
					}
				}
				if (!linked.isEmpty()) {
					discoveredItemEvidenceRepository.saveAll(linked);
					windowEvidence = new ArrayList<>(windowEvidence);
					windowEvidence.addAll(linked);
				}

				// (b) 逐項目重算統計
				Map<Long, List<DiscoveredItemEvidence>> evidenceByItem = windowEvidence.stream()
						.collect(Collectors.groupingBy(DiscoveredItemEvidence::getItemId));
				List<DiscoveredItem> changed = new ArrayList<>();
				for (DiscoveredItem item : discoveredItemRepository.findAllById(evidenceByItem.keySet())) {
					if (item.getStatus() == DiscoveredItemStatus.CONVERTED
							|| ExistingProductMatcher.matchesAny(item.getNormalizedName(), existingNames)) {
						continue;
					}
					List<DiscoveredItemEvidence> itemEvidence = evidenceByItem.get(item.getId());
					int push = 0;
					boolean seenNow = false;
					for (DiscoveredItemEvidence evidence : itemEvidence) {
						CollectedPost crawled = crawledByPath.get(evidence.getPostPath());
						push += crawled != null ? crawled.pushVolume()
								: (evidence.getPushVolume() == null ? 0 : evidence.getPushVolume());
						seenNow |= crawled != null;
					}
					boolean dirty = item.getMentionCount() == null || item.getMentionCount() != itemEvidence.size()
							|| item.getPushVolume() == null || item.getPushVolume() != push;
					item.setMentionCount(itemEvidence.size());
					item.setPushVolume(push);
					if (seenNow) {
						item.setLastSeenAt(now);
						dirty = true;
					}
					if (dirty) {
						changed.add(item);
					}
				}
				discoveredItemRepository.saveAll(changed);
			});
		} catch (RuntimeException e) {
			warnings.add("重算既有項目的提及篇數失敗（下次執行會再算）：" + e.getMessage());
			log.warn("PTT 新品探索：重算既有項目統計失敗", e);
		}
	}

	// ========================= 1. 爬文章列表 =========================

	private List<CollectedPost> crawlRecentPosts(BooleanSupplier shouldContinue, List<String> warnings) {
		long cutoff = clock.instant().getEpochSecond() - Duration.ofDays(recentDays).toSeconds();
		List<CollectedPost> result = new ArrayList<>();
		int succeededBoards = 0;
		for (String rawBoard : boards) {
			String board = rawBoard.trim();
			if (board.isEmpty()) {
				continue;
			}
			if (!shouldContinue.getAsBoolean()) {
				warnings.add("PTT 來源在執行途中被停用，未掃描完所有看板");
				break;
			}
			try {
				result.addAll(crawlBoard(board, cutoff, warnings));
				succeededBoards++;
			} catch (IOException | RuntimeException e) {
				warnings.add("看板 " + board + " 讀取失敗：" + e.getMessage());
				log.warn("PTT 新品探索：看板 {} 讀取失敗，略過：{}", board, e.getMessage());
			}
		}
		if (succeededBoards == 0 && !boards.isEmpty()) {
			throw new IllegalStateException("所有 PTT 看板都無法讀取，本次探索未執行");
		}
		return result;
	}

	private List<CollectedPost> crawlBoard(String board, long cutoffEpochSeconds, List<String> warnings)
			throws IOException {
		List<CollectedPost> result = new ArrayList<>();
		String path = String.format(PttSelectors.INDEX_PATH_FORMAT, board);
		for (int page = 1; page <= maxIndexPagesPerBoard && path != null; page++) {
			PttIndexPage parsed = PttBoardIndexParser.parse(pttClient.fetchBoardPage(path));
			if (parsed.over18Gated()) {
				log.info("PTT 看板 {} 需要年齡確認，依規範不繞過，略過此看板", board);
				return result;
			}
			boolean reachedOlder = false;
			for (PttIndexPost post : parsed.posts()) {
				if (post.postedAtEpochSeconds() < cutoffEpochSeconds) {
					reachedOlder = true;
					continue;
				}
				result.add(new CollectedPost(board, post.postPath(), post.title(), post.pushVolume(),
						post.postedAtEpochSeconds()));
			}
			if (reachedOlder) {
				return result;
			}
			if (page == maxIndexPagesPerBoard) {
				warnings.add("看板 " + board + " 已達翻頁上限 " + maxIndexPagesPerBoard + " 頁，較舊的文章未掃描");
			}
			path = parsed.previousPagePath();
		}
		return result;
	}

	// ========================= 3. 品類清單 =========================

	/** 「大類/小類」→ 小類 id。只給 AI 啟用中的小類，AI 回傳的 categoryHint 必須逐字對上才採用。 */
	private Map<String, Long> loadCategories() {
		Map<Long, String> majorNames = productTypeRepository.findByLevelAndIsActiveTrueOrderBySortOrderAsc(1).stream()
				.collect(Collectors.toMap(ProductType::getId, ProductType::getName, (a, b) -> a));
		Map<String, Long> result = new LinkedHashMap<>();
		for (ProductType minor : productTypeRepository.findByLevelAndIsActiveTrueOrderBySortOrderAsc(2)) {
			String major = majorNames.get(minor.getParentId());
			result.putIfAbsent(major == null ? minor.getName() : major + "/" + minor.getName(), minor.getId());
		}
		return result;
	}

	// ========================= 7. 寫入 =========================

	/** @return [新增筆數, 更新筆數] */
	private int[] persist(List<Aggregate> aggregates, Map<String, Long> productTypeIdByCategory) {
		Map<String, DiscoveredItem> existing = discoveredItemRepository
				.findByNormalizedNameIn(aggregates.stream().map(a -> a.normalizedName).toList()).stream()
				.collect(Collectors.toMap(DiscoveredItem::getNormalizedName, Function.identity(), (a, b) -> a));
		LocalDateTime now = LocalDateTime.now(clock);
		// 通路支援溫層可能隨時被調整，每次執行都重新判定，不沿用上次的結果。
		List<String> supportedZones = algorithmSettings.getSupportedTemperatureZones();
		int created = 0;
		int updated = 0;
		for (Aggregate aggregate : aggregates) {
			DiscoveredItem item = existing.get(aggregate.normalizedName);
			if (item == null) {
				item = new DiscoveredItem();
				item.setNormalizedName(aggregate.normalizedName);
				item.setDisplayName(truncate(aggregate.canonicalName, NAME_MAX_LENGTH));
				item.setStatus(DiscoveredItemStatus.NEW);
				item.setFirstSeenAt(now);
				created++;
			} else {
				updated++;
			}
			item.setSearchKeyword(truncate(mostFrequent(aggregate.mentionTexts, aggregate.canonicalName),
					KEYWORD_MAX_LENGTH));
			// 品類只在第一次有值時寫入，之後不讓 AI 的不同猜測來回覆蓋
			String hint = mostFrequent(aggregate.categoryHints, null);
			if (item.getCategoryHint() == null && hint != null) {
				item.setCategoryHint(truncate(hint, NAME_MAX_LENGTH));
				item.setProductTypeId(productTypeIdByCategory.get(hint));
			}
			// 7'. 溫層判定（第二階段）
			String zone = item.getProductTypeId() == null ? null
					: productTypeAttributeResolver.resolve(item.getProductTypeId()).temperatureZone().value();
			item.setTemperatureZone(zone);
			item.setTemperatureGate(DiscoveryFitRules.temperatureGate(zone, supportedZones));
			item.setMentionCount(aggregate.posts.size());
			item.setPushVolume(aggregate.pushVolume());
			item.setLastSeenAt(now);
			item.setModelName(truncate(groqDiscoveryClient.modelName(), 60));
			DiscoveredItem saved = discoveredItemRepository.save(item);
			saveEvidence(saved.getId(), aggregate.posts.values(), now);
			aggregate.savedItem = saved;
		}
		return new int[] { created, updated };
	}

	private void saveEvidence(Long itemId, Collection<CollectedPost> posts, LocalDateTime now) {
		Set<String> known = new HashSet<>();
		discoveredItemEvidenceRepository.findByItemId(itemId).forEach(e -> known.add(e.getPostPath()));
		List<DiscoveredItemEvidence> toSave = new ArrayList<>();
		for (CollectedPost post : posts) {
			if (!known.add(post.postPath())) {
				continue;
			}
			DiscoveredItemEvidence evidence = new DiscoveredItemEvidence();
			evidence.setItemId(itemId);
			evidence.setBoard(truncate(post.board(), 40));
			evidence.setPostPath(truncate(post.postPath(), 120));
			evidence.setTitle(truncate(post.title(), TITLE_MAX_LENGTH));
			evidence.setPushVolume(post.pushVolume());
			evidence.setPostedAt(LocalDateTime.ofInstant(Instant.ofEpochSecond(post.postedAtEpochSeconds()), ZONE));
			evidence.setCollectedAt(now);
			toSave.add(evidence);
		}
		if (!toSave.isEmpty()) {
			discoveredItemEvidenceRepository.saveAll(toSave);
		}
	}

	// ========================= 8. 查熱度 =========================

	private void checkBuzz(List<Aggregate> aggregates, BooleanSupplier shouldContinue, List<String> warnings) {
		List<DiscoveredItem> targets = aggregates.stream()
				.map(a -> a.savedItem)
				.filter(item -> item != null && item.getStatus() == DiscoveredItemStatus.NEW)
				.sorted(Comparator.comparing(DiscoveredItem::getMentionCount).reversed()
						.thenComparing(Comparator.comparing(DiscoveredItem::getPushVolume).reversed()))
				.limit(maxBuzzChecksPerRun)
				.toList();
		int failed = 0;
		for (DiscoveredItem item : targets) {
			if (!shouldContinue.getAsBoolean()) {
				warnings.add("PTT 來源在執行途中被停用，部分項目未查熱度");
				return;
			}
			MarketBuzzSignal signal;
			try {
				signal = pttMarketBuzzProvider.fetch(item.getSearchKeyword(), null);
			} catch (RuntimeException e) {
				failed++;
				log.warn("PTT 新品探索：「{}」熱度查詢失敗：{}", item.getSearchKeyword(), e.getMessage());
				continue;
			}
			new TransactionTemplate(transactionManager).execute(status -> {
				DiscoveredItem fresh = discoveredItemRepository.findById(item.getId()).orElse(null);
				if (fresh == null) {
					return null;
				}
				fresh.setPopularityScore(signal.popularityScore());
				fresh.setTrendScore(signal.trendScore());
				fresh.setTrendDirection(signal.trendDirection());
				fresh.setWindowVolume(signal.discussionVolume());
				fresh.setBuzzCheckedAt(LocalDateTime.now(clock));
				return discoveredItemRepository.save(fresh);
			});
		}
		if (failed > 0) {
			warnings.add(failed + " 個項目熱度查詢失敗，保留提及篇數，熱度留空");
		}
	}

	// ========================= 9. 適配評分（第二階段） =========================

	/** 每個項目送給 AI 的標題摘錄篇數與長度上限。 */
	private static final int SAMPLE_TITLES = 2;
	private static final int SAMPLE_TITLE_LENGTH = 80;

	/**
	 * @return [完成評分的項目數, AI 呼叫次數]
	 */
	private int[] evaluateFit(List<Aggregate> aggregates, List<DiscoveredItem> dismissed,
			BooleanSupplier shouldContinue, List<String> warnings) {
		List<AudienceProfile> profiles = audienceProfileRepository.findByIsActiveTrue();
		String signature = DiscoveryFitRules.audienceSignature(profiles);
		List<Aggregate> targets = aggregates.stream()
				.filter(a -> a.savedItem != null && a.savedItem.getStatus() == DiscoveredItemStatus.NEW)
				.filter(a -> DiscoveryFitRules.needsFitEvaluation(a.savedItem, signature))
				.sorted(Comparator.comparing((Aggregate a) -> a.posts.size()).reversed()
						.thenComparing(Comparator.comparing(Aggregate::pushVolume).reversed()))
				.limit(maxFitEvaluationsPerRun)
				.toList();
		if (targets.isEmpty()) {
			return new int[] { 0, 0 };
		}

		String context = DiscoveryFitRules.describeContext(profiles, algorithmSettings.getSupportedTemperatureZones());
		// 「不是實體商品」已經回饋到抽取階段；其餘原因（不適合團購、超出經營品類…）才是適配評分的反例。
		List<String> negativeExamples = dismissed.stream()
				.filter(item -> item.getDismissReasonCode() != null
						&& item.getDismissReasonCode() != DiscoveryDismissReason.NOT_A_PRODUCT)
				.map(item -> item.getDisplayName() + "（" + item.getDismissReasonCode().getLabel() + "）")
				.toList();

		int evaluated = 0;
		int calls = 0;
		int missing = 0;
		for (int start = 0; start < targets.size(); start += itemsPerFitCall) {
			if (!shouldContinue.getAsBoolean()) {
				warnings.add("PTT 來源在執行途中被停用，部分項目未做適配評分");
				break;
			}
			Map<String, DiscoveredItem> byId = new LinkedHashMap<>();
			List<FitCandidate> batch = new ArrayList<>();
			for (Aggregate aggregate : targets.subList(start, Math.min(start + itemsPerFitCall, targets.size()))) {
				DiscoveredItem item = aggregate.savedItem;
				String id = "d" + item.getId();
				byId.put(id, item);
				batch.add(new FitCandidate(id, item.getDisplayName(), item.getCategoryHint(),
						aggregate.posts.values().stream().limit(SAMPLE_TITLES)
								.map(post -> truncate(post.title(), SAMPLE_TITLE_LENGTH)).toList()));
			}
			List<FitResult> results;
			try {
				calls++;
				results = groqDiscoveryClient.evaluateFit(batch, context, negativeExamples);
			} catch (LlmAnalysisException e) {
				warnings.add("適配評分失敗（第 " + calls + " 批）：" + e.getMessage());
				log.warn("PTT 新品探索第 {} 批適配評分失敗：{}", calls, e.getMessage());
				if (e instanceof DiscoveryQuotaExceededException || e instanceof DiscoveryAiUnavailableException) {
					break; // 額度用完或服務過載：後面的批次也會失敗，未評的項目下次執行再評
				}
				continue;
			}
			Map<String, VerifiedFit> verified = DiscoveryFitRules.verifyFit(results, byId.keySet());
			missing += byId.size() - verified.size();
			LocalDateTime now = LocalDateTime.now(clock);
			String model = truncate(groqDiscoveryClient.modelName(), 60);
			new TransactionTemplate(transactionManager).execute(status -> {
				verified.forEach((id, fit) -> discoveredItemRepository.findById(byId.get(id).getId()).ifPresent(fresh -> {
					fresh.setFitScore(BigDecimal.valueOf(fit.score()));
					fresh.setFitReason(fit.reason());
					fresh.setFitConcerns(fit.concerns());
					fresh.setFitAudienceSig(signature);
					fresh.setFitModel(model);
					fresh.setFitEvaluatedAt(now);
					discoveredItemRepository.save(fresh);
				}));
				return null;
			});
			evaluated += verified.size();
		}
		if (missing > 0) {
			warnings.add(missing + " 個項目 AI 未回傳有效評分，下次執行再評");
		}
		return new int[] { evaluated, calls };
	}

	// ========================= 10. Google 趨勢交叉驗證（第二階段） =========================

	/** @return 本次成功查詢（含查無資料）的項目數 */
	private int checkGoogleTrends(List<Aggregate> aggregates, BooleanSupplier shouldContinue, List<String> warnings) {
		// Google 趨勢來源預設停用；停用或沒有金鑰就是不查，不算異常，不寫進警告。
		if (!googleTrendSettings.isEnabled() || !trendInterestProvider.isConfigured()) {
			return 0;
		}
		int budget = Math.min(maxGoogleChecksPerRun, googleTrendSettings.getRemainingThisMonth());
		if (budget <= 0) {
			warnings.add("Google 趨勢本月額度已用完，略過交叉驗證");
			return 0;
		}
		List<Long> ids = aggregates.stream().map(a -> a.savedItem).filter(Objects::nonNull)
				.map(DiscoveredItem::getId).toList();
		LocalDateTime recheckBefore = LocalDateTime.now(clock).minusDays(GOOGLE_RECHECK_DAYS);
		// 重新讀取：適配分是上一步才寫進去的
		List<DiscoveredItem> targets = discoveredItemRepository.findAllById(ids).stream()
				.filter(item -> item.getStatus() == DiscoveredItemStatus.NEW && item.getFitScore() != null)
				.filter(item -> item.getGoogleCheckedAt() == null || item.getGoogleCheckedAt().isBefore(recheckBefore))
				.sorted(Comparator.comparing(DiscoveredItem::getFitScore).reversed())
				.limit(budget)
				.toList();

		int checked = 0;
		int failed = 0;
		for (DiscoveredItem item : targets) {
			if (!shouldContinue.getAsBoolean()) {
				break;
			}
			if (checked + failed > 0 && !sleepQuietly(googleRequestDelayMs)) {
				break;
			}
			TrendInterest interest;
			try {
				interest = trendInterestProvider.fetch(item.getSearchKeyword());
			} catch (RuntimeException e) {
				failed++;
				log.warn("PTT 新品探索：「{}」Google 趨勢查詢失敗：{}", item.getSearchKeyword(), e.getMessage());
				continue;
			}
			// SerpApi 回成功（含查無資料）就計費，與 GoogleTrendService 相同。
			googleTrendSettings.recordCall();
			LocalDateTime now = LocalDateTime.now(clock);
			new TransactionTemplate(transactionManager).execute(status -> {
				discoveredItemRepository.findById(item.getId()).ifPresent(fresh -> {
					fresh.setGoogleStatus(interest.status());
					fresh.setGoogleDirection(interest.status() == GoogleTrendStatus.OK ? interest.direction() : null);
					fresh.setGoogleGrowthRate(interest.status() == GoogleTrendStatus.OK ? interest.growthRate() : null);
					fresh.setGoogleCheckedAt(now);
					discoveredItemRepository.save(fresh);
				});
				return null;
			});
			checked++;
		}
		if (failed > 0) {
			warnings.add(failed + " 個項目 Google 趨勢查詢失敗");
		}
		return checked;
	}

	private static boolean sleepQuietly(long millis) {
		if (millis <= 0) {
			return true;
		}
		try {
			Thread.sleep(millis);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	// ========================= 工具 =========================

	static String mostFrequent(Map<String, Integer> counts, String fallback) {
		return counts.entrySet().stream()
				.max(Map.Entry.<String, Integer>comparingByValue()
						.thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
				.map(Map.Entry::getKey)
				.orElse(fallback);
	}

	private static String truncate(String value, int max) {
		if (value == null) {
			return null;
		}
		return value.length() > max ? value.substring(0, max) : value;
	}
}
