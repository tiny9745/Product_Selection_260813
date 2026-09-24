package com.example.Product_Selection_260813.service.crawler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.entity.TrendSignal;
import com.example.Product_Selection_260813.enums.TrendSignalTrendDirection;

/**
 * 備援／模擬實作：原本 TrendService.generateSimulatedTrendSignal() 的 Random 邏輯，
 * 原封不動搬過來——以上一筆趨勢資料為基準（沒有則 50 分），每次隨機波動
 * ±(FLUCTUATION_RANGE/2) 分，來源標記固定為 SIMULATED。
 *
 * 用途有兩個：
 * <ol>
 * <li>PttMarketBuzzProvider 抓不到資料時，TrendService 改呼叫這裡，讓同步流程不中斷。
 * <li>要暫時停用 PTT 爬蟲（除錯、PTT 大規模異常）時，把 {@code @Primary} 從
 *     PttMarketBuzzProvider 移到本類別即可，TrendService 不需要任何更動。
 *     注意是「移過來」而不是只拿掉——兩個實作都沒有 {@code @Primary} 時，
 *     Spring 會因為無法決定注入哪一個而啟動失敗。
 * </ol>
 */
@Component
public class StubMarketBuzzProvider implements MarketBuzzProvider {

	public static final String SOURCE = "SIMULATED";

	// 無趨勢歷史紀錄時的起始基準分數，模擬用途，非任何實際市場資料
	private static final BigDecimal DEFAULT_BASELINE_SCORE = new BigDecimal("50.00");

	// 每次同步的隨機波動範圍：±(FLUCTUATION_RANGE/2)分
	private static final double FLUCTUATION_RANGE = 10.0;

	private static final BigDecimal SCORE_MIN = BigDecimal.ZERO;
	private static final BigDecimal SCORE_MAX = new BigDecimal("100.00");

	private final Random random = new Random();

	@Override
	public MarketBuzzSignal fetch(String keyword, TrendSignal previous) {
		BigDecimal previousTrendScore = previous != null && previous.getTrendScore() != null
				? previous.getTrendScore()
				: DEFAULT_BASELINE_SCORE;
		BigDecimal previousPopularityScore = previous != null && previous.getPopularityScore() != null
				? previous.getPopularityScore()
				: DEFAULT_BASELINE_SCORE;

		BigDecimal newTrendScore = fluctuate(previousTrendScore);
		BigDecimal newPopularityScore = fluctuate(previousPopularityScore);

		return new MarketBuzzSignal(SOURCE, keyword, newTrendScore, newPopularityScore,
				determineTrendDirection(previousTrendScore, newTrendScore), null);
	}

	private BigDecimal fluctuate(BigDecimal base) {
		double delta = (random.nextDouble() - 0.5) * FLUCTUATION_RANGE;
		BigDecimal result = base.add(BigDecimal.valueOf(delta)).setScale(2, RoundingMode.HALF_UP);
		if (result.compareTo(SCORE_MIN) < 0) {
			return SCORE_MIN;
		}
		if (result.compareTo(SCORE_MAX) > 0) {
			return SCORE_MAX;
		}
		return result;
	}

	private TrendSignalTrendDirection determineTrendDirection(BigDecimal previous, BigDecimal current) {
		int comparison = current.compareTo(previous);
		if (comparison > 0) {
			return TrendSignalTrendDirection.UP;
		}
		if (comparison < 0) {
			return TrendSignalTrendDirection.DOWN;
		}
		return TrendSignalTrendDirection.STABLE;
	}
}
