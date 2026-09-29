package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.Product_Selection_260813.entity.TrendSignal;
import com.example.Product_Selection_260813.enums.TrendSignalTrendDirection;

/** 「連續上升」判定（2026-09-29，取代熱度規則選品批次的「連續 3 筆 UP」條件）。 */
class RecentTrendServiceTest {

	private static final TrendSignalTrendDirection UP = TrendSignalTrendDirection.UP;
	private static final TrendSignalTrendDirection DOWN = TrendSignalTrendDirection.DOWN;
	private static final TrendSignalTrendDirection STABLE = TrendSignalTrendDirection.STABLE;

	@Test
	void 最近三次都上升才算連續上升() {
		assertThat(RecentTrendService.isConsecutiveRise(List.of(UP, UP, UP))).isTrue();
		assertThat(RecentTrendService.isConsecutiveRise(List.of(UP, UP, STABLE))).isFalse();
		assertThat(RecentTrendService.isConsecutiveRise(List.of(DOWN, UP, UP))).isFalse();
	}

	@Test
	void 筆數不足或沒有資料_不算連續上升() {
		assertThat(RecentTrendService.isConsecutiveRise(List.of(UP, UP))).isFalse();
		assertThat(RecentTrendService.isConsecutiveRise(List.of())).isFalse();
		assertThat(RecentTrendService.isConsecutiveRise(null)).isFalse();
	}

	@Test
	void 方向含null_不算連續上升() {
		assertThat(RecentTrendService.isConsecutiveRise(java.util.Arrays.asList(UP, null, UP))).isFalse();
	}

	@Test
	void 摘要取最新一筆的分數與來源_方向最多三筆() {
		TrendSignal latest = signal(UP, "72.50", "PTT");
		RecentTrendService.RecentTrend trend = RecentTrendService.summarize(
				List.of(latest, signal(UP, "60", "PTT"), signal(UP, "55", "SIMULATED"), signal(DOWN, "50", "PTT")));

		assertThat(trend.popularityScore()).isEqualByComparingTo("72.50");
		assertThat(trend.trendDirection()).isEqualTo(UP);
		assertThat(trend.source()).isEqualTo("PTT");
		assertThat(trend.recentTrendDirections()).containsExactly(UP, UP, UP);
		assertThat(trend.consecutiveRise()).isTrue();
	}

	@Test
	void 摘要只有一筆_方向一筆且不算連續上升() {
		RecentTrendService.RecentTrend trend = RecentTrendService.summarize(List.of(signal(UP, "80", "PTT")));
		assertThat(trend.recentTrendDirections()).containsExactly(UP);
		assertThat(trend.consecutiveRise()).isFalse();
	}

	@Test
	void 沒有商品id_不查資料庫直接回傳空結果() {
		assertThat(new RecentTrendService().byProductIds(List.of())).isEmpty();
		assertThat(new RecentTrendService().byProductIds(null)).isEmpty();
	}

	private static TrendSignal signal(TrendSignalTrendDirection direction, String score, String source) {
		TrendSignal signal = new TrendSignal();
		signal.setTrendDirection(direction);
		signal.setPopularityScore(new BigDecimal(score));
		signal.setSource(source);
		return signal;
	}
}
