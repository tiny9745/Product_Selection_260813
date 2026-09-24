package com.example.Product_Selection_260813.service.campaign;

import java.time.LocalDate;
import java.util.List;

/**
 * 檔期某一「期」的實際起訖日。
 *
 * @param cycleYear        週期年：國曆規則＝國曆年；農曆規則＝農曆年（以該年正月初一所在的國曆年表示）；
 *                         跨年季節＝開始日所在年。手動覆蓋與逐年覆寫都以這個值對應「哪一期」。
 * @param overridden       true＝起訖日來自逐年覆寫表，不是規則推算
 * @param observedHolidays 依補假規則算出的補假日（未展開連假時僅供顯示）
 */
public record CampaignOccurrence(int cycleYear, LocalDate startDate, LocalDate endDate, boolean overridden,
		List<LocalDate> observedHolidays) {
}
