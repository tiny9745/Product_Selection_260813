package com.example.Product_Selection_260813.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.Product_Selection_260813.entity.AudienceProfile;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductTypeScoreBand;
import com.example.Product_Selection_260813.entity.TrendSignal;
import com.example.Product_Selection_260813.repository.AudienceProfileRepository;
import com.example.Product_Selection_260813.repository.TrendSignalRepository;
import com.example.Product_Selection_260813.service.resolver.AlgorithmSettings;
import com.example.Product_Selection_260813.service.resolver.ScoreBandResolver;

/**
 * ProductFactorScorer 是評分引擎裡商業規則最多的一層，Javadoc 明確寫著
 * 「拆開的理由是這一層有最多的商業規則，獨立出來才能單獨測試」——但這句話
 * 說完之後一直沒有實際的測試檔案。這支測試把每個 scoreXxx() 的 null 語意
 * （缺漏值一律回傳 null，不給中性值 50）、邊界情況、以及 estimateFreightCost()
 * 對髒資料的容錯行為釘死。
 *
 * scoreAll() 本身（僅是七個方法的組裝）與 HistoricalScoreCalculator 的內部
 * 邏輯不在這支測試範圍內，前者過於單純不需要測試，後者是獨立元件應有自己
 * 的測試檔案。
 */
@ExtendWith(MockitoExtension.class)
class ProductFactorScorerTest {

	@Mock
	private AudienceProfileRepository audienceProfileRepository;

	@Mock
	private TrendSignalRepository trendSignalRepository;

	@Mock
	private ScoreBandResolver scoreBandResolver;

	@Mock
	private AlgorithmSettings algorithmSettings;

	@Mock
	private HistoricalScoreCalculator historicalScoreCalculator;

	@InjectMocks
	private ProductFactorScorer scorer;

	/** 沒有設定任何值的乾淨商品，各測試再依需求覆寫個別欄位。 */
	private Product newProduct() {
		Product product = new Product();
		product.setId(1L);
		product.setProductTypeId(9L);
		product.setName("測試商品");
		return product;
	}

	@Nested
	class ScoreMarginRate {

		@Test
		void 成本價為null時回傳null_不查詢區間() {
			Product product = newProduct();
			product.setSalePrice(new BigDecimal("1000"));

			assertThat(scorer.scoreMarginRate(product)).isNull();
			verifyNoInteractions(scoreBandResolver);
		}

		@Test
		void 售價為null時回傳null() {
			Product product = newProduct();
			product.setCostPrice(new BigDecimal("700"));

			assertThat(scorer.scoreMarginRate(product)).isNull();
		}

		@Test
		void 售價小於等於零時回傳null_避免除以零() {
			Product product = newProduct();
			product.setCostPrice(new BigDecimal("700"));
			product.setSalePrice(BigDecimal.ZERO);

			assertThat(scorer.scoreMarginRate(product)).isNull();
		}

		@Test
		void 查不到品類區間時回傳null_不自行編一組區間() {
			Product product = newProduct();
			product.setCostPrice(new BigDecimal("700"));
			product.setSalePrice(new BigDecimal("1000"));
			when(scoreBandResolver.resolve(eq(9L), eq(ScoreBandResolver.FACTOR_MARGIN_RATE)))
					.thenReturn(Optional.empty());

			assertThat(scorer.scoreMarginRate(product)).isNull();
		}

		@Test
		void 未填材積級距時運費視為零_毛利率正確依區間正規化() {
			Product product = newProduct();
			product.setCostPrice(new BigDecimal("700"));
			product.setSalePrice(new BigDecimal("1000"));
			// packageSizeTier 未設定 → estimateFreightCost() 不查 algorithmSettings，
			// 這裡刻意不 stub algorithmSettings，若程式碼誤查會直接因
			// UnnecessaryStubbingException 或 NPE 被抓到。
			ProductTypeScoreBand band = new ProductTypeScoreBand();
			band.setLowerBound(new BigDecimal("0.1"));
			band.setUpperBound(new BigDecimal("0.5"));
			when(scoreBandResolver.resolve(eq(9L), eq(ScoreBandResolver.FACTOR_MARGIN_RATE)))
					.thenReturn(Optional.of(band));

			// 毛利率 = (1000-700-0)/1000 = 0.3，區間 0.1~0.5 → (0.3-0.1)/(0.5-0.1)*100 = 50
			BigDecimal result = scorer.scoreMarginRate(product);
			assertThat(result.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(new BigDecimal("50.00"));
		}
	}

	@Nested
	class ScoreDiscountDepth {

		@Test
		void 市價為null時回傳null_NEW商品的預期行為() {
			Product product = newProduct();
			product.setSalePrice(new BigDecimal("1000"));

			assertThat(scorer.scoreDiscountDepth(product)).isNull();
			verifyNoInteractions(scoreBandResolver);
		}

		@Test
		void 售價為null時回傳null() {
			Product product = newProduct();
			product.setMarketPrice(new BigDecimal("1500"));

			assertThat(scorer.scoreDiscountDepth(product)).isNull();
		}

		@Test
		void 市價小於等於零時回傳null() {
			Product product = newProduct();
			product.setMarketPrice(BigDecimal.ZERO);
			product.setSalePrice(new BigDecimal("1000"));

			assertThat(scorer.scoreDiscountDepth(product)).isNull();
		}

		@Test
		void 正常情境依區間正規化折扣率() {
			Product product = newProduct();
			product.setMarketPrice(new BigDecimal("1500"));
			product.setSalePrice(new BigDecimal("1200"));
			ProductTypeScoreBand band = new ProductTypeScoreBand();
			band.setLowerBound(new BigDecimal("0.1"));
			band.setUpperBound(new BigDecimal("0.3"));
			when(scoreBandResolver.resolve(eq(9L), eq(ScoreBandResolver.FACTOR_DISCOUNT_DEPTH)))
					.thenReturn(Optional.of(band));

			// 折扣率 = (1500-1200)/1500 = 0.2，區間 0.1~0.3 → (0.2-0.1)/(0.3-0.1)*100 = 50
			BigDecimal result = scorer.scoreDiscountDepth(product);
			assertThat(result.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(new BigDecimal("50.00"));
		}
	}

	@Nested
	class ScoreSupplyStability {

		@Test
		void 未填寫時回傳null_不給中性值() {
			assertThat(scorer.scoreSupplyStability(newProduct())).isNull();
		}

		@Test
		void 最低等級一映射到二十分() {
			Product product = newProduct();
			product.setSupplyStability(1);
			assertThat(scorer.scoreSupplyStability(product)).isEqualByComparingTo(new BigDecimal("20"));
		}

		@Test
		void 最高等級五映射到一百分() {
			Product product = newProduct();
			product.setSupplyStability(5);
			assertThat(scorer.scoreSupplyStability(product)).isEqualByComparingTo(new BigDecimal("100"));
		}
	}

	@Nested
	class ScoreAudienceMatch {

		@Test
		void 沒有生效客群設定時回傳null() {
			when(audienceProfileRepository.findByIsActiveTrue()).thenReturn(List.of());
			assertThat(scorer.scoreAudienceMatch(newProduct())).isNull();
		}

		@Test
		void 生效客群沒有填關鍵字時回傳null() {
			AudienceProfile profile = new AudienceProfile();
			profile.setKeywords("  ");
			when(audienceProfileRepository.findByIsActiveTrue()).thenReturn(List.of(profile));

			assertThat(scorer.scoreAudienceMatch(newProduct())).isNull();
		}

		/**
		 * 這是 Javadoc 明確承認的已知限制：contains 比對對中文沒有詞界，
		 * 「缺貨」會命中「不缺貨」。這裡刻意把它寫成測試而不是留白，
		 * 目的是讓這個行為變成「有意識維持的現狀」，日後若要修（改成斷詞比對）
		 * 會是一次刻意的行為變更，而不是改壞了都沒人發現。
		 */
		@Test
		void 已知限制_contains比對沒有詞界_缺貨會命中不缺貨() {
			AudienceProfile profile = new AudienceProfile();
			profile.setKeywords("缺貨");
			when(audienceProfileRepository.findByIsActiveTrue()).thenReturn(List.of(profile));

			Product product = newProduct();
			product.setTargetCustomerDescription("本商品供應穩定，目前不缺貨");

			BigDecimal result = scorer.scoreAudienceMatch(product);
			assertThat(result).isEqualByComparingTo(new BigDecimal("100"));
		}

		@Test
		void 多個關鍵字時依命中比例計分() {
			AudienceProfile profile = new AudienceProfile();
			profile.setKeywords("家庭,團購,露營");
			when(audienceProfileRepository.findByIsActiveTrue()).thenReturn(List.of(profile));

			Product product = newProduct();
			product.setTargetCustomerDescription("適合家庭與公司團購");
			product.setName("烤肉組合");

			// 命中「家庭」「團購」，未命中「露營」：2/3 * 100 ≈ 66.6667
			BigDecimal result = scorer.scoreAudienceMatch(product);
			assertThat(result.setScale(4, RoundingMode.HALF_UP)).isEqualByComparingTo(new BigDecimal("66.6667"));
		}

		@Test
		void 大小寫不影響比對結果() {
			AudienceProfile profile = new AudienceProfile();
			profile.setKeywords("BBQ");
			when(audienceProfileRepository.findByIsActiveTrue()).thenReturn(List.of(profile));

			Product product = newProduct();
			product.setTargetCustomerDescription("適合 bbq party");

			assertThat(scorer.scoreAudienceMatch(product)).isEqualByComparingTo(new BigDecimal("100"));
		}
	}

	@Nested
	class ScorePurchaseRate {

		@Test
		void 未填寫時回傳null() {
			assertThat(scorer.scorePurchaseRate(newProduct())).isNull();
		}

		@Test
		void 零點八轉換為八十分() {
			Product product = newProduct();
			product.setEstimatedPurchaseRate(new BigDecimal("0.8"));
			assertThat(scorer.scorePurchaseRate(product)).isEqualByComparingTo(new BigDecimal("80"));
		}

		@Test
		void 滿分一點零轉換為一百分_不會超過上限() {
			Product product = newProduct();
			product.setEstimatedPurchaseRate(BigDecimal.ONE);
			assertThat(scorer.scorePurchaseRate(product)).isEqualByComparingTo(new BigDecimal("100"));
		}
	}

	@Nested
	class ScoreTrendHeat {

		@Test
		void 商品尚未儲存_id為null時回傳null_不查詢資料庫() {
			Product product = newProduct();
			product.setId(null);

			assertThat(scorer.scoreTrendHeat(product)).isNull();
			verifyNoInteractions(trendSignalRepository);
		}

		@Test
		void 查無趨勢訊號時回傳null() {
			when(trendSignalRepository.findFirstByProductIdOrderByCollectedAtDesc(1L)).thenReturn(Optional.empty());
			assertThat(scorer.scoreTrendHeat(newProduct())).isNull();
		}

		@Test
		void 趨勢分與熱門度分皆無值時回傳null() {
			TrendSignal signal = new TrendSignal();
			signal.setCollectedAt(LocalDateTime.now());
			when(trendSignalRepository.findFirstByProductIdOrderByCollectedAtDesc(1L))
					.thenReturn(Optional.of(signal));

			assertThat(scorer.scoreTrendHeat(newProduct())).isNull();
		}

		@Test
		void 沒有採集時間時保守不套用衰減_直接回傳原始分() {
			TrendSignal signal = new TrendSignal();
			signal.setTrendScore(new BigDecimal("80"));
			signal.setPopularityScore(new BigDecimal("60"));
			signal.setCollectedAt(null);
			when(trendSignalRepository.findFirstByProductIdOrderByCollectedAtDesc(1L))
					.thenReturn(Optional.of(signal));

			// (80+60)/2 = 70，且因為沒有採集時間，不應呼叫 algorithmSettings
			BigDecimal result = scorer.scoreTrendHeat(newProduct());
			assertThat(result.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(new BigDecimal("70.00"));
			verifyNoInteractions(algorithmSettings);
		}

		@Test
		void 剛採集完成時幾乎不衰減_接近原始分() {
			TrendSignal signal = new TrendSignal();
			signal.setTrendScore(new BigDecimal("90"));
			signal.setPopularityScore(new BigDecimal("90"));
			signal.setCollectedAt(LocalDateTime.now());
			when(trendSignalRepository.findFirstByProductIdOrderByCollectedAtDesc(1L))
					.thenReturn(Optional.of(signal));
			when(algorithmSettings.getTrendHalfLifeDays()).thenReturn(14);
			when(algorithmSettings.getNeutralBaselineScore()).thenReturn(BigDecimal.valueOf(50));

			BigDecimal result = scorer.scoreTrendHeat(newProduct());
			// daysAgo=0 → freshness=1 → 不衰減，仍是 90
			assertThat(result.setScale(0, RoundingMode.HALF_UP)).isEqualByComparingTo(new BigDecimal("90"));
		}

		@Test
		void 採集已久時收斂到中性基準分() {
			TrendSignal signal = new TrendSignal();
			signal.setTrendScore(new BigDecimal("90"));
			signal.setCollectedAt(LocalDateTime.now().minusDays(365));
			when(trendSignalRepository.findFirstByProductIdOrderByCollectedAtDesc(1L))
					.thenReturn(Optional.of(signal));
			when(algorithmSettings.getTrendHalfLifeDays()).thenReturn(14);
			when(algorithmSettings.getNeutralBaselineScore()).thenReturn(BigDecimal.valueOf(50));

			BigDecimal result = scorer.scoreTrendHeat(newProduct());
			assertThat(result.setScale(0, RoundingMode.HALF_UP)).isEqualByComparingTo(new BigDecimal("50"));
		}
	}

	@Nested
	class EstimateFreightCost {

		@Test
		void 未填材積級距時視為零_不查詢設定() {
			assertThat(scorer.estimateFreightCost(newProduct())).isEqualByComparingTo(new BigDecimal("0"));
			verifyNoInteractions(algorithmSettings);
		}

		@Test
		void 材積級距為空白字串時視為零() {
			Product product = newProduct();
			product.setPackageSizeTier("   ");
			assertThat(scorer.estimateFreightCost(product)).isEqualByComparingTo(new BigDecimal("0"));
		}

		@Test
		void 合法級距時查詢對應的運費設定() {
			Product product = newProduct();
			product.setPackageSizeTier("M");
			when(algorithmSettings.getFreightCost("freight_cost_m")).thenReturn(new BigDecimal("35"));

			assertThat(scorer.estimateFreightCost(product)).isEqualByComparingTo(new BigDecimal("35"));
			verify(algorithmSettings).getFreightCost("freight_cost_m");
		}

		@Test
		void 小寫級距也能正確解析() {
			Product product = newProduct();
			product.setPackageSizeTier("m");
			when(algorithmSettings.getFreightCost("freight_cost_m")).thenReturn(new BigDecimal("35"));

			assertThat(scorer.estimateFreightCost(product)).isEqualByComparingTo(new BigDecimal("35"));
		}

		/**
		 * 資料庫裡有不認得的級距值（手動改過資料、enum 改版未同步）時，
		 * 視為未填而非拋例外——一筆髒資料不該讓整條計分鏈路中斷。
		 */
		@Test
		void 資料庫存有不合法的級距值時視為未填_不拋例外() {
			Product product = newProduct();
			product.setPackageSizeTier("XXL_NOT_A_REAL_TIER");

			assertThat(scorer.estimateFreightCost(product)).isEqualByComparingTo(new BigDecimal("0"));
			verify(algorithmSettings, never()).getFreightCost(any());
		}
	}
}
