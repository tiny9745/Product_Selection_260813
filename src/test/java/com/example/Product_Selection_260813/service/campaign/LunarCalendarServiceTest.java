package com.example.Product_Selection_260813.service.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * 農曆對照表抽查。預期值來源：行政院人事行政總處 115 年（2026）、116 年（2027）
 * 政府行政機關辦公日曆表（春節、除夕、端午、中秋）；閏月與 2030 年春節以天文演算的
 * 朔日（UTC+8）核對。完整 2000–2099 逐日比對另見交付說明。
 */
class LunarCalendarServiceTest {

	private final LunarCalendarService service = new LunarCalendarService();

	@Test
	void 官方公告的2026年節日() {
		assertThat(service.toGregorian(2026, 1, 1)).isEqualTo(LocalDate.of(2026, 2, 17));
		assertThat(service.toGregorian(2026, 5, 5)).isEqualTo(LocalDate.of(2026, 6, 19));
		assertThat(service.toGregorian(2026, 8, 15)).isEqualTo(LocalDate.of(2026, 9, 25));
	}

	@Test
	void 二零二七年春節為2月6日_ICU4J會算成2月7日() {
		assertThat(service.toGregorian(2027, 1, 1)).isEqualTo(LocalDate.of(2027, 2, 6));
		assertThat(service.toGregorian(2027, 5, 5)).isEqualTo(LocalDate.of(2027, 6, 9));
		assertThat(service.toGregorian(2027, 8, 15)).isEqualTo(LocalDate.of(2027, 9, 15));
	}

	@Test
	void 二零三零年春節為2月3日_朔在午夜後七分鐘() {
		assertThat(service.toGregorian(2030, 1, 1)).isEqualTo(LocalDate.of(2030, 2, 3));
	}

	@Test
	void 閏月年度只取非閏月() {
		// 2028 年有閏五月：端午落在（非閏）五月初五 5/28，不是閏五月初五 6/27。
		assertThat(service.toGregorian(2028, 5, 5)).isEqualTo(LocalDate.of(2028, 5, 28));
		// 閏月之後的月份索引要多跳一格：2028 年中秋。
		assertThat(service.toGregorian(2028, 8, 15)).isEqualTo(LocalDate.of(2028, 10, 3));
	}

	@Test
	void 日數超過該月天數時取月末() {
		// 2026 農曆十二月只有 29 天：12/30 取月末 → 2027-02-05（官方公告的除夕）。
		assertThat(service.toGregorian(2026, 12, 30)).isEqualTo(LocalDate.of(2027, 2, 5));
	}

	@Test
	void 超出對照表範圍丟例外() {
		assertThatThrownBy(() -> service.toGregorian(2100, 1, 1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.toGregorian(1999, 1, 1)).isInstanceOf(IllegalArgumentException.class);
	}
}
