package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.example.Product_Selection_260813.enums.CampaignDateRuleType;
import com.example.Product_Selection_260813.enums.CampaignStatusSource;
import com.example.Product_Selection_260813.enums.FestiveCampaignStatus;
import com.example.Product_Selection_260813.enums.FestiveCategory;
import com.example.Product_Selection_260813.enums.ObservedHolidayRule;
import com.example.Product_Selection_260813.enums.SolarTerm;

/**
 * 檔期回應。
 *
 * 2026-09-24（V21 檔期規則改版）：startDate／endDate／campaignStatus 保留欄位名稱，語意改為
 * 「目前或下一期」的起訖日與推算後的有效狀態，前端列表與商品表單的既有讀取不必大改；
 * 另外帶出規則欄位、區域、週期年、補假日與中文規則描述。組裝邏輯在
 * FestiveCampaignRuleService（需要批次載入標籤、區域、覆寫，避免 N+1），這裡只是資料容器。
 */
public class FestiveCampaignResponse {

	private Long id;
	private String campaignCode;
	private String campaignName;
	private FestiveCategory category;
	/** 目前或下一期 occurrence 的開始日（V21 起語意變更）。 */
	private LocalDate startDate;
	/** 目前或下一期 occurrence 的結束日。 */
	private LocalDate endDate;
	private Integer preparationLeadDays;
	/** 推算後的有效狀態（見 statusSource）；V21 前是資料表存的值。 */
	private FestiveCampaignStatus campaignStatus;
	private Boolean isManualOverride;
	// V26：天氣檔期移除，region／weatherConfidence（僅天氣檔期使用）一併移除；區域見 regions。
	/** 季節型依 regions 與 region_weights 當下計算；節慶型一律 1.0。 */
	private BigDecimal regionCoverageRatio;
	private List<FestiveCampaignTagView> tags;
	private CampaignDateRuleType dateRuleType;
	private Integer ruleMonth;
	private Integer ruleDay;
	private Integer ruleWeekOrdinal;
	private Integer ruleWeekday;
	private SolarTerm ruleSolarTerm;
	private Integer ruleOffsetDays;
	private Integer durationDays;
	private Integer endMonth;
	private Integer endDay;
	private ObservedHolidayRule observedHolidayRule;
	private Boolean expandLongWeekend;
	/** 季節型的受影響區域；空＝全國。節慶型一律為空（全國）。 */
	private List<String> regions;
	/** 目前或下一期的週期年。 */
	private Integer cycleYear;
	/** 本期起訖日是否來自逐年覆寫。 */
	private Boolean occurrenceOverridden;
	/** 本期依補假規則算出的補假日。 */
	private List<LocalDate> observedHolidays;
	private CampaignStatusSource statusSource;
	private Integer manualOverrideCycle;
	/** 後端組好的中文規則描述，例：「每年農曆 5 月 5 日起 3 天」。 */
	private String ruleDescription;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getCampaignCode() {
		return campaignCode;
	}

	public void setCampaignCode(String campaignCode) {
		this.campaignCode = campaignCode;
	}

	public String getCampaignName() {
		return campaignName;
	}

	public void setCampaignName(String campaignName) {
		this.campaignName = campaignName;
	}

	public FestiveCategory getCategory() {
		return category;
	}

	public void setCategory(FestiveCategory category) {
		this.category = category;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public void setEndDate(LocalDate endDate) {
		this.endDate = endDate;
	}

	public Integer getPreparationLeadDays() {
		return preparationLeadDays;
	}

	public void setPreparationLeadDays(Integer preparationLeadDays) {
		this.preparationLeadDays = preparationLeadDays;
	}

	public FestiveCampaignStatus getCampaignStatus() {
		return campaignStatus;
	}

	public void setCampaignStatus(FestiveCampaignStatus campaignStatus) {
		this.campaignStatus = campaignStatus;
	}

	public Boolean getIsManualOverride() {
		return isManualOverride;
	}

	public void setIsManualOverride(Boolean isManualOverride) {
		this.isManualOverride = isManualOverride;
	}

	public BigDecimal getRegionCoverageRatio() {
		return regionCoverageRatio;
	}

	public void setRegionCoverageRatio(BigDecimal regionCoverageRatio) {
		this.regionCoverageRatio = regionCoverageRatio;
	}

	public List<FestiveCampaignTagView> getTags() {
		return tags;
	}

	public void setTags(List<FestiveCampaignTagView> tags) {
		this.tags = tags;
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

	public Integer getCycleYear() {
		return cycleYear;
	}

	public void setCycleYear(Integer cycleYear) {
		this.cycleYear = cycleYear;
	}

	public Boolean getOccurrenceOverridden() {
		return occurrenceOverridden;
	}

	public void setOccurrenceOverridden(Boolean occurrenceOverridden) {
		this.occurrenceOverridden = occurrenceOverridden;
	}

	public List<LocalDate> getObservedHolidays() {
		return observedHolidays;
	}

	public void setObservedHolidays(List<LocalDate> observedHolidays) {
		this.observedHolidays = observedHolidays;
	}

	public CampaignStatusSource getStatusSource() {
		return statusSource;
	}

	public void setStatusSource(CampaignStatusSource statusSource) {
		this.statusSource = statusSource;
	}

	public Integer getManualOverrideCycle() {
		return manualOverrideCycle;
	}

	public void setManualOverrideCycle(Integer manualOverrideCycle) {
		this.manualOverrideCycle = manualOverrideCycle;
	}

	public String getRuleDescription() {
		return ruleDescription;
	}

	public void setRuleDescription(String ruleDescription) {
		this.ruleDescription = ruleDescription;
	}
}
