package com.example.Product_Selection_260813.json;

import java.math.BigDecimal;

/**
 * GET /api/products/{id}/trend/history 清單裡的單一資料點。
 *
 * ⚠️ 2026-09-25 新增：品項詳情頁「熱度趨勢圖」用。跟 TrendSnapshot（單筆、
 * 給同步按鈕回傳用）不是同一個型別——這支是陣列裡的一個點，故意只留
 * 畫圖需要的三個欄位（時間、分數、方向），不重複帶 source／keyword，
 * 減少前端要處理的資料量（歷史可能有上百筆）。
 *
 * collectedAt 一樣用 String（ISO-8601），理由同 TrendSnapshot 類別註解：
 * 避免額外客製化 Jackson 的 LocalDateTime 序列化設定。
 */
public class TrendHistoryPoint {

    private String collectedAt;

    private BigDecimal popularityScore;

    private String trendDirection;

    public String getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(String collectedAt) {
        this.collectedAt = collectedAt;
    }

    public BigDecimal getPopularityScore() {
        return popularityScore;
    }

    public void setPopularityScore(BigDecimal popularityScore) {
        this.popularityScore = popularityScore;
    }

    public String getTrendDirection() {
        return trendDirection;
    }

    public void setTrendDirection(String trendDirection) {
        this.trendDirection = trendDirection;
    }
}
