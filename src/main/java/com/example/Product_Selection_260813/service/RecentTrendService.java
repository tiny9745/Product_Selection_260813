package com.example.Product_Selection_260813.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.Product_Selection_260813.entity.TrendSignal;
import com.example.Product_Selection_260813.enums.TrendSignalTrendDirection;
import com.example.Product_Selection_260813.repository.TrendSignalRepository;

/**
 * 商品「最近幾次熱度同步」的摘要（2026-09-29）：最新熱度分數、方向、來源、最近 3 次方向，以及是否「連續上升」。
 *
 * <b>為什麼新增：</b>熱度建議清單與 03:00 熱度規則選品批次移除後（決議：原批次會把操作人員已建立的待審商品
 * 改成 AI_SUGGESTED，使商品從品項清單、待審清單、推薦 Top 10 消失），規則裡仍有價值的「連續 3 次上升」訊號
 * 改成唯讀標記，顯示在品項管理清單與儀表板熱度排行。兩處共用這裡的判定，只有一份規則。
 *
 * 「連續上升」只是提醒，不改變任何商品狀態，也不參與評分（熱度本身已經是評分因子之一）。
 */
@Service
public class RecentTrendService {

	/** 連續上升需要的次數：最近這幾次同步的方向都必須是 UP，筆數不足不算。 */
	public static final int CONSECUTIVE_RISE_COUNT = 3;

	@Autowired
	private TrendSignalRepository trendSignalRepository;

	/**
	 * @param popularityScore        最新一筆的熱度分數（可能為 null）
	 * @param trendDirection         最新一筆的方向
	 * @param source                 最新一筆的來源（PTT／SIMULATED）
	 * @param recentTrendDirections  最近最多 {@value #CONSECUTIVE_RISE_COUNT} 筆的方向，新到舊
	 * @param consecutiveRise        最近 {@value #CONSECUTIVE_RISE_COUNT} 次同步都上升
	 */
	public record RecentTrend(BigDecimal popularityScore, TrendSignalTrendDirection trendDirection, String source,
			List<TrendSignalTrendDirection> recentTrendDirections, boolean consecutiveRise) {
	}

	/** 一次查出多個商品的摘要；沒有任何趨勢資料的商品不會出現在結果裡。 */
	public Map<Long, RecentTrend> byProductIds(Collection<Long> productIds) {
		if (productIds == null || productIds.isEmpty()) {
			return Map.of();
		}
		List<TrendSignal> signals = trendSignalRepository.findRecentByProductIds(productIds.stream().distinct().toList(),
				CONSECUTIVE_RISE_COUNT);
		// 查詢結果已依 product_id、新到舊排序；groupingBy 預設的 ArrayList 會保留這個順序
		Map<Long, List<TrendSignal>> byProduct = signals.stream()
				.collect(Collectors.groupingBy(TrendSignal::getProductId, HashMap::new, Collectors.toList()));
		Map<Long, RecentTrend> result = new HashMap<>();
		byProduct.forEach((productId, recent) -> result.put(productId, summarize(recent)));
		return result;
	}

	/** recentNewestFirst 不可為空。 */
	static RecentTrend summarize(List<TrendSignal> recentNewestFirst) {
		TrendSignal latest = recentNewestFirst.get(0);
		List<TrendSignalTrendDirection> directions = recentNewestFirst.stream()
				.limit(CONSECUTIVE_RISE_COUNT).map(TrendSignal::getTrendDirection).toList();
		return new RecentTrend(latest.getPopularityScore(), latest.getTrendDirection(), latest.getSource(), directions,
				isConsecutiveRise(directions));
	}

	/** 最近 {@value #CONSECUTIVE_RISE_COUNT} 次（新到舊）全部是 UP 才成立；筆數不足、含 null 都不成立。 */
	public static boolean isConsecutiveRise(List<TrendSignalTrendDirection> newestFirst) {
		return newestFirst != null && newestFirst.size() >= CONSECUTIVE_RISE_COUNT
				&& newestFirst.stream().limit(CONSECUTIVE_RISE_COUNT)
						.allMatch(direction -> direction == TrendSignalTrendDirection.UP);
	}
}
