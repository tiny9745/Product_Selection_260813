package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;

import com.example.Product_Selection_260813.enums.TrendSignalTrendDirection;

/**
 * GET /api/dashboard/trend-leaderboard 清單裡的單一項目。
 *
 * ⚠️ 2026-09-25 新增：跟 DashboardRecommendationItem（AI推薦Top10，依
 * 「綜合加權總分」排序，七大因子混在一起）刻意區隔開——這支只看趨勢
 * 單一因子最新一筆的排序，讓「總分被其他因子拉低、但熱度其實在飆升」
 * 的商品也能被看見，不是重複做一次 Top 10。
 *
 * source 欄位直接透傳 trend_signals.source（'PTT' 或 'SIMULATED'），
 * 前端依此判斷要不要用橘色字標示模擬資料，跟品項詳情頁的既有做法一致。
 */
public class DashboardTrendLeaderboardItem {

	private Long productId;
	private String productName;
	private BigDecimal popularityScore;
	private TrendSignalTrendDirection trendDirection;
	private String source;
	private String keyword;

	public Long getProductId() {
		return productId;
	}

	public void setProductId(Long productId) {
		this.productId = productId;
	}

	public String getProductName() {
		return productName;
	}

	public void setProductName(String productName) {
		this.productName = productName;
	}

	public BigDecimal getPopularityScore() {
		return popularityScore;
	}

	public void setPopularityScore(BigDecimal popularityScore) {
		this.popularityScore = popularityScore;
	}

	public TrendSignalTrendDirection getTrendDirection() {
		return trendDirection;
	}

	public void setTrendDirection(TrendSignalTrendDirection trendDirection) {
		this.trendDirection = trendDirection;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getKeyword() {
		return keyword;
	}

	public void setKeyword(String keyword) {
		this.keyword = keyword;
	}
}
