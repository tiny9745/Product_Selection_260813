package com.example.Product_Selection_260813.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/settings/festive-campaigns/{id}/occurrence-overrides/{cycleYear}：
 * 新增或更新某一週期年的日期覆寫（官方公告的例外，例如春節補假前移）。
 */
public class FestiveCampaignOccurrenceOverrideRequest {

	@NotNull(message = "覆寫開始日不可為空")
	private LocalDate startDate;

	@NotNull(message = "覆寫結束日不可為空")
	private LocalDate endDate;

	@Size(max = 200, message = "覆寫原因長度不可超過200字元")
	private String note;

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

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}
}
