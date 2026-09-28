package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.Product_Selection_260813.dto.request.ReviewSubmitRequest;
import com.example.Product_Selection_260813.dto.response.ReviewDetailResponse;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductEvaluation;
import com.example.Product_Selection_260813.entity.ReviewRecord;
import com.example.Product_Selection_260813.enums.ProductCandidateStatus;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;
import com.example.Product_Selection_260813.enums.ReviewRecordReviewStatus;
import com.example.Product_Selection_260813.json.WeatherBoostSnapshot;
import com.example.Product_Selection_260813.json.WeightSnapshot;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.ReviewRecordRepository;
import com.example.Product_Selection_260813.repository.ReviewRiskRepository;
import com.example.Product_Selection_260813.repository.RiskOptionRepository;
import com.example.Product_Selection_260813.service.gate.GateEvaluationService;
import com.example.Product_Selection_260813.service.gate.GateResult;
import com.example.Product_Selection_260813.service.resolver.MoqResolver;
import com.example.Product_Selection_260813.service.resolver.ResolvedValue;
import com.example.Product_Selection_260813.service.scoring.ProductFactorScorer;

/**
 * 2026-09-29 修正（審核快照漏存欄位）：審核送出與審核頁都必須使用含商品情境的
 * buildWeightSnapshot(modeId, product)，快照才會帶到 scoreBands／historySampleSize*／
 * historyIncludesSimulated。不帶商品的版本這四個欄位一律是 null。
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceWeightSnapshotTest {

	private static final Long PRODUCT_ID = 1L;
	private static final Long MODE_ID = 2L;

	@Mock
	private ProductRepository productRepository;
	@Mock
	private ReviewRecordRepository reviewRecordRepository;
	@Mock
	private ReviewRiskRepository reviewRiskRepository;
	@Mock
	private RiskOptionRepository riskOptionRepository;
	@Mock
	private AppUserRepository appUserRepository;
	@Mock
	private ScoringService scoringService;
	@Mock
	private AiSelectionService aiSelectionService;
	@Mock
	private GateEvaluationService gateEvaluationService;
	@Mock
	private MoqResolver moqResolver;
	@Mock
	private ProductFactorScorer productFactorScorer;

	@InjectMocks
	private ReviewService reviewService;

	@Test
	void 送出審核時權重快照使用含商品情境的版本_目標區間與歷史樣本數會被凍結() {
		Product product = pendingProduct();
		WeightSnapshot productAwareSnapshot = snapshotWithBands();
		givenCommonReviewContext(product);
		when(scoringService.buildWeightSnapshot(eq(MODE_ID), same(product))).thenReturn(productAwareSnapshot);
		when(moqResolver.resolve(product)).thenReturn(ResolvedValue.ofProduct(30));
		when(scoringService.buildWeatherBoostSnapshot(product)).thenReturn(new WeatherBoostSnapshot());
		when(appUserRepository.findByUsername("manager01")).thenReturn(Optional.of(manager()));
		when(productRepository.conditionalUpdateReviewStatus(PRODUCT_ID, ProductReviewStatus.PENDING,
				ProductReviewStatus.APPROVED)).thenReturn(1);
		when(reviewRecordRepository.save(any(ReviewRecord.class))).thenAnswer(invocation -> {
			ReviewRecord record = invocation.getArgument(0);
			record.setId(10L);
			return record;
		});

		ReviewSubmitRequest request = new ReviewSubmitRequest();
		request.setProductId(PRODUCT_ID);
		request.setReviewStatus(ReviewRecordReviewStatus.APPROVED);
		reviewService.submitReview(request, "manager01");

		ArgumentCaptor<ReviewRecord> saved = ArgumentCaptor.forClass(ReviewRecord.class);
		verify(reviewRecordRepository).save(saved.capture());
		WeightSnapshot frozen = saved.getValue().getWeightSnapshot();
		assertThat(frozen).isSameAs(productAwareSnapshot);
		assertThat(frozen.getScoreBands()).containsKeys("MARGIN_RATE", "DISCOUNT_DEPTH");
		assertThat(frozen.getHistorySampleSizeCategory()).isEqualTo(18L);
		assertThat(frozen.getHistoryIncludesSimulated()).isTrue();
		verify(scoringService, never()).buildWeightSnapshot(any(Long.class));
	}

	@Test
	void 審核頁顯示的權重與送出後凍結的是同一個版本() {
		Product product = pendingProduct();
		WeightSnapshot productAwareSnapshot = snapshotWithBands();
		givenCommonReviewContext(product);
		when(scoringService.buildWeightSnapshot(eq(MODE_ID), same(product))).thenReturn(productAwareSnapshot);

		ReviewDetailResponse detail = reviewService.getReviewDetail(PRODUCT_ID);

		assertThat(detail.getWeights()).isSameAs(productAwareSnapshot);
		verify(scoringService, never()).buildWeightSnapshot(any(Long.class));
	}

	// ---------------------------------------------------------------------

	private void givenCommonReviewContext(Product product) {
		ProductEvaluation evaluation = new ProductEvaluation();
		evaluation.setProductId(PRODUCT_ID);
		evaluation.setEvaluationModeId(MODE_ID);
		evaluation.setDataCompleteness(new BigDecimal("87.50"));
		evaluation.setTotalScore(new BigDecimal("61.20"));
		when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
		when(scoringService.getCurrentEvaluation(PRODUCT_ID)).thenReturn(Optional.of(evaluation));
		when(gateEvaluationService.evaluate(same(product), any(), any()))
				.thenReturn(GateResult.Summary.of(List.of()));
	}

	private static Product pendingProduct() {
		Product product = new Product();
		product.setId(PRODUCT_ID);
		product.setName("測試商品");
		product.setProductTypeId(2L);
		product.setReviewStatus(ProductReviewStatus.PENDING);
		product.setCandidateStatus(ProductCandidateStatus.CANDIDATE);
		product.setSubmissionCount(1);
		return product;
	}

	private static AppUser manager() {
		AppUser user = new AppUser();
		user.setId(1L);
		return user;
	}

	private static WeightSnapshot snapshotWithBands() {
		WeightSnapshot snapshot = new WeightSnapshot();
		snapshot.setModeCode("VOLUME");
		snapshot.setScoreBands(Map.of(
				"MARGIN_RATE", List.of(new BigDecimal("0.0000"), new BigDecimal("0.4000")),
				"DISCOUNT_DEPTH", List.of(new BigDecimal("0.0000"), new BigDecimal("0.5000"))));
		snapshot.setHistorySampleSizeCategory(18L);
		snapshot.setHistorySampleSizeProduct(0L);
		snapshot.setHistoryIncludesSimulated(true);
		return snapshot;
	}
}
