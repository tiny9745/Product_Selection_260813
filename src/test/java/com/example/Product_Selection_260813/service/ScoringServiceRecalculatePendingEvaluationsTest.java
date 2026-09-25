package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;
import com.example.Product_Selection_260813.repository.ProductRepository;

/**
 * 2026-09-24：評分設定變更後全量重算尚未核准商品；單筆失敗不擋其他商品。
 * calculateEvaluation() 本身的計分邏輯不在此測試範圍，以 spy 取代。
 */
@ExtendWith(MockitoExtension.class)
class ScoringServiceRecalculatePendingEvaluationsTest {

	@Mock
	private ProductRepository productRepository;

	@Spy
	@InjectMocks
	private ScoringService scoringService;

	private static Product product(long id) {
		Product product = new Product();
		product.setId(id);
		return product;
	}

	@Test
	void 依目前模式重算所有未核准商品_單筆失敗不影響其他商品() {
		when(productRepository.findByReviewStatusNot(ProductReviewStatus.APPROVED))
				.thenReturn(List.of(product(1L), product(2L), product(3L)));
		doNothing().when(scoringService).calculateEvaluation(1L, null);
		doThrow(new IllegalArgumentException("資料異常")).when(scoringService).calculateEvaluation(2L, null);
		doNothing().when(scoringService).calculateEvaluation(3L, null);

		int recalculated = scoringService.recalculatePendingEvaluations();

		assertThat(recalculated).isEqualTo(2);
		verify(scoringService).calculateEvaluation(3L, null);
	}
}
