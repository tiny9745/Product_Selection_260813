package com.example.Product_Selection_260813.service.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.Product_Selection_260813.enums.CampaignDateRuleType;
import com.example.Product_Selection_260813.enums.CampaignStatusSource;
import com.example.Product_Selection_260813.enums.FestiveCampaignStatus;
import com.example.Product_Selection_260813.enums.FestiveCategory;
import com.example.Product_Selection_260813.enums.ObservedHolidayRule;
import com.example.Product_Selection_260813.enums.SolarTerm;

/**
 * CampaignOccurrenceResolver 純單元測試（固定 today，不依賴資料庫與 Spring）。
 * 案例對應「檔期規則改版_實作指令」第 7.1 節表格，另補農曆、節氣、補假方向的邊界。
 */
class CampaignOccurrenceResolverTest {

	private final CampaignOccurrenceResolver resolver = new CampaignOccurrenceResolver(new LunarCalendarService(),
			new SolarTermCalculator());

	private static CampaignDateRule festival(CampaignDateRuleType type, Integer month, Integer day, Integer ordinal,
			Integer weekday, SolarTerm term, int offset, int duration, ObservedHolidayRule observed, boolean expand) {
		return new CampaignDateRule(FestiveCategory.FESTIVAL, type, month, day, ordinal, weekday, term, offset,
				duration, null, null, observed, expand);
	}

	private static CampaignDateRule fixed(int month, int day, int duration, ObservedHolidayRule observed,
			boolean expand) {
		return festival(CampaignDateRuleType.FIXED_DATE, month, day, null, null, null, 0, duration, observed, expand);
	}

	private static CampaignDateRule lunar(int month, int day, int offset, int duration) {
		return festival(CampaignDateRuleType.LUNAR_DATE, month, day, null, null, null, offset, duration,
				ObservedHolidayRule.NONE, false);
	}

	private static CampaignDateRule season(int month, int day, int endMonth, int endDay) {
		return new CampaignDateRule(FestiveCategory.SEASON, CampaignDateRuleType.FIXED_DATE, month, day, null, null,
				null, 0, null, endMonth, endDay, ObservedHolidayRule.NONE, false);
	}

	private CampaignOccurrence current(CampaignDateRule rule, LocalDate today) {
		return resolver.resolveCurrentOrNext(rule, Map.of(), today).orElseThrow();
	}

	private static LocalDate d(String iso) {
		return LocalDate.parse(iso);
	}

	// ----- 第 N 個星期幾 -----

	@Test
	void 母親節2026為5月第2個週日() {
		CampaignDateRule mothersDay = festival(CampaignDateRuleType.NTH_WEEKDAY, 5, null, 2, 7, null, 0, 1,
				ObservedHolidayRule.NONE, false);
		CampaignOccurrence occurrence = current(mothersDay, d("2026-04-01"));
		assertThat(occurrence.startDate()).isEqualTo(d("2026-05-10"));
		assertThat(occurrence.endDate()).isEqualTo(d("2026-05-10"));
	}

	@Test
	void 母親節今年已過時取下一期() {
		CampaignDateRule mothersDay = festival(CampaignDateRuleType.NTH_WEEKDAY, 5, null, 2, 7, null, 0, 1,
				ObservedHolidayRule.NONE, false);
		assertThat(current(mothersDay, d("2026-06-01")).startDate()).isEqualTo(d("2027-05-09"));
	}

	@Test
	void 最後一個星期幾() {
		CampaignDateRule lastFridayOfNovember = festival(CampaignDateRuleType.NTH_WEEKDAY, 11, null, -1, 5, null, 0,
				1, ObservedHolidayRule.NONE, false);
		assertThat(current(lastFridayOfNovember, d("2026-01-01")).startDate()).isEqualTo(d("2026-11-27"));
	}

	// ----- 補假與連假展開（228） -----

	@Test
	void 二二八逢週六於前一個週五補假_不展開() {
		CampaignOccurrence occurrence = current(fixed(2, 28, 1, ObservedHolidayRule.TW_STATUTORY, false),
				d("2026-01-10"));
		assertThat(occurrence.startDate()).isEqualTo(d("2026-02-28"));
		assertThat(occurrence.endDate()).isEqualTo(d("2026-02-28"));
		assertThat(occurrence.observedHolidays()).containsExactly(d("2026-02-27"));
	}

	@Test
	void 二二八逢週六展開連假() {
		CampaignOccurrence occurrence = current(fixed(2, 28, 1, ObservedHolidayRule.TW_STATUTORY, true),
				d("2026-01-10"));
		assertThat(occurrence.startDate()).isEqualTo(d("2026-02-27"));
		assertThat(occurrence.endDate()).isEqualTo(d("2026-03-01"));
	}

	@Test
	void 二二八逢週日展開連假() {
		CampaignOccurrence occurrence = current(fixed(2, 28, 1, ObservedHolidayRule.TW_STATUTORY, true),
				d("2027-01-10"));
		assertThat(occurrence.startDate()).isEqualTo(d("2027-02-27"));
		assertThat(occurrence.endDate()).isEqualTo(d("2027-03-01"));
		assertThat(occurrence.observedHolidays()).containsExactly(d("2027-03-01"));
	}

	@Test
	void 二二八逢週一展開連假() {
		CampaignOccurrence occurrence = current(fixed(2, 28, 1, ObservedHolidayRule.TW_STATUTORY, true),
				d("2028-01-10"));
		assertThat(occurrence.startDate()).isEqualTo(d("2028-02-26"));
		assertThat(occurrence.endDate()).isEqualTo(d("2028-02-28"));
		assertThat(occurrence.observedHolidays()).isEmpty();
	}

	@Test
	void 補假撞到檔期時沿原方向繼續找() {
		// 2026-02-14（六）～02-15（日）兩天的檔期：週六往前找到 02-13（五），週日往後找到 02-16（一）。
		assertThat(CampaignOccurrenceResolver.twObservedDays(d("2026-02-14"), d("2026-02-15")))
				.containsExactly(d("2026-02-13"), d("2026-02-16"));
		// 2026-02-13（五）～02-14（六）：週六的前一天在檔期內，繼續往前跳過週末到 02-12（四）。
		assertThat(CampaignOccurrenceResolver.twObservedDays(d("2026-02-13"), d("2026-02-14")))
				.containsExactly(d("2026-02-12"));
	}

	// ----- 2/29 -----

	@Test
	void 二月二十九日在非閏年為二月二十八日() {
		assertThat(current(fixed(2, 29, 1, ObservedHolidayRule.NONE, false), d("2027-01-01")).startDate())
				.isEqualTo(d("2027-02-28"));
	}

	@Test
	void 二月二十九日在閏年維持() {
		assertThat(current(fixed(2, 29, 1, ObservedHolidayRule.NONE, false), d("2028-01-01")).startDate())
				.isEqualTo(d("2028-02-29"));
	}

	// ----- 跨年季節 -----

	@Test
	void 跨年冬季進行中取去年開始的那一期() {
		CampaignOccurrence occurrence = current(season(12, 1, 2, 31), d("2027-01-15"));
		assertThat(occurrence.startDate()).isEqualTo(d("2026-12-01"));
		assertThat(occurrence.endDate()).isEqualTo(d("2027-02-28"));
		assertThat(occurrence.cycleYear()).isEqualTo(2026);
	}

	@Test
	void 跨年冬季遇閏年結束在二月二十九日() {
		CampaignOccurrence occurrence = current(season(12, 1, 2, 31), d("2027-12-15"));
		assertThat(occurrence.startDate()).isEqualTo(d("2027-12-01"));
		assertThat(occurrence.endDate()).isEqualTo(d("2028-02-29"));
	}

	// ----- 農曆與節氣 -----

	@Test
	void 端午節2026起三天() {
		CampaignOccurrence occurrence = current(lunar(5, 5, 0, 3), d("2026-01-01"));
		assertThat(occurrence.startDate()).isEqualTo(d("2026-06-19"));
		assertThat(occurrence.endDate()).isEqualTo(d("2026-06-21"));
	}

	@Test
	void 除夕為正月初一偏移負一天_2027年官方公告為2月5日() {
		// ICU4J 78.3 把 2027 春節算成 2/7，除夕就會錯成 2/6；人事總處 116 年公告除夕 2/5。
		assertThat(current(lunar(1, 1, -1, 1), d("2026-06-01")).startDate()).isEqualTo(d("2027-02-05"));
	}

	@Test
	void 清明節氣規則() {
		CampaignDateRule qingming = festival(CampaignDateRuleType.SOLAR_TERM, null, null, null, null,
				SolarTerm.QINGMING, 0, 1, ObservedHolidayRule.NONE, false);
		assertThat(current(qingming, d("2028-01-01")).startDate()).isEqualTo(d("2028-04-04"));
	}

	// ----- 覆寫、預覽 -----

	@Test
	void 逐年覆寫優先於規則() {
		Map<Integer, OccurrenceOverride> overrides = Map.of(2026,
				new OccurrenceOverride(d("2026-02-13"), d("2026-02-22")));
		CampaignOccurrence occurrence = resolver.resolveCurrentOrNext(lunar(1, 1, 0, 3), overrides, d("2026-01-01"))
				.orElseThrow();
		assertThat(occurrence.startDate()).isEqualTo(d("2026-02-13"));
		assertThat(occurrence.endDate()).isEqualTo(d("2026-02-22"));
		assertThat(occurrence.overridden()).isTrue();
	}

	@Test
	void 預覽回傳連續三期() {
		List<CampaignOccurrence> preview = resolver.previewOccurrences(lunar(8, 15, 0, 1), Map.of(),
				d("2026-10-01"), 3);
		assertThat(preview.stream().map(CampaignOccurrence::startDate).toList())
				.containsExactly(d("2027-09-15"), d("2028-10-03"), d("2029-09-22"));
	}

	@Test
	void 超出內建表範圍時不回傳錯誤日期() {
		assertThat(resolver.resolveCurrentOrNext(lunar(5, 5, 0, 1), Map.of(), d("2101-01-01")).isPresent())
				.isFalse();
	}

	// ----- 狀態推算 -----

	@Test
	void 狀態邊界_準備期三十天_持續三天() {
		CampaignOccurrence occurrence = new CampaignOccurrence(2026, d("2026-06-19"), d("2026-06-21"), false,
				List.of());
		assertThat(status(occurrence, "2026-05-19")).isEqualTo(FestiveCampaignStatus.UPCOMING);
		assertThat(status(occurrence, "2026-05-20")).isEqualTo(FestiveCampaignStatus.PREPARING);
		assertThat(status(occurrence, "2026-06-19")).isEqualTo(FestiveCampaignStatus.ACTIVE);
		assertThat(status(occurrence, "2026-06-21")).isEqualTo(FestiveCampaignStatus.ACTIVE);
	}

	@Test
	void 手動覆蓋只對當期有效() {
		CampaignOccurrence occurrence2026 = new CampaignOccurrence(2026, d("2026-06-19"), d("2026-06-21"), false,
				List.of());
		DerivedCampaignStatus stillManual = resolver.deriveStatus(true, 2026, FestiveCampaignStatus.EXPIRED, 30,
				occurrence2026, d("2026-06-01"));
		assertThat(stillManual.status()).isEqualTo(FestiveCampaignStatus.EXPIRED);
		assertThat(stillManual.source()).isEqualTo(CampaignStatusSource.MANUAL);

		DerivedCampaignStatus expired = resolver.deriveStatus(true, 2025, FestiveCampaignStatus.EXPIRED, 30,
				occurrence2026, d("2026-06-01"));
		assertThat(expired.status()).isEqualTo(FestiveCampaignStatus.PREPARING);
		assertThat(expired.source()).isEqualTo(CampaignStatusSource.AUTO);
	}

	private FestiveCampaignStatus status(CampaignOccurrence occurrence, String today) {
		return resolver.deriveStatus(false, null, FestiveCampaignStatus.UPCOMING, 30, occurrence, d(today)).status();
	}
}
