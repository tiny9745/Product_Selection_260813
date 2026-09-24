package com.example.Product_Selection_260813.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 2026-09-24（Bug C）：建立商品即第 1 次送審，submissionCount 預設必須是 1。
 * ProductService.createProduct() 是唯一 new Product() 的地方且不另外設定，Entity 預設值即資料源頭。
 */
class ProductSubmissionCountTest {

	@Test
	void 新建商品預設為第一次送審() {
		assertThat(new Product().getSubmissionCount()).isEqualTo(1);
	}
}
