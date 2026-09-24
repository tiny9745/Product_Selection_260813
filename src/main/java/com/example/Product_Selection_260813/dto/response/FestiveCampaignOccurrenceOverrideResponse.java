package com.example.Product_Selection_260813.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 逐年日期覆寫的一列。 */
public record FestiveCampaignOccurrenceOverrideResponse(int cycleYear, LocalDate startDate, LocalDate endDate,
		String note, LocalDateTime updatedAt) {
}
