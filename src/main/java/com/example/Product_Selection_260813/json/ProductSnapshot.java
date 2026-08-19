package com.example.Product_Selection_260813.json;

import java.math.BigDecimal;

public class ProductSnapshot {

    private String name;

    private String pricingType;

    private BigDecimal costPrice;

    private BigDecimal salePrice;

    private String campaignTags;

    private Integer moq;

    private BigDecimal supplyStability;

    private BigDecimal priceCompetitiveness;

    private String targetCustomerDescription;

    private BigDecimal estimatedPurchaseRate;

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
}