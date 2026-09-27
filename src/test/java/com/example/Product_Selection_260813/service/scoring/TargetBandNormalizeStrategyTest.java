package com.example.Product_Selection_260813.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.example.Product_Selection_260813.algorithm.ScoringAlgorithms;
import com.example.Product_Selection_260813.entity.FactorDefinition;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductTypeScoreBand;
import com.example.Product_Selection_260813.service.resolver.ScoreBandResolver;

/**
 * 目標區間正規化的線性／對數兩條曲線（社群聲量熱度 V29 改用對數）。
 */
class TargetBandNormalizeStrategyTest {

	private static BigDecimal log(double value) {
		return ScoringAlgorithms.normalizeByBandLog(BigDecimal.valueOf(value), BigDecimal.ZERO,
				new BigDecimal("300"));
	}

	@Nested
	class NormalizeByBandLog {

		@Test
		void 端點語意與線性版相同_下界0分_上界100分_高於上界滿分() {
			assertThat(log(0)).isEqualByComparingTo("0");
			assertThat(log(300)).isEqualByComparingTo("100");
			assertThat(log(5000)).isEqualByComparingTo("100");
			assertThat(log(-3)).isEqualByComparingTo("0");
		}

		@Test
		void 長尾資料中段拉得開_一般商品不會全擠在0分附近() {
			// 線性（上界 300）時 10 次只有 3.33 分
			assertThat(ScoringAlgorithms.normalizeByBand(BigDecimal.TEN, BigDecimal.ZERO, new BigDecimal("300")))
					.isLessThan(new BigDecimal("4"));
			assertThat(log(10)).isBetween(new BigDecimal("41"), new BigDecimal("43"));
			assertThat(log(30)).isBetween(new BigDecimal("59"), new BigDecimal("61"));
			assertThat(log(100)).isBetween(new BigDecimal("80"), new BigDecimal("82"));
		}

		@Test
		void 次數越多分數越高() {
			BigDecimal previous = log(0);
			for (int v = 1; v <= 300; v++) {
				BigDecimal current = log(v);
				assertThat(current).as("提及 %d 次", v).isGreaterThan(previous);
				previous = current;
			}
		}

		@Test
		void 下界不為零時以下界為起點() {
			BigDecimal score = ScoringAlgorithms.normalizeByBandLog(new BigDecimal("15"), BigDecimal.TEN,
					new BigDecimal("310"));
			assertThat(score).isEqualByComparingTo(log(5));
		}

		@Test
		void 缺值回傳null_區間不合理時拒絕() {
			assertThat(ScoringAlgorithms.normalizeByBandLog(null, BigDecimal.ZERO, BigDecimal.TEN)).isNull();
			assertThatThrownBy(() -> ScoringAlgorithms.normalizeByBandLog(BigDecimal.ONE, BigDecimal.TEN,
					BigDecimal.TEN)).isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Nested
	class Strategy {

		private ScoreBandResolver resolver;
		private TargetBandNormalizeStrategy strategy;
		private final Product product = new Product();

		@BeforeEach
		void setUp() {
			resolver = mock(ScoreBandResolver.class);
			strategy = new TargetBandNormalizeStrategy(resolver);
			ProductTypeScoreBand band = new ProductTypeScoreBand();
			band.setLowerBound(BigDecimal.ZERO);
			band.setUpperBound(new BigDecimal("300"));
			when(resolver.resolve(null, "SOCIAL_BUZZ")).thenReturn(Optional.of(band));
		}

		private FactorDefinition socialBuzz(Map<String, BigDecimal> params) {
			FactorDefinition definition = new FactorDefinition();
			definition.setFactorCode("SOCIAL_BUZZ");
			definition.setCustomFieldDefinitionId(2L);
			definition.setStrategyParams(params);
			return definition;
		}

		@Test
		void 帶logCurve時用對數曲線() {
			BigDecimal score = strategy.calculate(product, socialBuzz(Map.of("logCurve", BigDecimal.ONE)),
					Map.of(2L, BigDecimal.TEN));
			assertThat(score).isEqualByComparingTo(log(10));
		}

		@Test
		void 沒帶參數或logCurve為0時維持線性_既有因子不受影響() {
			BigDecimal linear = ScoringAlgorithms.normalizeByBand(BigDecimal.TEN, BigDecimal.ZERO,
					new BigDecimal("300"));
			assertThat(strategy.calculate(product, socialBuzz(null), Map.of(2L, BigDecimal.TEN)))
					.isEqualByComparingTo(linear);
			assertThat(strategy.calculate(product, socialBuzz(Map.of("logCurve", BigDecimal.ZERO)),
					Map.of(2L, BigDecimal.TEN))).isEqualByComparingTo(linear);
		}

		@Test
		void 沒填提及次數時回傳null_從加權分母排除() {
			assertThat(strategy.calculate(product, socialBuzz(Map.of("logCurve", BigDecimal.ONE)), Map.of()))
					.isNull();
		}
	}
}
