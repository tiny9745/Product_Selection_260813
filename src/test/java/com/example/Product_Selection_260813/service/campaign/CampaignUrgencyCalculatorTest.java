package com.example.Product_Selection_260813.service.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.example.Product_Selection_260813.enums.FestiveCampaignStatus;
import com.example.Product_Selection_260813.enums.FestiveCategory;
import com.example.Product_Selection_260813.enums.WeatherForecastConfidence;

/**
 * 急迫係數公式（ScoringService 節慶加成的核心）。案例對應實作指令第 7.2 節：
 * B2（ACTIVE 天氣檔期必須乘上可信度與覆蓋率）、季節型固定 0.20、以本期開始日計算剩餘天數。
 */
class CampaignUrgencyCalculatorTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 6, 4);

	@Test
	void 進行中的天氣檔期仍乘上可信度與覆蓋率_修正B2() {
		CampaignUrgencyCalculator.Result result = CampaignUrgencyCalculator.calculate(FestiveCategory.WEATHER,
				FestiveCampaignStatus.ACTIVE, 30, TODAY.minusDays(1), TODAY, WeatherForecastConfidence.MEDIUM,
				new BigDecimal("0.3000"));
		assertThat(result.timeFactor()).isEqualTo(new BigDecimal("1.00"));
		// 1.0 × 0.7 × 0.3 = 0.21（修正前會是 1.0）
		assertThat(result.urgencyFactor()).isEqualTo(new BigDecimal("0.21"));
	}

	@Test
	void 節慶準備期以本期開始日計算剩餘天數() {
		// 端午 6/19、準備 30 天、今天 6/4：剩 15 天 → 1 − 15/30 = 0.50；全國覆蓋率 1。
		CampaignUrgencyCalculator.Result result = CampaignUrgencyCalculator.calculate(FestiveCategory.FESTIVAL,
				FestiveCampaignStatus.PREPARING, 30, LocalDate.of(2026, 6, 19), TODAY, null, new BigDecimal("1.0000"));
		assertThat(result.urgencyFactor()).isEqualTo(new BigDecimal("0.50"));
		assertThat(result.weatherConfidenceFactor()).isEqualTo(null);
	}

	@Test
	void 節慶只影響南部時依占比打折() {
		CampaignUrgencyCalculator.Result result = CampaignUrgencyCalculator.calculate(FestiveCategory.FESTIVAL,
				FestiveCampaignStatus.ACTIVE, 30, TODAY, TODAY, null, new BigDecimal("0.3000"));
		assertThat(result.urgencyFactor()).isEqualTo(new BigDecimal("0.30"));
	}

	@Test
	void 季節型準備期固定零點二() {
		CampaignUrgencyCalculator.Result result = CampaignUrgencyCalculator.calculate(FestiveCategory.SEASON,
				FestiveCampaignStatus.PREPARING, 30, LocalDate.of(2026, 7, 1), TODAY, null, null);
		assertThat(result.urgencyFactor()).isEqualTo(new BigDecimal("0.20"));
	}

	@Test
	void 準備天數為零時不除以零() {
		CampaignUrgencyCalculator.Result result = CampaignUrgencyCalculator.calculate(FestiveCategory.FESTIVAL,
				FestiveCampaignStatus.PREPARING, 0, TODAY.plusDays(3), TODAY, null, null);
		assertThat(result.urgencyFactor()).isEqualTo(new BigDecimal("0.00"));
	}

	@Test
	void 非生效狀態為零() {
		CampaignUrgencyCalculator.Result result = CampaignUrgencyCalculator.calculate(FestiveCategory.FESTIVAL,
				FestiveCampaignStatus.UPCOMING, 30, TODAY.plusDays(60), TODAY, null, null);
		assertThat(result.urgencyFactor()).isEqualTo(new BigDecimal("0.00"));
	}
}
