package com.example.Product_Selection_260813.service.campaign;

import com.example.Product_Selection_260813.dto.request.FestiveCampaignRuleFields;
import com.example.Product_Selection_260813.entity.FestiveCampaign;
import com.example.Product_Selection_260813.enums.FestiveCategory;
import com.example.Product_Selection_260813.enums.ObservedHolidayRule;

/** 把 Entity 或 Request 轉成 CampaignDateRule，讓推算邏輯與 JPA／DTO 解耦。 */
public final class CampaignDateRules {

	private CampaignDateRules() {
	}

	public static CampaignDateRule of(FestiveCampaign campaign) {
		return new CampaignDateRule(campaign.getCategory(), campaign.getDateRuleType(), campaign.getRuleMonth(),
				campaign.getRuleDay(), campaign.getRuleWeekOrdinal(), campaign.getRuleWeekday(),
				campaign.getRuleSolarTerm(), effectiveOffset(campaign.getCategory(), campaign.getRuleOffsetDays()),
				campaign.getDurationDays(), campaign.getEndMonth(), campaign.getEndDay(),
				campaign.getObservedHolidayRule() == null ? ObservedHolidayRule.NONE
						: campaign.getObservedHolidayRule(),
				Boolean.TRUE.equals(campaign.getExpandLongWeekend()));
	}

	/** 預覽用：Request 需先經過 FestiveCampaignRuleService.validateRule() 與正規化。 */
	public static CampaignDateRule of(FestiveCampaignRuleFields fields) {
		return new CampaignDateRule(fields.getCategory(), fields.getDateRuleType(), fields.getRuleMonth(),
				fields.getRuleDay(), fields.getRuleWeekOrdinal(), fields.getRuleWeekday(), fields.getRuleSolarTerm(),
				effectiveOffset(fields.getCategory(), fields.getRuleOffsetDays()), fields.getDurationDays(),
				fields.getEndMonth(), fields.getEndDay(),
				fields.getObservedHolidayRule() == null ? ObservedHolidayRule.NONE : fields.getObservedHolidayRule(),
				Boolean.TRUE.equals(fields.getExpandLongWeekend()));
	}

	/**
	 * 2026-09-24：偏移天數只對節慶型生效。季節型一律視為 0——寫入端已由 validateRule() 擋下，
	 * 這裡防禦規則改版前留下的季節偏移舊資料，讓推算、規則描述、計分三處結果一致。
	 */
	private static int effectiveOffset(FestiveCategory category, Integer offsetDays) {
		if (offsetDays == null || category != FestiveCategory.FESTIVAL) {
			return 0;
		}
		return offsetDays;
	}
}
