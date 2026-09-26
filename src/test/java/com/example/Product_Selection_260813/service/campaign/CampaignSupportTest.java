package com.example.Product_Selection_260813.service.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.Product_Selection_260813.enums.CampaignDateRuleType;
import com.example.Product_Selection_260813.enums.FestiveCategory;
import com.example.Product_Selection_260813.enums.ObservedHolidayRule;

/** 地域覆蓋率與規則描述（兩者都是純函式）。 */
class CampaignSupportTest {

	private static final Map<String, BigDecimal> WEIGHTS = Map.of("NORTH", new BigDecimal("40.00"), "CENTRAL",
			new BigDecimal("25.00"), "SOUTH", new BigDecimal("30.00"), "EAST", new BigDecimal("5.00"));

	@Test
	void 未選區域為全國() {
		assertThat(RegionCoverageCalculator.coverageOf(List.of(), WEIGHTS, region -> {
		})).isEqualTo(new BigDecimal("1.0000"));
	}

	@Test
	void 只選南部取南部占比() {
		assertThat(RegionCoverageCalculator.coverageOf(List.of("SOUTH"), WEIGHTS, region -> {
		})).isEqualTo(new BigDecimal("0.3000"));
	}

	@Test
	void 缺資料的區域視為零並回報() {
		List<String> missing = new ArrayList<>();
		assertThat(RegionCoverageCalculator.coverageOf(List.of("SOUTH", "WEST"), WEIGHTS, missing::add))
				.isEqualTo(new BigDecimal("0.3000"));
		assertThat(missing).containsExactly("WEST");
	}

	@Test
	void 規則描述() {
		assertThat(CampaignRuleDescriber.describe(new CampaignDateRule(FestiveCategory.FESTIVAL,
				CampaignDateRuleType.LUNAR_DATE, 5, 5, null, null, null, 0, 3, null, null, ObservedHolidayRule.NONE,
				false))).isEqualTo("每年農曆 5 月 5 日起 3 天");
		assertThat(CampaignRuleDescriber.describe(new CampaignDateRule(FestiveCategory.FESTIVAL,
				CampaignDateRuleType.NTH_WEEKDAY, 5, null, 2, 7, null, 0, 1, null, null, ObservedHolidayRule.NONE,
				false))).isEqualTo("每年 5 月第 2 個星期日");
		assertThat(CampaignRuleDescriber.describe(new CampaignDateRule(FestiveCategory.SEASON,
				CampaignDateRuleType.FIXED_DATE, 12, 1, null, null, null, 0, null, 2, 31, ObservedHolidayRule.NONE,
				false))).isEqualTo("每年 12/1 至隔年 2 月底");
		assertThat(CampaignRuleDescriber.describe(new CampaignDateRule(FestiveCategory.FESTIVAL,
				CampaignDateRuleType.LUNAR_DATE, 1, 1, null, null, null, -1, 1, null, null, ObservedHolidayRule.NONE,
				false))).isEqualTo("每年農曆 1 月 1 日前 1 天");
		assertThat(CampaignRuleDescriber.describe(new CampaignDateRule(FestiveCategory.FESTIVAL,
				CampaignDateRuleType.FIXED_DATE, 2, 28, null, null, null, 0, 1, null, null,
				ObservedHolidayRule.TW_STATUTORY, true))).isEqualTo("每年 2/28，依國定假日補假並併入連假");
	}
}
