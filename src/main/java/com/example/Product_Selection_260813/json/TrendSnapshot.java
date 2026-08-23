package com.example.Product_Selection_260813.json;

import java.math.BigDecimal;

/**
 * review_records.trend_snapshot：審核當下凍結的趨勢資料快照，純供事後回顧顯示，
 * 不參與任何日期運算。
 *
 * collectedAt刻意用String（ISO-8601格式字串），不用LocalDateTime：
 * JSON格式本身沒有原生日期型別，Hibernate處理@JdbcTypeCode(SqlTypes.JSON)欄位
 * 內部走的是一個獨立於Spring主要設定之外的Jackson ObjectMapper，預設不認得
 * java.time.LocalDateTime，需要額外註冊JavaTimeModule才能處理。與其為了遷就
 * LocalDateTime另外客製化Hibernate的JSON序列化設定（多一層Spring Bean需要維護，
 * 且曾在Bean初始化順序上踩過坑），不如让「凍結快照、只供顯示」的欄位直接用
 * 最貼近JSON本質的String表示——前端拿到的JSON字串長相不變，序列化/反序列化
 * 完全不需要依賴任何額外框架設定，也不會有出錯風險。
 */
public class TrendSnapshot {

    private String source;

    private String keyword;

    private BigDecimal trendScore;

    private BigDecimal popularityScore;

    private String trendDirection;

    private String collectedAt;

    public TrendSnapshot() {
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

    public BigDecimal getTrendScore() {
        return trendScore;
    }

    public void setTrendScore(BigDecimal trendScore) {
        this.trendScore = trendScore;
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

    public String getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(String collectedAt) {
        this.collectedAt = collectedAt;
    }
}
