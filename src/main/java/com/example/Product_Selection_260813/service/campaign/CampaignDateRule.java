package com.example.Product_Selection_260813.service.campaign;

import com.example.Product_Selection_260813.enums.CampaignDateRuleType;
import com.example.Product_Selection_260813.enums.FestiveCategory;
import com.example.Product_Selection_260813.enums.ObservedHolidayRule;
import com.example.Product_Selection_260813.enums.SolarTerm;

/**
 * 檔期日期規則的純資料表示（不依賴 JPA Entity），CampaignOccurrenceResolver 的輸入。
 *
 * 與 FestiveCampaign Entity 分開的理由：推算邏輯要能在沒有資料庫、沒有 Spring
 * 的情況下單元測試；建立前的即時預覽（occurrence-preview）也沒有 Entity 可用。
 * 由 {@link CampaignDateRules} 從 Entity 或 Request 轉換而來。
 *
 * V26：天氣檔期移除後，原本只給天氣用的 weatherStartDate／weatherEndDate 一併移除。
 */
public record CampaignDateRule(
		FestiveCategory category,
		CampaignDateRuleType ruleType,
		Integer month,
		Integer day,
		Integer weekOrdinal,
		Integer weekday,
		SolarTerm solarTerm,
		int offsetDays,
		Integer durationDays,
		Integer endMonth,
		Integer endDay,
		ObservedHolidayRule observedHolidayRule,
		boolean expandLongWeekend) {
}
