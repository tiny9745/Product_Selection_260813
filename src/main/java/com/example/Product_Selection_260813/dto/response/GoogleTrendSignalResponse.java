package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.Product_Selection_260813.entity.GoogleTrendSignal;
import com.example.Product_Selection_260813.enums.GoogleTrendStatus;
import com.example.Product_Selection_260813.enums.TrendSignalTrendDirection;

/**
 * 品項詳情頁「Google 趨勢參考」（GET /api/products/{id}/google-trend）。
 * 只有方向與成長率；recentAvg／baselineAvg 是 0～100 的相對值，只能跟同一筆的另一個值比，不能跨商品比較。
 */
public record GoogleTrendSignalResponse(
		Long productId,
		String keyword,
		GoogleTrendStatus status,
		TrendSignalTrendDirection direction,
		BigDecimal growthRate,
		BigDecimal recentAvg,
		BigDecimal baselineAvg,
		int pointCount,
		LocalDateTime collectedAt) {

	public static GoogleTrendSignalResponse from(GoogleTrendSignal signal) {
		return new GoogleTrendSignalResponse(signal.getProductId(), signal.getKeyword(), signal.getStatus(),
				signal.getDirection(), signal.getGrowthRate(), signal.getRecentAvg(), signal.getBaselineAvg(),
				signal.getPointCount(), signal.getCollectedAt());
	}
}
