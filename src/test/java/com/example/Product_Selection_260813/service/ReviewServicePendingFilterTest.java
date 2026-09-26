package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.enums.ProductCandidateStatus;
import com.example.Product_Selection_260813.enums.ProductItemStatus;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;
import com.example.Product_Selection_260813.repository.ProductRepository;

/**
 * 2026-09-26 修正（待審清單分頁錯亂）：搜尋／分類／送審日期改由後端篩選。
 * 驗證條件換算：狀態固定、關鍵字去空白、日期閉區間換成 [起日 00:00, 迄日隔天 00:00)、起日晚於迄日 400。
 */
@ExtendWith(MockitoExtension.class)
class ReviewServicePendingFilterTest {

	@Mock
	private ProductRepository productRepository;

	@Mock
	private ScoringService scoringService;

	@InjectMocks
	private ReviewService reviewService;

	@Test
	void 條件換算後交給資料庫篩選_狀態固定為未審核使用中正式候選() {
		Pageable pageable = PageRequest.of(0, 20);
		when(productRepository.searchPending(any(), any(), any(), any(), any(), any(), any(), any()))
				.thenReturn(new PageImpl<Product>(List.of()));
		when(scoringService.getCurrentEvaluations(any())).thenReturn(Map.of());

		reviewService.getPendingReviews("  禮盒 ", 5L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20), pageable);

		verify(productRepository).searchPending(eq(ProductReviewStatus.PENDING), eq(ProductItemStatus.ACTIVE),
				eq(ProductCandidateStatus.CANDIDATE), eq("禮盒"), eq(5L), eq(LocalDateTime.of(2026, 9, 1, 0, 0)),
				eq(LocalDateTime.of(2026, 9, 21, 0, 0)), eq(pageable));
	}

	@Test
	void 空白關鍵字與未帶日期時不篩選() {
		Pageable pageable = PageRequest.of(1, 20);
		when(productRepository.searchPending(any(), any(), any(), any(), any(), any(), any(), any()))
				.thenReturn(new PageImpl<Product>(List.of()));
		when(scoringService.getCurrentEvaluations(any())).thenReturn(Map.of());

		reviewService.getPendingReviews("   ", null, null, null, pageable);

		verify(productRepository).searchPending(eq(ProductReviewStatus.PENDING), eq(ProductItemStatus.ACTIVE),
				eq(ProductCandidateStatus.CANDIDATE), isNull(), isNull(), isNull(), isNull(), eq(pageable));
	}

	@Test
	void 送審日期起日晚於迄日回400且不查詢() {
		assertThatThrownBy(() -> reviewService.getPendingReviews(null, null, LocalDate.of(2026, 9, 20),
				LocalDate.of(2026, 9, 1), PageRequest.of(0, 20))).isInstanceOf(IllegalArgumentException.class);
		verify(productRepository, never()).searchPending(any(), any(), any(), any(), any(), any(), any(), any());
	}
}
