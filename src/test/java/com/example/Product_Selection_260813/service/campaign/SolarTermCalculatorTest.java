package com.example.Product_Selection_260813.service.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.Product_Selection_260813.enums.SolarTerm;

class SolarTermCalculatorTest {

	private final SolarTermCalculator calculator = new SolarTermCalculator();

	@Test
	void 清明() {
		assertThat(calculator.dateOf(SolarTerm.QINGMING, 2026)).isEqualTo(LocalDate.of(2026, 4, 5));
		assertThat(calculator.dateOf(SolarTerm.QINGMING, 2027)).isEqualTo(LocalDate.of(2027, 4, 5));
		assertThat(calculator.dateOf(SolarTerm.QINGMING, 2028)).isEqualTo(LocalDate.of(2028, 4, 4));
	}

	@Test
	void 冬至() {
		assertThat(calculator.dateOf(SolarTerm.DONGZHI, 2024)).isEqualTo(LocalDate.of(2024, 12, 21));
		assertThat(calculator.dateOf(SolarTerm.DONGZHI, 2025)).isEqualTo(LocalDate.of(2025, 12, 21));
		assertThat(calculator.dateOf(SolarTerm.DONGZHI, 2026)).isEqualTo(LocalDate.of(2026, 12, 22));
	}

	/**
	 * 交叉檢查：壽星公式 日 = floor(Y×0.2422＋C) − floor(Y/4)（Y＝年份%100，清明 C=4.81、冬至 C=21.94）。
	 * 2001–2099 間只有 2021、2054 年冬至與天文演算差一天（公式 12/22，實際 12/21），
	 * 這正是改用內建表的原因；其餘年份必須完全一致。
	 */
	@Test
	void 內建表與壽星公式只在已知兩年不同() {
		List<Integer> mismatchYears = new ArrayList<>();
		for (int year = 2001; year <= 2099; year++) {
			int y = year % 100;
			int qingming = (int) Math.floor(y * 0.2422 + 4.81) - y / 4;
			int dongzhi = (int) Math.floor(y * 0.2422 + 21.94) - y / 4;
			if (!calculator.dateOf(SolarTerm.QINGMING, year).equals(LocalDate.of(year, 4, qingming))
					|| !calculator.dateOf(SolarTerm.DONGZHI, year).equals(LocalDate.of(year, 12, dongzhi))) {
				mismatchYears.add(year);
			}
		}
		assertThat(mismatchYears).containsExactly(2021, 2054);
	}

	@Test
	void 超出範圍丟例外() {
		assertThatThrownBy(() -> calculator.dateOf(SolarTerm.QINGMING, 2100))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
