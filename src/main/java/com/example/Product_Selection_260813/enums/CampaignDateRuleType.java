package com.example.Product_Selection_260813.enums;

/**
 * 節慶／季節檔期的「日期規則」類型（V21，2026-09-24 檔期規則改版 D2）。
 *
 * 節慶型與季節型檔期不再存具體年度的起訖日，改存「每年怎麼算」的規則，
 * 每年實際的起訖日（occurrence）由 CampaignOccurrenceResolver 即時推算。
 * 天氣型檔期不使用規則（start_date／end_date 由同步服務寫入），這個欄位維持 NULL。
 */
public enum CampaignDateRuleType {
	/** 固定國曆月日，例：228＝2/28。2/29 在非閏年解析為 2/28（D6）。 */
	FIXED_DATE,
	/** 某月第 N 個星期幾（N＝1～4，或 -1＝最後一個），例：母親節＝5 月第 2 個星期日。 */
	NTH_WEEKDAY,
	/** 農曆月日（一律指非閏月），例：端午＝農曆 5/5；日數超過該月天數時取月末。 */
	LUNAR_DATE,
	/** 節氣（清明、冬至），日期由內建節氣表查出。 */
	SOLAR_TERM
}
