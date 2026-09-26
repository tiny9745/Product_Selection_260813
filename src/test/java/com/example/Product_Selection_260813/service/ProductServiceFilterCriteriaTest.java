package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.example.Product_Selection_260813.dto.request.ProductFilterRequest;
import com.example.Product_Selection_260813.enums.ProductCandidateStatus;
import com.example.Product_Selection_260813.repository.ProductSearchCriteria;

/** 品項清單／CSV 匯出共用的篩選條件換算（ProductService.toCriteria()，純函式，不需要任何依賴）。 */
class ProductServiceFilterCriteriaTest {

	private final ProductService productService = new ProductService();

	@Test
	void 未帶任何條件時只套用候選狀態預設值() {
		ProductSearchCriteria criteria = productService.toCriteria(null);

		assertThat(criteria.candidateStatus()).isEqualTo(ProductCandidateStatus.CANDIDATE);
		assertThat(criteria.submittedBy()).isNull();
		assertThat(criteria.withoutSubmissionBatch()).isNull();
		assertThat(criteria.reviewedFrom()).isNull();
		assertThat(criteria.neverExported()).isNull();
	}

	@Test
	void 送審批次與審核日期換算成半開區間() {
		ProductFilterRequest filter = new ProductFilterRequest();
		filter.setSubmissionBatch("2026-09-25_3");
		filter.setReviewedFrom(LocalDate.of(2026, 9, 1));
		filter.setReviewedTo(LocalDate.of(2026, 9, 30));
		filter.setNeverExported(true);

		ProductSearchCriteria criteria = productService.toCriteria(filter);

		assertThat(criteria.submittedBy()).isEqualTo(3L);
		assertThat(criteria.submittedFrom()).isEqualTo(LocalDateTime.of(2026, 9, 25, 0, 0));
		assertThat(criteria.submittedToExclusive()).isEqualTo(LocalDateTime.of(2026, 9, 26, 0, 0));
		assertThat(criteria.reviewedFrom()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
		assertThat(criteria.reviewedToExclusive()).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0));
		assertThat(criteria.neverExported()).isTrue();
	}

	@Test
	void 選無批次資料時只篩submittedAt為空() {
		ProductFilterRequest filter = new ProductFilterRequest();
		filter.setSubmissionBatch("NONE");

		ProductSearchCriteria criteria = productService.toCriteria(filter);

		assertThat(criteria.withoutSubmissionBatch()).isTrue();
		assertThat(criteria.submittedBy()).isNull();
		assertThat(criteria.submittedFrom()).isNull();
	}

	@Test
	void neverExported為false等於不篩() {
		ProductFilterRequest filter = new ProductFilterRequest();
		filter.setNeverExported(false);
		assertThat(productService.toCriteria(filter).neverExported()).isNull();
	}

	@Test
	void 審核日期起日晚於迄日回400() {
		ProductFilterRequest filter = new ProductFilterRequest();
		filter.setReviewedFrom(LocalDate.of(2026, 10, 1));
		filter.setReviewedTo(LocalDate.of(2026, 9, 1));
		assertThatThrownBy(() -> productService.toCriteria(filter)).isInstanceOf(IllegalArgumentException.class);
	}
}
