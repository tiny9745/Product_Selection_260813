package com.example.Product_Selection_260813.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.example.Product_Selection_260813.enums.CampaignDateRuleType;
import com.example.Product_Selection_260813.enums.FestiveCampaignStatus;
import com.example.Product_Selection_260813.enums.FestiveCategory;
import com.example.Product_Selection_260813.enums.ObservedHolidayRule;
import com.example.Product_Selection_260813.enums.SolarTerm;
import com.example.Product_Selection_260813.enums.WeatherForecastConfidence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name="festive_campaigns")
public class FestiveCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "campaign_code", nullable = false, length = 50, unique = true)
    private String campaignCode;

    @Column(name = "campaign_name", nullable = false, length = 100)
    private String campaignName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private FestiveCategory category;

    /**
     * 2026-09-24（V21）：僅 category=WEATHER 使用（同步服務寫入的實際日期）。
     * FESTIVAL／SEASON 改存日期規則（下方 rule 系列欄位），每年的起訖日由
     * CampaignOccurrenceResolver 即時推算，這兩欄維持 NULL。
     */
    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    // ---------------- 日期規則（V21，FESTIVAL／SEASON 使用；WEATHER 維持 NULL） ----------------
    // tinyint／smallint 欄位用 @JdbcTypeCode 明確指定型別，否則 ddl-auto=validate 會因為
    // Integer 預設對應 integer 而啟動失敗（比照 ProductType.level 的既有作法）。

    @Enumerated(EnumType.STRING)
    @Column(name = "date_rule_type")
    private CampaignDateRuleType dateRuleType;

    /** FIXED_DATE／NTH_WEEKDAY 為國曆月；LUNAR_DATE 為農曆月（一律指非閏月）。 */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "rule_month")
    private Integer ruleMonth;

    /** FIXED_DATE 為國曆日（2/29 在非閏年取 2/28）；LUNAR_DATE 為農曆日（超過該月天數取月末）。 */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "rule_day")
    private Integer ruleDay;

    /** NTH_WEEKDAY：第幾個（1～4），-1＝最後一個。 */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "rule_week_ordinal")
    private Integer ruleWeekOrdinal;

    /** NTH_WEEKDAY：ISO 星期（1＝週一…7＝週日）。 */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "rule_weekday")
    private Integer ruleWeekday;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_solar_term")
    private SolarTerm ruleSolarTerm;

    /** 基準日偏移天數（-30～30），例：除夕＝農曆 1/1 偏移 -1。 */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "rule_offset_days", nullable = false)
    private Integer ruleOffsetDays = 0;

    /** FESTIVAL 必填：持續天數（含開始日）。 */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "duration_days")
    private Integer durationDays;

    /** SEASON 必填：結束月日；早於開始月日代表跨年。 */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "end_month")
    private Integer endMonth;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "end_day")
    private Integer endDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "observed_holiday_rule", nullable = false)
    private ObservedHolidayRule observedHolidayRule = ObservedHolidayRule.NONE;

    @Column(name = "expand_long_weekend", nullable = false)
    private Boolean expandLongWeekend = false;

    @Column(name = "preparation_lead_days", nullable = false)
    private Integer preparationLeadDays = 30;

    /**
     * 僅 category=WEATHER 時有值，見 V8 migration 的欄位註解與
     * ScoringService.calculateUrgencyFactor() 的 WEATHER 分支。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "weather_confidence")
    private WeatherForecastConfidence weatherConfidence;

    /**
     * 僅 category=WEATHER 時有值：這筆天氣檔期命中的區域代碼（NORTH/CENTRAL/
     * SOUTH/EAST，對照 WeatherRegionConfig.REGION_CITIES 的 key）。FESTIVAL／
     * SEASON 類檔期維持 NULL，語意上代表「全國性、不限地域」（見 V20 migration）。
     */
    @Column(name = "region", length = 10)
    private String region;

    /**
     * 僅 category=WEATHER 時有值：WeatherCampaignSyncService 同步當下，依
     * region_weights 算出「命中同一種天氣訊號類型的所有區域」加總的業務占比
     * （0~1，四區全中＝1.0）。在同步當下凍結寫入，不在計分當下即時查表重算，
     * 理由見 V20 migration 欄位註解（再現性：已核准商品的加成快照不因日後
     * 調整占比設定被追溯性改變）。
     */
    @Column(name = "region_coverage_ratio", precision = 5, scale = 4)
    private BigDecimal regionCoverageRatio;

    // target_tags(VARCHAR)欄位已移除：無法記錄「這個標籤屬於核心/一般/弱命中」的分級，
    // 改由festive_campaign_tags表承接（一檔期對多標籤、每個標籤各自帶match_tier），
    // 見FestiveCampaignTag.java。標籤清單查詢改用FestiveCampaignTagRepository.findByCampaignId()。

    @Enumerated(EnumType.STRING)
    @Column(name = "campaign_status", nullable = false)
    private FestiveCampaignStatus campaignStatus = FestiveCampaignStatus.UPCOMING;

    @Column(name = "is_manual_override", nullable = false)
    private Boolean isManualOverride = false;

    /**
     * V21（決議 D10）：FESTIVAL／SEASON 的手動覆蓋只對這個週期年的 occurrence 有效，
     * 進入下一期自動失效（讀取時判斷，不寫回；下次寫入時一併清除）。WEATHER 不使用。
     */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "manual_override_cycle")
    private Integer manualOverrideCycle;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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

	public WeatherForecastConfidence getWeatherConfidence() {
		return weatherConfidence;
	}

	public void setWeatherConfidence(WeatherForecastConfidence weatherConfidence) {
		this.weatherConfidence = weatherConfidence;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public BigDecimal getRegionCoverageRatio() {
		return regionCoverageRatio;
	}

	public void setRegionCoverageRatio(BigDecimal regionCoverageRatio) {
		this.regionCoverageRatio = regionCoverageRatio;
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

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
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

	public Integer getManualOverrideCycle() {
		return manualOverrideCycle;
	}

	public void setManualOverrideCycle(Integer manualOverrideCycle) {
		this.manualOverrideCycle = manualOverrideCycle;
	}
}
