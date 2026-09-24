package com.example.Product_Selection_260813.service.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.example.Product_Selection_260813.enums.TrendSignalTrendDirection;

/**
 * 把 MarketBuzzNormalizer 的校準結果釘死。數值取自 2026-09-24 PTT 實測（見該類別註解），
 * 之後若調整公式或預設錨點，這裡的分數落點會先壞，提醒一併檢查
 * AiSuggestionBatchService 的 &gt;70 門檻是否還合理。
 */
class MarketBuzzNormalizerTest {

	private static final int REFERENCE = 20;
	private static final int HOT = 1000;

	private static BigDecimal popularity(int volume) {
		return MarketBuzzNormalizer.popularityScore(volume, REFERENCE, HOT);
	}

	@Nested
	class PopularityScore {

		@Test
		void 沒有討論為零分() {
			assertThat(popularity(0)).isEqualByComparingTo("0");
		}

		@Test
		void 參考量為五十分() {
			assertThat(popularity(REFERENCE)).isEqualByComparingTo("50");
		}

		@Test
		void 達到或超過爆紅量為一百分() {
			assertThat(popularity(HOT)).isEqualByComparingTo("100");
			assertThat(popularity(1466)).isEqualByComparingTo("100");
		}

		@Test
		void 一般商品落在四十到六十分() {
			// 實測：雞胸肉19、泡麵21、咖啡豆22、除濕機30、牙膏/洗衣精41
			for (int volume : new int[] { 19, 21, 22, 30, 41 }) {
				assertThat(popularity(volume)).as("討論量 %d", volume).isBetween(new BigDecimal("40"),
						new BigDecimal("60"));
			}
		}

		@Test
		void 熱門商品超過七十分門檻_一般商品不會() {
			// 實測：蛋捲145、行動電源298 應被 AI 建議；衛生紙58 不應該
			assertThat(popularity(145)).isGreaterThan(new BigDecimal("70"));
			assertThat(popularity(298)).isGreaterThan(new BigDecimal("70"));
			assertThat(popularity(58)).isLessThan(new BigDecimal("70"));
		}

		@Test
		void 討論量越多分數越高() {
			BigDecimal previous = popularity(0);
			for (int volume = 1; volume <= HOT; volume++) {
				BigDecimal current = popularity(volume);
				assertThat(current).as("討論量 %d", volume).isGreaterThanOrEqualTo(previous);
				previous = current;
			}
		}

		@Test
		void 錨點設定不合理時拒絕() {
			assertThatThrownBy(() -> MarketBuzzNormalizer.popularityScore(10, 0, 100))
					.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> MarketBuzzNormalizer.popularityScore(10, 100, 100))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Nested
	class TrendScore {

		@Test
		void 沒有討論為中性五十分() {
			assertThat(MarketBuzzNormalizer.trendScore(0, 0, 90, 7)).isEqualByComparingTo("50");
		}

		@Test
		void 近期速度與平均相同為五十分() {
			// 90 天 900 量，平均每 7 天 70
			assertThat(MarketBuzzNormalizer.trendScore(900, 70, 90, 7)).isEqualByComparingTo("50");
		}

		@Test
		void 近期升溫高於五十分_退燒低於五十分() {
			// 實測：蛋捲 145（近7天41）升溫；行動電源 298（近7天0）退燒
			assertThat(MarketBuzzNormalizer.trendScore(145, 41, 90, 7)).isGreaterThan(new BigDecimal("55"));
			assertThat(MarketBuzzNormalizer.trendScore(298, 0, 90, 7)).isLessThan(new BigDecimal("45"));
		}

		@Test
		void 極小樣本不會被放大成暴漲() {
			// 平常沒討論、這週剛好 1 篇：不應該直接判定大幅升溫
			assertThat(MarketBuzzNormalizer.trendScore(1, 1, 90, 7)).isLessThan(new BigDecimal("55"));
		}

		@Test
		void 分數限制在零到一百() {
			assertThat(MarketBuzzNormalizer.trendScore(100000, 100000, 90, 7)).isEqualByComparingTo("100");
			assertThat(MarketBuzzNormalizer.trendScore(100000, 0, 90, 7)).isEqualByComparingTo("0");
		}
	}

	@Nested
	class Direction {

		@Test
		void 依門檻判斷方向() {
			assertThat(MarketBuzzNormalizer.direction(new BigDecimal("55"))).isEqualTo(TrendSignalTrendDirection.UP);
			assertThat(MarketBuzzNormalizer.direction(new BigDecimal("50"))).isEqualTo(TrendSignalTrendDirection.STABLE);
			assertThat(MarketBuzzNormalizer.direction(new BigDecimal("45"))).isEqualTo(TrendSignalTrendDirection.DOWN);
		}
	}
}
