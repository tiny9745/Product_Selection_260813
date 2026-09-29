package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.Product_Selection_260813.dto.response.GoogleTrendCoverageResponse;
import com.example.Product_Selection_260813.dto.response.GoogleTrendSignalResponse;
import com.example.Product_Selection_260813.entity.GoogleTrendSignal;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.TrendSignal;
import com.example.Product_Selection_260813.enums.GoogleTrendCoverageReason;
import com.example.Product_Selection_260813.enums.GoogleTrendStatus;
import com.example.Product_Selection_260813.enums.ProductCandidateStatus;
import com.example.Product_Selection_260813.enums.ProductItemStatus;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;
import com.example.Product_Selection_260813.enums.TrendSignalTrendDirection;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.repository.GoogleTrendRunRepository;
import com.example.Product_Selection_260813.repository.GoogleTrendSignalRepository;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.TrendSignalRepository;
import com.example.Product_Selection_260813.service.trends.GoogleTrendSettings;
import com.example.Product_Selection_260813.service.trends.TrendInterest;
import com.example.Product_Selection_260813.service.trends.TrendInterestProvider;
import com.example.Product_Selection_260813.service.trends.TrendInterestUnavailableException;

@ExtendWith(MockitoExtension.class)
class GoogleTrendServiceTest {

	@Mock
	private ProductRepository productRepository;

	@Mock
	private TrendSignalRepository trendSignalRepository;

	@Mock
	private GoogleTrendSignalRepository googleTrendSignalRepository;

	@Mock
	private GoogleTrendRunRepository googleTrendRunRepository;

	@Mock
	private AppUserRepository appUserRepository;

	@Mock
	private TrendInterestProvider trendInterestProvider;

	@Mock
	private GoogleTrendSettings googleTrendSettings;

	@InjectMocks
	private GoogleTrendService service;

	private static final TrendInterest UP = new TrendInterest(GoogleTrendStatus.OK, TrendSignalTrendDirection.UP,
			new BigDecimal("23.50"), new BigDecimal("52.00"), new BigDecimal("42.10"), 92);

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(service, "batchSize", 40);
		ReflectionTestUtils.setField(service, "requestDelayMs", 0L);
		ReflectionTestUtils.setField(service, "recheckDays", 7);
	}

	private static Product product(long id, String name) {
		Product product = new Product();
		ReflectionTestUtils.setField(product, "id", id);
		product.setName(name);
		product.setItemStatus(ProductItemStatus.ACTIVE);
		product.setReviewStatus(ProductReviewStatus.APPROVED);
		product.setCandidateStatus(ProductCandidateStatus.CANDIDATE);
		return product;
	}

	private static Product pending(long id, String name) {
		Product product = product(id, name);
		product.setReviewStatus(ProductReviewStatus.PENDING);
		return product;
	}

	/** 待審名單查詢（findCandidates() 第一段）。 */
	private void pendingList(Product... products) {
		when(productRepository.findPendingForGoogleTrend(any(), any(), any(), any(), any())).thenReturn(List.of(products));
	}

	private static TrendSignal latest(long productId, String source, String popularity) {
		TrendSignal signal = new TrendSignal();
		signal.setProductId(productId);
		signal.setSource(source);
		signal.setPopularityScore(new BigDecimal(popularity));
		return signal;
	}

	private void readyToCall() {
		lenient().when(googleTrendSettings.isEnabled()).thenReturn(true);
		lenient().when(trendInterestProvider.isConfigured()).thenReturn(true);
		lenient().when(googleTrendSettings.getRemainingThisMonth()).thenReturn(150);
	}

	@Test
	void 單一查詢_用去規格字樣的關鍵字查詢_計入額度並寫入一筆() {
		readyToCall();
		when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, "氣炸鍋 5L")));
		when(trendInterestProvider.fetch("氣炸鍋")).thenReturn(UP);
		when(googleTrendSignalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		GoogleTrendSignalResponse result = service.syncProduct(1L);

		assertThat(result.keyword()).isEqualTo("氣炸鍋");
		assertThat(result.direction()).isEqualTo(TrendSignalTrendDirection.UP);
		assertThat(result.growthRate()).isEqualByComparingTo("23.50");
		verify(googleTrendSettings).recordCall();
	}

	@Test
	void 查無資料也計入額度_SerpApi同樣計費() {
		readyToCall();
		when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, "兒童防潑水連帽外套")));
		when(trendInterestProvider.fetch(anyString())).thenReturn(TrendInterest.noData(0));
		when(googleTrendSignalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		assertThat(service.syncProduct(1L).status()).isEqualTo(GoogleTrendStatus.NO_DATA);
		verify(googleTrendSettings).recordCall();
	}

	@Test
	void 呼叫失敗不計額度也不寫資料() {
		readyToCall();
		when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, "氣炸鍋")));
		when(trendInterestProvider.fetch(anyString())).thenThrow(new TrendInterestUnavailableException("逾時"));

		assertThatThrownBy(() -> service.syncProduct(1L)).isInstanceOf(TrendInterestUnavailableException.class);
		verify(googleTrendSettings, never()).recordCall();
		verify(googleTrendSignalRepository, never()).save(any());
	}

	@Test
	void 停用_無金鑰_額度用完時不呼叫來源() {
		when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, "氣炸鍋")));

		when(googleTrendSettings.isEnabled()).thenReturn(false);
		assertThatThrownBy(() -> service.syncProduct(1L)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("停用");

		when(googleTrendSettings.isEnabled()).thenReturn(true);
		when(trendInterestProvider.isConfigured()).thenReturn(false);
		assertThatThrownBy(() -> service.syncProduct(1L)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("SERPAPI_API_KEY");

		when(trendInterestProvider.isConfigured()).thenReturn(true);
		when(googleTrendSettings.getRemainingThisMonth()).thenReturn(0);
		when(googleTrendSettings.getMonthlyLimit()).thenReturn(200);
		assertThatThrownBy(() -> service.syncProduct(1L)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("上限");

		verify(trendInterestProvider, never()).fetch(anyString());
	}

	@Test
	void 批次待審商品優先_剩餘名額依PTT熱度補位_與待審重複的不重算() {
		ReflectionTestUtils.setField(service, "batchSize", 3);
		pendingList(pending(7L, "待審冷門品"));
		when(trendSignalRepository.findPttRankedForGoogleTrend(any(), eq(3))).thenReturn(List.of(
				latest(4L, "PTT", "82.00"),
				latest(7L, "PTT", "80.00"),
				latest(3L, "PTT", "79.54"),
				latest(5L, "PTT", "60.00")));
		when(productRepository.findAllById(List.of(4L, 3L))).thenReturn(List.of(product(3L, "蛋捲"), product(4L, "行動電源")));

		List<Product> candidates = service.findCandidates();

		assertThat(candidates).extracting(Product::getName).containsExactly("待審冷門品", "行動電源", "蛋捲");
	}

	@Test
	void 待審商品已佔滿名額時不查PTT補位() {
		ReflectionTestUtils.setField(service, "batchSize", 2);
		pendingList(pending(1L, "甲"), pending(2L, "乙"));

		assertThat(service.findCandidates()).extracting(Product::getName).containsExactly("甲", "乙");
		verify(trendSignalRepository, never()).findPttRankedForGoogleTrend(any(), anyInt());
	}

	@Test
	void 重查間隔以日期計_7天前當天查的會重查() {
		assertThat(service.recheckSince()).isEqualTo(LocalDate.now().minusDays(6).atStartOfDay());
	}

	// ---------------- 批次涵蓋說明（品項詳情「尚未查詢」） ----------------

	@Test
	void 涵蓋說明_待審商品在名單內_優先查詢() {
		readyToCall();
		Product target = pending(7L, "待審冷門品");
		when(productRepository.findById(7L)).thenReturn(Optional.of(target));
		pendingList(target);
		when(trendSignalRepository.findPttRankedForGoogleTrend(any(), anyInt())).thenReturn(List.of());
		when(productRepository.findAllById(List.of())).thenReturn(List.of());

		GoogleTrendCoverageResponse coverage = service.getBatchCoverage(7L);

		assertThat(coverage.willBeQueried()).isTrue();
		assertThat(coverage.reason()).isEqualTo(GoogleTrendCoverageReason.PENDING_PRIORITY);
	}

	@Test
	void 涵蓋說明_非待審且PTT熱度為0_不會查詢() {
		readyToCall();
		when(productRepository.findById(5L)).thenReturn(Optional.of(product(5L, "冷門品")));
		pendingList();
		when(trendSignalRepository.findPttRankedForGoogleTrend(any(), anyInt())).thenReturn(List.of());
		when(productRepository.findAllById(List.of())).thenReturn(List.of());
		when(trendSignalRepository.findFirstByProductIdAndSourceOrderByCollectedAtDesc(5L, "PTT"))
				.thenReturn(Optional.of(latest(5L, "PTT", "0.00")));

		GoogleTrendCoverageResponse coverage = service.getBatchCoverage(5L);

		assertThat(coverage.willBeQueried()).isFalse();
		assertThat(coverage.reason()).isEqualTo(GoogleTrendCoverageReason.PTT_ZERO);
	}

	@Test
	void 涵蓋說明_非待審且沒有PTT資料_不會查詢() {
		readyToCall();
		when(productRepository.findById(5L)).thenReturn(Optional.of(product(5L, "新品")));
		pendingList();
		when(trendSignalRepository.findPttRankedForGoogleTrend(any(), anyInt())).thenReturn(List.of());
		when(productRepository.findAllById(List.of())).thenReturn(List.of());
		when(trendSignalRepository.findFirstByProductIdAndSourceOrderByCollectedAtDesc(5L, "PTT"))
				.thenReturn(Optional.empty());

		assertThat(service.getBatchCoverage(5L).reason()).isEqualTo(GoogleTrendCoverageReason.NO_PTT_DATA);
	}

	@Test
	void 涵蓋說明_重查間隔內已查過_下次略過() {
		readyToCall();
		when(productRepository.findById(5L)).thenReturn(Optional.of(product(5L, "氣炸鍋")));
		GoogleTrendSignal recent = new GoogleTrendSignal();
		recent.setCollectedAt(LocalDateTime.now().minusDays(1));
		when(googleTrendSignalRepository.findFirstByProductIdOrderByCollectedAtDesc(5L)).thenReturn(Optional.of(recent));

		assertThat(service.getBatchCoverage(5L).reason()).isEqualTo(GoogleTrendCoverageReason.RECENTLY_QUERIED);
		verify(productRepository, never()).findPendingForGoogleTrend(any(), any(), any(), any(), any());
	}

	@Test
	void 涵蓋說明_排名超過本月剩餘額度_本月查不到() {
		readyToCall();
		when(googleTrendSettings.getRemainingThisMonth()).thenReturn(1);
		Product target = pending(8L, "乙");
		when(productRepository.findById(8L)).thenReturn(Optional.of(target));
		pendingList(pending(7L, "甲"), target);
		when(trendSignalRepository.findPttRankedForGoogleTrend(any(), anyInt())).thenReturn(List.of());
		when(productRepository.findAllById(List.of())).thenReturn(List.of());

		GoogleTrendCoverageResponse coverage = service.getBatchCoverage(8L);

		assertThat(coverage.willBeQueried()).isFalse();
		assertThat(coverage.reason()).isEqualTo(GoogleTrendCoverageReason.QUOTA_SHORT);
		assertThat(coverage.message()).contains("第 2 位").contains("只剩 1 次");
	}

	@Test
	void 涵蓋說明_來源停用時直接說明_不查名單() {
		when(productRepository.findById(5L)).thenReturn(Optional.of(product(5L, "氣炸鍋")));
		when(googleTrendSettings.isEnabled()).thenReturn(false);

		assertThat(service.getBatchCoverage(5L).reason()).isEqualTo(GoogleTrendCoverageReason.SOURCE_DISABLED);
		verify(productRepository, never()).findPendingForGoogleTrend(any(), any(), any(), any(), any());
	}

	@Test
	void 批次只查到剩餘額度為止_單一商品失敗不中斷() {
		readyToCall();
		when(googleTrendSettings.getRemainingThisMonth()).thenReturn(2);
		pendingList();
		when(trendSignalRepository.findPttRankedForGoogleTrend(any(), anyInt())).thenReturn(List.of(
				latest(1L, "PTT", "90"), latest(2L, "PTT", "80"), latest(3L, "PTT", "70")));
		when(productRepository.findAllById(any())).thenReturn(
				List.of(product(1L, "甲"), product(2L, "乙"), product(3L, "丙")));
		when(googleTrendRunRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(trendInterestProvider.fetch("甲")).thenThrow(new TrendInterestUnavailableException("逾時"));
		when(trendInterestProvider.fetch("乙")).thenReturn(UP);
		when(googleTrendSignalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.scheduledRun();

		ArgumentCaptor<com.example.Product_Selection_260813.entity.GoogleTrendRun> runs = ArgumentCaptor
				.forClass(com.example.Product_Selection_260813.entity.GoogleTrendRun.class);
		verify(googleTrendRunRepository, org.mockito.Mockito.atLeastOnce()).save(runs.capture());
		var finished = runs.getValue();
		assertThat(finished.getTotalCount()).isEqualTo(2);
		assertThat(finished.getOkCount()).isEqualTo(1);
		assertThat(finished.getFailedCount()).isEqualTo(1);
		assertThat(finished.getMessage()).contains("剩餘額度 2 次");
		verify(trendInterestProvider, never()).fetch("丙");
		verify(googleTrendSignalRepository, org.mockito.Mockito.times(1)).save(any(GoogleTrendSignal.class));
	}

	@Test
	void 批次取得多個商品的最新一筆_同一時間兩筆時以id較大者為準() {
		GoogleTrendSignal older = new GoogleTrendSignal();
		older.setId(10L);
		older.setProductId(3L);
		older.setStatus(GoogleTrendStatus.OK);
		older.setDirection(TrendSignalTrendDirection.STABLE);
		GoogleTrendSignal newer = new GoogleTrendSignal();
		newer.setId(11L);
		newer.setProductId(3L);
		newer.setStatus(GoogleTrendStatus.OK);
		newer.setDirection(TrendSignalTrendDirection.UP);
		when(googleTrendSignalRepository.findLatestByProductIds(List.of(3L, 4L))).thenReturn(List.of(older, newer));

		var result = service.latestByProductIds(List.of(3L, 4L));

		assertThat(result).containsOnlyKeys(3L);
		assertThat(result.get(3L).direction()).isEqualTo(TrendSignalTrendDirection.UP);
		assertThat(service.latestByProductIds(List.of())).isEmpty();
	}

	@Test
	void 來源停用時排程記錄為略過() {
		when(googleTrendSettings.isEnabled()).thenReturn(false);
		when(googleTrendRunRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.scheduledRun();

		verify(trendInterestProvider, never()).fetch(anyString());
		verify(trendSignalRepository, never()).findPttRankedForGoogleTrend(any(), anyInt());
	}
}
