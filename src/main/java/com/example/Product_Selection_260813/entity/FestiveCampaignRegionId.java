package com.example.Product_Selection_260813.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** festive_campaign_regions 的複合主鍵（campaign_id, region），寫法比照 ReviewRiskId。 */
@Embeddable
public class FestiveCampaignRegionId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "campaign_id", nullable = false)
	private Long campaignId;

	@Column(name = "region", nullable = false, length = 10)
	private String region;

	public FestiveCampaignRegionId() {
	}

	public FestiveCampaignRegionId(Long campaignId, String region) {
		this.campaignId = campaignId;
		this.region = region;
	}

	public Long getCampaignId() {
		return campaignId;
	}

	public void setCampaignId(Long campaignId) {
		this.campaignId = campaignId;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof FestiveCampaignRegionId)) {
			return false;
		}
		FestiveCampaignRegionId that = (FestiveCampaignRegionId) o;
		return Objects.equals(campaignId, that.campaignId) && Objects.equals(region, that.region);
	}

	@Override
	public int hashCode() {
		return Objects.hash(campaignId, region);
	}
}
