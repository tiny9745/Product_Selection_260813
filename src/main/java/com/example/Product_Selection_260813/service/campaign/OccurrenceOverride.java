package com.example.Product_Selection_260813.service.campaign;

import java.time.LocalDate;

/** 逐年覆寫的純資料表示（對應 festive_campaign_occurrence_overrides 一列）。 */
public record OccurrenceOverride(LocalDate startDate, LocalDate endDate) {
}
