package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.TrendSignal;
import com.example.Product_Selection_260813.enums.ProductItemStatus;
import com.example.Product_Selection_260813.enums.TrendSignalTrendDirection;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.TrendSignalRepository;
import com.example.Product_Selection_260813.service.crawler.MarketBuzzProvider;
import com.example.Product_Selection_260813.service.crawler.MarketBuzzSignal;
import com.example.Product_Selection_260813.service.crawler.MarketBuzzUnavailableException;

@ExtendWith(MockitoExtension.class)
class TrendServiceTest {

	@Mock
	private ProductRepository productRepository;

	@Mock
	private TrendSignalRepository trendSignalRepository;

	@Mock
	private ScoringService scoringService;

	@Mock
	private MarketBuzzProvider marketBuzzProvider;

	@Mock
	private MarketBuzzProvider fallbackMarketBuzzProvider;

	@Mock
	private PlatformTransactionManager transactionManager;

	@InjectMocks
	private TrendService trendService;

	private static final MarketBuzzSignal PTT_SIGNAL = new MarketBuzzSignal("PTT", "氣炸鍋",
			new BigDecimal("56.00"), new BigDecimal("63.00"), TrendSignalTrendDirection.UP, 58);

	private static final MarketBuzzSignal SIMULATED_SIGNAL = new MarketBuzzSignal("SIMULATED", "氣炸鍋",
			new BigDecimal("51.00"), new BigDecimal("49.00"), TrendSignalTrendDirection.UP, null);

	private static Product product(long id, String name) {
		Product product = new Product();
		ReflectionTestUtils.setField(product, "id", id);
		product.setName(name);
		return product;
	}

	private TrendSignal savedSignal() {
		ArgumentCaptor<TrendSignal> captor = ArgumentCaptor.forClass(TrendSignal.class);
		verify(trendSignalRepository).save(captor.capture());
		return captor.getValue();
	}

	@Test
	void 同步成功時寫入PTT資料並重算評分() {
		when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, "氣炸鍋")));
		when(trendSignalRepository.findFirstByProductIdOrderByCollectedAtDesc(1L)).thenReturn(Optional.empty());
		when(marketBuzzProvider.fetch("氣炸鍋", null)).thenReturn(PTT_SIGNAL);

		trendService.syncTrend(1L);

		TrendSignal saved = savedSignal();
		assertThat(saved.getSource()).isEqualTo("PTT");
		assertThat(saved.getProductId()).isEqualTo(1L);
		assertThat(saved.getPopularityScore()).isEqualByComparingTo("63.00");
		assertThat(saved.getTrendScore()).isEqualByComparingTo("56.00");
		assertThat(saved.getTrendDirection()).isEqualTo(TrendSignalTrendDirection.UP);
		assertThat(saved.getCollectedAt()).isNotNull();
		verify(scoringService).calculateEvaluation(1L, null);
		verify(fallbackMarketBuzzProvider, never()).fetch(anyString(), any());
	}

	@Test
	void PTT抓不到資料時改用模擬資料_同步不中斷_來源標記SIMULATED() {
		TrendSignal previous = new TrendSignal();
		when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, "氣炸鍋")));
		when(trendSignalRepository.findFirstByProductIdOrderByCollectedAtDesc(1L)).thenReturn(Optional.of(previous));
		when(marketBuzzProvider.fetch("氣炸鍋", previous)).thenThrow(new MarketBuzzUnavailableException("全部逾時"));
		when(fallbackMarketBuzzProvider.fetch("氣炸鍋", previous)).thenReturn(SIMULATED_SIGNAL);

		trendService.syncTrend(1L);

		assertThat(savedSignal().getSource()).isEqualTo("SIMULATED");
		verify(scoringService).calculateEvaluation(1L, null);
	}

	@Test
	void 每日排程_單一商品失敗不影響其他商品() {
		Product ok = product(1L, "氣炸鍋");
		Product broken = product(2L, "衛生紙");
		Product next = product(3L, "蛋捲");
		when(productRepository.findByItemStatus(ProductItemStatus.ACTIVE)).thenReturn(List.of(ok, broken, next));
		when(marketBuzzProvider.fetch(anyString(), isNull())).thenReturn(PTT_SIGNAL);
		when(trendSignalRepository.findFirstByProductIdOrderByCollectedAtDesc(any())).thenReturn(Optional.empty());
		// 商品 2 重算評分時出錯（例如資料不完整）
		org.mockito.Mockito.doThrow(new IllegalStateException("重算失敗"))
				.when(scoringService).calculateEvaluation(eq(2L), isNull());

		trendService.syncAllActiveProducts();

		verify(scoringService).calculateEvaluation(1L, null);
		verify(scoringService).calculateEvaluation(3L, null);
		verify(trendSignalRepository, times(3)).save(any());
	}

	@Test
	void 搜尋關鍵字去掉括號與規格字樣() {
		assertThat(TrendService.toSearchKeyword("飛利浦 海星氣炸鍋 4.2L")).isEqualTo("飛利浦 海星氣炸鍋");
		assertThat(TrendService.toSearchKeyword("【團購價】舒潔衛生紙 100抽x12包")).isEqualTo("舒潔衛生紙");
		assertThat(TrendService.toSearchKeyword("手工蛋捲(原味) 500g")).isEqualTo("手工蛋捲");
		assertThat(TrendService.toSearchKeyword("iPhone 17")).isEqualTo("iPhone 17");
		assertThat(TrendService.toSearchKeyword("  月餅  ")).isEqualTo("月餅");
	}

	@Test
	void 清完變空字串時退回原始名稱() {
		assertThat(TrendService.toSearchKeyword("500g")).isEqualTo("500g");
	}
}
