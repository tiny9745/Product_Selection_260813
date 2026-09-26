package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** CSV 格式細節：RFC 4180 跳脫、公式注入防護、毛利率口徑（純函式）。 */
class ProductExportCsvFormatTest {

	@Test
	void 文字欄位跳脫與毛利率計算() {
		assertThat(ProductExportService.escape("a,b")).isEqualTo("\"a,b\"");
		assertThat(ProductExportService.escape("說\"明\"")).isEqualTo("\"說\"\"明\"\"\"");
		assertThat(ProductExportService.text("-5")).isEqualTo("'-5");
		assertThat(ProductExportService.text("@sum")).isEqualTo("'@sum");
		assertThat(ProductExportService.text("一般文字")).isEqualTo("一般文字");
		assertThat(ProductExportService.marginRate(new BigDecimal("3"), new BigDecimal("2"))).isEqualTo(new BigDecimal("33.3"));
		assertThat(ProductExportService.marginRate(BigDecimal.ZERO, BigDecimal.ONE)).isNull();
	}
}
