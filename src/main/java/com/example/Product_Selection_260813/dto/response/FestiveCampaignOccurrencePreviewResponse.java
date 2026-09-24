package com.example.Product_Selection_260813.dto.response;

import java.time.LocalDate;
import java.util.List;

/** 日期規則即時預覽的一期（POST .../occurrence-preview 回傳 3 期）。 */
public record FestiveCampaignOccurrencePreviewResponse(int cycleYear, LocalDate startDate, LocalDate endDate,
		List<LocalDate> observedHolidays, boolean overridden) {
}
