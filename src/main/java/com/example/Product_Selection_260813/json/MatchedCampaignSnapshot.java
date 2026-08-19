package com.example.Product_Selection_260813.json;

import java.math.BigDecimal;
import java.util.List;

public class MatchedCampaignSnapshot {

    private Long campaignId;

    private String campaignName;

    private List<String> matchedTags;

    private BigDecimal matchWeight;

    private BigDecimal urgencyFactor;

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
}
