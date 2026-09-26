package com.example.Product_Selection_260813.service.campaign;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.enums.CampaignDateRuleType;
import com.example.Product_Selection_260813.enums.CampaignStatusSource;
import com.example.Product_Selection_260813.enums.FestiveCampaignStatus;
import com.example.Product_Selection_260813.enums.FestiveCategory;
import com.example.Product_Selection_260813.enums.ObservedHolidayRule;

/**
 * 檔期 occurrence 推算（2026-09-24 檔期規則改版的核心，D2／D10）。
 *
 * 給定日期規則與「今天」，算出目前或下一期的實際起訖日，以及推算後的狀態。
 *
 * <b>純函式設計</b>：所有方法都接受 today 參數，內部不呼叫 LocalDate.now()；呼叫端統一用
 * {@code LocalDate.now(BusinessTimeZone.TAIPEI)} 取得今天，單元測試才能固定日期。
 * 依賴只有兩張內建表（農曆、節氣），不碰資料庫。
 */
@Component
public class CampaignOccurrenceResolver {

	private final LunarCalendarService lunarCalendarService;
	private final SolarTermCalculator solarTermCalculator;

	public CampaignOccurrenceResolver(LunarCalendarService lunarCalendarService,
			SolarTermCalculator solarTermCalculator) {
		this.lunarCalendarService = lunarCalendarService;
		this.solarTermCalculator = solarTermCalculator;
	}

	/**
	 * 目前或下一期：在週期年 Y-1、Y、Y+1（Y＝今天的國曆年）三個候選中，取「結束日 ≥ 今天」
	 * 且開始日最早的一期。Y-1 涵蓋跨年季節（去年 12/1 開始的冬季），Y+1 涵蓋今年已結束時的下一期。
	 *
	 * 規則不完整或超出內建表範圍的候選會被略過；
	 * 三個候選都算不出來時回傳 empty，由呼叫端決定如何處理（計分時略過該檔期）。
	 */
	public Optional<CampaignOccurrence> resolveCurrentOrNext(CampaignDateRule rule,
			Map<Integer, OccurrenceOverride> overridesByCycle, LocalDate today) {
		int year = today.getYear();
		return candidates(rule, overridesByCycle, year - 1, year + 1).stream()
				.filter(occurrence -> !occurrence.endDate().isBefore(today))
				.min(Comparator.comparing(CampaignOccurrence::startDate));
	}

	/**
	 * 由目前或下一期起算的連續 count 期，供設定畫面即時預覽（不寫入資料庫）。
	 */
	public List<CampaignOccurrence> previewOccurrences(CampaignDateRule rule,
			Map<Integer, OccurrenceOverride> overridesByCycle, LocalDate today, int count) {
		Optional<CampaignOccurrence> first = resolveCurrentOrNext(rule, overridesByCycle, today);
		if (first.isEmpty()) {
			return first.map(List::of).orElse(List.of());
		}
		List<CampaignOccurrence> result = new ArrayList<>();
		result.add(first.get());
		int cycle = first.get().cycleYear();
		while (result.size() < count) {
			cycle++;
			Optional<CampaignOccurrence> next = tryCompute(rule, cycle, overridesByCycle);
			if (next.isEmpty()) {
				break; // 超出內建表範圍，不再往後推
			}
			result.add(next.get());
		}
		return result;
	}

	/**
	 * 指定週期年的 occurrence。逐年覆寫優先；否則依規則推算。
	 *
	 * @throws IllegalArgumentException 規則不完整或年份超出內建表範圍
	 */
	public CampaignOccurrence computeOccurrence(CampaignDateRule rule, int cycleYear,
			Map<Integer, OccurrenceOverride> overridesByCycle) {
		OccurrenceOverride override = overridesByCycle == null ? null : overridesByCycle.get(cycleYear);
		if (override != null) {
			return new CampaignOccurrence(cycleYear, override.startDate(), override.endDate(), true, List.of());
		}
		LocalDate anchor = computeAnchor(rule, cycleYear).plusDays(rule.offsetDays());

		if (rule.category() == FestiveCategory.SEASON) {
			int endYear = isBefore(rule.endMonth(), rule.endDay(), rule.month(), rule.day()) ? cycleYear + 1
					: cycleYear;
			LocalDate end = clampDay(endYear, required(rule.endMonth(), "結束月"), required(rule.endDay(), "結束日"));
			return new CampaignOccurrence(cycleYear, anchor, end, false, List.of());
		}

		LocalDate start = anchor;
		LocalDate end = anchor.plusDays(required(rule.durationDays(), "持續天數") - 1L);
		List<LocalDate> observed = rule.observedHolidayRule() == ObservedHolidayRule.TW_STATUTORY
				? twObservedDays(start, end)
				: List.of();
		if (rule.expandLongWeekend()) {
			LocalDate[] expanded = expandLongWeekend(start, end, observed);
			start = expanded[0];
			end = expanded[1];
		}
		return new CampaignOccurrence(cycleYear, start, end, false, observed);
	}

	/**
	 * 推算狀態（僅節慶／季節型；天氣型狀態由同步服務寫入，呼叫端直接讀資料表）。
	 *
	 * <ol>
	 * <li>手動覆蓋且覆蓋週期＝本期 → 回傳資料表中的手動狀態（MANUAL）。</li>
	 * <li>手動覆蓋但週期不符 → 視為已失效，走自動規則。讀取時不寫回資料庫，下次寫入時一併清除。</li>
	 * <li>自動：今天 &lt; 開始日−準備天數 → UPCOMING；今天 &lt; 開始日 → PREPARING；今天 ≤ 結束日 → ACTIVE。</li>
	 * </ol>
	 * 循環檔期不會自動變成 EXPIRED（已選取「目前或下一期」）；EXPIRED 只能以手動覆蓋設定，作為「本期停用」。
	 */
	public DerivedCampaignStatus deriveStatus(boolean manualOverride, Integer manualOverrideCycle,
			FestiveCampaignStatus storedStatus, Integer preparationLeadDays, CampaignOccurrence occurrence,
			LocalDate today) {
		if (manualOverride && manualOverrideCycle != null && manualOverrideCycle == occurrence.cycleYear()
				&& storedStatus != null) {
			return new DerivedCampaignStatus(storedStatus, CampaignStatusSource.MANUAL);
		}
		long leadDays = preparationLeadDays != null && preparationLeadDays > 0 ? preparationLeadDays : 0;
		FestiveCampaignStatus status;
		if (today.isBefore(occurrence.startDate().minusDays(leadDays))) {
			status = FestiveCampaignStatus.UPCOMING;
		} else if (today.isBefore(occurrence.startDate())) {
			status = FestiveCampaignStatus.PREPARING;
		} else if (!today.isAfter(occurrence.endDate())) {
			status = FestiveCampaignStatus.ACTIVE;
		} else {
			// 防禦：正常流程已選取「目前或下一期」，不會走到這裡。
			status = FestiveCampaignStatus.EXPIRED;
		}
		return new DerivedCampaignStatus(status, CampaignStatusSource.AUTO);
	}

	// ------------------------------------------------------------------
	// 台灣補假與連假展開（D7；補假方向為 2026-09-24 決議）
	// ------------------------------------------------------------------

	/**
	 * 檔期內每一天：逢週六於前一個上班日補假，逢週日於次一個上班日補假。補假日撞到週末、
	 * 檔期內的日子或已經排定的補假日時，沿同一方向繼續找（週六往前、週日往後）。
	 * 除夕與春節「得」前得後的特別規定不處理，以逐年覆寫表處理。
	 */
	static List<LocalDate> twObservedDays(LocalDate start, LocalDate end) {
		TreeSet<LocalDate> observed = new TreeSet<>();
		for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
			int step;
			if (day.getDayOfWeek() == DayOfWeek.SATURDAY) {
				step = -1;
			} else if (day.getDayOfWeek() == DayOfWeek.SUNDAY) {
				step = 1;
			} else {
				continue;
			}
			LocalDate candidate = day.plusDays(step);
			while (isWeekend(candidate) || isWithin(candidate, start, end) || observed.contains(candidate)) {
				candidate = candidate.plusDays(step);
			}
			observed.add(candidate);
		}
		return List.copyOf(observed);
	}

	/**
	 * 以 H＝[start..end] ∪ 補假日 為放假日集合，從 min(H) 往前、max(H) 往後，只要相鄰那天是
	 * 週六、週日或屬於 H 就持續擴張。不考慮補班日（無法推導），例外以覆寫表處理。
	 */
	static LocalDate[] expandLongWeekend(LocalDate start, LocalDate end, List<LocalDate> observed) {
		TreeSet<LocalDate> holidays = new TreeSet<>(observed);
		for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
			holidays.add(day);
		}
		LocalDate low = holidays.first();
		LocalDate high = holidays.last();
		while (isWeekend(low.minusDays(1)) || holidays.contains(low.minusDays(1))) {
			low = low.minusDays(1);
		}
		while (isWeekend(high.plusDays(1)) || holidays.contains(high.plusDays(1))) {
			high = high.plusDays(1);
		}
		return new LocalDate[] { low, high };
	}

	// ------------------------------------------------------------------
	// 內部輔助
	// ------------------------------------------------------------------

	private List<CampaignOccurrence> candidates(CampaignDateRule rule, Map<Integer, OccurrenceOverride> overrides,
			int fromCycle, int toCycle) {
		List<CampaignOccurrence> result = new ArrayList<>();
		for (int cycle = fromCycle; cycle <= toCycle; cycle++) {
			tryCompute(rule, cycle, overrides).ifPresent(result::add);
		}
		return result;
	}

	private Optional<CampaignOccurrence> tryCompute(CampaignDateRule rule, int cycleYear,
			Map<Integer, OccurrenceOverride> overrides) {
		try {
			return Optional.of(computeOccurrence(rule, cycleYear, overrides));
		} catch (IllegalArgumentException | NullPointerException e) {
			return Optional.empty();
		}
	}

	private LocalDate computeAnchor(CampaignDateRule rule, int cycleYear) {
		CampaignDateRuleType type = rule.ruleType();
		if (type == null) {
			throw new IllegalArgumentException("檔期缺少日期規則");
		}
		return switch (type) {
		case FIXED_DATE -> clampDay(cycleYear, required(rule.month(), "月"), required(rule.day(), "日"));
		case NTH_WEEKDAY -> {
			LocalDate firstOfMonth = LocalDate.of(cycleYear, required(rule.month(), "月"), 1);
			DayOfWeek weekday = DayOfWeek.of(required(rule.weekday(), "星期"));
			int ordinal = required(rule.weekOrdinal(), "第幾個");
			yield ordinal == -1 ? firstOfMonth.with(TemporalAdjusters.lastInMonth(weekday))
					: firstOfMonth.with(TemporalAdjusters.dayOfWeekInMonth(ordinal, weekday));
		}
		case LUNAR_DATE -> lunarCalendarService.toGregorian(cycleYear, required(rule.month(), "月"),
				required(rule.day(), "日"));
		case SOLAR_TERM -> solarTermCalculator.dateOf(rule.solarTerm(), cycleYear);
		};
	}

	/** 2/29 在非閏年得 2/28；31 日在小月得月底。 */
	static LocalDate clampDay(int year, int month, int day) {
		return LocalDate.of(year, month, Math.min(day, YearMonth.of(year, month).lengthOfMonth()));
	}

	private static boolean isBefore(Integer month, Integer day, Integer otherMonth, Integer otherDay) {
		int left = required(month, "結束月") * 100 + required(day, "結束日");
		int right = required(otherMonth, "開始月") * 100 + required(otherDay, "開始日");
		return left < right;
	}

	private static boolean isWeekend(LocalDate day) {
		return day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
	}

	private static boolean isWithin(LocalDate day, LocalDate start, LocalDate end) {
		return !day.isBefore(start) && !day.isAfter(end);
	}

	private static int required(Integer value, String field) {
		if (value == null) {
			throw new IllegalArgumentException("檔期日期規則缺少欄位：" + field);
		}
		return value;
	}
}
