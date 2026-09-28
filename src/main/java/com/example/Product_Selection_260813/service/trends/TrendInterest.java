package com.example.Product_Selection_260813.service.trends;

import java.math.BigDecimal;

import com.example.Product_Selection_260813.enums.GoogleTrendStatus;
import com.example.Product_Selection_260813.enums.TrendSignalTrendDirection;

/**
 * 一次 Google 趨勢查詢換算出的結果：只有方向與成長率，沒有絕對熱度。
 *
 * @param status      OK＝有序列；NO_DATA＝搜尋量不足（此時其餘欄位為 null／0）
 * @param direction   依成長率判斷的方向；NO_DATA 為 null
 * @param growthRate  近期相對基準期的成長率（%）；基準期為 0 時為 null
 * @param recentAvg   近期平均（0~100 相對值）
 * @param baselineAvg 基準期平均（0~100 相對值）
 * @param pointCount  有效資料點數（不含未完整的最後一點）
 */
public record TrendInterest(
		GoogleTrendStatus status,
		TrendSignalTrendDirection direction,
		BigDecimal growthRate,
		BigDecimal recentAvg,
		BigDecimal baselineAvg,
		int pointCount) {

	public static TrendInterest noData(int pointCount) {
		return new TrendInterest(GoogleTrendStatus.NO_DATA, null, null, null, null, pointCount);
	}
}
