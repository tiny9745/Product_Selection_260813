package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.example.Product_Selection_260813.dto.request.EvaluationFactorUpdateRequest;
import com.example.Product_Selection_260813.dto.request.ProductTypeScoreBandCreateRequest;
import com.example.Product_Selection_260813.dto.request.ProductTypeScoreBandUpdateRequest;
import com.example.Product_Selection_260813.dto.request.RiskOptionUpdateRequest;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.entity.EvaluationFactor;
import com.example.Product_Selection_260813.entity.EvaluationMode;
import com.example.Product_Selection_260813.entity.FactorDefinition;
import com.example.Product_Selection_260813.entity.ProductType;
import com.example.Product_Selection_260813.entity.ProductTypeScoreBand;
import com.example.Product_Selection_260813.entity.RiskOption;
import com.example.Product_Selection_260813.enums.FactorStrategyCode;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.repository.EvaluationFactorRepository;
import com.example.Product_Selection_260813.repository.EvaluationModeRepository;
import com.example.Product_Selection_260813.repository.FactorDefinitionRepository;
import com.example.Product_Selection_260813.repository.GroupBuyRecordRepository;
import com.example.Product_Selection_260813.repository.ProductTypeRepository;
import com.example.Product_Selection_260813.repository.ProductTypeScoreBandRepository;
import com.example.Product_Selection_260813.repository.RiskOptionRepository;
import com.example.Product_Selection_260813.service.resolver.AlgorithmSettings;

/**
 * 2026-09-29「評分與審核規則」功能檢查的修正：
 * <ul>
 * <li>自訂模式儲存權重：新建立的自訂因子在 evaluation_factors 沒有列時要補建，不能被安靜丟掉。</li>
 * <li>目標區間「依歷史紀錄計算」：樣本要涵蓋大類底下的小類（開團紀錄只記在小類）。</li>
 * <li>新增品類覆寫：只能掛在大類、只能給會讀目標區間的因子。</li>
 * <li>編輯審核風險選項：沒送 description 時不能把原本的說明清掉。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SettingsServiceScoringRulesTest {

	private static final Long CUSTOM_MODE_ID = 4L;
	private static final Long ROOT_TYPE_ID = 1L;
	private static final String MANAGER = "manager01";

	@Mock
	private EvaluationModeRepository evaluationModeRepository;
	@Mock
	private EvaluationFactorRepository evaluationFactorRepository;
	@Mock
	private FactorDefinitionRepository factorDefinitionRepository;
	@Mock
	private ProductTypeRepository productTypeRepository;
	@Mock
	private ProductTypeScoreBandRepository productTypeScoreBandRepository;
	@Mock
	private GroupBuyRecordRepository groupBuyRecordRepository;
	@Mock
	private RiskOptionRepository riskOptionRepository;
	@Mock
	private AppUserRepository appUserRepository;
	@Mock
	private ScoringService scoringService;
	@Mock
	private AlgorithmSettings algorithmSettings;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private SettingsService settingsService;

	// ===================================================================
	// 自訂模式權重
	// ===================================================================

	@Test
	void 儲存自訂模式權重時_新自訂因子沒有權重列會補建_不會被安靜丟掉() {
		when(evaluationModeRepository.findById(CUSTOM_MODE_ID)).thenReturn(Optional.of(customMode()));
		when(scoringService.getAllActiveFactorCodes()).thenReturn(List.of("MARGIN_RATE", "TREND_HEAT", "NEW_FACTOR"));
		when(appUserRepository.findByUsername(MANAGER)).thenReturn(Optional.of(manager()));
		when(evaluationFactorRepository.findByEvaluationModeIdOrderBySortOrderAsc(CUSTOM_MODE_ID))
				.thenReturn(List.of(factorRow("MARGIN_RATE", "50", 1), factorRow("TREND_HEAT", "50", 7)));
		when(factorDefinitionRepository.findByFactorCodeInAndIsActiveTrue(List.of("NEW_FACTOR")))
				.thenReturn(List.of(definition("NEW_FACTOR", FactorStrategyCode.MANUAL_SCALE, null)));

		settingsService.updateEvaluationModeFactors(CUSTOM_MODE_ID,
				weights("MARGIN_RATE", "40", "TREND_HEAT", "40", "NEW_FACTOR", "20"), MANAGER);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<EvaluationFactor>> saved = ArgumentCaptor.forClass(List.class);
		verify(evaluationFactorRepository).saveAll(saved.capture());
		List<EvaluationFactor> rows = saved.getValue();
		assertThat(rows).extracting(EvaluationFactor::getFactorCode)
				.containsExactly("MARGIN_RATE", "TREND_HEAT", "NEW_FACTOR");
		EvaluationFactor created = rows.get(2);
		assertThat(created.getEvaluationModeId()).isEqualTo(CUSTOM_MODE_ID);
		assertThat(created.getWeight()).isEqualByComparingTo("20");
		assertThat(created.getFactorName()).isEqualTo("NEW_FACTOR 名稱");
		// 自訂因子沒有分組時填 CUSTOM（evaluation_factors.category 是 NOT NULL）
		assertThat(created.getCategory()).isEqualTo("CUSTOM");
		assertThat(created.getSortOrder()).isEqualTo(8);
		assertThat(created.getUpdatedBy()).isEqualTo(1L);
		assertThat(rows.get(0).getWeight()).isEqualByComparingTo("40");
		verify(eventPublisher).publishEvent(any(EvaluationSettingsChangedEvent.class));
	}

	@Test
	void 儲存自訂模式權重時_權重列都已存在就不查自訂因子定義() {
		when(evaluationModeRepository.findById(CUSTOM_MODE_ID)).thenReturn(Optional.of(customMode()));
		when(scoringService.getAllActiveFactorCodes()).thenReturn(List.of("MARGIN_RATE", "TREND_HEAT"));
		when(appUserRepository.findByUsername(MANAGER)).thenReturn(Optional.of(manager()));
		when(evaluationFactorRepository.findByEvaluationModeIdOrderBySortOrderAsc(CUSTOM_MODE_ID))
				.thenReturn(List.of(factorRow("MARGIN_RATE", "50", 1), factorRow("TREND_HEAT", "50", 7)));

		settingsService.updateEvaluationModeFactors(CUSTOM_MODE_ID, weights("MARGIN_RATE", "30", "TREND_HEAT", "70"),
				MANAGER);

		verify(factorDefinitionRepository, never()).findByFactorCodeInAndIsActiveTrue(anyList());
	}

	// ===================================================================
	// 目標區間
	// ===================================================================

	@Test
	void 依歷史紀錄計算目標區間時_樣本涵蓋大類與底下所有小類() {
		ProductTypeScoreBand band = new ProductTypeScoreBand();
		band.setId(10L);
		band.setProductTypeId(ROOT_TYPE_ID);
		band.setFactorCode("MARGIN_RATE");
		when(productTypeScoreBandRepository.findById(10L)).thenReturn(Optional.of(band));
		when(appUserRepository.findByUsername(MANAGER)).thenReturn(Optional.of(manager()));
		when(productTypeRepository.findByParentIdOrderBySortOrderAsc(ROOT_TYPE_ID))
				.thenReturn(List.of(productType(2L, 2), productType(3L, 2)));
		List<Object[]> samples = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			// 成本 60~79、售價 100 → 毛利率 0.21~0.40
			samples.add(new Object[] { BigDecimal.valueOf(60 + i), BigDecimal.valueOf(100) });
		}
		when(groupBuyRecordRepository.findMarginRateSamplesByProductTypes(List.of(1L, 2L, 3L))).thenReturn(samples);
		when(groupBuyRecordRepository.marginRateSamplesIncludeSimulated(List.of(1L, 2L, 3L))).thenReturn(true);
		when(algorithmSettings.getScoreBandMinSampleSize()).thenReturn(10);
		when(algorithmSettings.getScoreBandPercentileLower()).thenReturn(10);
		when(algorithmSettings.getScoreBandPercentileUpper()).thenReturn(90);

		ProductTypeScoreBandUpdateRequest request = new ProductTypeScoreBandUpdateRequest();
		request.setSourceMode("HISTORICAL");
		settingsService.updateProductTypeScoreBand(10L, request, MANAGER);

		assertThat(band.getSourceMode()).isEqualTo("HISTORICAL");
		assertThat(band.getSampleSize()).isEqualTo(20);
		assertThat(band.getIncludesSimulated()).isTrue();
		assertThat(band.getLowerBound()).isLessThan(band.getUpperBound());
	}

	@Test
	void 新增品類覆寫_掛在小類上會被拒絕() {
		when(productTypeRepository.findById(2L)).thenReturn(Optional.of(productType(2L, 2)));

		assertThatThrownBy(() -> settingsService.createProductTypeScoreBand(createBand(2L, "MARGIN_RATE"), MANAGER))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("大類");
		verify(productTypeScoreBandRepository, never()).save(any());
	}

	@Test
	void 新增品類覆寫_計分不讀目標區間的因子會被拒絕() {
		when(productTypeRepository.findById(ROOT_TYPE_ID)).thenReturn(Optional.of(productType(ROOT_TYPE_ID, 1)));
		when(factorDefinitionRepository.findByFactorCodeInAndIsActiveTrue(List.of("ECO_PACKAGING")))
				.thenReturn(List.of(definition("ECO_PACKAGING", FactorStrategyCode.MANUAL_SCALE, null)));

		assertThatThrownBy(
				() -> settingsService.createProductTypeScoreBand(createBand(ROOT_TYPE_ID, "ECO_PACKAGING"), MANAGER))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("不會讀取目標區間");
		verify(productTypeScoreBandRepository, never()).save(any());
	}

	@Test
	void 新增品類覆寫_目標區間正規化的自訂因子可以設定() {
		when(productTypeRepository.findById(ROOT_TYPE_ID)).thenReturn(Optional.of(productType(ROOT_TYPE_ID, 1)));
		when(factorDefinitionRepository.findByFactorCodeInAndIsActiveTrue(List.of("SOCIAL_BUZZ")))
				.thenReturn(List.of(definition("SOCIAL_BUZZ", FactorStrategyCode.TARGET_BAND_NORMALIZE, "FORECAST")));
		when(appUserRepository.findByUsername(MANAGER)).thenReturn(Optional.of(manager()));

		settingsService.createProductTypeScoreBand(createBand(ROOT_TYPE_ID, "SOCIAL_BUZZ"), MANAGER);

		verify(productTypeScoreBandRepository).save(any(ProductTypeScoreBand.class));
	}

	// ===================================================================
	// 審核風險選項
	// ===================================================================

	@Test
	void 編輯風險選項沒送說明時_保留原本的說明() {
		RiskOption option = new RiskOption();
		option.setId(1L);
		option.setName("實際供貨風險");
		option.setDescription("供應商交期、產能與庫存");
		when(riskOptionRepository.findById(1L)).thenReturn(Optional.of(option));
		when(riskOptionRepository.save(any(RiskOption.class))).thenAnswer(invocation -> invocation.getArgument(0));

		RiskOptionUpdateRequest request = new RiskOptionUpdateRequest();
		request.setName("供貨風險");
		request.setAlertKeywords("缺貨、斷貨");
		settingsService.updateRiskOption(1L, request);

		assertThat(option.getName()).isEqualTo("供貨風險");
		assertThat(option.getDescription()).isEqualTo("供應商交期、產能與庫存");
		assertThat(option.getAlertKeywords()).isEqualTo("缺貨、斷貨");
	}

	@Test
	void 編輯風險選項送空白說明時_清空說明() {
		RiskOption option = new RiskOption();
		option.setId(1L);
		option.setName("實際供貨風險");
		option.setDescription("供應商交期、產能與庫存");
		when(riskOptionRepository.findById(1L)).thenReturn(Optional.of(option));
		when(riskOptionRepository.save(any(RiskOption.class))).thenAnswer(invocation -> invocation.getArgument(0));

		RiskOptionUpdateRequest request = new RiskOptionUpdateRequest();
		request.setName("實際供貨風險");
		request.setDescription("  ");
		settingsService.updateRiskOption(1L, request);

		assertThat(option.getDescription()).isNull();
	}

	// ---------------------------------------------------------------------

	private static EvaluationMode customMode() {
		EvaluationMode mode = new EvaluationMode();
		mode.setId(CUSTOM_MODE_ID);
		mode.setModeCode("CUSTOM");
		mode.setIsEditable(true);
		return mode;
	}

	private static AppUser manager() {
		AppUser user = new AppUser();
		user.setId(1L);
		return user;
	}

	private static EvaluationFactor factorRow(String code, String weight, int sortOrder) {
		EvaluationFactor factor = new EvaluationFactor();
		factor.setEvaluationModeId(CUSTOM_MODE_ID);
		factor.setFactorCode(code);
		factor.setFactorName(code);
		factor.setCategory("BUSINESS");
		factor.setWeight(new BigDecimal(weight));
		factor.setSortOrder(sortOrder);
		return factor;
	}

	private static FactorDefinition definition(String code, FactorStrategyCode strategy, String category) {
		FactorDefinition definition = new FactorDefinition();
		definition.setFactorCode(code);
		definition.setFactorName(code + " 名稱");
		definition.setCategory(category);
		definition.setStrategyCode(strategy);
		definition.setIsActive(true);
		return definition;
	}

	private static ProductType productType(Long id, int level) {
		ProductType type = new ProductType();
		type.setId(id);
		type.setLevel(level);
		type.setParentId(level == 1 ? null : ROOT_TYPE_ID);
		return type;
	}

	private static ProductTypeScoreBandCreateRequest createBand(Long productTypeId, String factorCode) {
		ProductTypeScoreBandCreateRequest request = new ProductTypeScoreBandCreateRequest();
		request.setProductTypeId(productTypeId);
		request.setFactorCode(factorCode);
		request.setLowerBound(new BigDecimal("0.1"));
		request.setUpperBound(new BigDecimal("0.5"));
		return request;
	}

	private static EvaluationFactorUpdateRequest weights(String... codeAndWeight) {
		List<EvaluationFactorUpdateRequest.FactorWeight> factors = new ArrayList<>();
		for (int i = 0; i < codeAndWeight.length; i += 2) {
			EvaluationFactorUpdateRequest.FactorWeight weight = new EvaluationFactorUpdateRequest.FactorWeight();
			weight.setFactorCode(codeAndWeight[i]);
			weight.setWeight(new BigDecimal(codeAndWeight[i + 1]));
			factors.add(weight);
		}
		EvaluationFactorUpdateRequest request = new EvaluationFactorUpdateRequest();
		request.setFactors(factors);
		return request;
	}
}
