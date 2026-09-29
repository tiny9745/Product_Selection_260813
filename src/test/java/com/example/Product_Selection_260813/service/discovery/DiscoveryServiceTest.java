package com.example.Product_Selection_260813.service.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.Product_Selection_260813.common.exception.LlmAnalysisException;
import com.example.Product_Selection_260813.common.exception.SystemConfigurationException;
import com.example.Product_Selection_260813.entity.AudienceProfile;
import com.example.Product_Selection_260813.entity.DiscoveredItem;
import com.example.Product_Selection_260813.entity.DiscoveredItemEvidence;
import com.example.Product_Selection_260813.enums.DiscoveredItemStatus;
import com.example.Product_Selection_260813.enums.DiscoveryDismissReason;
import com.example.Product_Selection_260813.enums.GateStatus;
import com.example.Product_Selection_260813.enums.GoogleTrendStatus;
import com.example.Product_Selection_260813.enums.TrendSignalTrendDirection;
import com.example.Product_Selection_260813.repository.AudienceProfileRepository;
import com.example.Product_Selection_260813.repository.DiscoveredItemEvidenceRepository;
import com.example.Product_Selection_260813.repository.DiscoveredItemRepository;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.ProductTypeRepository;
import com.example.Product_Selection_260813.service.crawler.MarketBuzzSignal;
import com.example.Product_Selection_260813.service.crawler.PttClient;
import com.example.Product_Selection_260813.service.crawler.PttMarketBuzzProvider;
import com.example.Product_Selection_260813.service.discovery.DiscoveryFitRules.FitResult;
import com.example.Product_Selection_260813.service.discovery.DiscoveryVerifier.ExtractedItem;
import com.example.Product_Selection_260813.service.resolver.AlgorithmSettings;
import com.example.Product_Selection_260813.service.resolver.ProductTypeAttributeResolver;
import com.example.Product_Selection_260813.service.trends.GoogleTrendSettings;
import com.example.Product_Selection_260813.service.trends.TrendInterest;
import com.example.Product_Selection_260813.service.trends.TrendInterestProvider;

/**
 * 整條流程：假的 PTT 列表頁＋假的 AI（Groq）回應 → 驗證、排除既有商品與相似的已略過項目、寫入、
 * 查熱度、適配評分、Google 趨勢交叉驗證。交易用 mock 的 PlatformTransactionManager
 * （TransactionTemplate 照常呼叫 callback）。
 *
 * 用 LENIENT：共用的情境在 @BeforeEach 一次建好，個別測試只改需要的部分（比照 TrendSyncRunServiceTest）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiscoveryServiceTest {

	@Mock
	private PttClient pttClient;
	@Mock
	private PttMarketBuzzProvider pttMarketBuzzProvider;
	@Mock
	private GroqDiscoveryClient groqDiscoveryClient;
	@Mock
	private DiscoveredItemRepository discoveredItemRepository;
	@Mock
	private DiscoveredItemEvidenceRepository discoveredItemEvidenceRepository;
	@Mock
	private ProductRepository productRepository;
	@Mock
	private ProductTypeRepository productTypeRepository;
	@Mock
	private PlatformTransactionManager transactionManager;
	@Mock
	private AudienceProfileRepository audienceProfileRepository;
	@Mock
	private ProductTypeAttributeResolver productTypeAttributeResolver;
	@Mock
	private AlgorithmSettings algorithmSettings;
	@Mock
	private GoogleTrendSettings googleTrendSettings;
	@Mock
	private TrendInterestProvider trendInterestProvider;

	@InjectMocks
	private DiscoveryService discoveryService;

	/** 寫入過的探索項目（模擬資料庫），id 從 1 開始。 */
	private final List<DiscoveredItem> stored = new ArrayList<>();

	private static String entry(String nrec, long epochSeconds, String title) {
		return """
				<div class="r-ent"><div class="nrec"><span class="hl f3">%s</span></div>
				<div class="title"><a href="/bbs/Food/M.%d.A.ABC.html">%s</a></div></div>
				""".formatted(nrec, epochSeconds, title);
	}

	private static DiscoveredItem dismissedItem(String name, DiscoveryDismissReason reason) {
		DiscoveredItem item = new DiscoveredItem();
		item.setId(90L + reason.ordinal());
		item.setDisplayName(name);
		item.setNormalizedName(DiscoveryText.normalize(name));
		item.setStatus(DiscoveredItemStatus.DISMISSED);
		item.setDismissReasonCode(reason);
		return item;
	}

	@BeforeEach
	void configure() throws Exception {
		ReflectionTestUtils.setField(discoveryService, "boards", List.of("Food"));
		ReflectionTestUtils.setField(discoveryService, "recentDays", 7);
		ReflectionTestUtils.setField(discoveryService, "maxIndexPagesPerBoard", 30);
		ReflectionTestUtils.setField(discoveryService, "titlesPerAiCall", 150);
		ReflectionTestUtils.setField(discoveryService, "maxAiCallsPerRun", 15);
		ReflectionTestUtils.setField(discoveryService, "maxBuzzChecksPerRun", 10);
		ReflectionTestUtils.setField(discoveryService, "maxFitEvaluationsPerRun", 40);
		ReflectionTestUtils.setField(discoveryService, "itemsPerFitCall", 20);
		ReflectionTestUtils.setField(discoveryService, "maxGoogleChecksPerRun", 5);
		ReflectionTestUtils.setField(discoveryService, "googleRequestDelayMs", 0L);

		long now = System.currentTimeMillis() / 1000;
		String html = "<html><body><div class=\"btn-group btn-group-paging\">"
				+ "<a class=\"btn wide\" href=\"/bbs/Food/index99.html\">‹ 上頁</a></div>"
				+ entry("10", now - 3600, "[情報] 全聯 義美小泡芙 買一送一")
				+ entry("5", now - 3500, "Re: [情報] 全聯 義美小泡芙 買一送一")
				+ entry("20", now - 3400, "[心得] Dyson V15 開箱")
				+ entry("3", now - 3300, "[情報] 義美泡芙禮盒組 中秋限定")
				+ entry("", now - 3200, "[公告] 板規修訂公告")
				+ entry("1", now - 30L * 86400, "[閒聊] 很久以前的文章")
				+ "</body></html>";
		when(pttClient.fetchBoardPage("/bbs/Food/index.html")).thenReturn(Jsoup.parse(html));
		when(groqDiscoveryClient.isConfigured()).thenReturn(true);
		when(groqDiscoveryClient.modelName()).thenReturn("groq-test");
		when(productTypeRepository.findByLevelAndIsActiveTrueOrderBySortOrderAsc(any())).thenReturn(List.of());
		when(groqDiscoveryClient.extract(any(), anyList(), anyList())).thenReturn(List.of(
				new ExtractedItem("義美小泡芙", "義美小泡芙", null, List.of("t1")),
				new ExtractedItem("Dyson V15", "Dyson V15", null, List.of("t2")),
				new ExtractedItem("義美泡芙禮盒組", "義美泡芙禮盒組", null, List.of("t3")),
				new ExtractedItem("捏造商品", "捏造商品", null, List.of("t1"))));
		when(productRepository.findAllNames()).thenReturn(List.of("Dyson V15 吸塵器"));

		// 略過回饋：一個「不適合團購」（名稱與新結果相似）、一個「不是實體商品」
		when(discoveredItemRepository.findByStatusOrderByHandledAtDesc(DiscoveredItemStatus.DISMISSED))
				.thenReturn(List.of(dismissedItem("義美泡芙禮盒", DiscoveryDismissReason.NOT_FOR_GROUP_BUY),
						dismissedItem("街口支付", DiscoveryDismissReason.NOT_A_PRODUCT)));
		when(discoveredItemRepository.findByNormalizedNameIn(any())).thenReturn(List.of());
		when(discoveredItemRepository.save(any(DiscoveredItem.class))).thenAnswer(invocation -> {
			DiscoveredItem item = invocation.getArgument(0);
			if (item.getId() == null) {
				item.setId((long) stored.size() + 1);
				stored.add(item);
			}
			return item;
		});
		when(discoveredItemRepository.findById(any())).thenAnswer(invocation -> stored.stream()
				.filter(item -> item.getId().equals(invocation.getArgument(0))).findFirst());
		when(discoveredItemRepository.findAllById(any())).thenAnswer(invocation -> new ArrayList<>(stored));
		when(discoveredItemEvidenceRepository.findByItemId(any())).thenReturn(List.of());
		when(pttMarketBuzzProvider.fetch(eq("義美小泡芙"), any())).thenReturn(new MarketBuzzSignal("PTT", "義美小泡芙",
				new BigDecimal("62.00"), new BigDecimal("71.50"), TrendSignalTrendDirection.UP, 180));

		// 第二階段
		when(algorithmSettings.getSupportedTemperatureZones()).thenReturn(List.of("NORMAL", "CHILLED"));
		AudienceProfile profile = new AudienceProfile();
		profile.setId(1L);
		profile.setVersion(3);
		profile.setName("雙薪家庭");
		when(audienceProfileRepository.findByIsActiveTrue()).thenReturn(List.of(profile));
		when(groqDiscoveryClient.evaluateFit(anyList(), any(), anyList()))
				.thenReturn(List.of(new FitResult("d1", 82, "適合家庭分享的平價零食", List.of("單價未知"))));
		when(googleTrendSettings.isEnabled()).thenReturn(true);
		when(googleTrendSettings.getRemainingThisMonth()).thenReturn(100);
		when(trendInterestProvider.isConfigured()).thenReturn(true);
		when(trendInterestProvider.fetch("義美小泡芙")).thenReturn(new TrendInterest(GoogleTrendStatus.OK,
				TrendSignalTrendDirection.UP, new BigDecimal("21.40"), new BigDecimal("58"), new BigDecimal("47.8"), 92));
	}

	@Test
	@SuppressWarnings("unchecked")
	void 整條流程_驗證排除後寫入_並完成熱度適配評分與Google趨勢() {
		DiscoveryService.RunResult result = discoveryService.run(() -> true);

		// 30 天前那篇被時間窗排除；公告被規則排除；回文與原文合併成同一則標題
		assertThat(result.postCount()).isEqualTo(5);
		assertThat(result.titleCount()).isEqualTo(3);
		assertThat(result.extractedCount()).isEqualTo(4);
		assertThat(result.rejectedCount()).isEqualTo(1); // 捏造商品
		assertThat(result.matchedExistingCount()).isEqualTo(1); // Dyson V15
		assertThat(result.similarDismissedCount()).isEqualTo(1); // 義美泡芙禮盒組 ≈ 已略過的義美泡芙禮盒
		assertThat(result.newCount()).isEqualTo(1);
		assertThat(result.fitCount()).isEqualTo(1);
		assertThat(result.googleCount()).isEqualTo(1);
		assertThat(result.aiCallCount()).isEqualTo(2); // 抽取 1 次＋評分 1 次
		assertThat(result.warnings()).isEmpty();

		DiscoveredItem item = stored.get(0);
		assertThat(item.getNormalizedName()).isEqualTo("義美小泡芙");
		assertThat(item.getMentionCount()).isEqualTo(2);
		assertThat(item.getPushVolume()).isEqualTo(15);
		assertThat(item.getPopularityScore()).isEqualByComparingTo("71.50");
		// 品類沒判定出來 → 溫層「資料不足」，不是「不通過」
		assertThat(item.getTemperatureGate()).isEqualTo(GateStatus.INSUFFICIENT_DATA);
		assertThat(item.getFitScore()).isEqualByComparingTo("82");
		assertThat(item.getFitReason()).isEqualTo("適合家庭分享的平價零食");
		assertThat(item.getFitConcerns()).isEqualTo("單價未知");
		assertThat(item.getFitAudienceSig()).isEqualTo("1@3");
		assertThat(item.getGoogleStatus()).isEqualTo(GoogleTrendStatus.OK);
		assertThat(item.getGoogleDirection()).isEqualTo(TrendSignalTrendDirection.UP);
		assertThat(item.getGoogleGrowthRate()).isEqualByComparingTo("21.40");
		verify(googleTrendSettings).recordCall();

		// 略過回饋：「不是實體商品」進抽取 prompt，其他原因進評分 prompt
		ArgumentCaptor<List<String>> notProduct = ArgumentCaptor.forClass(List.class);
		verify(groqDiscoveryClient).extract(any(), anyList(), notProduct.capture());
		assertThat(notProduct.getValue()).containsExactly("街口支付");
		ArgumentCaptor<List<String>> negative = ArgumentCaptor.forClass(List.class);
		verify(groqDiscoveryClient).evaluateFit(anyList(), any(), negative.capture());
		assertThat(negative.getValue()).containsExactly("義美泡芙禮盒（不適合團購）");

		ArgumentCaptor<List<DiscoveredItemEvidence>> evidence = ArgumentCaptor.forClass(List.class);
		verify(discoveredItemEvidenceRepository).saveAll(evidence.capture());
		assertThat(evidence.getValue()).extracting(DiscoveredItemEvidence::getTitle).containsExactly(
				"[情報] 全聯 義美小泡芙 買一送一", "Re: [情報] 全聯 義美小泡芙 買一送一");
		verify(pttMarketBuzzProvider, never()).fetch(eq("Dyson V15"), any());
	}

	@Test
	void 客群版本沒變且評過分_不重複花AI額度評分() {
		DiscoveredItem existing = new DiscoveredItem();
		existing.setId(1L);
		existing.setNormalizedName("義美小泡芙");
		existing.setDisplayName("義美小泡芙");
		existing.setStatus(DiscoveredItemStatus.NEW);
		existing.setFitEvaluatedAt(LocalDateTime.now().minusDays(1));
		existing.setFitAudienceSig("1@3");
		existing.setFitScore(new BigDecimal("70"));
		stored.add(existing);
		when(discoveredItemRepository.findByNormalizedNameIn(any())).thenReturn(List.of(existing));

		DiscoveryService.RunResult result = discoveryService.run(() -> true);

		assertThat(result.newCount()).isZero();
		assertThat(result.updatedCount()).isEqualTo(1);
		assertThat(result.fitCount()).isZero();
		verify(groqDiscoveryClient, never()).evaluateFit(anyList(), any(), anyList());
	}

	@Test
	void 品類溫層不在通路支援清單_判定不通過() {
		when(algorithmSettings.getSupportedTemperatureZones()).thenReturn(List.of("NORMAL"));
		when(productTypeRepository.findByLevelAndIsActiveTrueOrderBySortOrderAsc(2)).thenAnswer(invocation -> {
			var minor = new com.example.Product_Selection_260813.entity.ProductType();
			minor.setId(8L);
			minor.setName("冰品");
			return List.of(minor);
		});
		when(groqDiscoveryClient.extract(any(), anyList(), anyList())).thenReturn(List.of(
				new ExtractedItem("義美小泡芙", "義美小泡芙", "冰品", List.of("t1"))));
		when(productTypeAttributeResolver.resolve(8L)).thenReturn(
				new com.example.Product_Selection_260813.service.resolver.ResolvedProductTypeAttributes(8L, 8L,
						com.example.Product_Selection_260813.service.resolver.ResolvedValue.ofProductType("FROZEN"),
						null, null, null, null, null, null, null));

		discoveryService.run(() -> true);

		DiscoveredItem item = stored.get(0);
		assertThat(item.getProductTypeId()).isEqualTo(8L);
		assertThat(item.getTemperatureZone()).isEqualTo("FROZEN");
		assertThat(item.getTemperatureGate()).isEqualTo(GateStatus.FAILED);
	}

	@Test
	void Google趨勢來源停用_不查也不算異常() {
		when(googleTrendSettings.isEnabled()).thenReturn(false);

		DiscoveryService.RunResult result = discoveryService.run(() -> true);

		assertThat(result.googleCount()).isZero();
		assertThat(result.warnings()).isEmpty();
		verify(trendInterestProvider, never()).fetch(any());
	}

	@Test
	void AI沒回傳某些項目的評分_不補預設分數並記在警告() {
		when(groqDiscoveryClient.evaluateFit(anyList(), any(), anyList())).thenReturn(List.of());

		DiscoveryService.RunResult result = discoveryService.run(() -> true);

		assertThat(result.fitCount()).isZero();
		assertThat(stored.get(0).getFitScore()).isNull();
		assertThat(result.warnings()).anyMatch(w -> w.contains("未回傳有效評分"));
		// 沒有適配分就不挑它查 Google 趨勢
		verify(trendInterestProvider, times(0)).fetch(any());
	}

	@Test
	void AI服務過載重試仍失敗_停止後續批次並標記抽取失敗() {
		ReflectionTestUtils.setField(discoveryService, "titlesPerAiCall", 1); // 3 則標題 → 3 批
		when(groqDiscoveryClient.extract(any(), anyList(), anyList())).thenThrow(
				new DiscoveryAiUnavailableException("AI 服務暫時無法使用：Groq 服務暫時無法使用（503）（模型 groq-test，已嘗試 4 次）", null));

		DiscoveryService.RunResult result = discoveryService.run(() -> true);

		verify(groqDiscoveryClient, times(1)).extract(any(), anyList(), anyList());
		assertThat(result.extractionFailed()).isTrue();
		assertThat(result.aiCallCount()).isEqualTo(1);
		assertThat(result.newCount()).isZero();
		assertThat(result.warnings()).anyMatch(w -> w.contains("剩餘 2 則標題未處理"));
		verify(groqDiscoveryClient, never()).evaluateFit(anyList(), any(), anyList());
	}

	@Test
	void AI每日額度用完_停止後續批次_已抽到的照樣寫入() {
		ReflectionTestUtils.setField(discoveryService, "titlesPerAiCall", 1); // 3 則標題 → 3 批
		when(groqDiscoveryClient.extract(any(), anyList(), anyList()))
				.thenReturn(List.of())
				.thenThrow(new DiscoveryQuotaExceededException("Groq 免費額度暫時用完（需等待約 30 分鐘，通常是每日 token 上限）"));

		DiscoveryService.RunResult result = discoveryService.run(() -> true);

		// 第 2 批額度用完就不再送第 3 批；第 1 批成功，所以不算整次抽取失敗
		verify(groqDiscoveryClient, times(2)).extract(any(), anyList(), anyList());
		assertThat(result.extractionFailed()).isFalse();
		assertThat(result.warnings()).anyMatch(w -> w.contains("Groq 免費額度暫時用完"));
	}

	@Test
	void 單批回應格式錯誤_只略過該批_其他批成功就不算抽取失敗() {
		ReflectionTestUtils.setField(discoveryService, "titlesPerAiCall", 1);
		when(groqDiscoveryClient.extract(any(), anyList(), anyList()))
				.thenThrow(new LlmAnalysisException("AI 回應格式錯誤"))
				.thenReturn(List.of());

		DiscoveryService.RunResult result = discoveryService.run(() -> true);

		verify(groqDiscoveryClient, times(3)).extract(any(), anyList(), anyList());
		assertThat(result.extractionFailed()).isFalse();
		assertThat(result.warnings()).anyMatch(w -> w.contains("第 1 批"));
	}

	@Test
	void 沒有設定AI金鑰_不爬PTT直接失敗() throws Exception {
		when(groqDiscoveryClient.isConfigured()).thenReturn(false);
		assertThatThrownBy(() -> discoveryService.run(() -> true)).isInstanceOf(SystemConfigurationException.class);
		verify(pttClient, never()).fetchBoardPage(any());
	}

	@Test
	void 相同次數時取字典序較前的寫法_結果穩定() {
		assertThat(DiscoveryService.mostFrequent(Map.of("小泡芙", 2, "義美小泡芙", 2, "泡芙", 1), null))
				.isEqualTo("小泡芙");
		assertThat(DiscoveryService.mostFrequent(Map.of(), "預設")).isEqualTo("預設");
	}
}
