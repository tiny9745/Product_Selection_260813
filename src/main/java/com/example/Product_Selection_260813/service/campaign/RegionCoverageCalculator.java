package com.example.Product_Selection_260813.service.campaign;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 地域覆蓋率的純計算（D9）：選取區域的業務占比加總 ÷ 100，上限 1，四捨五入到小數 4 位。
 * 空集合＝全國性＝1.0000。缺資料的區域視為 0，並透過 onMissingRegion 回報（由呼叫端記 log）。
 */
public final class RegionCoverageCalculator {

	public static final BigDecimal NATIONWIDE = new BigDecimal("1.0000");
	private static final BigDecimal HUNDRED = new BigDecimal("100");

	private RegionCoverageCalculator() {
	}

	public static BigDecimal coverageOf(Collection<String> regions, Map<String, BigDecimal> weightPercentages,
			Consumer<String> onMissingRegion) {
		if (regions == null || regions.isEmpty()) {
			return NATIONWIDE;
		}
		BigDecimal sum = BigDecimal.ZERO;
		for (String region : new LinkedHashSet<>(regions)) {
			BigDecimal weight = weightPercentages.get(region);
			if (weight == null) {
				onMissingRegion.accept(region);
				continue;
			}
			sum = sum.add(weight);
		}
		BigDecimal ratio = sum.divide(HUNDRED, 4, RoundingMode.HALF_UP);
		return ratio.compareTo(BigDecimal.ONE) > 0 ? NATIONWIDE : ratio;
	}
}
