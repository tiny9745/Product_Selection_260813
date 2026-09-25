package com.example.Product_Selection_260813.service;

import java.time.Duration;
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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.Product_Selection_260813.dto.response.TrendCrawlerStatusResponse;
import com.example.Product_Selection_260813.dto.response.TrendSyncRunResponse;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.TrendSyncRun;
import com.example.Product_Selection_260813.enums.TrendSyncRunStatus;
import com.example.Product_Selection_260813.enums.TrendSyncTrigger;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.repository.TrendSyncRunRepository;
import com.example.Product_Selection_260813.service.TrendService.SyncAllResult;
import com.example.Product_Selection_260813.service.crawler.TrendCrawlerSettings;

import jakarta.annotation.PreDestroy;

/**
 * PTT 熱度「全商品同步」的排程、手動觸發與執行紀錄（系統設定 → 爬蟲排程控制）。
 * 逐一同步商品的邏輯在 TrendService.syncAll()，這裡只管「什麼時候跑、同一時間只跑一個、
 * 跑完留下紀錄」。
 *
 * <b>同一時間只允許一個全商品同步：</b>凌晨排程與管理者手動觸發共用 running 旗標。
 * 同時跑兩個不會出錯，但會讓每個商品在同一晚被寫入兩筆趨勢資料，對 PTT 的請求量也加倍。
 *
 * <b>手動觸發在背景執行：</b>全商品同步每個商品約 8 秒，幾十個商品就要好幾分鐘，
 * 不能讓 HTTP 請求一直掛著等。POST 立即回 202，畫面輪詢 GET 看進度。
 * 用獨立的單一執行緒，不佔用排程執行緒，也不需要額外開 @EnableAsync。
 */
@Service
public class TrendSyncRunService {

	private static final Logger log = LoggerFactory.getLogger(TrendSyncRunService.class);

	/** 超過這個時間會壓到 03:00 的 AI 建議批次（見 scheduledRun() 說明）。 */
	private static final Duration SCHEDULE_WARNING_DURATION = Duration.ofMinutes(50);

	public static final String SCHEDULE_DESCRIPTION = "每天 02:00（早於 03:00 AI 主動選品批次）";

	@Autowired
	private TrendService trendService;

	@Autowired
	private TrendCrawlerSettings trendCrawlerSettings;

	@Autowired
	private TrendSyncRunRepository trendSyncRunRepository;

	@Autowired
	private AppUserRepository appUserRepository;

	private final ExecutorService manualRunExecutor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "trend-sync-manual");
		thread.setDaemon(true);
		return thread;
	});

	private final AtomicBoolean running = new AtomicBoolean(false);

	/** 執行中的即時進度，只存在記憶體；結束後清空，最終數字以 trend_sync_runs 為準。 */
	private final AtomicReference<SyncAllResult> progress = new AtomicReference<>();

	@PreDestroy
	void shutdown() {
		manualRunExecutor.shutdownNow();
	}

	/**
	 * 上次應用程式在同步途中被關掉時，那筆紀錄會停在 RUNNING。啟動時改成「中斷」，
	 * 否則畫面會永遠顯示有一個同步在執行中。
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void markInterruptedRuns() {
		List<TrendSyncRun> stale = trendSyncRunRepository.findByStatus(TrendSyncRunStatus.RUNNING);
		for (TrendSyncRun run : stale) {
			run.setStatus(TrendSyncRunStatus.FAILED);
			run.setFinishedAt(LocalDateTime.now());
			run.setMessage("應用程式在同步途中關閉，本次未完成");
			trendSyncRunRepository.save(run);
		}
		if (!stale.isEmpty()) {
			log.warn("發現 {} 筆上次未完成的 PTT 熱度同步紀錄，已標記為中斷", stale.size());
		}
	}

	/**
	 * 每天 02:00 同步所有未封存商品的 PTT 熱度。
	 *
	 * ⚠️ 排程順序不可調換：02:00 本排程 → 03:00 AiSuggestionBatchService →
	 * 05:00 WeatherCampaignSyncService。AI 建議批次讀的是 trend_signals 最新資料，
	 * 本排程若晚於 03:00，AI 建議永遠看到前一天的討論量，而且不會有任何錯誤提示。
	 * 每個商品約 8 秒，若總耗時接近 1 小時會壓到 03:00，屆時需要減少看板數或請求間隔。
	 */
	@Scheduled(cron = "0 0 2 * * *")
	public void scheduledRun() {
		if (!trendCrawlerSettings.isPttEnabled()) {
			saveSkipped(TrendSyncTrigger.SCHEDULED, "PTT 來源已停用，本次排程未執行");
			log.info("PTT 來源已停用，略過今日熱度同步排程");
			return;
		}
		if (!running.compareAndSet(false, true)) {
			saveSkipped(TrendSyncTrigger.SCHEDULED, "已有手動觸發的同步正在執行，本次排程未重複執行");
			log.info("已有全商品同步正在執行，略過今日熱度同步排程");
			return;
		}
		TrendSyncRun run;
		try {
			run = startRecord(TrendSyncTrigger.SCHEDULED, null);
		} catch (RuntimeException e) {
			running.set(false);
			throw e;
		}
		execute(run);
	}

	/**
	 * 管理者按「立即同步全部商品」：建立執行紀錄後立即回傳，同步在背景執行。
	 *
	 * @throws IllegalStateException PTT 來源已停用，或已有同步正在執行（GlobalExceptionHandler 轉成 409）
	 */
	public TrendCrawlerStatusResponse startManualRun(String username) {
		if (!trendCrawlerSettings.isPttEnabled()) {
			throw new IllegalStateException("PTT 熱度來源目前已停用，請先啟用再同步");
		}
		if (!running.compareAndSet(false, true)) {
			throw new IllegalStateException("已有全商品同步正在執行中，請等目前這次完成後再試");
		}
		TrendSyncRun run;
		try {
			run = startRecord(TrendSyncTrigger.MANUAL, resolveUserId(username));
		} catch (RuntimeException e) {
			running.set(false);
			throw e;
		}
		manualRunExecutor.submit(() -> execute(run));
		return getStatus();
	}

	/** 系統設定畫面：開關狀態、是否執行中與進度、最近 10 次執行紀錄。 */
	public TrendCrawlerStatusResponse getStatus() {
		List<TrendSyncRun> runs = trendSyncRunRepository.findTop10ByOrderByStartedAtDesc();
		Map<Long, String> userNames = appUserRepository.findAllById(runs.stream()
				.map(TrendSyncRun::getTriggeredBy).filter(Objects::nonNull).distinct().toList())
				.stream().collect(Collectors.toMap(AppUser::getId, AppUser::getName, (a, b) -> a));
		Function<TrendSyncRun, TrendSyncRunResponse> toResponse = run -> TrendSyncRunResponse.from(run,
				run.getTriggeredBy() == null ? null : userNames.get(run.getTriggeredBy()));

		SyncAllResult current = running.get() ? progress.get() : null;
		return new TrendCrawlerStatusResponse(
				trendCrawlerSettings.isPttEnabled(),
				running.get(),
				current == null ? null : current.processed(),
				current == null ? null : current.total(),
				SCHEDULE_DESCRIPTION,
				runs.stream().map(toResponse).toList());
	}

	public TrendCrawlerStatusResponse setEnabled(boolean enabled, String username) {
		trendCrawlerSettings.setPttEnabled(enabled, resolveUserId(username));
		log.info("PTT 熱度來源已{}（操作者：{}）", enabled ? "啟用" : "停用", username);
		return getStatus();
	}

	/** running 旗標必須已由呼叫端設為 true；結束時一定會放掉。 */
	private void execute(TrendSyncRun run) {
		long startedAt = System.currentTimeMillis();
		try {
			List<Product> products = trendService.findProductsToSync();
			run.setTotalCount(products.size());
			trendSyncRunRepository.save(run);
			progress.set(new SyncAllResult(products.size(), 0, 0, 0));
			log.info("開始 PTT 熱度全商品同步（{}）：共 {} 個未封存商品", run.getTriggerType(), products.size());

			SyncAllResult result = trendService.syncAll(products, trendCrawlerSettings::isPttEnabled, progress::set);

			run.setRealCount(result.realCount());
			run.setFallbackCount(result.fallbackCount());
			run.setFailedCount(result.failedCount());
			if (result.processed() < result.total()) {
				run.setStatus(TrendSyncRunStatus.FAILED);
				run.setMessage(String.format("執行途中 PTT 來源被停用，已處理 %d / %d 個商品後停止",
						result.processed(), result.total()));
			} else {
				run.setStatus(TrendSyncRunStatus.COMPLETED);
			}
		} catch (RuntimeException e) {
			log.error("PTT 熱度全商品同步中斷", e);
			SyncAllResult partial = progress.get();
			if (partial != null) {
				run.setRealCount(partial.realCount());
				run.setFallbackCount(partial.fallbackCount());
				run.setFailedCount(partial.failedCount());
			}
			run.setStatus(TrendSyncRunStatus.FAILED);
			run.setMessage(truncate("同步中斷：" + e.getMessage()));
		} finally {
			run.setFinishedAt(LocalDateTime.now());
			try {
				trendSyncRunRepository.save(run);
			} finally {
				progress.set(null);
				running.set(false);
			}
		}

		Duration elapsed = Duration.ofMillis(System.currentTimeMillis() - startedAt);
		log.info("PTT 熱度全商品同步結束（{}）：真實資料 {} 筆、改用模擬資料 {} 筆、失敗 {} 筆，耗時 {} 秒",
				run.getStatus(), run.getRealCount(), run.getFallbackCount(), run.getFailedCount(),
				elapsed.toSeconds());
		if (run.getTriggerType() == TrendSyncTrigger.SCHEDULED && elapsed.compareTo(SCHEDULE_WARNING_DURATION) > 0) {
			log.warn("每日 PTT 熱度同步耗時超過 50 分鐘，可能壓到 03:00 的 AI 建議批次，請減少看板數或請求間隔");
		}
	}

	private TrendSyncRun startRecord(TrendSyncTrigger trigger, Long triggeredBy) {
		TrendSyncRun run = new TrendSyncRun();
		run.setTriggerType(trigger);
		run.setStatus(TrendSyncRunStatus.RUNNING);
		run.setStartedAt(LocalDateTime.now());
		run.setTriggeredBy(triggeredBy);
		return trendSyncRunRepository.save(run);
	}

	private void saveSkipped(TrendSyncTrigger trigger, String message) {
		TrendSyncRun run = new TrendSyncRun();
		run.setTriggerType(trigger);
		run.setStatus(TrendSyncRunStatus.SKIPPED);
		run.setStartedAt(LocalDateTime.now());
		run.setFinishedAt(LocalDateTime.now());
		run.setMessage(message);
		trendSyncRunRepository.save(run);
	}

	private Long resolveUserId(String username) {
		return appUserRepository.findByUsername(username)
				.map(AppUser::getId)
				.orElseThrow(() -> new IllegalArgumentException("使用者不存在"));
	}

	private static String truncate(String message) {
		return message.length() > 255 ? message.substring(0, 255) : message;
	}
}
