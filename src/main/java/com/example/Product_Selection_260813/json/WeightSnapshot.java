package com.example.Product_Selection_260813.json;

import java.util.Map;
import java.math.BigDecimal;
import java.util.List;

public class WeightSnapshot {

    private String modeCode;

    private String modeName;

    private Integer version;

    private List<WeightFactorSnapshot> factors;

    // ---- 演算法參數快照 ----
    //
    // 只存權重不存參數，事後仍然無法重現當時的計算。這些數字都放在
    // system_settings，是刻意設計成可調的——而可調就代表會被調。
    // 半年後想驗證「當初這個 78.5 分是怎麼算出來的」，需要知道當時的
    // 平滑常數、半衰期與目標區間，否則用今天的參數重算必然對不上。

    /** 品類層貝氏收縮的平滑常數。 */
    private Integer shrinkageKCategory;

    /** 商品層貝氏收縮的平滑常數。 */
    private Integer shrinkageKProduct;

    /** 趨勢新鮮度衰減的半衰期（天）。 */
    private Integer trendHalfLifeDays;

    /**
     * 歷史分數的品類層樣本數。
     *
     * 樣本數決定了收縮的強度，也決定這個分數有多可信。只存分數不存樣本數,
     * 事後看到 78.5 分無從判斷它是 3 筆樣本收縮出來的、還是 50 筆算出來的——
     * 兩者的參考價值差很多。
     */
    private Long historySampleSizeCategory;

    /** 歷史分數的商品層樣本數。 */
    private Long historySampleSizeProduct;

    /** 該次計算所用的歷史資料是否含模擬紀錄。 */
    private Boolean historyIncludesSimulated;

    /** 各因子當時採用的目標區間，key 為 factorCode，值為 [下界, 上界]。 */
    private Map<String, List<BigDecimal>> scoreBands;

    public WeightSnapshot() {
    }

    public String getModeCode() {
        return modeCode;
    }

    public void setModeCode(String modeCode) {
        this.modeCode = modeCode;
    }

    public String getModeName() {
        return modeName;
    }

    public void setModeName(String modeName) {
        this.modeName = modeName;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public List<WeightFactorSnapshot> getFactors() {
        return factors;
    }

    public void setFactors(List<WeightFactorSnapshot> factors) {
        this.factors = factors;
    }

    public Integer getShrinkageKCategory() {
        return shrinkageKCategory;
    }

    public void setShrinkageKCategory(Integer shrinkageKCategory) {
        this.shrinkageKCategory = shrinkageKCategory;
    }

    public Integer getShrinkageKProduct() {
        return shrinkageKProduct;
    }

    public void setShrinkageKProduct(Integer shrinkageKProduct) {
        this.shrinkageKProduct = shrinkageKProduct;
    }

    public Integer getTrendHalfLifeDays() {
        return trendHalfLifeDays;
    }

    public void setTrendHalfLifeDays(Integer trendHalfLifeDays) {
        this.trendHalfLifeDays = trendHalfLifeDays;
    }

    public Long getHistorySampleSizeCategory() {
        return historySampleSizeCategory;
    }

    public void setHistorySampleSizeCategory(Long historySampleSizeCategory) {
        this.historySampleSizeCategory = historySampleSizeCategory;
    }

    public Long getHistorySampleSizeProduct() {
        return historySampleSizeProduct;
    }

    public void setHistorySampleSizeProduct(Long historySampleSizeProduct) {
        this.historySampleSizeProduct = historySampleSizeProduct;
    }

    public Boolean getHistoryIncludesSimulated() {
        return historyIncludesSimulated;
    }

    public void setHistoryIncludesSimulated(Boolean historyIncludesSimulated) {
        this.historyIncludesSimulated = historyIncludesSimulated;
    }

    public Map<String, List<BigDecimal>> getScoreBands() {
        return scoreBands;
    }

    public void setScoreBands(Map<String, List<BigDecimal>> scoreBands) {
        this.scoreBands = scoreBands;
    }
}