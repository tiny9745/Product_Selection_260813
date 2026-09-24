package com.example.Product_Selection_260813.service.campaign;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.example.Product_Selection_260813.enums.FestiveCampaignStatus;
import com.example.Product_Selection_260813.enums.FestiveCategory;
import com.example.Product_Selection_260813.enums.WeatherForecastConfidence;

/**
 * 節慶加成的急迫係數（Urgency Factor），2026-09-24 統一公式並修正 B2。
 *
 * <pre>
 * timeFactor =
 *   ACTIVE                        → 1.00
 *   PREPARING＋FESTIVAL／WEATHER  → clamp(1 − 剩餘天數 / 準備天數, 0, 1)
 *                                   剩餘天數＝本期開始日 − 今天（不小於 0）；準備天數 ≤ 0 時以 1 計
 *   PREPARING＋SEASON             → 0.20
 *   其他                           → 0
 * confidence = WEATHER ? weatherConfidence（null 視為 LOW） : 1
 * coverage   = WEATHER ? 同步凍結的 region_coverage_ratio（null 視為 0）
 *                       : 依受影響區域當下計算（null 視為 1＝全國）
 * urgency    = timeFactor × confidence × coverage，四捨五入到小數 2 位
 * </pre>
 *
 * ⚠️ B2：原本「ACTIVE 直接回傳 1.0」寫在最前面，天氣檔期一進入窗口就跳過可信度與覆蓋率兩個乘數、
 * 拿到滿額加成。這裡刻意不留任何提前 return，三個乘數一律相乘。
 * 抽成純函式是為了能在沒有 Spring 的情況下把公式與邊界釘死在單元測試裡。
 */
public final class CampaignUrgencyCalculator {

	/** 季節型在準備期的固定時間係數（原 ScoringService.SEASON_PREPARING_URGENCY_FACTOR，數值不變）。 */
	public static final BigDecimal SEASON_PREPARING_TIME_FACTOR = new BigDecimal("0.20");

	/** @param weatherConfidenceFactor 僅 WEATHER 有值，其餘為 null */
	public record Result(BigDecimal timeFactor, BigDecimal weatherConfidenceFactor, BigDecimal regionCoverage,
			BigDecimal urgencyFactor) {
	}

	private CampaignUrgencyCalculator() {
	}

	public static Result calculate(FestiveCategory category, FestiveCampaignStatus status,
			Integer preparationLeadDays, LocalDate occurrenceStart, LocalDate today,
			WeatherForecastConfidence weatherConfidence, BigDecimal regionCoverage) {
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

		BigDecimal confidenceFactor = null;
		BigDecimal coverage;
		if (category == FestiveCategory.WEATHER) {
			confidenceFactor = (weatherConfidence != null ? weatherConfidence : WeatherForecastConfidence.LOW)
					.getConfidenceFactor();
			coverage = regionCoverage != null ? regionCoverage : BigDecimal.ZERO;
		} else {
			coverage = regionCoverage != null ? regionCoverage : BigDecimal.ONE;
		}

		BigDecimal urgency = timeFactor.multiply(confidenceFactor != null ? confidenceFactor : BigDecimal.ONE)
				.multiply(coverage).setScale(2, RoundingMode.HALF_UP);
		return new Result(timeFactor.setScale(2, RoundingMode.HALF_UP), confidenceFactor, coverage, urgency);
	}
}
