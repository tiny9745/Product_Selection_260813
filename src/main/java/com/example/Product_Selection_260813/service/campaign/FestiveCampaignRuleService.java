package com.example.Product_Selection_260813.service.campaign;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.constants.BusinessTimeZone;
import com.example.Product_Selection_260813.constants.ValidationMessage;
import com.example.Product_Selection_260813.dto.request.FestiveCampaignOccurrenceOverrideRequest;
import com.example.Product_Selection_260813.dto.request.FestiveCampaignRuleFields;
import com.example.Product_Selection_260813.dto.response.FestiveCampaignOccurrenceOverrideResponse;
import com.example.Product_Selection_260813.dto.response.FestiveCampaignOccurrencePreviewResponse;
import com.example.Product_Selection_260813.dto.response.FestiveCampaignResponse;
import com.example.Product_Selection_260813.dto.response.FestiveCampaignTagView;
import com.example.Product_Selection_260813.entity.FestiveCampaign;
import com.example.Product_Selection_260813.entity.FestiveCampaignOccurrenceOverride;
import com.example.Product_Selection_260813.entity.FestiveCampaignOccurrenceOverrideId;
import com.example.Product_Selection_260813.entity.FestiveCampaignRegion;
import com.example.Product_Selection_260813.entity.FestiveCampaignTag;
import com.example.Product_Selection_260813.enums.CampaignDateRuleType;
import com.example.Product_Selection_260813.enums.CampaignStatusSource;
import com.example.Product_Selection_260813.enums.FestiveCampaignStatus;
import com.example.Product_Selection_260813.enums.FestiveCategory;
import com.example.Product_Selection_260813.enums.ObservedHolidayRule;
import com.example.Product_Selection_260813.repository.FestiveCampaignOccurrenceOverrideRepository;
import com.example.Product_Selection_260813.repository.FestiveCampaignRegionRepository;
import com.example.Product_Selection_260813.repository.FestiveCampaignRepository;
import com.example.Product_Selection_260813.repository.FestiveCampaignTagRepository;

/**
 * 檔期日期規則的驗證、寫入、回應組裝、預覽與逐年覆寫（2026-09-24 檔期規則改版）。
 *
 * SettingsService 已經很大，這次新增的檔期規則邏輯集中在這裡，SettingsService 的
 * 檔期方法只做「查 Entity → 呼叫這裡 → 存檔」的薄轉接，Controller 與 API 路徑不變。
 *
 * <b>validateRule() 是規則驗證的單一入口</b>，新增、修改、預覽三支 API 共用；
 * 單一欄位的範圍由 DTO 的 Jakarta 註解先擋（見 FestiveCampaignRuleFields）。
 */
@Service
public class FestiveCampaignRuleService {

	/** 四個區域代碼，與 region_weights／WeatherRegionConfig 的 key 一致。 */
	public static final Set<String> REGION_CODES = Set.of("NORTH", "CENTRAL", "SOUTH", "EAST");

	private static final Pattern CODE_FORMAT = Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");
	private static final Pattern CODE_WITH_YEAR = Pattern.compile(".*_\\d{4}$");
	private static final int PREVIEW_COUNT = 3;
	private static final int OVERRIDE_MAX_SPAN_DAYS = 120;
	private static final List<FestiveCampaignStatus> LIVE_STATUSES = List.of(FestiveCampaignStatus.PREPARING,
			FestiveCampaignStatus.ACTIVE);

	private final CampaignOccurrenceResolver resolver;
	private final RegionCoverageService regionCoverageService;
	private final FestiveCampaignRepository campaignRepository;
	private final FestiveCampaignTagRepository tagRepository;
	private final FestiveCampaignRegionRepository regionRepository;
	private final FestiveCampaignOccurrenceOverrideRepository overrideRepository;

	public FestiveCampaignRuleService(CampaignOccurrenceResolver resolver, RegionCoverageService regionCoverageService,
			FestiveCampaignRepository campaignRepository, FestiveCampaignTagRepository tagRepository,
			FestiveCampaignRegionRepository regionRepository,
			FestiveCampaignOccurrenceOverrideRepository overrideRepository) {
		this.resolver = resolver;
		this.regionCoverageService = regionCoverageService;
		this.campaignRepository = campaignRepository;
		this.tagRepository = tagRepository;
		this.regionRepository = regionRepository;
		this.overrideRepository = overrideRepository;
	}

	/** 檔期判斷用的「今天」一律以台灣時間為準（見 BusinessTimeZone）。 */
	public LocalDate today() {
		return LocalDate.now(BusinessTimeZone.TAIPEI);
	}

	// ==================================================================
	// 驗證（單一入口）
	// ==================================================================

	/** D5：代碼不帶年份，例 DRAGON_BOAT；以 _ 加 4 位數字結尾一律拒絕。 */
	public void validateCode(String campaignCode) {
		if (campaignCode == null || !CODE_FORMAT.matcher(campaignCode).matches()
				|| CODE_WITH_YEAR.matcher(campaignCode).matches()) {
			throw new IllegalArgumentException(ValidationMessage.CAMPAIGN_CODE_FORMAT_INVALID);
		}
	}

	/**
	 * 規則內部一致性驗證，並把「這個規則類型用不到的欄位」清成 null（避免殘值被存進資料庫、
	 * 日後切換規則類型時被誤用）。呼叫後 fields 即為可直接寫入的正規化結果。
	 */
	public void validateRule(FestiveCampaignRuleFields fields) {
		FestiveCategory category = fields.getCategory();
		if (category == null) {
			throw new IllegalArgumentException("檔期類別不可為空");
		}
		if (fields.getRuleOffsetDays() == null) {
			fields.setRuleOffsetDays(0);
		}
		if (fields.getObservedHolidayRule() == null) {
			fields.setObservedHolidayRule(ObservedHolidayRule.NONE);
		}
		if (fields.getExpandLongWeekend() == null) {
			fields.setExpandLongWeekend(false);
		}
		fields.setRegions(normalizeRegions(fields.getRegions()));

		if (category == FestiveCategory.SEASON) {
			if (fields.getDateRuleType() == null) {
				fields.setDateRuleType(CampaignDateRuleType.FIXED_DATE);
			}
			if (fields.getDateRuleType() != CampaignDateRuleType.FIXED_DATE) {
				throw new IllegalArgumentException(ValidationMessage.CAMPAIGN_SEASON_RULE_TYPE_INVALID);
			}
			if (fields.getDurationDays() != null) {
				throw new IllegalArgumentException(ValidationMessage.CAMPAIGN_DURATION_INVALID);
			}
			if (fields.getEndMonth() == null || fields.getEndDay() == null) {
				throw new IllegalArgumentException(ValidationMessage.CAMPAIGN_SEASON_END_REQUIRED);
			}
			if (fields.getObservedHolidayRule() != ObservedHolidayRule.NONE
					|| Boolean.TRUE.equals(fields.getExpandLongWeekend())) {
				throw new IllegalArgumentException(ValidationMessage.CAMPAIGN_HOLIDAY_RULE_FESTIVAL_ONLY);
			}
			// 2026-09-24：季節型開始月日可直接填，偏移只會讓人誤以為結束日也跟著移動（實際只移開始日），
			// 因此季節型不開放偏移；未帶值時上方已補 0。
			if (fields.getRuleOffsetDays() != 0) {
				throw new IllegalArgumentException(ValidationMessage.CAMPAIGN_OFFSET_FESTIVAL_ONLY);
			}
		} else {
			// 2026-09-24：節慶不帶地域屬性，一律視為全國（覆蓋率 1.0）；只有季節型可指定區域。
			// 用拒絕而非靜默清空，與「補假規則僅適用節慶型」的既有處理一致，呼叫端送錯時看得到原因。
			if (!fields.getRegions().isEmpty()) {
				throw new IllegalArgumentException(ValidationMessage.CAMPAIGN_REGION_SEASON_ONLY);
			}
			if (fields.getDateRuleType() == null) {
				throw new IllegalArgumentException(ValidationMessage.CAMPAIGN_RULE_TYPE_REQUIRED);
			}
			if (fields.getDurationDays() == null) {
				throw new IllegalArgumentException(ValidationMessage.CAMPAIGN_DURATION_INVALID);
			}
			if (fields.getEndMonth() != null || fields.getEndDay() != null) {
				throw new IllegalArgumentException(ValidationMessage.CAMPAIGN_SEASON_END_REQUIRED);
			}
		}

		switch (fields.getDateRuleType()) {
		case FIXED_DATE -> {
			requireValidGregorianDate(fields.getRuleMonth(), fields.getRuleDay());
			clear(fields, false, true, true, true);
		}
		case NTH_WEEKDAY -> {
			Integer ordinal = fields.getRuleWeekOrdinal();
			if (fields.getRuleMonth() == null || ordinal == null || ordinal == 0 || fields.getRuleWeekday() == null) {
				throw new IllegalArgumentException(ValidationMessage.CAMPAIGN_RULE_NTH_WEEKDAY_INVALID);
			}
			fields.setRuleDay(null);
			fields.setRuleSolarTerm(null);
		}
		case LUNAR_DATE -> {
			if (fields.getRuleMonth() == null || fields.getRuleDay() == null || fields.getRuleDay() > 30) {
				throw new IllegalArgumentException(ValidationMessage.CAMPAIGN_RULE_LUNAR_DATE_INVALID);
			}
			clear(fields, false, true, true, true);
		}
		case SOLAR_TERM -> {
			if (fields.getRuleSolarTerm() == null) {
				throw new IllegalArgumentException(ValidationMessage.CAMPAIGN_RULE_SOLAR_TERM_REQUIRED);
			}
			fields.setRuleMonth(null);
			fields.setRuleDay(null);
			clear(fields, false, true, true, false);
		}
		}
	}

	/** FIXED_DATE：月 1–12、日必須是該月存在的日子；2/29 允許（非閏年自動改 2/28，D6）。 */
	private static void requireValidGregorianDate(Integer month, Integer day) {
		if (month == null || day == null || month < 1 || month > 12 || day < 1
				|| day > Month.of(month).maxLength()) {
			throw new IllegalArgumentException(ValidationMessage.CAMPAIGN_RULE_DATE_INVALID);
		}
	}

	private static void clear(FestiveCampaignRuleFields fields, boolean day, boolean ordinal, boolean weekday,
			boolean solarTerm) {
		if (day) {
			fields.setRuleDay(null);
		}
		if (ordinal) {
			fields.setRuleWeekOrdinal(null);
		}
		if (weekday) {
			fields.setRuleWeekday(null);
		}
		if (solarTerm) {
			fields.setRuleSolarTerm(null);
		}
	}

	/** 去除重複並檢查代碼；null 視為空清單（＝全國）。 */
	public List<String> normalizeRegions(Collection<String> regions) {
		if (regions == null) {
			return List.of();
		}
		Set<String> result = new LinkedHashSet<>();
		for (String region : regions) {
			if (region == null || !REGION_CODES.contains(region)) {
				throw new IllegalArgumentException(ValidationMessage.CAMPAIGN_REGION_INVALID);
			}
			result.add(region);
		}
		return List.copyOf(result);
	}

	// ==================================================================
	// 寫入
	// ==================================================================

	/**
	 * 把已驗證的規則寫進 Entity。
	 * 若既有的手動覆蓋已不屬於新規則的本期（週期不符），一併清除（D10：失效的旗標在下次寫入時清除）。
	 */
	public void applyRule(FestiveCampaign campaign, FestiveCampaignRuleFields fields) {
		campaign.setCategory(fields.getCategory());
		campaign.setDateRuleType(fields.getDateRuleType());
		campaign.setRuleMonth(fields.getRuleMonth());
		campaign.setRuleDay(fields.getRuleDay());
		campaign.setRuleWeekOrdinal(fields.getRuleWeekOrdinal());
		campaign.setRuleWeekday(fields.getRuleWeekday());
		campaign.setRuleSolarTerm(fields.getRuleSolarTerm());
		campaign.setRuleOffsetDays(fields.getRuleOffsetDays());
		campaign.setDurationDays(fields.getDurationDays());
		campaign.setEndMonth(fields.getEndMonth());
		campaign.setEndDay(fields.getEndDay());
		campaign.setObservedHolidayRule(fields.getObservedHolidayRule());
		campaign.setExpandLongWeekend(fields.getExpandLongWeekend());
		clearStaleManualOverride(campaign);
	}

	/** 手動覆蓋的週期已不是本期時清除旗標（寫入時才做；讀取時只是忽略）。 */
	public void clearStaleManualOverride(FestiveCampaign campaign) {
		if (!Boolean.TRUE.equals(campaign.getIsManualOverride())) {
			return;
		}
		Integer currentCycle = currentOccurrence(campaign).map(CampaignOccurrence::cycleYear).orElse(null);
		if (campaign.getManualOverrideCycle() == null || !campaign.getManualOverrideCycle().equals(currentCycle)) {
			campaign.setIsManualOverride(false);
			campaign.setManualOverrideCycle(null);
		}
	}

	/** regions 整份覆蓋：先刪再寫（刪除前會先 flush 同交易的檔期異動，見 Repository 註解）。 */
	public void replaceRegions(Long campaignId, List<String> regions) {
		regionRepository.deleteByCampaignId(campaignId);
		for (String region : regions) {
			regionRepository.save(new FestiveCampaignRegion(campaignId, region));
		}
	}

	// ==================================================================
	// 本期推算與狀態
	// ==================================================================

	public Optional<CampaignOccurrence> currentOccurrence(FestiveCampaign campaign) {
		Map<Integer, OccurrenceOverride> overrides = campaign.getId() == null ? Map.of()
				: toOverrideMap(overrideRepository.findByCampaignId(campaign.getId()));
		return resolver.resolveCurrentOrNext(CampaignDateRules.of(campaign), overrides, today());
	}

	/**
	 * 「恢復自動判斷」時要寫回資料表的狀態。節慶／季節型的讀取本來就即時推算，這個值只是讓資料表
	 * 不留下過期的手動值。
	 */
	public FestiveCampaignStatus resolveAutomaticStatus(FestiveCampaign campaign) {
		return currentOccurrence(campaign)
				.map(occurrence -> resolver.deriveStatus(false, null, campaign.getCampaignStatus(),
						campaign.getPreparationLeadDays(), occurrence, today()).status())
				.orElse(FestiveCampaignStatus.EXPIRED);
	}

	/**
	 * 計分用：取出所有節慶／季節檔期（V26 起已無天氣檔期），批次載入區域與覆寫後逐一推算，
	 * 只回傳推算後為 PREPARING／ACTIVE 的檔期（修正 B1：不再依賴資料表裡從不推進的狀態）。
	 */
	@Transactional(readOnly = true)
	public List<ActiveCampaignWindow> findLiveWindows(LocalDate today) {
		List<FestiveCampaign> campaigns = new ArrayList<>(
				campaignRepository.findByCategoryIn(List.of(FestiveCategory.FESTIVAL, FestiveCategory.SEASON)));
		if (campaigns.isEmpty()) {
			return List.of();
		}
		List<Long> ids = campaigns.stream().map(FestiveCampaign::getId).toList();
		Map<Long, List<String>> regionsById = loadRegions(ids);
		Map<Long, Map<Integer, OccurrenceOverride>> overridesById = loadOverrideMaps(ids);
		Map<String, BigDecimal> weights = regionCoverageService.currentWeights();

		List<ActiveCampaignWindow> result = new ArrayList<>();
		for (FestiveCampaign campaign : campaigns) {
			CampaignDateRule rule = CampaignDateRules.of(campaign);
			Optional<CampaignOccurrence> occurrence = resolver.resolveCurrentOrNext(rule,
					overridesById.getOrDefault(campaign.getId(), Map.of()), today);
			if (occurrence.isEmpty()) {
				continue; // 規則不完整或超出內建表範圍：略過，不讓一筆異常資料影響整批計分
			}
			FestiveCampaignStatus status = resolver.deriveStatus(Boolean.TRUE.equals(campaign.getIsManualOverride()),
					campaign.getManualOverrideCycle(), campaign.getCampaignStatus(), campaign.getPreparationLeadDays(),
					occurrence.get(), today).status();
			if (!LIVE_STATUSES.contains(status)) {
				continue;
			}
			List<String> regions = effectiveRegions(campaign, regionsById);
			result.add(new ActiveCampaignWindow(campaign, occurrence.get(), status,
					regionCoverageService.coverageOf(regions, weights), regions));
		}
		return result;
	}

	// ==================================================================
	// 回應組裝（批次，避免 N+1）
	// ==================================================================

	@Transactional(readOnly = true)
	public List<FestiveCampaignResponse> toResponses(List<FestiveCampaign> campaigns) {
		if (campaigns.isEmpty()) {
			return List.of();
		}
		List<Long> ids = campaigns.stream().map(FestiveCampaign::getId).toList();
		Map<Long, List<FestiveCampaignTagView>> tagsById = tagRepository.findByCampaignIdIn(ids).stream()
				.collect(Collectors.groupingBy(FestiveCampaignTag::getCampaignId,
						Collectors.mapping(FestiveCampaignTagView::from, Collectors.toList())));
		Map<Long, List<String>> regionsById = loadRegions(ids);
		Map<Long, Map<Integer, OccurrenceOverride>> overridesById = loadOverrideMaps(ids);
		Map<String, BigDecimal> weights = regionCoverageService.currentWeights();
		LocalDate today = today();
		return campaigns.stream()
				.map(campaign -> buildResponse(campaign, tagsById.getOrDefault(campaign.getId(), List.of()),
						effectiveRegions(campaign, regionsById),
						overridesById.getOrDefault(campaign.getId(), Map.of()), weights, today))
				.toList();
	}

	public FestiveCampaignResponse toResponse(FestiveCampaign campaign) {
		return toResponses(List.of(campaign)).get(0);
	}

	private FestiveCampaignResponse buildResponse(FestiveCampaign campaign, List<FestiveCampaignTagView> tags,
			List<String> regions, Map<Integer, OccurrenceOverride> overrides, Map<String, BigDecimal> weights,
			LocalDate today) {
		CampaignDateRule rule = CampaignDateRules.of(campaign);
		FestiveCampaignResponse dto = new FestiveCampaignResponse();
		dto.setId(campaign.getId());
		dto.setCampaignCode(campaign.getCampaignCode());
		dto.setCampaignName(campaign.getCampaignName());
		dto.setCategory(campaign.getCategory());
		dto.setPreparationLeadDays(campaign.getPreparationLeadDays());
		dto.setIsManualOverride(campaign.getIsManualOverride());
		dto.setManualOverrideCycle(campaign.getManualOverrideCycle());
		dto.setTags(tags);
		dto.setDateRuleType(campaign.getDateRuleType());
		dto.setRuleMonth(campaign.getRuleMonth());
		dto.setRuleDay(campaign.getRuleDay());
		dto.setRuleWeekOrdinal(campaign.getRuleWeekOrdinal());
		dto.setRuleWeekday(campaign.getRuleWeekday());
		dto.setRuleSolarTerm(campaign.getRuleSolarTerm());
		dto.setRuleOffsetDays(campaign.getRuleOffsetDays());
		dto.setDurationDays(campaign.getDurationDays());
		dto.setEndMonth(campaign.getEndMonth());
		dto.setEndDay(campaign.getEndDay());
		dto.setObservedHolidayRule(campaign.getObservedHolidayRule());
		dto.setExpandLongWeekend(campaign.getExpandLongWeekend());
		dto.setRuleDescription(CampaignRuleDescriber.describe(rule));

		dto.setRegions(regions);
		dto.setRegionCoverageRatio(regionCoverageService.coverageOf(regions, weights));
		Optional<CampaignOccurrence> occurrence = resolver.resolveCurrentOrNext(rule, overrides, today);
		if (occurrence.isEmpty()) {
			dto.setObservedHolidays(List.of());
			dto.setOccurrenceOverridden(false);
			dto.setCampaignStatus(campaign.getCampaignStatus());
			dto.setStatusSource(CampaignStatusSource.AUTO);
			return dto;
		}
		CampaignOccurrence current = occurrence.get();
		DerivedCampaignStatus derived = resolver.deriveStatus(Boolean.TRUE.equals(campaign.getIsManualOverride()),
				campaign.getManualOverrideCycle(), campaign.getCampaignStatus(), campaign.getPreparationLeadDays(),
				current, today);
		dto.setStartDate(current.startDate());
		dto.setEndDate(current.endDate());
		dto.setCycleYear(current.cycleYear());
		dto.setOccurrenceOverridden(current.overridden());
		dto.setObservedHolidays(current.observedHolidays());
		dto.setCampaignStatus(derived.status());
		dto.setStatusSource(derived.source());
		return dto;
	}

	// ==================================================================
	// 預覽
	// ==================================================================

	/** 不寫入資料庫：由今天起的 3 期起訖日與補假日，供設定畫面即時預覽。 */
	public List<FestiveCampaignOccurrencePreviewResponse> preview(FestiveCampaignRuleFields fields) {
		validateRule(fields);
		return resolver.previewOccurrences(CampaignDateRules.of(fields), Map.of(), today(), PREVIEW_COUNT).stream()
				.map(o -> new FestiveCampaignOccurrencePreviewResponse(o.cycleYear(), o.startDate(), o.endDate(),
						o.observedHolidays(), o.overridden()))
				.toList();
	}

	// ==================================================================
	// 逐年覆寫
	// ==================================================================

	@Transactional(readOnly = true)
	public List<FestiveCampaignOccurrenceOverrideResponse> listOverrides(Long campaignId) {
		findCampaignOrThrow(campaignId);
		return overrideRepository.findByCampaignId(campaignId).stream().map(FestiveCampaignRuleService::toOverrideView)
				.toList();
	}

	@Transactional
	public FestiveCampaignOccurrenceOverrideResponse upsertOverride(Long campaignId, int cycleYear,
			FestiveCampaignOccurrenceOverrideRequest request, Long operatorId) {
		FestiveCampaign campaign = findCampaignOrThrow(campaignId);
		LocalDate start = request.getStartDate();
		LocalDate end = request.getEndDate();
		if (cycleYear < 2000 || cycleYear > 2099 || start == null || end == null || end.isBefore(start)
				|| ChronoUnit.DAYS.between(start, end) > OVERRIDE_MAX_SPAN_DAYS) {
			throw new IllegalArgumentException(ValidationMessage.CAMPAIGN_OVERRIDE_RANGE_INVALID);
		}
		FestiveCampaignOccurrenceOverrideId id = new FestiveCampaignOccurrenceOverrideId(campaignId, cycleYear);
		FestiveCampaignOccurrenceOverride entity = overrideRepository.findById(id).orElseGet(() -> {
			FestiveCampaignOccurrenceOverride created = new FestiveCampaignOccurrenceOverride();
			created.setId(id);
			return created;
		});
		entity.setStartDate(start);
		entity.setEndDate(end);
		entity.setNote(request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim());
		entity.setUpdatedBy(operatorId);
		return toOverrideView(overrideRepository.saveAndFlush(entity));
	}

	@Transactional
	public void deleteOverride(Long campaignId, int cycleYear) {
		findCampaignOrThrow(campaignId);
		overrideRepository.deleteById(new FestiveCampaignOccurrenceOverrideId(campaignId, cycleYear));
	}

	private static FestiveCampaignOccurrenceOverrideResponse toOverrideView(FestiveCampaignOccurrenceOverride entity) {
		return new FestiveCampaignOccurrenceOverrideResponse(entity.getCycleYear(), entity.getStartDate(),
				entity.getEndDate(), entity.getNote(), entity.getUpdatedAt());
	}

	// ==================================================================
	// 內部輔助
	// ==================================================================

	private FestiveCampaign findCampaignOrThrow(Long campaignId) {
		return campaignRepository.findById(campaignId).orElseThrow(() -> new IllegalArgumentException("檔期不存在"));
	}

	/**
	 * 讀取端的地域：只有季節型採用 festive_campaign_regions；節慶型一律回空清單（＝全國）。
	 * 寫入端已由 validateRule() 擋下節慶帶地域，這裡是防禦舊資料（規則改版前建立、或直接改表的列）
	 * 讓節慶加成被地域打折——計分與列表顯示走同一個判斷，不會一邊全國、一邊打折。
	 */
	private static List<String> effectiveRegions(FestiveCampaign campaign, Map<Long, List<String>> regionsById) {
		if (campaign.getCategory() != FestiveCategory.SEASON) {
			return List.of();
		}
		return regionsById.getOrDefault(campaign.getId(), List.of());
	}

	private Map<Long, List<String>> loadRegions(Collection<Long> ids) {
		return regionRepository.findByCampaignIdIn(ids).stream()
				.collect(Collectors.groupingBy(FestiveCampaignRegion::getCampaignId,
						Collectors.mapping(FestiveCampaignRegion::getRegion, Collectors.toList())));
	}

	private Map<Long, Map<Integer, OccurrenceOverride>> loadOverrideMaps(Collection<Long> ids) {
		return overrideRepository.findByCampaignIdIn(ids).stream()
				.collect(Collectors.groupingBy(FestiveCampaignOccurrenceOverride::getCampaignId,
						Collectors.toMap(FestiveCampaignOccurrenceOverride::getCycleYear,
								o -> new OccurrenceOverride(o.getStartDate(), o.getEndDate()))));
	}

	private static Map<Integer, OccurrenceOverride> toOverrideMap(List<FestiveCampaignOccurrenceOverride> list) {
		return list.stream().collect(Collectors.toMap(FestiveCampaignOccurrenceOverride::getCycleYear,
				o -> new OccurrenceOverride(o.getStartDate(), o.getEndDate())));
	}
}
