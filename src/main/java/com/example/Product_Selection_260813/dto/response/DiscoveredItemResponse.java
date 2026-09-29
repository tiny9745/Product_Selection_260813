package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.example.Product_Selection_260813.entity.DiscoveredItem;
import com.example.Product_Selection_260813.enums.DiscoveredItemStatus;
import com.example.Product_Selection_260813.enums.DiscoveryDismissReason;
import com.example.Product_Selection_260813.enums.GateStatus;
import com.example.Product_Selection_260813.enums.GoogleTrendStatus;
import com.example.Product_Selection_260813.enums.TrendSignalTrendDirection;

/**
 * PTT 新品探索清單的一筆（GET /api/discoveries）。
 *
 * <ul>
 * <li>mentionCount／pushVolume：最近一次探索時「近 7 天」的提及篇數與淨推文量。</li>
 * <li>popularityScore／trendScore／trendDirection：與商品熱度同一套換算的 90 天熱度；
 * 只有被選進當次前 N 名的項目才會查，其餘為 null（畫面顯示「未查熱度」，不是 0 分）。</li>
 * <li>這些都是「討論量」，不是銷量——畫面文案不可寫成銷售預測。</li>
 * <li>第二階段：fitScore＝AI 適配分（未評為 null）；temperatureGate＝規則判定的溫層（未判定為 null）；
 * google*＝Google 趨勢交叉驗證（未查為 null）。fitConcerns 已拆成陣列。</li>
 * </ul>
 *
 * @param evidence 佐證文章，新到舊，最多 {@value #MAX_EVIDENCE} 篇
 */
public record DiscoveredItemResponse(
		Long id,
		String displayName,
		String searchKeyword,
		String categoryHint,
		Long productTypeId,
		DiscoveredItemStatus status,
		int mentionCount,
		int pushVolume,
		BigDecimal popularityScore,
		BigDecimal trendScore,
		TrendSignalTrendDirection trendDirection,
		Integer windowVolume,
		LocalDateTime buzzCheckedAt,
		BigDecimal fitScore,
		String fitReason,
		List<String> fitConcerns,
		LocalDateTime fitEvaluatedAt,
		String temperatureZone,
		GateStatus temperatureGate,
		GoogleTrendStatus googleStatus,
		TrendSignalTrendDirection googleDirection,
		BigDecimal googleGrowthRate,
		LocalDateTime googleCheckedAt,
		LocalDateTime firstSeenAt,
		LocalDateTime lastSeenAt,
		Long convertedProductId,
		DiscoveryDismissReason dismissReasonCode,
		String dismissReason,
		String handledByName,
		LocalDateTime handledAt,
		List<Evidence> evidence) {

	public static final int MAX_EVIDENCE = 5;

	/** @param url 完整 PTT 網址（由 post_path 組出） */
	public record Evidence(String board, String url, String title, int pushVolume, LocalDateTime postedAt) {
	}

	public static DiscoveredItemResponse from(DiscoveredItem item, String handledByName, List<Evidence> evidence) {
		return new DiscoveredItemResponse(item.getId(), item.getDisplayName(), item.getSearchKeyword(),
				item.getCategoryHint(), item.getProductTypeId(), item.getStatus(), item.getMentionCount(),
				item.getPushVolume(), item.getPopularityScore(), item.getTrendScore(), item.getTrendDirection(),
				item.getWindowVolume(), item.getBuzzCheckedAt(), item.getFitScore(), item.getFitReason(),
				splitConcerns(item.getFitConcerns()), item.getFitEvaluatedAt(), item.getTemperatureZone(),
				item.getTemperatureGate(), item.getGoogleStatus(), item.getGoogleDirection(), item.getGoogleGrowthRate(),
				item.getGoogleCheckedAt(), item.getFirstSeenAt(), item.getLastSeenAt(), item.getConvertedProductId(),
				item.getDismissReasonCode(), item.getDismissReason(), handledByName, item.getHandledAt(), evidence);
	}

	private static List<String> splitConcerns(String concerns) {
		if (concerns == null || concerns.isBlank()) {
			return List.of();
		}
		return java.util.Arrays.stream(concerns.split("、")).map(String::trim).filter(s -> !s.isEmpty()).toList();
	}
}
