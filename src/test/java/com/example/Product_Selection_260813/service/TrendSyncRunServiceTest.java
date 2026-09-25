package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.Product_Selection_260813.dto.response.TrendCrawlerStatusResponse;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.TrendSyncRun;
import com.example.Product_Selection_260813.enums.TrendSyncRunStatus;
import com.example.Product_Selection_260813.enums.TrendSyncTrigger;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.repository.TrendSyncRunRepository;
import com.example.Product_Selection_260813.service.TrendService.SyncAllResult;
import com.example.Product_Selection_260813.service.crawler.TrendCrawlerSettings;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrendSyncRunServiceTest {

	@Mock
	private TrendService trendService;

	@Mock
	private TrendCrawlerSettings trendCrawlerSettings;

	@Mock
	private TrendSyncRunRepository trendSyncRunRepository;

	@Mock
	private AppUserRepository appUserRepository;

	@InjectMocks
	private TrendSyncRunService service;

	/** 模擬資料庫：每次 save 都記下當下的快照，方便驗證紀錄內容與寫入順序。 */
	private final List<TrendSyncRun> savedSnapshots = new ArrayList<>();

	@BeforeEach
	void setUp() {
		when(trendSyncRunRepository.save(any())).thenAnswer(invocation -> {
			TrendSyncRun run = invocation.getArgument(0);
			if (run.getId() == null) {
				run.setId((long) savedSnapshots.size() + 1);
			}
			savedSnapshots.add(copy(run));
			return run;
		});
		AppUser manager = new AppUser();
		ReflectionTestUtils.setField(manager, "id", 9L);
		manager.setName("管理測試人員");
		when(appUserRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
		when(appUserRepository.findAllById(any())).thenReturn(List.of(manager));
		when(trendSyncRunRepository.findTop10ByOrderByStartedAtDesc()).thenReturn(List.of());
	}

	private static TrendSyncRun copy(TrendSyncRun run) {
		TrendSyncRun c = new TrendSyncRun();
		c.setId(run.getId());
		c.setTriggerType(run.getTriggerType());
		c.setStatus(run.getStatus());
		c.setStartedAt(run.getStartedAt());
		c.setFinishedAt(run.getFinishedAt());
		c.setTotalCount(run.getTotalCount());
		c.setRealCount(run.getRealCount());
		c.setFallbackCount(run.getFallbackCount());
		c.setFailedCount(run.getFailedCount());
		c.setMessage(run.getMessage());
		c.setTriggeredBy(run.getTriggeredBy());
		return c;
	}

	private TrendSyncRun lastSaved() {
		return savedSnapshots.get(savedSnapshots.size() - 1);
	}

	private static List<Product> products(int n) {
		List<Product> list = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			list.add(new Product());
		}
		return list;
	}

	@Test
	void 排程執行_完成後留下各項筆數() {
		when(trendCrawlerSettings.isPttEnabled()).thenReturn(true);
		when(trendService.findProductsToSync()).thenReturn(products(5));
		when(trendService.syncAll(any(), any(), any())).thenReturn(new SyncAllResult(5, 3, 1, 1));

		service.scheduledRun();

		TrendSyncRun run = lastSaved();
		assertThat(run.getTriggerType()).isEqualTo(TrendSyncTrigger.SCHEDULED);
		assertThat(run.getStatus()).isEqualTo(TrendSyncRunStatus.COMPLETED);
		assertThat(run.getTotalCount()).isEqualTo(5);
		assertThat(run.getRealCount()).isEqualTo(3);
		assertThat(run.getFallbackCount()).isEqualTo(1);
		assertThat(run.getFailedCount()).isEqualTo(1);
		assertThat(run.getFinishedAt()).isNotNull();
		assertThat(run.getTriggeredBy()).isNull();
		// 第一次寫入就是 RUNNING，執行途中畫面才看得到「執行中」
		assertThat(savedSnapshots.get(0).getStatus()).isEqualTo(TrendSyncRunStatus.RUNNING);
		assertThat(service.getStatus().running()).isFalse();
	}

	@Test
	void PTT停用時排程記錄為已略過_不同步任何商品() {
		when(trendCrawlerSettings.isPttEnabled()).thenReturn(false);

		service.scheduledRun();

		assertThat(lastSaved().getStatus()).isEqualTo(TrendSyncRunStatus.SKIPPED);
		assertThat(lastSaved().getMessage()).contains("已停用");
		verify(trendService, never()).syncAll(any(), any(), any());
	}

	@Test
	void PTT停用時手動同步全部回409() {
		when(trendCrawlerSettings.isPttEnabled()).thenReturn(false);

		assertThatThrownBy(() -> service.startManualRun("manager"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("已停用");
		assertThat(savedSnapshots).isEmpty();
	}

	@Test
	void 手動同步在背景執行_執行中再按一次回409_排程也不重複執行() throws Exception {
		when(trendCrawlerSettings.isPttEnabled()).thenReturn(true);
		when(trendService.findProductsToSync()).thenReturn(products(2));
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(trendService.syncAll(any(), any(), any())).thenAnswer(invocation -> {
			Consumer<SyncAllResult> onProgress = invocation.getArgument(2);
			onProgress.accept(new SyncAllResult(2, 1, 0, 0));
			started.countDown();
			release.await(5, TimeUnit.SECONDS);
			return new SyncAllResult(2, 2, 0, 0);
		});

		TrendCrawlerStatusResponse first = service.startManualRun("manager");
		assertThat(first.running()).isTrue();
		assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

		// 執行中：畫面看得到進度
		TrendCrawlerStatusResponse during = service.getStatus();
		assertThat(during.running()).isTrue();
		assertThat(during.processedCount()).isEqualTo(1);
		assertThat(during.totalCount()).isEqualTo(2);

		// 執行中再按一次：409
		assertThatThrownBy(() -> service.startManualRun("manager"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("正在執行中");
		// 執行中遇到排程：記錄為已略過，不重複執行
		service.scheduledRun();
		assertThat(lastSaved().getStatus()).isEqualTo(TrendSyncRunStatus.SKIPPED);
		assertThat(lastSaved().getTriggerType()).isEqualTo(TrendSyncTrigger.SCHEDULED);

		release.countDown();
		// 等背景執行緒寫完最後一筆
		for (int i = 0; i < 50 && service.getStatus().running(); i++) {
			Thread.sleep(50);
		}
		assertThat(service.getStatus().running()).isFalse();
		TrendSyncRun manual = savedSnapshots.stream()
				.filter(r -> r.getTriggerType() == TrendSyncTrigger.MANUAL)
				.reduce((a, b) -> b).orElseThrow();
		assertThat(manual.getStatus()).isEqualTo(TrendSyncRunStatus.COMPLETED);
		assertThat(manual.getRealCount()).isEqualTo(2);
		assertThat(manual.getTriggeredBy()).isEqualTo(9L);
	}

	@Test
	void 執行途中PTT被停用_紀錄為中斷並說明處理到哪裡() {
		when(trendCrawlerSettings.isPttEnabled()).thenReturn(true);
		when(trendService.findProductsToSync()).thenReturn(products(4));
		when(trendService.syncAll(any(), any(), any())).thenAnswer(invocation -> {
			BooleanSupplier shouldContinue = invocation.getArgument(1);
			assertThat(shouldContinue.getAsBoolean()).isTrue();
			return new SyncAllResult(4, 1, 0, 0);
		});

		service.scheduledRun();

		assertThat(lastSaved().getStatus()).isEqualTo(TrendSyncRunStatus.FAILED);
		assertThat(lastSaved().getMessage()).contains("1 / 4");
	}

	@Test
	void 同步過程拋出例外_紀錄為中斷_之後仍可再次執行() {
		when(trendCrawlerSettings.isPttEnabled()).thenReturn(true);
		when(trendService.findProductsToSync()).thenThrow(new IllegalStateException("資料庫連線中斷"));

		service.scheduledRun();

		assertThat(lastSaved().getStatus()).isEqualTo(TrendSyncRunStatus.FAILED);
		assertThat(lastSaved().getMessage()).contains("資料庫連線中斷");
		assertThat(service.getStatus().running()).isFalse();
	}

	@Test
	void 啟動時把上次停在執行中的紀錄改為中斷() {
		TrendSyncRun stale = new TrendSyncRun();
		stale.setId(1L);
		stale.setTriggerType(TrendSyncTrigger.MANUAL);
		stale.setStatus(TrendSyncRunStatus.RUNNING);
		stale.setStartedAt(LocalDateTime.now().minusHours(1));
		when(trendSyncRunRepository.findByStatus(TrendSyncRunStatus.RUNNING)).thenReturn(List.of(stale));

		service.markInterruptedRuns();

		assertThat(lastSaved().getStatus()).isEqualTo(TrendSyncRunStatus.FAILED);
		assertThat(lastSaved().getFinishedAt()).isNotNull();
		assertThat(lastSaved().getMessage()).contains("應用程式在同步途中關閉");
	}

	@Test
	void 切換開關會記錄操作者() {
		service.setEnabled(false, "manager");
		verify(trendCrawlerSettings).setPttEnabled(false, 9L);
	}
}
