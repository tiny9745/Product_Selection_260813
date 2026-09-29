package com.example.Product_Selection_260813.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

/** 排程執行紀錄分頁參數夾限（2026-09-29）。 */
class RunHistoryPagingTest {

	@Test
	void 一般參數原樣使用() {
		Pageable pageable = RunHistoryPaging.of(2, 10);
		assertThat(pageable.getPageNumber()).isEqualTo(2);
		assertThat(pageable.getPageSize()).isEqualTo(10);
	}

	@Test
	void 負數頁碼視為第一頁_筆數夾在1到50之間() {
		assertThat(RunHistoryPaging.of(-3, 10).getPageNumber()).isZero();
		assertThat(RunHistoryPaging.of(0, 0).getPageSize()).isEqualTo(1);
		assertThat(RunHistoryPaging.of(0, 100_000).getPageSize()).isEqualTo(RunHistoryPaging.MAX_SIZE);
	}
}
