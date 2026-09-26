package com.example.Product_Selection_260813.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.example.Product_Selection_260813.entity.GroupBuyRecord;
import com.example.Product_Selection_260813.enums.UserRole;

/** 2026-09 歷史銷售紀錄職責分層：成本價與毛利率只揭露給管理層。 */
class GroupBuyRecordResponseTest {

	private GroupBuyRecord record(String sale, String cost) {
		GroupBuyRecord record = new GroupBuyRecord();
		record.setSalePriceAtTime(sale == null ? null : new BigDecimal(sale));
		record.setCostPriceAtTime(cost == null ? null : new BigDecimal(cost));
		return record;
	}

	@Test
	void purchaser_看不到成本價與毛利率_但售價照常回傳() {
		GroupBuyRecordResponse response = GroupBuyRecordResponse.from(record("400", "300"), UserRole.PURCHASER);

		assertThat(response.getCostPriceAtTime()).isNull();
		assertThat(response.getMarginRate()).isNull();
		assertThat(response.getSalePriceAtTime()).isEqualTo(new BigDecimal("400"));
	}

	@Test
	void manager_取得成本價與後端算好的毛利率() {
		GroupBuyRecordResponse response = GroupBuyRecordResponse.from(record("400", "300"), UserRole.MANAGER);

		assertThat(response.getCostPriceAtTime()).isEqualTo(new BigDecimal("300"));
		assertThat(response.getMarginRate()).isEqualTo(new BigDecimal("25.00"));
	}

	@Test
	void 角色未知時比照操作層_不揭露成本() {
		GroupBuyRecordResponse response = GroupBuyRecordResponse.from(record("400", "300"), null);

		assertThat(response.getCostPriceAtTime()).isNull();
		assertThat(response.getMarginRate()).isNull();
	}

	@Test
	void 售價為零或資料缺漏時毛利率為null而不是0() {
		assertThat(GroupBuyRecordResponse.calculateMarginRate(BigDecimal.ZERO, new BigDecimal("10"))).isNull();
		assertThat(GroupBuyRecordResponse.calculateMarginRate(null, new BigDecimal("10"))).isNull();
		assertThat(GroupBuyRecordResponse.calculateMarginRate(new BigDecimal("10"), null)).isNull();
		assertThat(GroupBuyRecordResponse.calculateMarginRate(new BigDecimal("3"), new BigDecimal("2")))
				.isEqualTo(new BigDecimal("33.33"));
	}
}
