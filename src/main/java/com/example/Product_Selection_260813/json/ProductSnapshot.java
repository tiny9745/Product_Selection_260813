package com.example.Product_Selection_260813.json;

import java.math.BigDecimal;

public class ProductSnapshot {

    private String name;

    private String pricingType;

    private BigDecimal costPrice;

    private BigDecimal salePrice;

    /**
     * 市售價格。
     *
     * 改版前市價不參與計分，快照沒存它並無影響；但折扣深度成為計分因子之後
     * 就不同了——折扣深度 = (市價 − 售價) / 市價，快照少了市價就無法重現
     * 那一項的分數。與 freightCostEstimate 是同一類問題：凡是進入分數計算的
     * 輸入值都必須留在快照裡。
     */
    private BigDecimal marketPrice;

    private String campaignTags;

    private Integer moq;

    private BigDecimal supplyStability;

    private BigDecimal priceCompetitiveness;

    private String targetCustomerDescription;

    private BigDecimal estimatedPurchaseRate;

    // ---- 以下為評分擴充後新增的快照欄位 ----
    //
    // 為什麼要存進快照：審核紀錄必須能還原「當時的判斷依據」。
    // 這些欄位之後可能被改（商品被編輯、品類預設值被調整），只存商品 id
    // 在事後查核時撈到的會是今天的值，而不是審核當下的值。

    /**
     * 解析後實際生效的最低訂購量。
     *
     * 與 moq 欄位並存而非取代它：moq 存的是商品層原始值（可能為 null 代表
     * 繼承品類），resolvedMoq 存的是三層解析後真正用於判斷的數字。
     * 只存原始值的話，之後品類預設被改動，就無法判斷當時實際用的是多少。
     */
    private Integer resolvedMoq;

    /** 上述數值的來源層級：PRODUCT / PRODUCT_TYPE / GLOBAL_DEFAULT。 */
    private String moqSource;

    // Gate 判定所依據的商品屬性。存下來才能解釋「為什麼當時這個 Gate 不通過」。
    private String temperatureZone;
    private String shelfLifeTier;
    private String supplierLeadTimeTier;
    private String packageSizeTier;
    private String packingType;
    private String handlingFlags;
    private String certificationFlags;
    private Integer supplierMaxCapacity;

    /**
     * 當時採用的運費估算。
     *
     * 毛利率是扣掉運費後才正規化的，而運費來自 system_settings 的設定值，
     * 之後會被調整。不存下來的話，事後拿商品的成本價與售價重算會對不上
     * 快照裡的分數。
     */
    private BigDecimal freightCostEstimate;

    /** 再販售參考商品 id，供追溯歷史分數的商品層來源。 */
    private Long resaleReferenceProductId;


    public ProductSnapshot() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPricingType() {
        return pricingType;
    }

    public void setPricingType(String pricingType) {
        this.pricingType = pricingType;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(BigDecimal salePrice) {
        this.salePrice = salePrice;
    }

    public String getCampaignTags() {
        return campaignTags;
    }

    public void setCampaignTags(String campaignTags) {
        this.campaignTags = campaignTags;
    }

    public Integer getMoq() {
        return moq;
    }

    public void setMoq(Integer moq) {
        this.moq = moq;
    }

    public BigDecimal getSupplyStability() {
        return supplyStability;
    }

    public void setSupplyStability(BigDecimal supplyStability) {
        this.supplyStability = supplyStability;
    }

    public BigDecimal getPriceCompetitiveness() {
        return priceCompetitiveness;
    }

    public void setPriceCompetitiveness(BigDecimal priceCompetitiveness) {
        this.priceCompetitiveness = priceCompetitiveness;
    }

    public String getTargetCustomerDescription() {
        return targetCustomerDescription;
    }

    public void setTargetCustomerDescription(String targetCustomerDescription) {
        this.targetCustomerDescription = targetCustomerDescription;
    }

    public BigDecimal getEstimatedPurchaseRate() {
        return estimatedPurchaseRate;
    }

    public void setEstimatedPurchaseRate(BigDecimal estimatedPurchaseRate) {
        this.estimatedPurchaseRate = estimatedPurchaseRate;
    }

    public Integer getResolvedMoq() {
        return resolvedMoq;
    }

    public void setResolvedMoq(Integer resolvedMoq) {
        this.resolvedMoq = resolvedMoq;
    }

    public String getMoqSource() {
        return moqSource;
    }

    public void setMoqSource(String moqSource) {
        this.moqSource = moqSource;
    }

    public String getTemperatureZone() {
        return temperatureZone;
    }

    public void setTemperatureZone(String temperatureZone) {
        this.temperatureZone = temperatureZone;
    }

    public String getShelfLifeTier() {
        return shelfLifeTier;
    }

    public void setShelfLifeTier(String shelfLifeTier) {
        this.shelfLifeTier = shelfLifeTier;
    }

    public String getSupplierLeadTimeTier() {
        return supplierLeadTimeTier;
    }

    public void setSupplierLeadTimeTier(String supplierLeadTimeTier) {
        this.supplierLeadTimeTier = supplierLeadTimeTier;
    }

    public String getPackageSizeTier() {
        return packageSizeTier;
    }

    public void setPackageSizeTier(String packageSizeTier) {
        this.packageSizeTier = packageSizeTier;
    }

    public String getPackingType() {
        return packingType;
    }

    public void setPackingType(String packingType) {
        this.packingType = packingType;
    }

    public String getHandlingFlags() {
        return handlingFlags;
    }

    public void setHandlingFlags(String handlingFlags) {
        this.handlingFlags = handlingFlags;
    }

    public String getCertificationFlags() {
        return certificationFlags;
    }

    public void setCertificationFlags(String certificationFlags) {
        this.certificationFlags = certificationFlags;
    }

    public Integer getSupplierMaxCapacity() {
        return supplierMaxCapacity;
    }

    public void setSupplierMaxCapacity(Integer supplierMaxCapacity) {
        this.supplierMaxCapacity = supplierMaxCapacity;
    }

    public BigDecimal getFreightCostEstimate() {
        return freightCostEstimate;
    }

    public void setFreightCostEstimate(BigDecimal freightCostEstimate) {
        this.freightCostEstimate = freightCostEstimate;
    }

    public Long getResaleReferenceProductId() {
        return resaleReferenceProductId;
    }

    public void setResaleReferenceProductId(Long resaleReferenceProductId) {
        this.resaleReferenceProductId = resaleReferenceProductId;
    }

    public BigDecimal getMarketPrice() {
        return marketPrice;
    }

    public void setMarketPrice(BigDecimal marketPrice) {
        this.marketPrice = marketPrice;
    }
}