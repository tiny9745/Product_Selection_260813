package com.example.Product_Selection_260813.service.trends;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.example.Product_Selection_260813.enums.GoogleTrendStatus;
import com.example.Product_Selection_260813.enums.TrendSignalTrendDirection;

/**
 * 把一條 Google 趨勢序列（每日 0～100 相對值）換算成方向與成長率。
 *
 * 只比較「同一條序列內部」的前後變化：Google 每次查詢都以該次峰值縮放到 100，
 * 不同商品、不同次查詢的絕對值不可比，但同一條序列的前後比例是有意義的。
 *
 * 近期＝最後 7 個完整資料點；基準期＝再往前最多 28 個點（約 4 週）。
 * 用 7 天而不是單日，是因為每日值受星期幾影響很大（週末搜尋量不同），
 * 7 天平均剛好抵銷星期效應。
 */
public final class TrendInterestCalculator {

	static final int RECENT_POINTS = 7;
	static final int BASELINE_POINTS = 28;

	/** 基準期至少要有這麼多點才判斷方向，太短的序列雜訊太大。 */
	static final int MIN_BASELINE_POINTS = 7;

	/** 成長率超過 ±15% 才算上升／下降。實測氣炸鍋一般週間波動約 ±5%，15% 可避免被日常起伏觸發。 */
	static final BigDecimal DIRECTION_THRESHOLD = BigDecimal.valueOf(15);

	private TrendInterestCalculator() {
	}

	/** @param values 依時間正序、已排除「未完整」最後一點的每日值 */
	public static TrendInterest fromSeries(List<Integer> values) {
		int size = values.size();
		boolean allZero = values.stream().allMatch(v -> v == null || v == 0);
		if (size < RECENT_POINTS + MIN_BASELINE_POINTS || allZero) {
			return TrendInterest.noData(size);
		}

		List<Integer> recent = values.subList(size - RECENT_POINTS, size);
		List<Integer> baseline = values.subList(Math.max(0, size - RECENT_POINTS - BASELINE_POINTS),
				size - RECENT_POINTS);
		BigDecimal recentAvg = average(recent);
		BigDecimal baselineAvg = average(baseline);

		BigDecimal growthRate;
		TrendSignalTrendDirection direction;
		if (baselineAvg.signum() == 0) {
			// 基準期完全沒有搜尋量：成長率無法定義（除以 0），近期有量就是從無到有
			growthRate = null;
			direction = recentAvg.signum() > 0 ? TrendSignalTrendDirection.UP : TrendSignalTrendDirection.STABLE;
		} else {
			growthRate = recentAvg.subtract(baselineAvg)
					.multiply(BigDecimal.valueOf(100))
					.divide(baselineAvg, 2, RoundingMode.HALF_UP);
			if (growthRate.compareTo(DIRECTION_THRESHOLD) >= 0) {
				direction = TrendSignalTrendDirection.UP;
			} else if (growthRate.compareTo(DIRECTION_THRESHOLD.negate()) <= 0) {
				direction = TrendSignalTrendDirection.DOWN;
			} else {
				direction = TrendSignalTrendDirection.STABLE;
			}
		}
		return new TrendInterest(GoogleTrendStatus.OK, direction, growthRate, recentAvg, baselineAvg, size);
	}

	private static BigDecimal average(List<Integer> values) {
		int sum = values.stream().mapToInt(v -> v == null ? 0 : v).sum();
		return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
	}
}
