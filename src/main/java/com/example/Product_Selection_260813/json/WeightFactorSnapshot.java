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
}