package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.Product_Selection_260813.entity.FestiveCampaign;
import com.example.Product_Selection_260813.entity.FestiveCampaignTag;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductEvaluation;
import com.example.Product_Selection_260813.enums.FestiveCampaignStatus;
import com.example.Product_Selection_260813.enums.FestiveCampaignTagMatchTier;
import com.example.Product_Selection_260813.enums.FestiveCategory;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;
import com.example.Product_Selection_260813.repository.FestiveCampaignTagRepository;
import com.example.Product_Selection_260813.repository.ProductEvaluationRepository;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.service.campaign.ActiveCampaignWindow;
import com.example.Product_Selection_260813.service.campaign.CampaignOccurrence;
import com.example.Product_Selection_260813.service.campaign.FestiveCampaignRuleService;

/**
 * 2026-09-24（方案 2）：refreshFestivalBoosts() 只重算加成三欄，讓 product_evaluations
 * 不再停留在上次編輯時的加成（實例：急迫係數 100%、核心標籤，卻顯示 4.44）。
 */
@ExtendWith(MockitoExtension.class)
class ScoringServiceRefreshFestivalBoostsTest {

	private static final Long CAMPAIGN_ID = 9L;

	@Mock
	private ProductRepository productRepository;

	@Mock
	private ProductEvaluationRepository productEvaluationRepository;

	@Mock
	private FestiveCampaignTagRepository festiveCampaignTagRepository;

	@Mock
	private FestiveCampaignRuleService festiveCampaignRuleService;

	@InjectMocks
	private ScoringService scoringService;

	private static Product product(long id, String tags) {
		Product product = new Product();
		product.setId(id);
		product.setCampaignTags(tags);
		product.setReviewStatus(ProductReviewStatus.PENDING);
		return product;
	}

	private static ProductEvaluation evaluation(long productId, String total, String boost, String finalScore) {
		ProductEvaluation evaluation = new ProductEvaluation();
		evaluation.setProductId(productId);
		evaluation.setTotalScore(total == null ? null : new BigDecimal(total));
		evaluation.setFestivalBoost(boost == null ? null : new BigDecimal(boost));
		evaluation.setFinalScore(finalScore == null ? null : new BigDecimal(finalScore));
		evaluation.setMatchedCampaignId(boost == null ? null : CAMPAIGN_ID);
		return evaluation;
	}

	/** 進行中的端午檔期，核心標籤「粽子」：matchWeight 1.0 × urgency 1.0 × 5 = 5.00。 */
	private void givenActiveCampaign() {
		FestiveCampaign campaign = new FestiveCampaign();
		campaign.setId(CAMPAIGN_ID);
		campaign.setCampaignName("端午節");
		campaign.setCategory(FestiveCategory.FESTIVAL);
		campaign.setPreparationLeadDays(30);
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
		ActiveCampaignWindow window = new ActiveCampaignWindow(campaign,
				new CampaignOccurrence(today.getYear(), today, today.plusDays(2), false, List.of()),
				FestiveCampaignStatus.ACTIVE, BigDecimal.ONE, List.of());
		when(festiveCampaignRuleService.findLiveWindows(any())).thenReturn(List.of(window));

		FestiveCampaignTag tag = new FestiveCampaignTag();
		tag.setCampaignId(CAMPAIGN_ID);
		tag.setTag("粽子");
		tag.setMatchTier(FestiveCampaignTagMatchTier.CORE);
		when(festiveCampaignTagRepository.findByCampaignIdIn(anyList())).thenReturn(List.of(tag));
	}

	@Test
	@SuppressWarnings("unchecked")
	void 過期加成改為今天的值_未變動與無分數的列不寫回() {
		givenActiveCampaign();
		ProductEvaluation stale = evaluation(1L, "80.00", "4.44", "84.44");
		ProductEvaluation noTags = evaluation(2L, "70.00", "3.00", "73.00");
		ProductEvaluation neverScored = evaluation(3L, null, null, null);
		ProductEvaluation alreadyFresh = evaluation(4L, "60.00", "5.00", "65.00");
		when(productRepository.findByReviewStatusNot(ProductReviewStatus.APPROVED)).thenReturn(
				List.of(product(1L, "粽子"), product(2L, null), product(3L, "粽子"), product(4L, "粽子")));
		when(productEvaluationRepository.findByProductIdIn(anyCollection()))
				.thenReturn(new ArrayList<>(List.of(stale, noTags, neverScored, alreadyFresh)));

		int updated = scoringService.refreshFestivalBoosts();

		assertThat(updated).isEqualTo(2);
		assertThat(stale.getFestivalBoost()).isEqualByComparingTo("5.00");
		assertThat(stale.getFinalScore()).isEqualByComparingTo("85.00");
		assertThat(noTags.getFestivalBoost()).isEqualByComparingTo("0");
		assertThat(noTags.getMatchedCampaignId()).isNull();
		assertThat(noTags.getFinalScore()).isEqualByComparingTo("70.00");
		assertThat(neverScored.getFinalScore()).isNull();

		ArgumentCaptor<List<ProductEvaluation>> saved = ArgumentCaptor.forClass(List.class);
		verify(productEvaluationRepository).saveAll(saved.capture());
		assertThat(saved.getValue()).containsExactly(stale, noTags);
		// 整批只查一次候選檔期
		verify(festiveCampaignRuleService, times(1)).findLiveWindows(any());
	}
}
