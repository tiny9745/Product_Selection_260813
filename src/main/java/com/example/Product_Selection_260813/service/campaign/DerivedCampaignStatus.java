package com.example.Product_Selection_260813.service.campaign;

import com.example.Product_Selection_260813.enums.CampaignStatusSource;
import com.example.Product_Selection_260813.enums.FestiveCampaignStatus;

/** 推算後的有效狀態與其來源（見 CampaignStatusSource）。 */
public record DerivedCampaignStatus(FestiveCampaignStatus status, CampaignStatusSource source) {
}
