package com.example.Product_Selection_260813.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 節慶／季節檔期的受影響區域（V21，決議 D9：單一期間＋受影響區域集合）。
 *
 * 一筆檔期沒有任何列＝全國性（覆蓋率 1.0）。計分當下以 region_weights 即時算出覆蓋率並寫進快照；
 * 天氣型檔期不使用這張表（沿用 festive_campaigns.region／region_coverage_ratio 的凍結值）。
 */
@Entity
@Table(name = "festive_campaign_regions")
public class FestiveCampaignRegion {

	@EmbeddedId
	private FestiveCampaignRegionId id;

	public FestiveCampaignRegion() {
	}

	public FestiveCampaignRegion(Long campaignId, String region) {
		this.id = new FestiveCampaignRegionId(campaignId, region);
	}

	public FestiveCampaignRegionId getId() {
		return id;
	}

	public void setId(FestiveCampaignRegionId id) {
		this.id = id;
	}

	public Long getCampaignId() {
		return id == null ? null : id.getCampaignId();
	}

	public String getRegion() {
		return id == null ? null : id.getRegion();
	}
}
