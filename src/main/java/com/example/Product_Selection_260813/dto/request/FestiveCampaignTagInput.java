package com.example.Product_Selection_260813.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.example.Product_Selection_260813.enums.FestiveCampaignTagMatchTier;

/**
 * 新增／編輯檔期時，單一標籤的命中權重分級輸入。
 *
 * 對應festive_campaign_tags表（取代原target_tags純文字欄位，見資料庫討論的
 * 「新增festive_campaign_tags表」異動）——這個專案的「新增／編輯檔期」欄位
 * 已經不是企劃書原始UI樹狀圖寫的單一target_tags字串，而是「標籤＋分級」清單，
 * 與目前的資料表結構保持一致。
 */
public class FestiveCampaignTagInput {

	@NotBlank(message = "標籤內容不可為空")
	private String tag;

	@NotNull(message = "標籤命中權重層級不可為空")
	private FestiveCampaignTagMatchTier matchTier;

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
