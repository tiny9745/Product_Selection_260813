package com.example.Product_Selection_260813.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.Product_Selection_260813.dto.response.GoogleTrendRunResponse;
import com.example.Product_Selection_260813.dto.response.GoogleTrendSignalResponse;
import com.example.Product_Selection_260813.dto.response.GoogleTrendStatusResponse;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.entity.GoogleTrendRun;
import com.example.Product_Selection_260813.entity.GoogleTrendSignal;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.TrendSignal;
import com.example.Product_Selection_260813.enums.GoogleTrendStatus;
import com.example.Product_Selection_260813.enums.ProductItemStatus;
import com.example.Product_Selection_260813.enums.TrendSyncRunStatus;
import com.example.Product_Selection_260813.enums.TrendSyncTrigger;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.repository.GoogleTrendRunRepository;
import com.example.Product_Selection_260813.repository.GoogleTrendSignalRepository;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.TrendSignalRepository;
import com.example.Product_Selection_260813.service.crawler.PttMarketBuzzProvider;
import com.example.Product_Selection_260813.service.trends.GoogleTrendSettings;
import com.example.Product_Selection_260813.service.trends.TrendInterest;
import com.example.Product_Selection_260813.service.trends.TrendInterestProvider;

import jakarta.annotation.PreDestroy;

/**
 * Google 趨勢（第二資料源）：單一商品查詢、每週批次、執行紀錄與控制面板狀態。
 *
 * <b>定位：獨立參考資訊，不併入熱度分數。</b>結果寫在 google_trend_signals，不碰 trend_signals，
 * 所以熱度排行榜、AI 主動選品（熱度 &gt; 70 門檻）、評分與 Gemini prompt 完全不受影響，
 * 也不需要重新校準門檻。理由見 V30 migration 說明。
 *
 * <b>額度：</b>SerpApi 免費方案每月 250 次，「查無資料」也計費。所以：
 * <ul>
 * <li>批次只查「最新一筆 PTT 真實資料、熱度 &gt; 0」的前 N 名（預設 40）——PTT 都沒人討論的冷門品名，
 * 實測 Google 也多半查無資料，查了只是白花額度</li>
 * <li>每週一次就夠：一次查詢就回傳近 3 個月的完整序列，成長率當下就算得出來，不用每天累積</li>
 * <li>每次呼叫前檢查本月剩餘額度，用完就停（單一查詢回 409、批次提早結束並記錄原因）</li>
 * </ul>
 *
 * <b>單一商品查詢限管理層：</b>每按一次就花一次額度，操作層只看結果。
 */
@Service
public class GoogleTrendService {

	private static final Logger log = LoggerFactory.getLogger(GoogleTrendService.class);

	public static final String SCHEDULE_DESCRIPTION = "每週一 04:00（PTT 熱度同步 02:00、AI 選品批次 03:00 之後）";

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private TrendSignalRepository trendSignalRepository;

	@Autowired
	private GoogleTrendSignalRepository googleTrendSignalRepository;

	@Autowired
	private GoogleTrendRunRepository googleTrendRunRepository;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private TrendInterestProvider trendInterestProvider;

	@Autowired
	private GoogleTrendSettings googleTrendSettings;

	/** 每次批次最多查詢的商品數（PTT 熱度前 N 名）。40 個 × 每週 1 次 ≈ 每月 170 次，在免費額度內。 */
	@Value("${google-trends.batch-size:40}")
	private int batchSize;

	/** 批次中兩次呼叫的間隔，避免短時間內對 SerpApi 連續送出請求。 */
	@Value("${google-trends.request-delay-ms:2000}")
	private long requestDelayMs;

	private final ExecutorService manualRunExecutor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "google-trend-manual");
		thread.setDaemon(true);
		return thread;
	});

	private final AtomicBoolean running = new AtomicBoolean(false);

	/** 執行中的進度（已處理、總數），只存在記憶體；結束後清空。 */
	private final AtomicReference<int[]> progress = new AtomicReference<>();

	@PreDestroy
	void shutdown() {
		manualRunExecutor.shutdownNow();
	}

	/** 上次應用程式在批次途中被關掉時，那筆紀錄會停在 RUNNING，啟動時改成中斷。 */
	@EventListener(ApplicationReadyEvent.class)
	public void markInterruptedRuns() {
		List<GoogleTrendRun> stale = googleTrendRunRepository.findByStatus(TrendSyncRunStatus.RUNNING);
		for (GoogleTrendRun run : stale) {
			run.setStatus(TrendSyncRunStatus.FAILED);
			run.setFinishedAt(LocalDateTime.now());
			run.setMessage("應用程式在查詢途中關閉，本次未完成");
			googleTrendRunRepository.save(run);
		}
	}

	// ========================= 單一商品 =========================

	/** GET /api/products/{id}/google-trend：最新一筆；尚未查詢過回傳 null。 */
	public GoogleTrendSignalResponse getLatest(Long productId) {
		if (!productRepository.existsById(productId)) {
			throw new IllegalArgumentException("商品不存在");
		}
		return googleTrendSignalRepository.findFirstByProductIdOrderByCollectedAtDesc(productId)
				.map(GoogleTrendSignalResponse::from)
				.orElse(null);
	}

	/**
	 * POST /api/products/{id}/google-trend/sync（限管理層）：立即查詢一個商品，花費 1 次額度。
	 *
	 * @throws IllegalStateException 來源停用、金鑰未設定或本月額度用完（409）
	 * @throws com.example.Product_Selection_260813.service.trends.TrendInterestUnavailableException 呼叫失敗（502）
	 */
	public GoogleTrendSignalResponse syncProduct(Long productId) {
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new IllegalArgumentException("商品不存在"));
		ensureCanCall();
		return GoogleTrendSignalResponse.from(fetchAndSave(product));
	}

	// ========================= 批次 =========================

	/**
	 * 每週一 04:00 查詢 PTT 熱度前 N 名。排在 02:00 PTT 同步之後（要用當天的熱度挑商品）、
	 * 05:00 天氣同步之前；Google 趨勢不影響 03:00 AI 選品批次，所以先後無所謂。
	 */
	@Scheduled(cron = "0 0 4 * * MON")
	public void scheduledRun() {
		if (!googleTrendSettings.isEnabled()) {
			saveSkipped("Google 趨勢來源已停用，本次排程未執行");
			return;
		}
		if (!trendInterestProvider.isConfigured()) {
			saveSkipped("尚未設定 SerpApi 金鑰，本次排程未執行");
			return;
		}
		if (!running.compareAndSet(false, true)) {
			saveSkipped("已有手動觸發的查詢正在執行，本次排程未重複執行");
			return;
		}
		GoogleTrendRun run;
		try {
			run = startRecord(TrendSyncTrigger.SCHEDULED, null);
		} catch (RuntimeException e) {
			running.set(false);
			throw e;
		}
		execute(run);
	}

	/** 管理層按「立即查詢熱度前 N 名」：建立紀錄後立即回傳，查詢在背景執行。 */
	public GoogleTrendStatusResponse startManualRun(String username) {
		ensureCanCall();
		if (!running.compareAndSet(false, true)) {
			throw new IllegalStateException("已有 Google 趨勢查詢正在執行中，請等目前這次完成後再試");
		}
		GoogleTrendRun run;
		try {
			run = startRecord(TrendSyncTrigger.MANUAL, resolveUserId(username));
		} catch (RuntimeException e) {
			running.set(false);
			throw e;
		}
		manualRunExecutor.submit(() -> execute(run));
		return getStatus();
	}

	/**
	 * 批次查詢對象：每個未封存商品最新一筆趨勢資料是 PTT 真實資料、且熱度 &gt; 0，
	 * 依熱度由高到低取前 batchSize 個。模擬資料不算（那不是真的有人討論）。
	 */
	public List<Product> findCandidates() {
		List<Long> ids = trendSignalRepository.findLatestSignalsRankedByScore(batchSize).stream()
				.filter(signal -> PttMarketBuzzProvider.SOURCE.equals(signal.getSource()))
				.filter(signal -> signal.getPopularityScore() != null && signal.getPopularityScore().signum() > 0)
				.map(TrendSignal::getProductId)
				.toList();
		Map<Long, Product> byId = productRepository.findAllById(ids).stream()
				.filter(product -> product.getItemStatus() == ProductItemStatus.ACTIVE)
				.collect(Collectors.toMap(Product::getId, Function.identity()));
		// 維持熱度排序
		return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
	}

	// ========================= 控制面板 =========================

	public GoogleTrendStatusResponse getStatus() {
		List<GoogleTrendRun> runs = googleTrendRunRepository.findTop10ByOrderByStartedAtDesc();
		Map<Long, String> userNames = appUserRepository.findAllById(runs.stream()
				.map(GoogleTrendRun::getTriggeredBy).filter(Objects::nonNull).distinct().toList())
				.stream().collect(Collectors.toMap(AppUser::getId, AppUser::getName, (a, b) -> a));
		int[] current = running.get() ? progress.get() : null;
		return new GoogleTrendStatusResponse(
				googleTrendSettings.isEnabled(),
				trendInterestProvider.isConfigured(),
				googleTrendSettings.getUsedThisMonth(),
				googleTrendSettings.getMonthlyLimit(),
				running.get(),
				current == null ? null : current[0],
				current == null ? null : current[1],
				batchSize,
				SCHEDULE_DESCRIPTION,
				runs.stream().map(run -> GoogleTrendRunResponse.from(run,
						run.getTriggeredBy() == null ? null : userNames.get(run.getTriggeredBy()))).toList());
	}

	public GoogleTrendStatusResponse setEnabled(boolean enabled, String username) {
		googleTrendSettings.setEnabled(enabled, resolveUserId(username));
		log.info("Google 趨勢來源已{}（操作者：{}）", enabled ? "啟用" : "停用", username);
		return getStatus();
	}

	// ========================= 內部 =========================

	private void ensureCanCall() {
		if (!googleTrendSettings.isEnabled()) {
			throw new IllegalStateException("Google 趨勢來源目前已停用，請管理者到系統設定啟用後再查詢");
		}
		if (!trendInterestProvider.isConfigured()) {
			throw new IllegalStateException("尚未設定 SerpApi 金鑰（環境變數 SERPAPI_API_KEY），請聯絡維運人員設定");
		}
		if (googleTrendSettings.getRemainingThisMonth() <= 0) {
			throw new IllegalStateException("本月 Google 趨勢查詢次數已達上限（" + googleTrendSettings.getMonthlyLimit()
					+ " 次），下個月自動重置");
		}
	}

	/** 呼叫來源 → 計入額度 → 寫入一筆。呼叫失敗直接往外丟，不計額度（SerpApi 失敗不計費）、不寫資料。 */
	private GoogleTrendSignal fetchAndSave(Product product) {
		String keyword = TrendService.toSearchKeyword(product.getName());
		TrendInterest interest = trendInterestProvider.fetch(keyword);
		googleTrendSettings.recordCall();

		GoogleTrendSignal signal = new GoogleTrendSignal();
		signal.setProductId(product.getId());
		signal.setKeyword(keyword);
		signal.setStatus(interest.status());
		signal.setDirection(interest.direction());
		signal.setGrowthRate(interest.growthRate());
		signal.setRecentAvg(interest.recentAvg());
		signal.setBaselineAvg(interest.baselineAvg());
		signal.setPointCount(interest.pointCount());
		signal.setCollectedAt(LocalDateTime.now());
		return googleTrendSignalRepository.save(signal);
	}

	/** running 旗標必須已由呼叫端設為 true；結束時一定會放掉。 */
	private void execute(GoogleTrendRun run) {
		int ok = 0;
		int noData = 0;
		int failed = 0;
		try {
			List<Product> candidates = findCandidates();
			int remaining = googleTrendSettings.getRemainingThisMonth();
			List<Product> targets = candidates.size() > remaining ? candidates.subList(0, remaining) : candidates;
			run.setTotalCount(targets.size());
			if (targets.size() < candidates.size()) {
				run.setMessage(String.format("本月剩餘額度 %d 次，只查詢前 %d 個商品（共 %d 個符合條件）",
						remaining, targets.size(), candidates.size()));
			} else if (targets.isEmpty()) {
				run.setMessage("沒有符合條件的商品（需要最新一筆 PTT 熱度 > 0）");
			}
			googleTrendRunRepository.save(run);
			progress.set(new int[] { 0, targets.size() });
			log.info("開始 Google 趨勢批次查詢（{}）：共 {} 個商品", run.getTriggerType(), targets.size());

			for (int i = 0; i < targets.size(); i++) {
				Product product = targets.get(i);
				if (!googleTrendSettings.isEnabled()) {
					run.setMessage(String.format("執行途中來源被停用，已處理 %d / %d 個商品後停止", i, targets.size()));
					break;
				}
				if (i > 0 && !sleepBetweenRequests()) {
					break;
				}
				try {
					GoogleTrendSignal saved = fetchAndSave(product);
					if (saved.getStatus() == GoogleTrendStatus.OK) {
						ok++;
					} else {
						noData++;
					}
				} catch (RuntimeException e) {
					failed++;
					log.warn("商品 {}（{}）Google 趨勢查詢失敗，繼續下一個：{}", product.getId(), product.getName(),
							e.getMessage());
				}
				progress.set(new int[] { i + 1, targets.size() });
			}
			run.setStatus(TrendSyncRunStatus.COMPLETED);
		} catch (RuntimeException e) {
			log.error("Google 趨勢批次查詢中斷", e);
			run.setStatus(TrendSyncRunStatus.FAILED);
			run.setMessage("查詢中斷：" + truncate(String.valueOf(e.getMessage()), 240));
		} finally {
			run.setOkCount(ok);
			run.setNoDataCount(noData);
			run.setFailedCount(failed);
			run.setFinishedAt(LocalDateTime.now());
			try {
				googleTrendRunRepository.save(run);
			} finally {
				progress.set(null);
				running.set(false);
			}
		}
		log.info("Google 趨勢批次查詢結束（{}）：取得序列 {}、查無資料 {}、失敗 {}", run.getStatus(), ok, noData, failed);
	}

	private boolean sleepBetweenRequests() {
		try {
			Thread.sleep(requestDelayMs);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private GoogleTrendRun startRecord(TrendSyncTrigger trigger, Long triggeredBy) {
		GoogleTrendRun run = new GoogleTrendRun();
		run.setTriggerType(trigger);
		run.setStatus(TrendSyncRunStatus.RUNNING);
		run.setStartedAt(LocalDateTime.now());
		run.setTriggeredBy(triggeredBy);
		return googleTrendRunRepository.save(run);
	}

	private void saveSkipped(String message) {
		GoogleTrendRun run = new GoogleTrendRun();
		run.setTriggerType(TrendSyncTrigger.SCHEDULED);
		run.setStatus(TrendSyncRunStatus.SKIPPED);
		run.setStartedAt(LocalDateTime.now());
		run.setFinishedAt(LocalDateTime.now());
		run.setMessage(message);
		googleTrendRunRepository.save(run);
		log.info("{}", message);
	}

	private Long resolveUserId(String username) {
		return appUserRepository.findByUsername(username)
				.map(AppUser::getId)
				.orElseThrow(() -> new IllegalArgumentException("使用者不存在"));
	}

	private static String truncate(String message, int max) {
		return message.length() > max ? message.substring(0, max) : message;
	}
}
