package com.example.Product_Selection_260813.service.discovery;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.Product_Selection_260813.common.RunHistoryPaging;
import com.example.Product_Selection_260813.dto.response.DiscoveryRunResponse;
import com.example.Product_Selection_260813.dto.response.DiscoveryStatusResponse;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.entity.DiscoveryRun;
import com.example.Product_Selection_260813.enums.TrendSyncRunStatus;
import com.example.Product_Selection_260813.enums.TrendSyncTrigger;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.repository.DiscoveryRunRepository;
import com.example.Product_Selection_260813.service.crawler.TrendCrawlerSettings;

import jakarta.annotation.PreDestroy;

/**
 * PTT 新品探索的排程、手動觸發與執行紀錄（比照 TrendSyncRunService 的寫法）。
 * 處理流程本身在 {@link DiscoveryService}。
 *
 * <b>排程時間 01:30：</b>排在 02:00 熱度同步之前。兩者互不依賴，
 * 但都會打 PTT；錯開時間讓每一段的請求量與耗時都容易觀察。PttClient 本身 synchronized，
 * 即使重疊也不會加倍請求速度。
 *
 * <b>開關：</b>沿用 PTT 熱度來源的開關（system_settings.ptt_crawler_enabled）——停用 PTT
 * 就是不想碰 PTT，探索同樣不執行，不另外做一個開關。
 */
@Service
public class DiscoveryRunService {

	private static final Logger log = LoggerFactory.getLogger(DiscoveryRunService.class);

	public static final String SCHEDULE_DESCRIPTION = "每天 01:30（早於 02:00 熱度同步）";

	/** 超過這個時間會壓到 02:00 的熱度同步，記 WARN 提醒調整看板頁數或 AI 批次數。 */
	private static final Duration SCHEDULE_WARNING_DURATION = Duration.ofMinutes(25);

	@Autowired
	private DiscoveryService discoveryService;

	@Autowired
	private TrendCrawlerSettings trendCrawlerSettings;

	@Autowired
	private GroqDiscoveryClient groqDiscoveryClient;

	@Autowired
	private DiscoveryRunRepository discoveryRunRepository;

	@Autowired
	private AppUserRepository appUserRepository;

	private final ExecutorService manualRunExecutor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "ptt-discovery-manual");
		thread.setDaemon(true);
		return thread;
	});

	private final AtomicBoolean running = new AtomicBoolean(false);

	@PreDestroy
	void shutdown() {
		manualRunExecutor.shutdownNow();
	}

	/** 上次在執行途中關閉應用程式時，紀錄會停在 RUNNING；啟動時改成失敗。 */
	@EventListener(ApplicationReadyEvent.class)
	public void markInterruptedRuns() {
		List<DiscoveryRun> stale = discoveryRunRepository.findByStatus(TrendSyncRunStatus.RUNNING);
		for (DiscoveryRun run : stale) {
			run.setStatus(TrendSyncRunStatus.FAILED);
			run.setFinishedAt(LocalDateTime.now());
			run.setMessage("應用程式在探索途中關閉，本次未完成");
			discoveryRunRepository.save(run);
		}
		if (!stale.isEmpty()) {
			log.warn("發現 {} 筆上次未完成的 PTT 新品探索紀錄，已標記為中斷", stale.size());
		}
	}

	@Scheduled(cron = "0 30 1 * * *")
	public void scheduledRun() {
		if (!trendCrawlerSettings.isPttEnabled()) {
			saveSkipped("PTT 來源已停用，本次排程未執行");
			log.info("PTT 來源已停用，略過今日新品探索");
			return;
		}
		if (!running.compareAndSet(false, true)) {
			saveSkipped("已有手動觸發的探索正在執行，本次排程未重複執行");
			return;
		}
		DiscoveryRun run;
		try {
			run = startRecord(TrendSyncTrigger.SCHEDULED, null);
		} catch (RuntimeException e) {
			running.set(false);
			throw e;
		}
		execute(run);
	}

	/**
	 * 管理者手動觸發：建立紀錄後立即回傳，探索在背景執行（整體需要數分鐘）。
	 *
	 * @throws IllegalStateException PTT 來源停用、未設定 AI 金鑰，或已有探索正在執行（409）
	 */
	public DiscoveryStatusResponse startManualRun(String username) {
		if (!trendCrawlerSettings.isPttEnabled()) {
			throw new IllegalStateException("PTT 來源目前已停用，請先到「PTT 熱度同步」啟用後再執行");
		}
		if (!groqDiscoveryClient.isConfigured()) {
			throw new IllegalStateException("尚未設定 Groq API 金鑰（GROQ_API_KEY），無法執行新品探索");
		}
		if (!running.compareAndSet(false, true)) {
			throw new IllegalStateException("已有新品探索正在執行中，請等這次完成後再試");
		}
		DiscoveryRun run;
		try {
			run = startRecord(TrendSyncTrigger.MANUAL, resolveUserId(username));
		} catch (RuntimeException e) {
			running.set(false);
			throw e;
		}
		manualRunExecutor.submit(() -> execute(run));
		return getStatus();
	}

	public DiscoveryStatusResponse getStatus() {
		List<DiscoveryRun> runs = discoveryRunRepository.findTop10ByOrderByStartedAtDesc();
		Function<DiscoveryRun, DiscoveryRunResponse> toResponse = toRunResponse(runs);
		int[] quota = groqDiscoveryClient.quotaUsage();
		return new DiscoveryStatusResponse(
				trendCrawlerSettings.isPttEnabled(),
				groqDiscoveryClient.isConfigured(),
				running.get(),
				SCHEDULE_DESCRIPTION,
				quota[0],
				quota[1],
				runs.stream().map(toResponse).toList());
	}

	/** GET /api/settings/discovery/runs：執行紀錄分頁（2026-09-29，每頁預設 10 筆）。 */
	public Page<DiscoveryRunResponse> getRuns(int page, int size) {
		Page<DiscoveryRun> runs = discoveryRunRepository
				.findAllByOrderByStartedAtDescIdDesc(RunHistoryPaging.of(page, size));
		return runs.map(toRunResponse(runs.getContent()));
	}

	/** 一次批次查出觸發者姓名（避免逐筆查詢），回傳轉換函式。 */
	private Function<DiscoveryRun, DiscoveryRunResponse> toRunResponse(List<DiscoveryRun> runs) {
		Map<Long, String> userNames = appUserRepository.findAllById(runs.stream()
				.map(DiscoveryRun::getTriggeredBy).filter(Objects::nonNull).distinct().toList())
				.stream().collect(Collectors.toMap(AppUser::getId, AppUser::getName, (a, b) -> a));
		return run -> DiscoveryRunResponse.from(run,
				run.getTriggeredBy() == null ? null : userNames.get(run.getTriggeredBy()));
	}

	/** running 旗標必須已由呼叫端設為 true；結束時一定會放掉。 */
	private void execute(DiscoveryRun run) {
		long startedAt = System.currentTimeMillis();
		try {
			DiscoveryService.RunResult result = discoveryService.run(trendCrawlerSettings::isPttEnabled);
			run.setPostCount(result.postCount());
			run.setTitleCount(result.titleCount());
			run.setAiCallCount(result.aiCallCount());
			run.setExtractedCount(result.extractedCount());
			run.setRejectedCount(result.rejectedCount());
			run.setMatchedExistingCount(result.matchedExistingCount());
			run.setSimilarDismissedCount(result.similarDismissedCount());
			run.setNewCount(result.newCount());
			run.setUpdatedCount(result.updatedCount());
			run.setFitCount(result.fitCount());
			run.setGoogleCount(result.googleCount());
			if (result.extractionFailed()) {
				// 每一批 AI 抽取都失敗：「抽出 0 筆」不代表 PTT 上沒有新品，不能顯示成「已完成」。
				// 數字照樣記錄（看得出文章有掃到、卡在 AI），狀態標成中斷，提醒稍後重新執行。
				run.setStatus(TrendSyncRunStatus.FAILED);
				run.setMessage(truncate("AI 抽取全部失敗，本次沒有產生探索結果；" + String.join("；", result.warnings())));
			} else {
				run.setStatus(TrendSyncRunStatus.COMPLETED);
				if (!result.warnings().isEmpty()) {
					run.setMessage(truncate(String.join("；", result.warnings())));
				}
			}
		} catch (RuntimeException e) {
			log.error("PTT 新品探索中斷", e);
			run.setStatus(TrendSyncRunStatus.FAILED);
			run.setMessage(truncate("探索中斷：" + e.getMessage()));
		} finally {
			run.setFinishedAt(LocalDateTime.now());
			try {
				discoveryRunRepository.save(run);
			} finally {
				running.set(false);
			}
		}
		Duration elapsed = Duration.ofMillis(System.currentTimeMillis() - startedAt);
		log.info("PTT 新品探索結束（{}）：耗時 {} 秒", run.getStatus(), elapsed.toSeconds());
		if (run.getTriggerType() == TrendSyncTrigger.SCHEDULED && elapsed.compareTo(SCHEDULE_WARNING_DURATION) > 0) {
			log.warn("PTT 新品探索耗時超過 25 分鐘，可能壓到 02:00 熱度同步，請調低 discovery.max-index-pages-per-board 或 max-ai-calls-per-run");
		}
	}

	private DiscoveryRun startRecord(TrendSyncTrigger trigger, Long triggeredBy) {
		DiscoveryRun run = new DiscoveryRun();
		run.setTriggerType(trigger);
		run.setStatus(TrendSyncRunStatus.RUNNING);
		run.setStartedAt(LocalDateTime.now());
		run.setTriggeredBy(triggeredBy);
		return discoveryRunRepository.save(run);
	}

	private void saveSkipped(String message) {
		DiscoveryRun run = new DiscoveryRun();
		run.setTriggerType(TrendSyncTrigger.SCHEDULED);
		run.setStatus(TrendSyncRunStatus.SKIPPED);
		LocalDateTime now = LocalDateTime.now();
		run.setStartedAt(now);
		run.setFinishedAt(now);
		run.setMessage(message);
		discoveryRunRepository.save(run);
	}

	private Long resolveUserId(String username) {
		return appUserRepository.findByUsername(username)
				.orElseThrow(() -> new IllegalArgumentException("使用者不存在"))
				.getId();
	}

	private static String truncate(String message) {
		if (message == null) {
			return null;
		}
		return message.length() > 255 ? message.substring(0, 252) + "…" : message;
	}
}
