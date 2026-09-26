package com.example.Product_Selection_260813.service.campaign;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.example.Product_Selection_260813.enums.FestiveCampaignStatus;
import com.example.Product_Selection_260813.enums.FestiveCategory;

/**
 * 節慶加成的急迫係數（Urgency Factor），2026-09-24 統一公式並修正 B2。
 *
 * <pre>
 * timeFactor =
 *   ACTIVE                        → 1.00
 *   PREPARING＋FESTIVAL           → clamp(1 − 剩餘天數 / 準備天數, 0, 1)
 *                                   剩餘天數＝本期開始日 − 今天（不小於 0）；準備天數 ≤ 0 時以 1 計
 *   PREPARING＋SEASON             → 0.20
 *   其他                           → 0
 * coverage   = 依受影響區域當下計算（null 視為 1＝全國）
 * urgency    = timeFactor × coverage，四捨五入到小數 2 位
 * </pre>
 *
 * V26（2026-09-25）：天氣檔期移除，原本只給天氣用的「預報可信度」乘數一併移除；天氣改為
 * 獨立的天氣加成（WeatherBoostService），不經過這個公式。
 * ⚠️ B2：刻意不留任何提前 return，乘數一律相乘（ACTIVE 也要乘上區域覆蓋率）。
 * 抽成純函式是為了能在沒有 Spring 的情況下把公式與邊界釘死在單元測試裡。
 */
public final class CampaignUrgencyCalculator {

	/** 季節型在準備期的固定時間係數（原 ScoringService.SEASON_PREPARING_URGENCY_FACTOR，數值不變）。 */
	public static final BigDecimal SEASON_PREPARING_TIME_FACTOR = new BigDecimal("0.20");

	public record Result(BigDecimal timeFactor, BigDecimal regionCoverage, BigDecimal urgencyFactor) {
	}

	private CampaignUrgencyCalculator() {
	}

	public static Result calculate(FestiveCategory category, FestiveCampaignStatus status,
			Integer preparationLeadDays, LocalDate occurrenceStart, LocalDate today, BigDecimal regionCoverage) {
		BigDecimal timeFactor;
		if (status == FestiveCampaignStatus.ACTIVE) {
			timeFactor = BigDecimal.ONE;
		} else if (status != FestiveCampaignStatus.PREPARING) {
			timeFactor = BigDecimal.ZERO;
		} else if (category == FestiveCategory.SEASON) {
			timeFactor = SEASON_PREPARING_TIME_FACTOR;
		} else {
			long leadDays = preparationLeadDays != null && preparationLeadDays > 0 ? preparationLeadDays : 1;
			long remainingDays = Math.max(ChronoUnit.DAYS.between(today, occurrenceStart), 0);
			BigDecimal ratio = BigDecimal.valueOf(remainingDays).divide(BigDecimal.valueOf(leadDays), 4,
					RoundingMode.HALF_UP);
			timeFactor = BigDecimal.ONE.subtract(ratio).max(BigDecimal.ZERO).min(BigDecimal.ONE);
		}

		BigDecimal coverage = regionCoverage != null ? regionCoverage : BigDecimal.ONE;
		BigDecimal urgency = timeFactor.multiply(coverage).setScale(2, RoundingMode.HALF_UP);
		return new Result(timeFactor.setScale(2, RoundingMode.HALF_UP), coverage, urgency);
	}
}
