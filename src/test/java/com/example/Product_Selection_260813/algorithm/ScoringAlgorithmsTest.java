package com.example.Product_Selection_260813.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.example.Product_Selection_260813.algorithm.ScoringAlgorithms.WeightedScore;

/**
 * ScoringAlgorithms 是系統可重現性約束的核心：同一輸入必須永遠得到同一輸出。
 * 這支測試不是為了測「Java 的四則運算對不對」，而是把每個方法 Javadoc 裡
 * 明確承諾的行為（null 語意、邊界值、Javadoc 附的計算範例）釘死，避免日後
 * 有人「順手優化」一行程式碼卻悄悄改變了分數的計算結果。
 */
class ScoringAlgorithmsTest {

	@Nested
	class Shrink {

		@Test
		void 樣本數為零時完全採用先驗值() {
			BigDecimal result = ScoringAlgorithms.shrink(new BigDecimal("0.9"), 0, new BigDecimal("0.72"), 10);
			assertThat(result).isEqualByComparingTo(new BigDecimal("0.72"));
		}

		@Test
		void 原始比率為null時完全採用先驗值_不需要呼叫端先做null判斷() {
			BigDecimal result = ScoringAlgorithms.shrink(null, 5, new BigDecimal("0.72"), 10);
			assertThat(result).isEqualByComparingTo(new BigDecimal("0.72"));
		}

		/**
		 * 對照 Javadoc 附的範例：品類 A 開 3 次全成團（100%）vs 品類 B 開 50 次
		 * 成團 45 次（90%），先驗值 0.72、k=10 時，收縮後 A=0.785、B=0.870，
		 * 排序反轉（B 反而更高）。這個反轉正是貝氏收縮存在的理由，是整支
		 * 方法唯一真正重要的行為，必須釘死。
		 */
		@Test
		void 對照Javadoc範例_樣本少的A收縮後排序輸給樣本多的B() {
			BigDecimal scoreA = ScoringAlgorithms.shrink(BigDecimal.ONE, 3, new BigDecimal("0.72"), 10);
			BigDecimal scoreB = ScoringAlgorithms.shrink(new BigDecimal("0.9"), 50, new BigDecimal("0.72"), 10);

			assertThat(scoreA.setScale(3, RoundingMode.HALF_UP)).isEqualByComparingTo(new BigDecimal("0.785"));
			assertThat(scoreB.setScale(3, RoundingMode.HALF_UP)).isEqualByComparingTo(new BigDecimal("0.870"));
			assertThat(scoreA).isLessThan(scoreB);
		}

		@Test
		void 平滑常數k不可為零或負數() {
			assertThatIllegalArgumentException()
					.isThrownBy(() -> ScoringAlgorithms.shrink(BigDecimal.ONE, 5, new BigDecimal("0.5"), 0));
			assertThatIllegalArgumentException()
					.isThrownBy(() -> ScoringAlgorithms.shrink(BigDecimal.ONE, 5, new BigDecimal("0.5"), -1));
		}

		@Test
		void 先驗值不可為null() {
			assertThatIllegalArgumentException()
					.isThrownBy(() -> ScoringAlgorithms.shrink(BigDecimal.ONE, 5, null, 10));
		}
	}

	@Nested
	class ApplyFreshnessDecay {

		@Test
		void 原始分數為null時原樣回傳null() {
			assertThat(ScoringAlgorithms.applyFreshnessDecay(null, 10, 14, BigDecimal.valueOf(50))).isNull();
		}

		@Test
		void 半衰期不可為零或負數() {
			assertThatIllegalArgumentException().isThrownBy(
					() -> ScoringAlgorithms.applyFreshnessDecay(BigDecimal.valueOf(90), 10, 0, BigDecimal.valueOf(50)));
		}

		@Test
		void 距今零天時完全不衰減_回傳原始分數() {
			BigDecimal result = ScoringAlgorithms.applyFreshnessDecay(BigDecimal.valueOf(90), 0, 14,
					BigDecimal.valueOf(50));
			assertThat(result).isEqualByComparingTo(new BigDecimal("90"));
		}

		/** 資料時間比現在還新（時鐘誤差）時，負數天數視為 0，不讓 freshness 超過 1。 */
		@Test
		void 距今天數為負數時視為零天_效果與零天相同() {
			BigDecimal atZero = ScoringAlgorithms.applyFreshnessDecay(BigDecimal.valueOf(90), 0, 14,
					BigDecimal.valueOf(50));
			BigDecimal negative = ScoringAlgorithms.applyFreshnessDecay(BigDecimal.valueOf(90), -3, 14,
					BigDecimal.valueOf(50));
			assertThat(negative).isEqualByComparingTo(atZero);
		}

		@Test
		void 距今天數等於半衰期時_衰減至原始分與基準分的中點() {
			BigDecimal result = ScoringAlgorithms.applyFreshnessDecay(BigDecimal.valueOf(90), 14, 14,
					BigDecimal.valueOf(50));
			// freshness = 0.5 時，調整後 = 0.5*90 + 0.5*50 = 70
			assertThat(result.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(new BigDecimal("70.00"));
		}

		@Test
		void 距今天數遠大於半衰期時_收斂到中性基準分而非歸零() {
			BigDecimal result = ScoringAlgorithms.applyFreshnessDecay(BigDecimal.valueOf(90), 365, 14,
					BigDecimal.valueOf(50));
			assertThat(result.setScale(0, RoundingMode.HALF_UP)).isEqualByComparingTo(new BigDecimal("50"));
		}
	}

	@Nested
	class NormalizeByBand {

		@Test
		void 實際值為null時回傳null_而非中性值() {
			assertThat(ScoringAlgorithms.normalizeByBand(null, BigDecimal.ZERO, BigDecimal.ONE)).isNull();
		}

		@Test
		void 上下界不可為null() {
			assertThatIllegalArgumentException().isThrownBy(
					() -> ScoringAlgorithms.normalizeByBand(BigDecimal.valueOf(0.3), null, BigDecimal.ONE));
			assertThatIllegalArgumentException().isThrownBy(
					() -> ScoringAlgorithms.normalizeByBand(BigDecimal.valueOf(0.3), BigDecimal.ZERO, null));
		}

		@Test
		void 上界必須大於下界() {
			assertThatIllegalArgumentException().isThrownBy(() -> ScoringAlgorithms
					.normalizeByBand(BigDecimal.valueOf(0.3), BigDecimal.valueOf(0.5), BigDecimal.valueOf(0.1)));
		}

		@Test
		void 值落在區間中點時得五十分() {
			BigDecimal result = ScoringAlgorithms.normalizeByBand(new BigDecimal("0.3"), new BigDecimal("0.1"),
					new BigDecimal("0.5"));
			assertThat(result.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(new BigDecimal("50.00"));
		}

		@Test
		void 值低於下界時夾在零分_而非負分() {
			BigDecimal result = ScoringAlgorithms.normalizeByBand(new BigDecimal("-0.2"), new BigDecimal("0.1"),
					new BigDecimal("0.5"));
			assertThat(result.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(new BigDecimal("0.00"));
		}

		@Test
		void 值高於上界時夾在一百分_而非超過一百() {
			BigDecimal result = ScoringAlgorithms.normalizeByBand(new BigDecimal("0.9"), new BigDecimal("0.1"),
					new BigDecimal("0.5"));
			assertThat(result.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(new BigDecimal("100.00"));
		}

		@Test
		void 值恰好等於上下界時得零分或一百分() {
			assertThat(ScoringAlgorithms.normalizeByBand(new BigDecimal("0.1"), new BigDecimal("0.1"),
					new BigDecimal("0.5")).setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(new BigDecimal("0.00"));
			assertThat(ScoringAlgorithms.normalizeByBand(new BigDecimal("0.5"), new BigDecimal("0.1"),
					new BigDecimal("0.5")).setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(new BigDecimal("100.00"));
		}
	}

	@Nested
	class Percentile {

		@Test
		void 樣本為空或null時拋例外() {
			assertThatIllegalArgumentException().isThrownBy(() -> ScoringAlgorithms.percentile(List.of(), 50));
			assertThatIllegalArgumentException().isThrownBy(() -> ScoringAlgorithms.percentile(null, 50));
		}

		@Test
		void 分位數必須介於零至一百() {
			List<Integer> values = List.of(1, 2, 3);
			assertThatIllegalArgumentException().isThrownBy(() -> ScoringAlgorithms.percentile(values, -1));
			assertThatIllegalArgumentException().isThrownBy(() -> ScoringAlgorithms.percentile(values, 101));
		}

		@Test
		void 只有一筆樣本時_任何分位數都回傳同一個值() {
			List<Integer> values = List.of(42);
			assertThat(ScoringAlgorithms.percentile(values, 10)).isEqualByComparingTo(new BigDecimal("42"));
			assertThat(ScoringAlgorithms.percentile(values, 90)).isEqualByComparingTo(new BigDecimal("42"));
		}

		/**
		 * 對照設計文件附的範例資料：[40,55,60,75,80,90,110,120,150,300]，
		 * P50=85、P75=117.5、P90=165，與 NumPy 預設 method='linear' 一致。
		 */
		@Test
		void 對照設計文件範例資料_線性內插結果() {
			List<Integer> values = List.of(40, 55, 60, 75, 80, 90, 110, 120, 150, 300);

			assertThat(ScoringAlgorithms.percentile(values, 50)).isEqualByComparingTo(new BigDecimal("85"));
			assertThat(ScoringAlgorithms.percentile(values, 75)).isEqualByComparingTo(new BigDecimal("117.5"));
			assertThat(ScoringAlgorithms.percentile(values, 90)).isEqualByComparingTo(new BigDecimal("165"));
		}

		@Test
		void 傳入順序不影響結果_內部會自行排序且不修改原始List() {
			List<Integer> shuffled = List.of(300, 40, 150, 90, 55, 110, 75, 60, 120, 80);
			assertThat(ScoringAlgorithms.percentile(shuffled, 50)).isEqualByComparingTo(new BigDecimal("85"));
		}
	}

	@Nested
	class WeightedAverageTest {

		@Test
		void 樣本為空或null時回傳null() {
			assertThat(ScoringAlgorithms.weightedAverage(null)).isNull();
			assertThat(ScoringAlgorithms.weightedAverage(List.of())).isNull();
		}

		/**
		 * 所有因子皆無資料時回傳 null 而非 0——呼叫端要能區分「零分」與
		 * 「無法計算」，這種情況理論上會先被 GATE_DATA_COMPLETENESS 攔下，
		 * 但這支方法本身不能假設呼叫端一定做了這件事。
		 */
		@Test
		void 所有分項皆無資料時回傳null_而非零分() {
			List<WeightedScore> scores = List.of(
					new WeightedScore("A", null, BigDecimal.valueOf(60)),
					new WeightedScore("B", null, BigDecimal.valueOf(40)));
			assertThat(ScoringAlgorithms.weightedAverage(scores)).isNull();
		}

		@Test
		void 缺漏因子從分子分母排除_而非算作零分拉低總分() {
			List<WeightedScore> scores = List.of(
					new WeightedScore("A", BigDecimal.valueOf(80), BigDecimal.valueOf(60)),
					new WeightedScore("B", null, BigDecimal.valueOf(40)));
			// 只剩 A 有資料：權重重新正規化後等同 A 自己的分數 80，
			// 不是 (80*60+0*40)/100=48 這種把缺漏值當 0 分計入的算法。
			BigDecimal result = ScoringAlgorithms.weightedAverage(scores);
			assertThat(result.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(new BigDecimal("80.00"));
		}

		@Test
		void 權重為零的因子視為未啟用_不影響分母() {
			List<WeightedScore> scores = List.of(
					new WeightedScore("A", BigDecimal.valueOf(80), BigDecimal.valueOf(60)),
					new WeightedScore("B", BigDecimal.valueOf(20), BigDecimal.ZERO));
			BigDecimal result = ScoringAlgorithms.weightedAverage(scores);
			assertThat(result.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(new BigDecimal("80.00"));
		}

		@Test
		void list中混入null元素時安全跳過_不拋例外() {
			List<WeightedScore> scores = new java.util.ArrayList<>();
			scores.add(new WeightedScore("A", BigDecimal.valueOf(80), BigDecimal.valueOf(60)));
			scores.add(null);
			BigDecimal result = ScoringAlgorithms.weightedAverage(scores);
			assertThat(result.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(new BigDecimal("80.00"));
		}

		@Test
		void 兩個分項都有資料時_依權重比例加權平均() {
			List<WeightedScore> scores = List.of(
					new WeightedScore("A", BigDecimal.valueOf(80), BigDecimal.valueOf(60)),
					new WeightedScore("B", BigDecimal.valueOf(60), BigDecimal.valueOf(40)));
			// (80*60 + 60*40) / 100 = 72
			BigDecimal result = ScoringAlgorithms.weightedAverage(scores);
			assertThat(result.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(new BigDecimal("72.00"));
		}
	}

	@Nested
	class Clamp {

		@Test
		void 值為null時原樣回傳null() {
			assertThat(ScoringAlgorithms.clamp(null, BigDecimal.ZERO, BigDecimal.TEN)).isNull();
		}

		@Test
		void 低於下限時夾在下限() {
			assertThat(ScoringAlgorithms.clamp(BigDecimal.valueOf(-5), BigDecimal.ZERO, BigDecimal.TEN))
					.isEqualByComparingTo(new BigDecimal("0"));
		}

		@Test
		void 高於上限時夾在上限() {
			assertThat(ScoringAlgorithms.clamp(BigDecimal.valueOf(15), BigDecimal.ZERO, BigDecimal.TEN))
					.isEqualByComparingTo(new BigDecimal("10"));
		}

		@Test
		void 落在區間內時原樣回傳() {
			assertThat(ScoringAlgorithms.clamp(BigDecimal.valueOf(5), BigDecimal.ZERO, BigDecimal.TEN))
					.isEqualByComparingTo(new BigDecimal("5"));
		}
	}
}
