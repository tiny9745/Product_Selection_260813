package com.example.Product_Selection_260813.dto.request;

import java.util.List;

import com.example.Product_Selection_260813.constants.ValidationMessage;
import com.example.Product_Selection_260813.enums.CampaignDateRuleType;
import com.example.Product_Selection_260813.enums.FestiveCategory;
import com.example.Product_Selection_260813.enums.ObservedHolidayRule;
import com.example.Product_Selection_260813.enums.SolarTerm;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 檔期日期規則欄位（V21，2026-09-24 檔期規則改版），新增、修改、即時預覽三支 API 共用。
 *
 * 這裡只放「單一欄位」的範圍檢查（Jakarta 註解）；各規則類型的必填與互斥由
 * FestiveCampaignRuleService.validateRule() 單一入口檢查，三支 API 共用同一套規則。
 * 欄位語意見 V21 migration 與 FestiveCampaign 欄位註解。
 */
public class FestiveCampaignRuleFields {

	@NotNull(message = "檔期類別不可為空")
	private FestiveCategory category;

	private CampaignDateRuleType dateRuleType;

	@Min(value = 1, message = ValidationMessage.CAMPAIGN_RULE_DATE_INVALID)
	@Max(value = 12, message = ValidationMessage.CAMPAIGN_RULE_DATE_INVALID)
	private Integer ruleMonth;

	@Min(value = 1, message = ValidationMessage.CAMPAIGN_RULE_DATE_INVALID)
	@Max(value = 31, message = ValidationMessage.CAMPAIGN_RULE_DATE_INVALID)
	private Integer ruleDay;

	/** 1～4 或 -1（最後一個）；0 的排除在 Service 層檢查。 */
	@Min(value = -1, message = ValidationMessage.CAMPAIGN_RULE_NTH_WEEKDAY_INVALID)
	@Max(value = 4, message = ValidationMessage.CAMPAIGN_RULE_NTH_WEEKDAY_INVALID)
	private Integer ruleWeekOrdinal;

	@Min(value = 1, message = ValidationMessage.CAMPAIGN_RULE_NTH_WEEKDAY_INVALID)
	@Max(value = 7, message = ValidationMessage.CAMPAIGN_RULE_NTH_WEEKDAY_INVALID)
	private Integer ruleWeekday;

	private SolarTerm ruleSolarTerm;

	/** 不填視為 0。 */
	@Min(value = -30, message = ValidationMessage.CAMPAIGN_RULE_OFFSET_INVALID)
	@Max(value = 30, message = ValidationMessage.CAMPAIGN_RULE_OFFSET_INVALID)
	private Integer ruleOffsetDays;

	@Min(value = 1, message = ValidationMessage.CAMPAIGN_DURATION_INVALID)
	@Max(value = 60, message = ValidationMessage.CAMPAIGN_DURATION_INVALID)
	private Integer durationDays;

	@Min(value = 1, message = ValidationMessage.CAMPAIGN_SEASON_END_REQUIRED)
	@Max(value = 12, message = ValidationMessage.CAMPAIGN_SEASON_END_REQUIRED)
	private Integer endMonth;

	@Min(value = 1, message = ValidationMessage.CAMPAIGN_SEASON_END_REQUIRED)
	@Max(value = 31, message = ValidationMessage.CAMPAIGN_SEASON_END_REQUIRED)
	private Integer endDay;

	/** 不填視為 NONE。 */
	private ObservedHolidayRule observedHolidayRule;

	/** 不填視為 false。 */
	private Boolean expandLongWeekend;

	/** 僅季節型：受影響區域（NORTH／CENTRAL／SOUTH／EAST），不填或空清單＝全國。節慶型必須為空（一律全國）。 */
	private List<String> regions;

	public FestiveCategory getCategory() {
		return category;
	}

	public void setCategory(FestiveCategory category) {
		this.category = category;
	}

	public CampaignDateRuleType getDateRuleType() {
		return dateRuleType;
	}

	public void setDateRuleType(CampaignDateRuleType dateRuleType) {
		this.dateRuleType = dateRuleType;
	}

	public Integer getRuleMonth() {
		return ruleMonth;
	}

	public void setRuleMonth(Integer ruleMonth) {
		this.ruleMonth = ruleMonth;
	}

	public Integer getRuleDay() {
		return ruleDay;
	}

	public void setRuleDay(Integer ruleDay) {
		this.ruleDay = ruleDay;
	}

	public Integer getRuleWeekOrdinal() {
		return ruleWeekOrdinal;
	}

	public void setRuleWeekOrdinal(Integer ruleWeekOrdinal) {
		this.ruleWeekOrdinal = ruleWeekOrdinal;
	}

	public Integer getRuleWeekday() {
		return ruleWeekday;
	}

	public void setRuleWeekday(Integer ruleWeekday) {
		this.ruleWeekday = ruleWeekday;
	}

	public SolarTerm getRuleSolarTerm() {
		return ruleSolarTerm;
	}

	public void setRuleSolarTerm(SolarTerm ruleSolarTerm) {
		this.ruleSolarTerm = ruleSolarTerm;
	}

	public Integer getRuleOffsetDays() {
		return ruleOffsetDays;
	}

	public void setRuleOffsetDays(Integer ruleOffsetDays) {
		this.ruleOffsetDays = ruleOffsetDays;
	}

	public Integer getDurationDays() {
		return durationDays;
	}

	public void setDurationDays(Integer durationDays) {
		this.durationDays = durationDays;
	}

	public Integer getEndMonth() {
		return endMonth;
	}

	public void setEndMonth(Integer endMonth) {
		this.endMonth = endMonth;
	}

	public Integer getEndDay() {
		return endDay;
	}

	public void setEndDay(Integer endDay) {
		this.endDay = endDay;
	}

	public ObservedHolidayRule getObservedHolidayRule() {
		return observedHolidayRule;
	}

	public void setObservedHolidayRule(ObservedHolidayRule observedHolidayRule) {
		this.observedHolidayRule = observedHolidayRule;
	}

	public Boolean getExpandLongWeekend() {
		return expandLongWeekend;
	}

	public void setExpandLongWeekend(Boolean expandLongWeekend) {
		this.expandLongWeekend = expandLongWeekend;
	}

	public List<String> getRegions() {
		return regions;
	}

	public void setRegions(List<String> regions) {
		this.regions = regions;
	}
}
