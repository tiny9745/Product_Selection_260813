package com.example.Product_Selection_260813.json;

import java.math.BigDecimal;
import java.util.Map;

public class WeightFactorSnapshot {

    private String factorCode;

    private String factorName;

    private String category;

    private BigDecimal weight;

    /**
     * 這個因子當時採用的運算邏輯代碼（FactorStrategyCode 的 name()）。
     * 既有七個因子沒有對應的 FactorDefinition，這裡恆為 null；只有自訂因子
     * 才會有值。2026-09-20新增，理由見 ScoringService.toWeightFactorSnapshot()：
     * 因子定義之後可能被改策略或改參數，只凍結權重無法重現當時的計算。
     */
    private String strategyCode;

    /** 這個因子當時的策略參數（例如 MANUAL_SCALE 的 "scale" 倍率）。同上，僅自訂因子有值。 */
    private Map<String, BigDecimal> strategyParams;

    /**
     * 這個因子當時綁定的既有 Product 固定欄位（FactorDataSource 的 name()）。
     * 跟 customFieldCode 二選一，只會有一個非 null；兩者都是 null 代表這是
     * 既有七個固定因子之一。2026-09-20新增，理由同 strategyCode——資料源
     * 之後可能被改，Snapshot 要留住當時真正讀的是哪一個。
     */
    private String dataSourceCode;

    /**
     * 這個因子當時綁定的自訂商品屬性（動態問卷）題目代碼。跟 dataSourceCode
     * 二選一。凍結字串代碼而非只存 id：就算這個題目之後被刪除，Snapshot
     * 仍然清楚記著「當時讀的是哪個代碼」，不會因為外鍵對應的資料消失就
     * 看不出來源。
     */
    private String customFieldCode;

    public WeightFactorSnapshot() {
    }

    public String getFactorCode() {
        return factorCode;
    }

    public void setFactorCode(String factorCode) {
        this.factorCode = factorCode;
    }

    public String getFactorName() {
        return factorName;
    }

    public void setFactorName(String factorName) {
        this.factorName = factorName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public String getStrategyCode() {
        return strategyCode;
    }

    public void setStrategyCode(String strategyCode) {
        this.strategyCode = strategyCode;
    }

    public Map<String, BigDecimal> getStrategyParams() {
        return strategyParams;
    }

    public void setStrategyParams(Map<String, BigDecimal> strategyParams) {
        this.strategyParams = strategyParams;
    }

    public String getDataSourceCode() {
        return dataSourceCode;
    }

    public void setDataSourceCode(String dataSourceCode) {
        this.dataSourceCode = dataSourceCode;
    }

    public String getCustomFieldCode() {
        return customFieldCode;
    }

    public void setCustomFieldCode(String customFieldCode) {
        this.customFieldCode = customFieldCode;
    }
}