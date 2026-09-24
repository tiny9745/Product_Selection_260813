package com.example.Product_Selection_260813.service.campaign;

import java.math.BigDecimal;
import java.util.List;

import com.example.Product_Selection_260813.entity.FestiveCampaign;
import com.example.Product_Selection_260813.enums.FestiveCampaignStatus;

/**
 * 計分當下「生效中」（PREPARING／ACTIVE）的檔期與本期資訊，ScoringService 據此做標籤比對與加成計算。
 *
 * @param regionCoverage 節慶／季節型為依 regions 當下計算的覆蓋率；天氣型為同步凍結值（null 視為 0）
 * @param regions        節慶／季節型的受影響區域（空＝全國）；天氣型放 [region]
 */
public record ActiveCampaignWindow(FestiveCampaign campaign, CampaignOccurrence occurrence,
		FestiveCampaignStatus status, BigDecimal regionCoverage, List<String> regions) {
}
