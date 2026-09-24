package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.Product_Selection_260813.dto.response.FestivalBoostResponse;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductEvaluation;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;
import com.example.Product_Selection_260813.json.MatchedCampaignSnapshot;
import com.example.Product_Selection_260813.repository.ProductEvaluationRepository;
import com.example.Product_Selection_260813.repository.ProductRepository;

/**
 * 2026-09-24（Bug B）：getFestivalBoostDetail() 的 LIVE 分支，festivalBoost 必須由同一份即時
 * matchedCampaign 算出，finalScore＝已存 totalScore＋即時 festivalBoost；不再讀
 * product_evaluations 裡可能已過期的 festival_boost／final_score。
 * buildMatchedCampaignSnapshot() 依賴檔期推算，這裡用 spy 直接給定命中結果，只驗證組裝邏輯。
 */
@ExtendWith(MockitoExtension.class)
class ScoringServiceFestivalBoostDetailTest {

	private static final Long PRODUCT_ID = 7L;

	@Mock
	private ProductRepository productRepository;

	@Mock
	private ProductEvaluationRepository productEvaluationRepository;

	@Spy
	@InjectMocks
	private ScoringService scoringService;

	private Product product;

	@BeforeEach
	void setUp() {
		product = new Product();
		product.setId(PRODUCT_ID);
		product.setReviewStatus(ProductReviewStatus.PENDING);
		when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
	}

	private static MatchedCampaignSnapshot snapshot(String matchWeight, String urgencyFactor) {
		MatchedCampaignSnapshot snapshot = new MatchedCampaignSnapshot();
		snapshot.setMatchWeight(new BigDecimal(matchWeight));
		snapshot.setUrgencyFactor(new BigDecimal(urgencyFactor));
		return snapshot;
	}

	/** 模擬「上次計分時 urgencyFactor 只有 0.888」留下的舊值：boost 4.44、final 84.44。 */
	private static ProductEvaluation staleEvaluation(String totalScore) {
		ProductEvaluation evaluation = new ProductEvaluation();
		evaluation.setTotalScore(totalScore == null ? null : new BigDecimal(totalScore));
		evaluation.setFestivalBoost(new BigDecimal("4.44"));
		evaluation.setFinalScore(new BigDecimal("84.44"));
		return evaluation;
	}

	@Test
	void 即時命中明細與加成一致_不再讀取已過期的加成() {
		doReturn(snapshot("1.0", "1.0")).when(scoringService).buildMatchedCampaignSnapshot(product);
		when(productEvaluationRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(staleEvaluation("80.00")));

		FestivalBoostResponse response = scoringService.getFestivalBoostDetail(PRODUCT_ID);

		assertThat(response.getDataSource()).isEqualTo("LIVE");
		assertThat(response.getFestivalBoost()).isEqualByComparingTo("5.00");
		assertThat(response.getFinalScore()).isEqualByComparingTo("85.00");
	}

	@Test
	void 未命中任何檔期時加成為零_最終分數等於加權總分() {
		doReturn(null).when(scoringService).buildMatchedCampaignSnapshot(product);
		when(productEvaluationRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(staleEvaluation("80.00")));

		FestivalBoostResponse response = scoringService.getFestivalBoostDetail(PRODUCT_ID);

		assertThat(response.getMatchedCampaign()).isNull();
		assertThat(response.getFestivalBoost()).isEqualByComparingTo("0");
		assertThat(response.getFinalScore()).isEqualByComparingTo("80.00");
	}

	@Test
	void 加權總分為空時最終分數維持空值() {
		doReturn(snapshot("0.6", "0.5")).when(scoringService).buildMatchedCampaignSnapshot(product);
		when(productEvaluationRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(staleEvaluation(null)));

		FestivalBoostResponse response = scoringService.getFestivalBoostDetail(PRODUCT_ID);

		assertThat(response.getFestivalBoost()).isEqualByComparingTo("1.50");
		assertThat(response.getFinalScore()).isNull();
	}
}
