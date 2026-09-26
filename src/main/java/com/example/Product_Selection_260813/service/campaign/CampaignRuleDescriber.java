package com.example.Product_Selection_260813.service.campaign;

import java.time.Month;

import com.example.Product_Selection_260813.enums.FestiveCategory;
import com.example.Product_Selection_260813.enums.ObservedHolidayRule;

/**
 * 把日期規則組成中文描述（FestiveCampaignResponse.ruleDescription），例：
 * 「每年農曆 5 月 5 日起 3 天」「每年 5 月第 2 個星期日」「每年 12/1 至隔年 2 月底」。
 *
 * 描述由後端組好，前端列表直接顯示，避免前後端各維護一份規則文字。
 */
public final class CampaignRuleDescriber {

	private static final String[] WEEKDAY = { "", "一", "二", "三", "四", "五", "六", "日" };

	private CampaignRuleDescriber() {
	}

	public static String describe(CampaignDateRule rule) {
		if (rule.ruleType() == null) {
			return "尚未設定日期規則";
		}
		if (rule.category() == FestiveCategory.SEASON) {
			boolean crossYear = rule.endMonth() * 100 + rule.endDay() < rule.month() * 100 + rule.day();
			return "每年 " + rule.month() + "/" + rule.day() + offset(rule.offsetDays()) + " 至"
					+ (crossYear ? "隔年 " : " ") + endText(rule.endMonth(), rule.endDay());
		}
		String base = switch (rule.ruleType()) {
		case FIXED_DATE -> "每年 " + rule.month() + "/" + rule.day();
		case NTH_WEEKDAY -> "每年 " + rule.month() + " 月"
				+ (rule.weekOrdinal() == -1 ? "最後一個" : "第 " + rule.weekOrdinal() + " 個") + "星期"
				+ WEEKDAY[rule.weekday()];
		case LUNAR_DATE -> "每年農曆 " + rule.month() + " 月 " + rule.day() + " 日";
		case SOLAR_TERM -> "每年" + rule.solarTerm().getDisplayName();
		};
		StringBuilder text = new StringBuilder(base).append(offset(rule.offsetDays()));
		if (rule.durationDays() != null && rule.durationDays() > 1) {
			text.append("起 ").append(rule.durationDays()).append(" 天");
		}
		if (rule.observedHolidayRule() == ObservedHolidayRule.TW_STATUTORY) {
			text.append("，依國定假日補假");
			if (rule.expandLongWeekend()) {
				text.append("並併入連假");
			}
		}
		return text.toString();
	}

	private static String offset(int days) {
		if (days == 0) {
			return "";
		}
		return days < 0 ? "前 " + (-days) + " 天" : "後 " + days + " 天";
	}

	/** 結束日達到該月可能的最大天數（2 月為 29、小月為 30、大月為 31）時顯示「月底」。 */
	private static String endText(int month, int day) {
		return day >= Month.of(month).maxLength() ? month + " 月底" : month + "/" + day;
	}
}
