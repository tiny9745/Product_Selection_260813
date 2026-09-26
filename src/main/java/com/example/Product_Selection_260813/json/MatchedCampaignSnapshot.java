package com.example.Product_Selection_260813.json;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 命中檔期快照（review_records.matched_campaign_snapshot，Hibernate JSON 欄位）。
 *
 * 2026-09-24（V21 檔期規則改版）新增 category 以下 9 個欄位，全部允許 null：舊快照沒有這些 key，
 * 反序列化時就是 null，不會失敗。urgencyFactor 為 timeFactor × regionCoverageRatio 的乘積
 * （V26 起不再有天氣檔期與其可信度乘數）。
 *
 * ⚠️ occurrenceStartDate／occurrenceEndDate 刻意用 ISO-8601 字串（yyyy-MM-dd），不用 LocalDate：
 * 與 TrendSnapshot.collectedAt 同一個理由——Hibernate JSON 欄位的 Jackson 沒有註冊
 * JavaTimeModule，用 java.time 型別會在寫入快照時失敗。需要日期運算時用
 * {@link #occurrenceStartLocalDate()}。
 */
public class MatchedCampaignSnapshot {

    private Long campaignId;

    private String campaignName;

    private List<String> matchedTags;

    private BigDecimal matchWeight;

    private BigDecimal urgencyFactor;

    /** FESTIVAL／SEASON（V26 前的舊快照可能是 WEATHER，保留原值不改寫）。 */
    private String category;

    /** 命中當期的週期年。 */
    private Integer cycleYear;

    /** 命中當期的開始日（ISO yyyy-MM-dd）；備貨期限檢核改讀這個值（修正 B3）。 */
    private String occurrenceStartDate;

    /** 命中當期的結束日（ISO yyyy-MM-dd）。 */
    private String occurrenceEndDate;

    /** 本期起訖日是否為人工逐年覆寫。 */
    private Boolean occurrenceOverridden;

    /** 受影響區域；空＝全國。 */
    private List<String> regions;

    /** 當次使用的地域覆蓋率。 */
    private BigDecimal regionCoverageRatio;

    /** 乘上覆蓋率之前的時間係數。 */
    private BigDecimal timeFactor;

    /**
     * V26 前的天氣檔期快照才有值（天氣預報可信度係數）。V26 起不再寫入，保留欄位只為了讓
     * 既有審核快照 JSON 能完整反序列化、畫面照常顯示當時的明細（審核快照不可改寫）。
     */
    private BigDecimal weatherConfidenceFactor;

    public MatchedCampaignSnapshot() {
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(Long campaignId) {
        this.campaignId = campaignId;
    }

    public String getCampaignName() {
        return campaignName;
    }

    public void setCampaignName(String campaignName) {
        this.campaignName = campaignName;
    }

    public List<String> getMatchedTags() {
        return matchedTags;
    }

    public void setMatchedTags(List<String> matchedTags) {
        this.matchedTags = matchedTags;
    }

    public BigDecimal getMatchWeight() {
        return matchWeight;
    }

    public void setMatchWeight(BigDecimal matchWeight) {
        this.matchWeight = matchWeight;
    }

    public BigDecimal getUrgencyFactor() {
        return urgencyFactor;
    }

    public void setUrgencyFactor(BigDecimal urgencyFactor) {
        this.urgencyFactor = urgencyFactor;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getCycleYear() {
        return cycleYear;
    }

    public void setCycleYear(Integer cycleYear) {
        this.cycleYear = cycleYear;
    }

    public String getOccurrenceStartDate() {
        return occurrenceStartDate;
    }

    public void setOccurrenceStartDate(String occurrenceStartDate) {
        this.occurrenceStartDate = occurrenceStartDate;
    }

    public String getOccurrenceEndDate() {
        return occurrenceEndDate;
    }

    public void setOccurrenceEndDate(String occurrenceEndDate) {
        this.occurrenceEndDate = occurrenceEndDate;
    }

    public Boolean getOccurrenceOverridden() {
        return occurrenceOverridden;
    }

    public void setOccurrenceOverridden(Boolean occurrenceOverridden) {
        this.occurrenceOverridden = occurrenceOverridden;
    }

    public List<String> getRegions() {
        return regions;
    }

    public void setRegions(List<String> regions) {
        this.regions = regions;
    }

    public BigDecimal getRegionCoverageRatio() {
        return regionCoverageRatio;
    }

    public void setRegionCoverageRatio(BigDecimal regionCoverageRatio) {
        this.regionCoverageRatio = regionCoverageRatio;
    }

    public BigDecimal getTimeFactor() {
        return timeFactor;
    }

    public void setTimeFactor(BigDecimal timeFactor) {
        this.timeFactor = timeFactor;
    }

    public BigDecimal getWeatherConfidenceFactor() {
        return weatherConfidenceFactor;
    }

    public void setWeatherConfidenceFactor(BigDecimal weatherConfidenceFactor) {
        this.weatherConfidenceFactor = weatherConfidenceFactor;
    }

    /** occurrenceStartDate 轉 LocalDate；舊快照沒有這個值時回傳 null。不是 getter，不會被序列化。 */
    public LocalDate occurrenceStartLocalDate() {
        return occurrenceStartDate == null ? null : LocalDate.parse(occurrenceStartDate);
    }
}
