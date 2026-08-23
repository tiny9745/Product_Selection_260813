package com.example.Product_Selection_260813.dto.response;

import com.example.Product_Selection_260813.entity.FestiveCampaignTag;
import com.example.Product_Selection_260813.enums.FestiveCampaignTagMatchTier;

public class FestiveCampaignTagView {

	private String tag;
	private FestiveCampaignTagMatchTier matchTier;

	public static FestiveCampaignTagView from(FestiveCampaignTag entity) {
		FestiveCampaignTagView dto = new FestiveCampaignTagView();
		dto.tag = entity.getTag();
		dto.matchTier = entity.getMatchTier();
		return dto;
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public FestiveCampaignTagMatchTier getMatchTier() {
		return matchTier;
	}

	public void setMatchTier(FestiveCampaignTagMatchTier matchTier) {
		this.matchTier = matchTier;
	}
}
