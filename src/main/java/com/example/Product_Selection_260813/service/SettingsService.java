package com.example.Product_Selection_260813.service;

import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.common.exception.SystemConfigurationException;
import com.example.Product_Selection_260813.dto.request.AudienceProfileUpdateRequest;
import com.example.Product_Selection_260813.dto.request.FestiveCampaignCreateRequest;
import com.example.Product_Selection_260813.dto.request.FestiveCampaignManualStatusRequest;
import com.example.Product_Selection_260813.dto.request.FestiveCampaignTagInput;
import com.example.Product_Selection_260813.dto.request.FestiveCampaignUpdateRequest;
import com.example.Product_Selection_260813.dto.request.ProductTypeCreateRequest;
import com.example.Product_Selection_260813.dto.request.ProductTypeUpdateRequest;
import com.example.Product_Selection_260813.dto.request.RiskOptionCreateRequest;
import com.example.Product_Selection_260813.dto.request.WeatherSignalTagMappingCreateRequest;
import com.example.Product_Selection_260813.dto.request.WeatherSignalTagMappingUpdateRequest;
import com.example.Product_Selection_260813.dto.request.RegionWeightUpdateRequest;
import com.example.Product_Selection_260813.dto.request.RiskOptionUpdateRequest;
import com.example.Product_Selection_260813.dto.request.SwitchEvaluationModeRequest;
import com.example.Product_Selection_260813.dto.response.AudienceProfileResponse;
import com.example.Product_Selection_260813.dto.response.EvaluationModeResponse;
import com.example.Product_Selection_260813.dto.response.FestiveCampaignResponse;
import com.example.Product_Selection_260813.dto.response.FestiveCampaignTagView;
import com.example.Product_Selection_260813.dto.response.ProductTypeResponse;
import com.example.Product_Selection_260813.dto.response.RiskOptionSettingResponse;
import com.example.Product_Selection_260813.dto.response.WeatherSignalTagMappingResponse;
import com.example.Product_Selection_260813.dto.response.WeatherSignalTagOptionResponse;
import com.example.Product_Selection_260813.dto.response.RegionWeightResponse;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.entity.AudienceProfile;
import com.example.Product_Selection_260813.entity.EvaluationMode;
import com.example.Product_Selection_260813.entity.FestiveCampaign;
import com.example.Product_Selection_260813.entity.FestiveCampaignTag;
import com.example.Product_Selection_260813.entity.ProductType;
import com.example.Product_Selection_260813.entity.RiskOption;
import com.example.Product_Selection_260813.entity.WeatherSignalTagMapping;
import com.example.Product_Selection_260813.entity.RegionWeight;
import com.example.Product_Selection_260813.repository.RegionWeightRepository;
import com.example.Product_Selection_260813.service.weather.WeatherRegionConfig;
import com.example.Product_Selection_260813.enums.WeatherSignalType;
import com.example.Product_Selection_260813.entity.SystemSetting;
import com.example.Product_Selection_260813.json.WeightSnapshot;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.example.Product_Selection_260813.algorithm.ScoringAlgorithms;
import com.example.Product_Selection_260813.dto.request.ProductTypeScoreBandCreateRequest;
import com.example.Product_Selection_260813.dto.request.ProductTypeScoreBandUpdateRequest;
import com.example.Product_Selection_260813.dto.response.ProductTypeScoreBandResponse;
import com.example.Product_Selection_260813.entity.ProductTypeScoreBand;
import com.example.Product_Selection_260813.enums.CustomFieldType;
import com.example.Product_Selection_260813.enums.FactorDataSource;
import com.example.Product_Selection_260813.enums.FactorStrategyCode;
import com.example.Product_Selection_260813.enums.FestiveCampaignStatus;
import com.example.Product_Selection_260813.enums.ScoreBandSourceMode;
import com.example.Product_Selection_260813.repository.GroupBuyRecordRepository;
import com.example.Product_Selection_260813.repository.ProductTypeScoreBandRepository;
import com.example.Product_Selection_260813.service.resolver.ScoreBandResolver;

import com.example.Product_Selection_260813.constants.ValidationMessage;
import com.example.Product_Selection_260813.dto.request.EvaluationFactorUpdateRequest;
import com.example.Product_Selection_260813.dto.request.CustomFieldDefinitionCreateRequest;
import com.example.Product_Selection_260813.dto.request.CustomFieldDefinitionUpdateRequest;
import com.example.Product_Selection_260813.dto.request.FactorDefinitionCreateRequest;
import com.example.Product_Selection_260813.dto.request.FactorDefinitionUpdateRequest;
import com.example.Product_Selection_260813.dto.response.CustomFieldDefinitionResponse;
import com.example.Product_Selection_260813.dto.response.FactorDefinitionResponse;
import com.example.Product_Selection_260813.entity.CustomFieldApplicableType;
import com.example.Product_Selection_260813.entity.CustomFieldDefinition;
import com.example.Product_Selection_260813.entity.EvaluationFactor;
import com.example.Product_Selection_260813.entity.FactorDefinition;
import com.example.Product_Selection_260813.repository.AudienceProfileRepository;
import com.example.Product_Selection_260813.repository.CustomFieldApplicableTypeRepository;
import com.example.Product_Selection_260813.repository.CustomFieldDefinitionRepository;
import com.example.Product_Selection_260813.repository.EvaluationFactorRepository;
import com.example.Product_Selection_260813.repository.EvaluationModeRepository;
import com.example.Product_Selection_260813.repository.FactorDefinitionRepository;
import com.example.Product_Selection_260813.repository.FestiveCampaignRepository;
import com.example.Product_Selection_260813.repository.FestiveCampaignTagRepository;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.ProductTypeRepository;
import com.example.Product_Selection_260813.repository.RiskOptionRepository;
import com.example.Product_Selection_260813.repository.WeatherSignalTagMappingRepository;
import com.example.Product_Selection_260813.constants.SystemSettingRegistry;
import com.example.Product_Selection_260813.constants.FactorCode;
import com.example.Product_Selection_260813.dto.response.SystemSettingResponse;
import com.example.Product_Selection_260813.repository.SystemSettingRepository;
import com.example.Product_Selection_260813.service.scoring.FactorStrategyRegistry;
import com.example.Product_Selection_260813.service.resolver.ProductTypeAttributeResolver;

/**
 * 對應企劃書十二-13分層決議：「統一承接四、資料表設計中所有『設定類』CRUD
 * （product_types／evaluation_modes／evaluation_factors／audience_profiles／
 * risk_options／festive_campaigns／system_settings），避免這些端點被隨手塞進
 * 其他Controller導致邊界模糊」。
 *
 * <b>本輪範圍（分批實作，第二批）：</b>核心客群設定（2支）／商品類型設定（5支，
 * 含update與enable）／人工風險選項的停用與復用（2支）。加上第一批已完成的
 * 評估模式（4支）、人工風險選項的GET／POST（2支）、商品類型的GET／POST／
 * disable（3支）與節慶檔期管理（4支），七、系統設定端點皆已完成。
 *
 * <b>「停用只單向、不提供啟用」的決策已推翻：</b>商品類型與人工風險選項原本都
 * 只有disable、沒有enable，理由是「企劃書只定義停用這個單向動作」；但實務上
 * 一旦誤停用就沒有復原手段（重新新增一筆同名資料的id不同，無法接回既有
 * 品項／審核紀錄的外鍵關聯），故補上對稱的enable端點，兩者維持與user一致的
 * 「停用不刪除＋可復用」模式。
 *
 *
 * PUT /api/settings/evaluation-mode/current則不受這個文件矛盾影響：它修改的
 * 是system_settings.current_evaluation_mode_id這個「指向哪個既有模式」的指標值，
 * 不是新增或調整evaluation_modes表本身的資料列，跟已知限制描述的「這兩張表的
 * 資料」是兩回事，故本輪正常實作。
 *
 * <b>核心客群設定（audience-profile）單數路徑：</b>version／is_active欄位本階段
 * 僅預留、不實作版本切換邏輯（企劃書四-6備註明訂），故本類別固定操作
 * is_active=true的那一筆，PUT是直接覆蓋既有使用中設定，不是新增一個新版本。
 *
 * <b>節慶檔期的「標籤」欄位已跟企劃書原始UI樹狀圖不同：</b>原文寫「新增／編輯
 * 檔期（...／target_tags）」，但target_tags欄位已在資料庫討論中移除、改由
 * festive_campaign_tags表承接（一檔期對多標籤、每個標籤各自帶match_tier），
 * 見FestiveCampaignTag.java與ScoringService類別註解。本類別的create／update
 * 因此改用「標籤＋分級」清單（FestiveCampaignTagInput），不是單一字串，
 */
@Service
public class SettingsService {

	private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

	// 對應SystemSettingRepository註解裡的既定用法與四-14設計取捨
	private static final String CURRENT_EVALUATION_MODE_KEY = "current_evaluation_mode_id";

	@Autowired
	private EvaluationModeRepository evaluationModeRepository;

	// 自訂模式的權重編輯需要直接寫入 evaluation_factors；
	// 既有的查詢路徑是透過 scoringService.buildWeightSnapshot()，那是唯讀的。
	@Autowired
	private EvaluationFactorRepository evaluationFactorRepository;

	@Autowired
	private SystemSettingRepository systemSettingRepository;

	@Autowired
	private RiskOptionRepository riskOptionRepository;

	@Autowired
	private WeatherSignalTagMappingRepository weatherSignalTagMappingRepository;

	@Autowired
	private RegionWeightRepository regionWeightRepository;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private ScoringService scoringService;

	@Autowired
	private FactorDefinitionRepository factorDefinitionRepository;

	@Autowired
	private CustomFieldDefinitionRepository customFieldDefinitionRepository;

	@Autowired
	private CustomFieldApplicableTypeRepository customFieldApplicableTypeRepository;

	@Autowired
	private ProductTypeAttributeResolver productTypeAttributeResolver;

	@Autowired
	private FactorStrategyRegistry factorStrategyRegistry;

	@Autowired
	private AudienceProfileRepository audienceProfileRepository;

	@Autowired
	private ProductTypeRepository productTypeRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private FestiveCampaignRepository festiveCampaignRepository;

	@Autowired
	private FestiveCampaignTagRepository festiveCampaignTagRepository;

	// 目標區間 HISTORICAL 模式需要的兩個依賴
	@Autowired
	private ProductTypeScoreBandRepository productTypeScoreBandRepository;

	@Autowired
	private GroupBuyRecordRepository groupBuyRecordRepository;

	@Autowired
	private com.example.Product_Selection_260813.service.resolver.AlgorithmSettings algorithmSettings;

	// ========================= 評估模式 =========================

	/**
	 * GET /api/settings/evaluation-modes：取得所有評估模式列表
	 * （模式名稱／版本／是否生效）。
	 */
	@Transactional(readOnly = true)
	public List<EvaluationModeResponse> getAllEvaluationModes() {
		return evaluationModeRepository.findAll().stream().map(EvaluationModeResponse::from).toList();
	}

	/**
	 * GET /api/settings/evaluation-modes/{id}/factors：取得指定評估模式底下每個
	 * evaluation_factors的固定權重明細。直接複用ScoringService.buildWeightSnapshot()，
	 * 不重複寫一份組裝邏輯——這支端點跟review_records.weight_snapshot的資料來源
	 * 本來就是同一份（見ScoringService類別註解）。
	 */
	@Transactional(readOnly = true)
	public WeightSnapshot getEvaluationModeFactors(Long evaluationModeId) {
		if (!evaluationModeRepository.existsById(evaluationModeId)) {
			throw new IllegalArgumentException("評估模式不存在");
		}
		return scoringService.buildWeightSnapshot(evaluationModeId);
	}


	/**
	 * PUT /api/settings/evaluation-modes/{id}/factors：更新自訂模式的權重。
	 *
	 * <b>只有 is_editable = true 的模式可以改</b>。三套固定模式（均衡／衝量／
	 * 高利潤）是系統設計好、已驗證過的標準組合，開放編輯之後就很難分辨
	 * 「現在這個分數是照哪一套邏輯算的」，對照歷史審核紀錄會很混亂。
	 * 只開放自訂模式，主管想客製化時有地方調，但不動搖前三套的公信力。
	 *
	 * 採整份覆蓋語意：必須送齊全部七個因子。權重之間有「加總為 100」的約束，
	 * 只送部分欄位的話，後端得把送來的值與資料庫既有值混合才能驗證，
	 * 而使用者在畫面上算出的加總與後端實際驗的可能不同。
	 *
	 * 這裡不觸發既有商品重算——重算範圍可能很大，應由呼叫端決定時機。
	 * 而且已完成審核的商品有 weight_snapshot 保護，本來就不受影響。
	 */
	@Transactional
	public WeightSnapshot updateEvaluationModeFactors(Long evaluationModeId,
			EvaluationFactorUpdateRequest request, String username) {
		EvaluationMode mode = evaluationModeRepository.findById(evaluationModeId)
				.orElseThrow(() -> new IllegalArgumentException("評估模式不存在"));

		// 檢查一：這套模式允許改嗎。
		// 這是最重要的一道防線——沒有它，任何人只要知道均衡型的 id，
		// 就能透過這支 API 改掉三套固定模式，「固定」的設計形同虛設。
		if (!Boolean.TRUE.equals(mode.getIsEditable())) {
			throw new IllegalStateException(ValidationMessage.FACTOR_MODE_NOT_EDITABLE);
		}

		// 檢查二：因子代碼必須正確且齊全
		Map<String, BigDecimal> incoming = validateAndCollectFactors(request);

		// 檢查三：加總必須恰為 100。
		// 用 compareTo 而非 equals——BigDecimal 的 equals 會比較 scale，
		// 100 與 100.00 用 equals 判定為不相等，會誤擋正確的輸入。
		BigDecimal sum = incoming.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		if (sum.compareTo(BigDecimal.valueOf(100)) != 0) {
			throw new IllegalArgumentException(
					ValidationMessage.FACTOR_SUM_NOT_100 + sum.stripTrailingZeros().toPlainString());
		}

		// 全部通過才寫入
		Long operatorId = resolveUserId(username);
		java.time.LocalDateTime now = java.time.LocalDateTime.now();
		List<EvaluationFactor> factors = evaluationFactorRepository
				.findByEvaluationModeIdOrderBySortOrderAsc(evaluationModeId);
		for (EvaluationFactor factor : factors) {
			BigDecimal newWeight = incoming.get(factor.getFactorCode());
			if (newWeight != null) {
				factor.setWeight(newWeight);
				// 稽核欄位：權重被改了，要能查到是誰、什麼時候改的，不能只靠
				// 應用程式日誌（不可查詢、會被輪替清除）。
				factor.setUpdatedAt(now);
				factor.setUpdatedBy(operatorId);
			}
		}
		evaluationFactorRepository.saveAll(factors);
		log.info("自訂模式權重已更新：模式 {}，操作者 {}", evaluationModeId, username);

		return scoringService.buildWeightSnapshot(evaluationModeId);
	}

	/**
	 * 驗證送來的因子代碼並轉成 Map。
	 *
	 * 三件事一起檢查：代碼是否認得、是否重複、七項是否齊全。
	 * 缺一不可——少檢查「齊全」的話，使用者只送三項且加總 100 也會通過，
	 * 但另外四項會維持舊值，實際加總就不是 100 了。
	 */
	/** 列出全部目標區間，供設定頁呈現。 */
	@Transactional(readOnly = true)
	public List<ProductTypeScoreBandResponse> getProductTypeScoreBands() {
		return productTypeScoreBandRepository.findAllActive().stream()
				.map(ProductTypeScoreBandResponse::from)
				.toList();
	}

	/**
	 * 新增目標區間——原本這支方法完全不存在，Controller 呼叫的是一個從未
	 * 被實作過的方法名稱，導致整個專案編譯不過。
	 *
	 * 新增一律是 MANUAL 模式（見 settings.html 註解：「新增的列一律為
	 * 『手動填入』模式；建立後若已有足夠歷史開團紀錄，可再切換成
	 * 『依歷史紀錄計算』」）——不提供新增當下就選 HISTORICAL 的入口，
	 * 因為 HISTORICAL 模式的語意是「已經有真實資料可以算」，一個剛
	 * 新增、還沒人手動填過任何數字的區間沒有這個前提。
	 *
	 * 同一商品類型的同一因子只能有一筆生效中（is_active=true）的區間，
	 * 重複新增直接拒絕，不會讓兩筆同時生效造成評分邏輯不知道該用哪一筆。
	 */
	@Transactional
	public ProductTypeScoreBandResponse createProductTypeScoreBand(
			ProductTypeScoreBandCreateRequest request, String username) {
		if (productTypeScoreBandRepository.existsByProductTypeIdAndFactorCodeAndIsActiveTrue(
				request.getProductTypeId(), request.getFactorCode())) {
			throw new IllegalStateException(
					"此商品類型的「" + request.getFactorCode() + "」因子已有生效中的目標區間，重複新增會被拒絕。");
		}
		if (request.getUpperBound().compareTo(request.getLowerBound()) <= 0) {
			throw new IllegalArgumentException(
					"上界必須大於下界，目前下界=" + request.getLowerBound() + " 上界=" + request.getUpperBound());
		}

		Long operatorId = resolveUserId(username);
		LocalDateTime now = LocalDateTime.now();

		ProductTypeScoreBand band = new ProductTypeScoreBand();
		band.setProductTypeId(request.getProductTypeId());
		band.setFactorCode(request.getFactorCode());
		band.setLowerBound(request.getLowerBound());
		band.setUpperBound(request.getUpperBound());
		band.setSourceMode(ScoreBandSourceMode.MANUAL.name());
		band.setIsActive(true);
		band.setUpdatedAt(now);
		band.setUpdatedBy(operatorId);
		productTypeScoreBandRepository.save(band);

		log.info("目標區間已新增：productTypeId={}，factorCode={}，操作者={}",
				request.getProductTypeId(), request.getFactorCode(), username);
		return ProductTypeScoreBandResponse.from(band);
	}

	// ============================================================
	// 目標區間：HISTORICAL / MANUAL 切換
	// ============================================================

	/**
	 * 更新目標區間。依 sourceMode 分兩條路徑，見 {@link ProductTypeScoreBandUpdateRequest}
	 * 的類別註解說明兩種模式的差異。
	 *
	 * HISTORICAL 模式在這裡「當下計算一次並凍結寫入」，不是留一個公式讓評分時
	 * 動態運算——這是為了維持可重現性：如果每次評分都重新查歷史資料算區間，
	 * 同一件商品在不同時間會因為資料庫累積了新紀錄而算出不同分數，而且
	 * 已審核商品的快照會對不上重新計算的結果。
	 */
	@Transactional
	public ProductTypeScoreBandResponse updateProductTypeScoreBand(Long bandId,
			ProductTypeScoreBandUpdateRequest request, String username) {
		ProductTypeScoreBand band = productTypeScoreBandRepository.findById(bandId)
				.orElseThrow(() -> new IllegalArgumentException("目標區間不存在"));

		ScoreBandSourceMode mode;
		try {
			mode = ScoreBandSourceMode.valueOf(request.getSourceMode().trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("來源模式須為 HISTORICAL 或 MANUAL，目前為：" + request.getSourceMode());
		}

		Long operatorId = resolveUserId(username);
		LocalDateTime now = LocalDateTime.now();

		if (mode == ScoreBandSourceMode.MANUAL) {
			applyManualBand(band, request);
		} else {
			applyHistoricalBand(band, now);
		}

		band.setSourceMode(mode.name());
		band.setUpdatedAt(now);
		band.setUpdatedBy(operatorId);
		productTypeScoreBandRepository.save(band);

		log.info("目標區間已更新：id={}，模式={}，操作者={}", bandId, mode, username);
		return ProductTypeScoreBandResponse.from(band);
	}

	/**
	 * MANUAL 模式：主管直接輸入固定數字。
	 *
	 * 未提供新數字時沿用資料庫裡目前的值，不清空——對應「手動設定預設值為
	 * 固定值」的需求：切到 MANUAL 不代表要求主管立刻重新輸入，可以先沿用
	 * 現狀，之後再慢慢調整。
	 *
	 * 切到 MANUAL 後，HISTORICAL 專屬的三個欄位（樣本數、是否含模擬資料、
	 * 計算時間）清空——這些欄位的語意是「這組數字是算出來的」，MANUAL 模式
	 * 下不成立，留著舊值會誤導看畫面的人以為現在的數字還是算出來的。
	 */
	private void applyManualBand(ProductTypeScoreBand band, ProductTypeScoreBandUpdateRequest request) {
		BigDecimal lower = request.getLowerBound() != null ? request.getLowerBound() : band.getLowerBound();
		BigDecimal upper = request.getUpperBound() != null ? request.getUpperBound() : band.getUpperBound();

		if (lower == null || upper == null) {
			throw new IllegalArgumentException("首次設定為 MANUAL 模式時，上下界必須提供");
		}
		if (upper.compareTo(lower) <= 0) {
			throw new IllegalArgumentException("上界必須大於下界，目前下界=" + lower + " 上界=" + upper);
		}

		band.setLowerBound(lower);
		band.setUpperBound(upper);
		band.setSampleSize(null);
		band.setIncludesSimulated(null);
		band.setComputedAt(null);
	}

	/**
	 * HISTORICAL 模式：從歷史開團紀錄算出建議區間並凍結。
	 *
	 * 只有 MARGIN_RATE、DISCOUNT_DEPTH 這兩個因子有對應的歷史資料來源可以算
	 * （分別對應 group_buy_records 的成本價／售價、市價／售價）。其餘因子
	 * 目前沒有歷史區間的計算邏輯，切 HISTORICAL 會被拒絕——不是遺漏，是
	 * 因為那些因子本來就不是靠「成本／售價比較」這種公式算分數。
	 *
	 * 上下界採用分位數而非最大最小值，理由與 MOQ 可行性判定用分位數而非
	 * 歷史最大值一致：最大最小值容易被單一離群樣本主導。預設取 P10／P90，
	 * 可調整（見 AlgorithmSettings）。
	 */
	private void applyHistoricalBand(ProductTypeScoreBand band, LocalDateTime now) {
		Long productTypeId = band.getProductTypeId();
		if (productTypeId == null) {
			throw new IllegalArgumentException("全域保底區間（product_type_id 為空）不支援 HISTORICAL 模式，" +
					"歷史資料需要指定品類才能計算");
		}

		List<BigDecimal> ratios;
		boolean includesSimulated;

		if (ScoreBandResolver.FACTOR_MARGIN_RATE.equals(band.getFactorCode())) {
			List<Object[]> samples = groupBuyRecordRepository.findMarginRateSamplesByProductType(productTypeId);
			ratios = samples.stream()
					.map(row -> computeRatio((BigDecimal) row[1], (BigDecimal) row[0], (BigDecimal) row[1]))
					.filter(java.util.Objects::nonNull)
					.toList();
			includesSimulated = groupBuyRecordRepository.marginRateSamplesIncludeSimulated(productTypeId);
		} else if (ScoreBandResolver.FACTOR_DISCOUNT_DEPTH.equals(band.getFactorCode())) {
			List<Object[]> samples = groupBuyRecordRepository.findDiscountDepthSamplesByProductType(productTypeId);
			ratios = samples.stream()
					.map(row -> computeRatio((BigDecimal) row[0], (BigDecimal) row[1], (BigDecimal) row[0]))
					.filter(java.util.Objects::nonNull)
					.toList();
			includesSimulated = false; // 折扣深度目前未提供含模擬資料的查詢，先保守標 false 而非猜測
		} else {
			throw new IllegalArgumentException(
					"因子「" + band.getFactorCode() + "」沒有對應的歷史資料計算邏輯，僅 MARGIN_RATE／DISCOUNT_DEPTH 支援 HISTORICAL 模式");
		}

		int minSample = algorithmSettings.getScoreBandMinSampleSize();
		if (ratios.size() < minSample) {
			throw new IllegalArgumentException(String.format(
					"該品類有效樣本僅 %d 筆（需 %d 筆以上），樣本不足無法計算歷史區間，請改用 MANUAL 模式手動輸入",
					ratios.size(), minSample));
		}

		BigDecimal lower = ScoringAlgorithms.percentile(ratios, algorithmSettings.getScoreBandPercentileLower());
		BigDecimal upper = ScoringAlgorithms.percentile(ratios, algorithmSettings.getScoreBandPercentileUpper());
		if (upper.compareTo(lower) <= 0) {
			// 極端情況：樣本高度集中導致兩個分位數算出相同或反轉的值。
			// 不寫入一個無效區間，明確報錯讓人知道資料異常，而不是靜默寫入
			// 一組會讓 normalizeByBand() 拋例外的壞資料。
			throw new IllegalStateException("計算出的歷史區間上下界異常（下界=" + lower + " 上界=" + upper
					+ "），可能是樣本過度集中，建議改用 MANUAL 模式");
		}

		band.setLowerBound(lower);
		band.setUpperBound(upper);
		band.setSampleSize(ratios.size());
		band.setIncludesSimulated(includesSimulated);
		band.setComputedAt(now);
	}

	/** (分子1 − 分子2) / 分母，分母為 0 或任一輸入為 null 時回傳 null（跳過該筆樣本）。 */
	private BigDecimal computeRatio(BigDecimal minuend, BigDecimal subtrahend, BigDecimal denominator) {
		if (minuend == null || subtrahend == null || denominator == null
				|| denominator.compareTo(BigDecimal.ZERO) == 0) {
			return null;
		}
		return minuend.subtract(subtrahend).divide(denominator, 6, java.math.RoundingMode.HALF_UP);
	}

	private Map<String, BigDecimal> validateAndCollectFactors(EvaluationFactorUpdateRequest request) {
		// 2026-09-20改用 scoringService.getAllActiveFactorCodes()：涵蓋既有七個
		// 因子＋factor_definitions 裡目前生效中的自訂因子，不再是寫死的 FactorCode.ALL——
		// 否則自訂因子建立後，永遠無法透過這支端點被送出權重（一律被當成「未知代碼」拒絕）。
		List<String> allActiveFactorCodes = scoringService.getAllActiveFactorCodes();
		Map<String, BigDecimal> collected = new LinkedHashMap<>();
		for (EvaluationFactorUpdateRequest.FactorWeight fw : request.getFactors()) {
			String code = fw.getFactorCode().trim().toUpperCase();
			if (!allActiveFactorCodes.contains(code)) {
				throw new IllegalArgumentException(ValidationMessage.FACTOR_CODE_UNKNOWN + code);
			}
			if (collected.containsKey(code)) {
				throw new IllegalArgumentException(ValidationMessage.FACTOR_CODE_DUPLICATE + code);
			}
			collected.put(code, fw.getWeight());
		}

		List<String> missing = allActiveFactorCodes.stream()
				.filter(code -> !collected.containsKey(code))
				.toList();
		if (!missing.isEmpty()) {
			throw new IllegalArgumentException(
					ValidationMessage.FACTOR_CODE_MISSING + String.join("、", missing));
		}
		return collected;
	}

	// ========================= 自訂計分因子 =========================

	/**
	 * GET /api/settings/factor-definitions：列出全部自訂因子（含已停用／已被
	 * 編輯取代的歷史版本），供設定頁管理列表使用。既有七個固定因子不在這張表
	 * 裡，不會出現在這份清單。
	 *
	 * isSuperseded 的計算方式：同一批 findAll() 結果裡，凡是出現在其他列的
	 * previousVersionId 裡的 id，就代表那一列已經被取代——這裡直接對記憶體中
	 * 的清單做一次 stream 運算，不需要為此再多查一次資料庫。
	 */
	@Transactional(readOnly = true)
	public List<FactorDefinitionResponse> listFactorDefinitions() {
		List<FactorDefinition> all = factorDefinitionRepository.findAll();
		Set<Long> supersededIds = all.stream().map(FactorDefinition::getPreviousVersionId)
				.filter(Objects::nonNull).collect(Collectors.toSet());
		return all.stream().map(d -> FactorDefinitionResponse.from(d, supersededIds.contains(d.getId()))).toList();
	}

	/**
	 * POST /api/settings/factor-definitions：新增自訂計分因子。
	 *
	 * 三件事一起檢查（見 validateFactorStrategyAndSource()）：策略是否已實作、
	 * 資料源二選一、資料源與策略是否相容。這段驗證邏輯與 updateFactorDefinition()
	 * 共用，不重複寫一次。
	 *
	 * 新增後這個因子預設<b>不影響任何評估模式的分數</b>：它會被 ProductFactorScorer
	 * 算出分數、也會被 getAllActiveFactorCodes() 認得，但除非管理層另外呼叫
	 * PUT /api/settings/evaluation-modes/{id}/factors 把它加進某個自訂模式的權重
	 * 配置，否則沒有任何模式的 evaluation_factors 會有它的權重列，
	 * resolveFactorWeights() 查不到值，weightedAverage() 視為未啟用，不影響總分。
	 */
	@Transactional
	public FactorDefinitionResponse createFactorDefinition(FactorDefinitionCreateRequest request, String username) {
		String factorCode = request.getFactorCode().trim().toUpperCase();

		if (FactorCode.ALL.contains(factorCode)
				|| factorDefinitionRepository.existsByFactorCodeAndIsActiveTrue(factorCode)) {
			throw new IllegalArgumentException(ValidationMessage.FACTOR_DEFINITION_CODE_DUPLICATE + factorCode);
		}

		validateFactorStrategyAndSource(request.getStrategyCode(), request.getDataSourceCode(),
				request.getCustomFieldDefinitionId());

		Long userId = resolveUserId(username);

		FactorDefinition definition = new FactorDefinition();
		definition.setFactorCode(factorCode);
		definition.setFactorName(request.getFactorName());
		definition.setCategory(request.getCategory());
		definition.setStrategyCode(request.getStrategyCode());
		definition.setDataSourceCode(request.getDataSourceCode());
		definition.setCustomFieldDefinitionId(request.getCustomFieldDefinitionId());
		definition.setStrategyParams(request.getStrategyParams());
		definition.setIsActive(true);
		definition.setIsSystemDefault(false);
		definition.setCreatedBy(userId);

		FactorDefinition saved = factorDefinitionRepository.save(definition);
		log.info("自訂因子已新增：{}，策略 {}，操作者 {}", factorCode, request.getStrategyCode(), username);
		return FactorDefinitionResponse.from(saved);
	}

	/**
	 * PUT /api/settings/factor-definitions/{id}：編輯自訂計分因子。
	 *
	 * <b>版本鏈設計（V14新增）：</b>不是就地更新 id={id} 這一列，而是：
	 * <ol>
	 * <li>把 id={id} 這一列停用（isActive=false），並立刻flush，確保它在
	 *     資料庫層真的變成非生效狀態，避免下一步插入新列時跟它在
	 *     uk_factor_definitions_active_code 唯一索引上互撞（見V14 migration
	 *     類別註解）。</li>
	 * <li>新增一列，factorCode 沿用舊列（<b>編輯不開放修改代碼</b>，見
	 *     FactorDefinitionUpdateRequest 類別註解），其餘欄位取自 request；
	 *     isActive 沿用舊列被編輯前的狀態（舊列本來停用中，編輯後的新版本
	 *     也維持停用，不會因為編輯而被動變成生效中）；previousVersionId
	 *     指向舊列 id。</li>
	 * </ol>
	 * 這樣 evaluation_factors／product_type_score_bands 等既有表因為代碼不變，
	 * 完全不需要跟著搬移；review_records.weight_snapshot 是編輯當下已經凍結的
	 * 歷史資料，不會被這裡的變動影響。
	 *
	 * 舊列被停用後無法再被 enableFactorDefinition() 重新啟用——見該方法內的
	 * existsByPreviousVersionId() 防呆。
	 */
	@Transactional
	public FactorDefinitionResponse updateFactorDefinition(Long id, FactorDefinitionUpdateRequest request,
			String username) {
		FactorDefinition old = factorDefinitionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException(ValidationMessage.FACTOR_DEFINITION_NOT_FOUND + id));
		if (factorDefinitionRepository.existsByPreviousVersionId(id)) {
			throw new IllegalArgumentException(
					ValidationMessage.FACTOR_DEFINITION_SUPERSEDED_CANNOT_ENABLE + id);
		}

		validateFactorStrategyAndSource(request.getStrategyCode(), request.getDataSourceCode(),
				request.getCustomFieldDefinitionId());

		Long userId = resolveUserId(username);
		boolean wasActive = Boolean.TRUE.equals(old.getIsActive());

		old.setIsActive(false);
		old.setUpdatedBy(userId);
		factorDefinitionRepository.saveAndFlush(old);

		FactorDefinition updated = new FactorDefinition();
		updated.setFactorCode(old.getFactorCode());
		updated.setFactorName(request.getFactorName());
		updated.setCategory(request.getCategory());
		updated.setStrategyCode(request.getStrategyCode());
		updated.setDataSourceCode(request.getDataSourceCode());
		updated.setCustomFieldDefinitionId(request.getCustomFieldDefinitionId());
		updated.setStrategyParams(request.getStrategyParams());
		updated.setIsActive(wasActive);
		updated.setIsSystemDefault(false);
		updated.setPreviousVersionId(old.getId());
		updated.setCreatedBy(userId);

		FactorDefinition saved = factorDefinitionRepository.save(updated);
		log.info("自訂因子已編輯：{}（新版本id={}，取代舊版本id={}），操作者 {}", saved.getFactorCode(), saved.getId(),
				old.getId(), username);
		return FactorDefinitionResponse.from(saved);
	}

	/**
	 * 共用驗證：strategyCode 是否已實作、dataSourceCode／customFieldDefinitionId
	 * 是否恰好擇一、擇一後的資料源是否與策略相容。createFactorDefinition() 與
	 * updateFactorDefinition() 共用，不重複寫一次（原本兩處各寫一次是這次新增
	 * 編輯功能時順便修正的重複程式碼）。
	 */
	private void validateFactorStrategyAndSource(FactorStrategyCode strategyCode, FactorDataSource dataSourceCode,
			Long customFieldDefinitionId) {
		if (!factorStrategyRegistry.isImplemented(strategyCode)) {
			throw new IllegalArgumentException(
					ValidationMessage.FACTOR_DEFINITION_STRATEGY_NOT_IMPLEMENTED + strategyCode);
		}

		boolean hasDataSource = dataSourceCode != null;
		boolean hasCustomField = customFieldDefinitionId != null;
		if (hasDataSource == hasCustomField) {
			throw new IllegalArgumentException(ValidationMessage.FACTOR_DEFINITION_SOURCE_XOR_VIOLATION);
		}

		if (hasDataSource) {
			if (dataSourceCode.getCompatibleStrategy() != strategyCode) {
				throw new IllegalArgumentException(
						ValidationMessage.FACTOR_DEFINITION_DATA_SOURCE_INCOMPATIBLE
								+ dataSourceCode.getCompatibleStrategy());
			}
		} else {
			CustomFieldDefinition customField = customFieldDefinitionRepository.findById(customFieldDefinitionId)
					.filter(f -> Boolean.TRUE.equals(f.getIsActive()))
					.orElseThrow(() -> new IllegalArgumentException(
							ValidationMessage.CUSTOM_FIELD_NOT_APPLICABLE + customFieldDefinitionId));
			if (!customField.getFieldType().isNumeric()
					|| customField.getFieldType().getCompatibleStrategy() != strategyCode) {
				throw new IllegalArgumentException(
						ValidationMessage.FACTOR_DEFINITION_DATA_SOURCE_INCOMPATIBLE
								+ customField.getFieldType().getCompatibleStrategy());
			}
		}
	}

	/**
	 * PUT /api/settings/factor-definitions/{id}/disable：停用自訂因子。
	 * 這同時也是「刪除」自訂因子的方式——本專案沿用既有慣例（商品類型／
	 * 人工風險選項等既有「設定類」資料皆是如此），不真的刪除資料列，一律
	 * 用 is_active 表示是否生效，停用即等同於軟刪除，見企劃書「保留可追蹤的
	 * 審核紀錄」原則與 SettingsService 類別註解「停用不刪除」的既定作法。
	 *
	 * 停用後 ProductFactorScorer 不再計算這個因子（scoreAll() 只查
	 * findByIsActiveTrue()）。2026-09-20修正：原本只停用 factor_definitions
	 * 這一列，任何模式裡殘留的 evaluation_factors 權重列完全沒有處理——數學上
	 * 停用後這些權重確實不會再被計入分母（factorScores.get(code) 變成 null），
	 * 但畫面上（見 settings.html 的權重編輯器勾選框，2026-09-20新增）
	 * 「已勾選但實際上因子已被全域停用」會顯示成一種矛盾的中間狀態，容易誤導
	 * 管理層以為這個因子還在生效。現在停用時一併把所有模式裡這個因子的權重
	 * 歸零，讓畫面狀態（未勾選）跟實際計算行為（不生效）保持一致。
	 *
	 * 不影響過往已審核商品：review_records.weight_snapshot 是停用當下已經
	 * 凍結的 JSON，跟 evaluation_factors／factor_definitions 都沒有外鍵關聯，
	 * 這裡的批次歸零不會讓任何歷史資料被動改變。
	 */
	@Transactional
	public FactorDefinitionResponse disableFactorDefinition(Long id, String username) {
		FactorDefinition definition = factorDefinitionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException(ValidationMessage.FACTOR_DEFINITION_NOT_FOUND + id));
		definition.setIsActive(false);
		definition.setUpdatedBy(resolveUserId(username));
		FactorDefinition saved = factorDefinitionRepository.save(definition);

		List<EvaluationFactor> affected = evaluationFactorRepository.findByFactorCode(definition.getFactorCode());
		affected.stream().filter(f -> f.getWeight() != null && f.getWeight().signum() > 0).forEach(f -> {
			f.setWeight(BigDecimal.ZERO);
			evaluationFactorRepository.save(f);
		});

		return FactorDefinitionResponse.from(saved);
	}

	/**
	 * PUT /api/settings/factor-definitions/{id}/enable：復用已停用的自訂因子。
	 * 與 disableFactorDefinition() 對稱，比照既有風險選項／商品類型的既定模式。
	 * 不會恢復停用前各模式的權重——重新啟用後預設權重0（未生效），管理層要
	 * 到各模式的權重編輯器裡重新勾選並分配權重，不自動假設要恢復到哪個數字。
	 *
	 * V14新增防呆：如果這一列已經被 updateFactorDefinition() 產生的新版本取代
	 * （existsByPreviousVersionId(id)==true），拒絕啟用——一旦放行，會讓同一個
	 * factorCode 同時存在兩列 isActive=true，版本鏈語意矛盾，且會直接撞上
	 * uk_factor_definitions_active_code 唯一索引在資料庫層丟例外。
	 */
	@Transactional
	public FactorDefinitionResponse enableFactorDefinition(Long id, String username) {
		FactorDefinition definition = factorDefinitionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException(ValidationMessage.FACTOR_DEFINITION_NOT_FOUND + id));
		if (factorDefinitionRepository.existsByPreviousVersionId(id)) {
			throw new IllegalArgumentException(ValidationMessage.FACTOR_DEFINITION_SUPERSEDED_CANNOT_ENABLE + id);
		}
		definition.setIsActive(true);
		definition.setUpdatedBy(resolveUserId(username));
		FactorDefinition saved = factorDefinitionRepository.save(definition);
		return FactorDefinitionResponse.from(saved);
	}

	// ========================= 自訂商品屬性（動態問卷） =========================
	//
	// 「開新計分因子資料源」需求的第一階段：讓管理層自己定義新的商品屬性題目，
	// 不需要改資料庫欄位。這裡先只做題目本身的 CRUD；商品表單動態渲染、答案
	// 讀寫（product_custom_field_values）、以及計分系統把這裡的題目當成
	// FactorDataSource 使用，都是後續階段，這裡先不做。

	/**
	 * GET /api/settings/custom-field-definitions：列出全部題目（含已停用／已被
	 * 編輯取代的歷史版本）。isSuperseded 計算方式同 listFactorDefinitions()。
	 */
	@Transactional(readOnly = true)
	public List<CustomFieldDefinitionResponse> listCustomFieldDefinitions() {
		List<CustomFieldDefinition> definitions = customFieldDefinitionRepository.findAll();
		if (definitions.isEmpty()) {
			return List.of();
		}
		Set<Long> supersededIds = definitions.stream().map(CustomFieldDefinition::getPreviousVersionId)
				.filter(Objects::nonNull).collect(Collectors.toSet());
		List<Long> ids = definitions.stream().map(CustomFieldDefinition::getId).toList();
		// 批次撈品類限制，避免逐題各查一次（N+1）——比照 ScoringService
		// 修過的 toWeightFactorSnapshot() N+1，這裡從一開始就用對的寫法。
		Map<Long, List<Long>> applicableTypesByFieldId = customFieldApplicableTypeRepository
				.findByFieldDefinitionIdIn(ids).stream()
				.collect(Collectors.groupingBy(CustomFieldApplicableType::getFieldDefinitionId,
						Collectors.mapping(CustomFieldApplicableType::getRootProductTypeId, Collectors.toList())));
		return definitions.stream()
				.map(d -> CustomFieldDefinitionResponse.from(d,
						applicableTypesByFieldId.getOrDefault(d.getId(), List.of()), supersededIds.contains(d.getId())))
				.toList();
	}

	/**
	 * GET /api/products/custom-field-schema?productTypeId=X：商品新增/編輯表單
	 * 依這個 productTypeId（商品實際掛的小類）用哪些自訂屬性題目。
	 *
	 * 只回傳「生效中」且「品類範圍涵蓋這個商品的大類（或沒有品類限制）」的
	 * 題目——沿用既有 ScoreBandResolver 的既定作法，商品掛的是小類，但依
	 * 品類判斷一律以大類為準（resolveRootTypeId()）。
	 *
	 * 這支方法同時也是 ProductService 寫入答案時用來驗證「這個欄位代碼
	 * 對這個商品而言是否合法」的依據——見 ProductService.
	 * validateAndCollectCustomFieldValues() 的說明。
	 */
	@Transactional(readOnly = true)
	public List<CustomFieldDefinitionResponse> getApplicableCustomFields(Long productTypeId) {
		Long rootTypeId = productTypeAttributeResolver.resolveRootTypeId(productTypeId);
		List<CustomFieldDefinition> active = customFieldDefinitionRepository.findByIsActiveTrue();
		if (active.isEmpty()) {
			return List.of();
		}
		List<Long> ids = active.stream().map(CustomFieldDefinition::getId).toList();
		Map<Long, List<Long>> applicableTypesByFieldId = customFieldApplicableTypeRepository
				.findByFieldDefinitionIdIn(ids).stream()
				.collect(Collectors.groupingBy(CustomFieldApplicableType::getFieldDefinitionId,
						Collectors.mapping(CustomFieldApplicableType::getRootProductTypeId, Collectors.toList())));
		return active.stream()
				.filter(d -> {
					List<Long> scope = applicableTypesByFieldId.getOrDefault(d.getId(), List.of());
					return scope.isEmpty() || scope.contains(rootTypeId);
				})
				.map(d -> CustomFieldDefinitionResponse.from(d,
						applicableTypesByFieldId.getOrDefault(d.getId(), List.of())))
				.toList();
	}

	/**
	 * POST /api/settings/custom-field-definitions：新增自訂商品屬性題目。
	 *
	 * 三件事要檢查：
	 * <ol>
	 * <li>fieldCode 不能跟其他<b>生效中</b>的題目重複（V14修改：原本是跟
	 *     「全部」題目比對，改成只比對生效中的——已經被編輯取代或刪除的舊
	 *     版本不再佔用這個代碼，允許重新使用，見
	 *     CustomFieldDefinitionRepository.existsByFieldCodeAndIsActiveTrue()）。</li>
	 * <li>applicableRootProductTypeIds 裡每一個 id 都必須是「大類」
	 *     （product_types.level=1）——選到小類會讓商品表單「依大類判斷」的
	 *     邏輯永遠比對不到，這一題實質上永遠不會出現在任何商品表單上，
	 *     寧可在建立當下就擋下來。</li>
	 * <li>scaleLabels 只有 fieldType=SCALE_1_5 才能提供，且 key 必須落在
	 *     1~5 之間（見 validateScaleLabels()，V14新增）。</li>
	 * </ol>
	 *
	 * 省略或傳空陣列＝適用全部品類，不需要另外處理——沒有任何限制列本身就是
	 * 「無限制」的意思，見 CustomFieldApplicableType 類別註解。
	 */
	@Transactional
	public CustomFieldDefinitionResponse createCustomFieldDefinition(CustomFieldDefinitionCreateRequest request,
			String username) {
		String fieldCode = request.getFieldCode().trim().toUpperCase();

		if (customFieldDefinitionRepository.existsByFieldCodeAndIsActiveTrue(fieldCode)) {
			throw new IllegalArgumentException(ValidationMessage.CUSTOM_FIELD_CODE_DUPLICATE + fieldCode);
		}

		List<Long> applicableIds = validateApplicableRootTypes(request.getApplicableRootProductTypeIds());
		validateScaleLabels(request.getFieldType(), request.getScaleLabels());

		Long userId = resolveUserId(username);

		CustomFieldDefinition definition = new CustomFieldDefinition();
		definition.setFieldCode(fieldCode);
		definition.setFieldName(request.getFieldName());
		definition.setHelpText(request.getHelpText());
		definition.setFieldType(request.getFieldType());
		definition.setIsRequired(Boolean.TRUE.equals(request.getIsRequired()));
		definition.setIsActive(true);
		definition.setScaleLabels(request.getScaleLabels());
		definition.setCreatedBy(userId);

		CustomFieldDefinition saved = customFieldDefinitionRepository.save(definition);

		for (Long typeId : applicableIds) {
			CustomFieldApplicableType applicable = new CustomFieldApplicableType();
			applicable.setFieldDefinitionId(saved.getId());
			applicable.setRootProductTypeId(typeId);
			customFieldApplicableTypeRepository.save(applicable);
		}

		log.info("自訂商品屬性已新增：{}，型態 {}，操作者 {}", fieldCode, request.getFieldType(), username);
		return CustomFieldDefinitionResponse.from(saved, applicableIds);
	}

	/**
	 * PUT /api/settings/custom-field-definitions/{id}：編輯自訂商品屬性題目。
	 *
	 * <b>版本鏈設計與 updateFactorDefinition() 完全對稱</b>：停用舊列並flush、
	 * 新增一列（fieldCode 沿用舊列，其餘欄位取自 request，previousVersionId
	 * 指向舊列）。品類範圍是「整份覆蓋」語意，直接依 request 為新列新建，
	 * 不去動舊列既有的品類限制列（歷史保留）。
	 *
	 * <b>與 updateFactorDefinition() 不同、需要額外處理的地方：</b>
	 * custom_field_definitions 被 factor_definitions.custom_field_definition_id
	 * 用 <b>id</b>（不是代碼）參照。編輯後舊列的 id 不變但已停用，任何原本綁定
	 * 這個 id 的生效中因子若不重新綁到新 id，未來新商品填的答案會存在新 id
	 * 底下，但因子還在讀舊 id，等於這個因子從此收不到任何新資料、卻不會有
	 * 任何錯誤訊息（FactorRawValueResolver.resolve() 查無值時回傳null，
	 * 因子安靜地從加權分母排除）。
	 *
	 * <b>因此這裡採用的做法（已與 Gary 確認的預設方案）：</b>編輯時自動把所有
	 * 目前綁定這個題目、且生效中的因子，重新指向新版本的 id。這不算竄改
	 * 歷史：每次審核凍結的 WeightFactorSnapshot.customFieldCode 存的是代碼
	 * 字串（不是id），不受這裡的id搬移影響，可重現性不受影響。若新的
	 * fieldType 導致與某個已綁定因子的 strategyCode 不相容（例如把
	 * SCALE_1_5 改成 TEXT），則整個編輯動作失敗並回滾，要求管理層先處理
	 * 該因子的綁定，不會留下「因子綁定壞掉」的半殘狀態。
	 */
	@Transactional
	public CustomFieldDefinitionResponse updateCustomFieldDefinition(Long id,
			CustomFieldDefinitionUpdateRequest request, String username) {
		CustomFieldDefinition old = customFieldDefinitionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException(ValidationMessage.CUSTOM_FIELD_NOT_FOUND + id));
		if (customFieldDefinitionRepository.existsByPreviousVersionId(id)) {
			throw new IllegalArgumentException(ValidationMessage.CUSTOM_FIELD_SUPERSEDED_CANNOT_ENABLE + id);
		}

		List<Long> applicableIds = validateApplicableRootTypes(request.getApplicableRootProductTypeIds());
		validateScaleLabels(request.getFieldType(), request.getScaleLabels());

		// 先驗證所有綁定這個題目的生效中因子，跟新的fieldType是否還相容，
		// 全部通過才動手改資料，避免中途失敗留下半殘狀態（雖然@Transactional
		// 本身就會整筆回滾，這裡先驗證只是讓錯誤訊息更早、更明確）。
		List<FactorDefinition> boundFactors = factorDefinitionRepository
				.findByCustomFieldDefinitionIdAndIsActiveTrue(id);
		for (FactorDefinition factor : boundFactors) {
			if (!request.getFieldType().isNumeric()
					|| request.getFieldType().getCompatibleStrategy() != factor.getStrategyCode()) {
				throw new IllegalArgumentException(ValidationMessage.FACTOR_DEFINITION_DATA_SOURCE_INCOMPATIBLE
						+ "此屬性目前被自訂因子「" + factor.getFactorCode() + "」使用中，新的欄位型態與該因子不相容，"
						+ "請先調整該因子的設定：" + factor.getFactorCode());
			}
		}

		Long userId = resolveUserId(username);
		boolean wasActive = Boolean.TRUE.equals(old.getIsActive());

		old.setIsActive(false);
		old.setUpdatedBy(userId);
		customFieldDefinitionRepository.saveAndFlush(old);

		CustomFieldDefinition updated = new CustomFieldDefinition();
		updated.setFieldCode(old.getFieldCode());
		updated.setFieldName(request.getFieldName());
		updated.setHelpText(request.getHelpText());
		updated.setFieldType(request.getFieldType());
		updated.setIsRequired(Boolean.TRUE.equals(request.getIsRequired()));
		updated.setIsActive(wasActive);
		updated.setScaleLabels(request.getScaleLabels());
		updated.setPreviousVersionId(old.getId());
		updated.setCreatedBy(userId);

		CustomFieldDefinition saved = customFieldDefinitionRepository.save(updated);

		for (Long typeId : applicableIds) {
			CustomFieldApplicableType applicable = new CustomFieldApplicableType();
			applicable.setFieldDefinitionId(saved.getId());
			applicable.setRootProductTypeId(typeId);
			customFieldApplicableTypeRepository.save(applicable);
		}

		for (FactorDefinition factor : boundFactors) {
			factor.setCustomFieldDefinitionId(saved.getId());
			factor.setUpdatedBy(userId);
			factorDefinitionRepository.save(factor);
		}

		log.info("自訂商品屬性已編輯：{}（新版本id={}，取代舊版本id={}），連動改綁因子數={}，操作者 {}",
				saved.getFieldCode(), saved.getId(), old.getId(), boundFactors.size(), username);
		return CustomFieldDefinitionResponse.from(saved, applicableIds);
	}

	/** 共用驗證：品類範圍每一個id都必須是大類，省略或空陣列＝適用全部品類。 */
	private List<Long> validateApplicableRootTypes(List<Long> applicableRootProductTypeIds) {
		List<Long> applicableIds = applicableRootProductTypeIds == null ? List.of() : applicableRootProductTypeIds;
		for (Long typeId : applicableIds) {
			ProductType type = productTypeRepository.findById(typeId).orElse(null);
			if (type == null || !Integer.valueOf(1).equals(type.getLevel())) {
				throw new IllegalArgumentException(ValidationMessage.CUSTOM_FIELD_ROOT_TYPE_INVALID + typeId);
			}
		}
		return applicableIds;
	}

	/**
	 * 共用驗證：scaleLabels 只有 fieldType=SCALE_1_5 才能提供（其餘型態送這個
	 * 欄位視為設定錯誤，直接拒絕，避免產生永遠不會被讀取的死資料），且提供時
	 * 每個 key 必須落在 1~5 之間。createCustomFieldDefinition() 與
	 * updateCustomFieldDefinition() 共用。
	 */
	private void validateScaleLabels(CustomFieldType fieldType, Map<Integer, String> scaleLabels) {
		if (scaleLabels == null || scaleLabels.isEmpty()) {
			return;
		}
		if (fieldType != CustomFieldType.SCALE_1_5) {
			throw new IllegalArgumentException(ValidationMessage.CUSTOM_FIELD_SCALE_LABEL_NOT_APPLICABLE);
		}
		for (Integer key : scaleLabels.keySet()) {
			if (key == null || key < 1 || key > 5) {
				throw new IllegalArgumentException(ValidationMessage.CUSTOM_FIELD_SCALE_LABEL_KEY_INVALID + key);
			}
		}
	}

	/**
	 * PUT /api/settings/custom-field-definitions/{id}/disable：停用題目。
	 * 這同時也是「刪除」自訂商品屬性的方式，理由同 disableFactorDefinition()
	 * 類別註解——本專案的「設定類」資料一律用 is_active 表示是否生效，不真的
	 * 刪除資料列。
	 *
	 * 停用後商品新增/編輯表單不會再顯示這一題，但商品身上已經填過的答案
	 * 完全不受影響——product_custom_field_values 的既有列不會被刪除或修改，
	 * 商品詳情頁仍會顯示（唯讀）。這是稀疏表 EAV 設計天生的特性：停用一個
	 * 題目定義，不代表「刪除大家的答案」，兩件事本來就分開。
	 */
	@Transactional
	public CustomFieldDefinitionResponse disableCustomFieldDefinition(Long id, String username) {
		CustomFieldDefinition definition = customFieldDefinitionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException(ValidationMessage.CUSTOM_FIELD_NOT_FOUND + id));
		definition.setIsActive(false);
		definition.setUpdatedBy(resolveUserId(username));
		CustomFieldDefinition saved = customFieldDefinitionRepository.save(definition);
		List<Long> applicableIds = customFieldApplicableTypeRepository.findByFieldDefinitionId(id).stream()
				.map(CustomFieldApplicableType::getRootProductTypeId).toList();
		return CustomFieldDefinitionResponse.from(saved, applicableIds);
	}

	/**
	 * PUT /api/settings/custom-field-definitions/{id}/enable：復用已停用的題目，
	 * 與 disable 對稱。V14新增防呆：理由與 enableFactorDefinition() 完全對稱，
	 * 擋下「重新啟用一個已被 updateCustomFieldDefinition() 取代的舊題目」。
	 */
	@Transactional
	public CustomFieldDefinitionResponse enableCustomFieldDefinition(Long id, String username) {
		CustomFieldDefinition definition = customFieldDefinitionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException(ValidationMessage.CUSTOM_FIELD_NOT_FOUND + id));
		if (customFieldDefinitionRepository.existsByPreviousVersionId(id)) {
			throw new IllegalArgumentException(ValidationMessage.CUSTOM_FIELD_SUPERSEDED_CANNOT_ENABLE + id);
		}
		definition.setIsActive(true);
		definition.setUpdatedBy(resolveUserId(username));
		CustomFieldDefinition saved = customFieldDefinitionRepository.save(definition);
		List<Long> applicableIds = customFieldApplicableTypeRepository.findByFieldDefinitionId(id).stream()
				.map(CustomFieldApplicableType::getRootProductTypeId).toList();
		return CustomFieldDefinitionResponse.from(saved, applicableIds);
	}

	/**
	 * GET /api/settings/evaluation-mode/current：取得目前生效模式。
	 * 實作依企劃書備註：先讀system_settings取得
	 * setting_key='current_evaluation_mode_id'的值，再查evaluation_modes取得完整資料。
	 */
	@Transactional(readOnly = true)
	public EvaluationModeResponse getCurrentEvaluationMode() {
		EvaluationMode mode = findCurrentEvaluationModeOrThrow();
		return EvaluationModeResponse.from(mode);
	}

	/**
	 * PUT /api/settings/evaluation-mode/current：切換目前生效模式。
	 *
	 * 只能切換成既有3套模式其中之一，不能直接修改既有模式的固定權重；
	 * 寫入前需驗證目標evaluation_mode_id存在，再UPDATE system_settings的對應值
	 * （見企劃書四-14設計取捨）。
	 */
	@Transactional
	public EvaluationModeResponse switchCurrentEvaluationMode(SwitchEvaluationModeRequest request, String username) {
		EvaluationMode targetMode = evaluationModeRepository.findById(request.getEvaluationModeId())
				.orElseThrow(() -> new IllegalArgumentException("評估模式不存在"));

		Long userId = resolveUserId(username);

		SystemSetting setting = systemSettingRepository.findById(CURRENT_EVALUATION_MODE_KEY)
				.orElseThrow(() -> new SystemConfigurationException("尚未設定目前生效模式，請先透過資料庫初始化此設定"));
		setting.setSettingValue(String.valueOf(targetMode.getId()));
		setting.setUpdatedBy(userId);
		// updatedAt不在此手動賦值：SystemSetting已標註@UpdateTimestamp，由Hibernate
		// 自動填入，與其餘Entity的處理方式一致。兩套機制並存反而容易讓後續維護者
		// 誤以為必須手動維護這個欄位。
		systemSettingRepository.save(setting);

		return EvaluationModeResponse.from(targetMode);
	}

	private EvaluationMode findCurrentEvaluationModeOrThrow() {
		String rawModeId = systemSettingRepository.findById(CURRENT_EVALUATION_MODE_KEY)
				.map(SystemSetting::getSettingValue)
				.orElseThrow(() -> new SystemConfigurationException("尚未設定目前生效模式"));
		Long modeId;
		try {
			modeId = Long.valueOf(rawModeId);
		} catch (NumberFormatException e) {
			throw new SystemConfigurationException("目前生效模式設定值格式錯誤：" + rawModeId);
		}
		return evaluationModeRepository.findById(modeId)
				.orElseThrow(() -> new SystemConfigurationException("目前生效模式指向不存在的評估模式：" + modeId));
	}

	// ========================= 人工風險選項 =========================

	/**
	 * GET /api/settings/risk-options：取得全部人工風險選項（含系統預設與自訂）。
	 *
	 * 刻意用findAll()而非review流程用的findByIsActiveTrue()：這是管理視角的
	 * 設定清單，管理層應該能看到包含已停用的完整清單，不像審核頁只需要顯示
	 * 「目前可勾選」的選項——兩個端點的用途不同，篩選規則本來就不該一樣。
	 *
	 * 回應改用RiskOptionSettingResponse（非審核頁用的RiskOptionResponse）：
	 * 設定頁需要顯示alertKeywords／isActive，理由見該DTO類別註解。
	 */
	@Transactional(readOnly = true)
	public List<RiskOptionSettingResponse> getAllRiskOptions() {
		return riskOptionRepository.findAll().stream().map(RiskOptionSettingResponse::from).toList();
	}

	/**
	 * POST /api/settings/risk-options：新增自訂人工風險類型。
	 *
	 * 新增後立即出現在審核頁的可勾選清單（該清單讀is_active=true者），
	 * 不需要額外的啟用步驟。
	 *
	 * 不檢查name是否重複：風險選項名稱沒有唯一性約束，實務上也可能存在
	 * 語意相近但描述不同的兩個項目（例如「供貨風險」與「季節性供貨風險」），
	 * 由管理層自行判斷是否重複，系統不代為阻擋。
	 */
	@Transactional
	public RiskOptionSettingResponse createRiskOption(RiskOptionCreateRequest request, String username) {
		Long userId = resolveUserId(username);

		RiskOption option = new RiskOption();
		option.setName(request.getName());
		option.setDescription(request.getDescription());
		option.setAlertKeywords(request.getAlertKeywords());
		option.setIsSystemDefault(false);
		option.setCreatedBy(userId);

		RiskOption saved = riskOptionRepository.save(option);
		return RiskOptionSettingResponse.from(saved);
	}

	/**
	 * PUT /api/settings/risk-options/{id}：重新命名／調整風險選項（name／
	 * description／alertKeywords）。
	 *
	 * 與updateProductType()同一套設計：不檢查name重複（理由同createRiskOption()）；
	 * isSystemDefault／isActive不受此方法影響，isActive維持由disable/enable
	 * 專責管理。alertKeywords允許改成空字串或null，代表退出Dashboard的自動
	 * 示警比對（見RiskOptionUpdateRequest類別註解），不特別擋。
	 */
	@Transactional
	public RiskOptionSettingResponse updateRiskOption(Long id, RiskOptionUpdateRequest request) {
		RiskOption option = riskOptionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("風險選項不存在"));
		option.setName(request.getName());
		option.setDescription(request.getDescription());
		option.setAlertKeywords(request.getAlertKeywords());
		RiskOption saved = riskOptionRepository.save(option);
		return RiskOptionSettingResponse.from(saved);
	}

	/**
	 * PUT /api/settings/risk-options/{id}/disable：停用人工風險選項。
	 *
	 * 停用後不再出現於審核頁的可勾選清單（該清單讀is_active=true者，見
	 * ReviewService/RiskOptionRepository.findByIsActiveTrue()），但既有
	 * review_risks歷史紀錄的關聯不受影響——與商品類型停用是同一套設計
	 * （只停用不刪除、歷史資料仍保留關聯），故此處直接複用相同模式。
	 * 冪等：重複停用已停用的選項不視為錯誤。
	 */
	@Transactional
	public RiskOptionSettingResponse disableRiskOption(Long id) {
		RiskOption option = riskOptionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("風險選項不存在"));
		option.setIsActive(false);
		RiskOption saved = riskOptionRepository.save(option);
		return RiskOptionSettingResponse.from(saved);
	}

	/**
	 * PUT /api/settings/risk-options/{id}/enable：復用（重新啟用）已停用的風險選項。
	 * 與disableRiskOption()對稱，冪等處理。
	 */
	@Transactional
	public RiskOptionSettingResponse enableRiskOption(Long id) {
		RiskOption option = riskOptionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("風險選項不存在"));
		option.setIsActive(true);
		RiskOption saved = riskOptionRepository.save(option);
		return RiskOptionSettingResponse.from(saved);
	}

	// ========================= 天氣訊號標籤對照 =========================

	/**
	 * GET /api/settings/weather-signal-tags：取得全部天氣訊號標籤對照（含系統
	 * 預設與自訂、含已停用）。管理視角，比照 getAllRiskOptions() 用 findAll()
	 * 而非只查生效中的——WeatherCampaignSyncService 同步時才只讀生效中的列
	 * （見 findByIsActiveTrue()），兩者用途不同。
	 */
	@Transactional(readOnly = true)
	public List<WeatherSignalTagMappingResponse> getAllWeatherSignalTagMappings() {
		return weatherSignalTagMappingRepository.findAll().stream()
				.map(WeatherSignalTagMappingResponse::from).toList();
	}

	/**
	 * GET /api/settings/weather-signal-tags/options：操作角色也能打（不加
	 * @PreAuthorize，比照 getAllFestiveCampaigns() 的既有慣例）。只回傳
	 * isActive=true 的列，且只回傳 WeatherSignalTagOptionResponse 的三個
	 * 欄位（不含 id／isSystemDefault 等管理用 metadata）——這支是給商品表單
	 * 「可選標籤」下拉用的查詢端點，跟上面 getAllWeatherSignalTagMappings()
	 * 這支設定頁 CRUD 用的管理端點分開，語意與權限都不同，不要合併。
	 */
	@Transactional(readOnly = true)
	public List<WeatherSignalTagOptionResponse> getActiveWeatherSignalTagOptions() {
		return weatherSignalTagMappingRepository.findByIsActiveTrue().stream()
				.map(WeatherSignalTagOptionResponse::from).toList();
	}

	/**
	 * POST /api/settings/weather-signal-tags：新增一筆天氣訊號 → 商品標籤對照。
	 *
	 * 新增後下一次天氣同步（排程或手動觸發）就會採計這筆對照，不需要另外
	 * 啟用步驟——與 createRiskOption() 同樣的「新增就是要用」直覺。
	 */
	@Transactional
	public WeatherSignalTagMappingResponse createWeatherSignalTagMapping(
			WeatherSignalTagMappingCreateRequest request, String username) {
		validateWeatherSignalTagMapping(request.getWeatherSignalType(), request.getTag());

		Long userId = resolveUserId(username);

		WeatherSignalTagMapping mapping = new WeatherSignalTagMapping();
		mapping.setWeatherSignalType(request.getWeatherSignalType());
		mapping.setTag(request.getTag());
		mapping.setMatchTier(request.getMatchTier());
		mapping.setIsActive(true);
		mapping.setIsSystemDefault(false);
		mapping.setCreatedBy(userId);

		WeatherSignalTagMapping saved = weatherSignalTagMappingRepository.save(mapping);
		log.info("天氣訊號標籤對照已新增：{} -> {}（{}），操作者={}", request.getWeatherSignalType(), request.getTag(),
				request.getMatchTier(), username);
		return WeatherSignalTagMappingResponse.from(saved);
	}

	/**
	 * 共用驗證：weatherSignalType 不可為 NORMAL（一般天氣不該命中任何商品，
	 * 見 WeatherSignalTagMapping 類別註解），且同一組合不可有兩筆同時生效中
	 * ——重複的話 WeatherCampaignSyncService 同步時會對同一個天氣類型套用
	 * 兩筆權重不同的規則，語意上不知道該採哪一筆。
	 */
	private void validateWeatherSignalTagMapping(WeatherSignalType weatherSignalType, String tag) {
		if (weatherSignalType == WeatherSignalType.NORMAL) {
			throw new IllegalArgumentException(ValidationMessage.WEATHER_SIGNAL_TYPE_NORMAL_NOT_ALLOWED);
		}
		if (weatherSignalTagMappingRepository.existsByWeatherSignalTypeAndTagAndIsActiveTrue(weatherSignalType, tag)) {
			throw new IllegalArgumentException(
					ValidationMessage.WEATHER_SIGNAL_TAG_MAPPING_DUPLICATE + weatherSignalType + " -> " + tag);
		}
	}

	/**
	 * PUT /api/settings/weather-signal-tags/{id}：調整命中權重層級。不開放改
	 * weatherSignalType／tag，見 WeatherSignalTagMappingUpdateRequest 類別註解。
	 */
	@Transactional
	public WeatherSignalTagMappingResponse updateWeatherSignalTagMapping(Long id,
			WeatherSignalTagMappingUpdateRequest request, String username) {
		WeatherSignalTagMapping mapping = weatherSignalTagMappingRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("天氣訊號標籤對照不存在"));
		Long userId = resolveUserId(username);

		mapping.setMatchTier(request.getMatchTier());
		mapping.setUpdatedBy(userId);
		WeatherSignalTagMapping saved = weatherSignalTagMappingRepository.save(mapping);
		return WeatherSignalTagMappingResponse.from(saved);
	}

	/**
	 * PUT /api/settings/weather-signal-tags/{id}/disable：停用（含系統預設列，
	 * 比照 risk_options「is_system_default 不可刪除，僅可停用」的既有原則，
	 * 這裡沒有另外擋 isSystemDefault=true——管理層若判斷某筆系統預設對照
	 * 已不合時宜，應該可以停用，只是不能刪除，兩者是不同的限制）。停用後
	 * WeatherCampaignSyncService 下一次同步就不再採計；已經 upsert 過的
	 * 天氣檔期／festive_campaign_tags 不會被回溯修改，跟既有檔期同步邏輯
	 * 一致（見該服務類別註解）。冪等：重複停用不視為錯誤。
	 */
	@Transactional
	public WeatherSignalTagMappingResponse disableWeatherSignalTagMapping(Long id, String username) {
		WeatherSignalTagMapping mapping = weatherSignalTagMappingRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("天氣訊號標籤對照不存在"));
		mapping.setIsActive(false);
		mapping.setUpdatedBy(resolveUserId(username));
		WeatherSignalTagMapping saved = weatherSignalTagMappingRepository.save(mapping);
		return WeatherSignalTagMappingResponse.from(saved);
	}

	/**
	 * PUT /api/settings/weather-signal-tags/{id}/enable：復用。重新啟用前一樣
	 * 要檢查會不會跟另一筆生效中的對照撞組合，避免復用後又立刻產生重複規則
	 * ——這點 disable 不需要檢查（停用只會減少組合，不會製造重複）。
	 */
	@Transactional
	public WeatherSignalTagMappingResponse enableWeatherSignalTagMapping(Long id, String username) {
		WeatherSignalTagMapping mapping = weatherSignalTagMappingRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("天氣訊號標籤對照不存在"));
		if (!Boolean.TRUE.equals(mapping.getIsActive())
				&& weatherSignalTagMappingRepository.existsByWeatherSignalTypeAndTagAndIsActiveTrue(
						mapping.getWeatherSignalType(), mapping.getTag())) {
			throw new IllegalArgumentException(
					ValidationMessage.WEATHER_SIGNAL_TAG_MAPPING_DUPLICATE
							+ mapping.getWeatherSignalType() + " -> " + mapping.getTag());
		}
		mapping.setIsActive(true);
		mapping.setUpdatedBy(resolveUserId(username));
		WeatherSignalTagMapping saved = weatherSignalTagMappingRepository.save(mapping);
		return WeatherSignalTagMappingResponse.from(saved);
	}

	// ========================= 地域占比設定（region_weights） =========================
	// 2026-09-23新增：地域性影響評分方案B+D決議。WeatherCampaignSyncService
	// 同步天氣檔期時，依這裡設定的占比計算 region_coverage_ratio（見該類別
	// 與 FestiveCampaign 類別的欄位註解）。四區固定，不開放新增/刪除，只開放
	// 調整占比，權限比照因子權重編輯（updateEvaluationModeFactors()）一律
	// [僅管理]。

	/**
	 * GET /api/settings/region-weights：取得四區目前的占比設定。
	 *
	 * 固定回傳 WeatherRegionConfig.REGION_CITIES 的四個 key，即使
	 * region_weights 資料表因故缺列（理論上不會，V20 migration 已種好四筆），
	 * 也用 25.00 補齊，不讓畫面因為缺一區而整頁掛掉——比照本類別其餘
	 * 「資料異常時保守處理，不讓單一筆壞資料波及整個查詢」的既有原則。
	 */
	@Transactional(readOnly = true)
	public List<RegionWeightResponse> getRegionWeights() {
		Map<String, RegionWeight> byRegion = regionWeightRepository.findAll().stream()
				.collect(Collectors.toMap(RegionWeight::getRegion, r -> r));

		// 固定順序（北→中→南→東）：Map.of()（WeatherRegionConfig.REGION_CITIES
		// 的底層實作）的 keySet() 迭代順序不保證穩定，直接依它輸出會讓畫面上
		// 四區列的順序在不同次伺服器重啟之間跳動，改用固定順序清單。
		return List.of("NORTH", "CENTRAL", "SOUTH", "EAST").stream()
				.map(region -> {
					RegionWeight weight = byRegion.get(region);
					if (weight == null) {
						weight = new RegionWeight();
						weight.setRegion(region);
						weight.setWeightPercentage(BigDecimal.valueOf(25.00));
					}
					return RegionWeightResponse.from(weight);
				})
				.toList();
	}

	/**
	 * PUT /api/settings/region-weights：整份覆蓋四區占比，加總須為100
	 * （比照 updateEvaluationModeFactors() 的既有作法，見 RegionWeightUpdateRequest
	 * 類別註解）。
	 *
	 * 這裡刻意不觸發既有天氣檔期的 region_coverage_ratio 重算——已經同步落地
	 * 的檔期維持同步當下凍結的值，比照 WeightSnapshot 的再現性原則（見
	 * FestiveCampaign.regionCoverageRatio 欄位註解），新占比從下一次
	 * WeatherCampaignSyncService 排程（每天05:00）或手動觸發同步開始生效。
	 */
	@Transactional
	public List<RegionWeightResponse> updateRegionWeights(RegionWeightUpdateRequest request, String username) {
		Set<String> validRegions = WeatherRegionConfig.REGION_CITIES.keySet();

		Map<String, BigDecimal> incoming = new LinkedHashMap<>();
		for (RegionWeightUpdateRequest.RegionWeightItem item : request.getRegionWeights()) {
			if (!validRegions.contains(item.getRegion())) {
				throw new IllegalArgumentException(ValidationMessage.REGION_WEIGHT_UNKNOWN_REGION + item.getRegion());
			}
			incoming.put(item.getRegion(), item.getWeightPercentage());
		}

		Set<String> missing = new java.util.LinkedHashSet<>(validRegions);
		missing.removeAll(incoming.keySet());
		if (!missing.isEmpty()) {
			throw new IllegalArgumentException(ValidationMessage.REGION_WEIGHT_MISSING_REGION + missing);
		}

		// 加總必須恰為100。用 compareTo 而非 equals——BigDecimal 的 equals 會比較
		// scale，100 與 100.00 用 equals 判定為不相等，會誤擋正確的輸入（同
		// updateEvaluationModeFactors() 的既有註解）。
		BigDecimal sum = incoming.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		if (sum.compareTo(BigDecimal.valueOf(100)) != 0) {
			throw new IllegalArgumentException(
					ValidationMessage.REGION_WEIGHT_SUM_NOT_100 + sum.stripTrailingZeros().toPlainString());
		}

		// 全部通過才寫入。updated_at 由 @UpdateTimestamp 自動填，不用手動 set。
		Long operatorId = resolveUserId(username);
		Map<String, RegionWeight> existing = regionWeightRepository.findAll().stream()
				.collect(Collectors.toMap(RegionWeight::getRegion, r -> r));

		for (Map.Entry<String, BigDecimal> entry : incoming.entrySet()) {
			RegionWeight weight = existing.get(entry.getKey());
			if (weight == null) {
				weight = new RegionWeight();
				weight.setRegion(entry.getKey());
			}
			weight.setWeightPercentage(entry.getValue());
			weight.setUpdatedBy(operatorId);
			regionWeightRepository.save(weight);
		}

		log.info("區域占比設定已更新：{}，操作者={}", incoming, username);
		return getRegionWeights();
	}

	// ========================= 核心客群設定 =========================

	/**
	 * GET /api/settings/audience-profile：取得目前使用中的核心客群設定。
	 */
	@Transactional(readOnly = true)
	public AudienceProfileResponse getActiveAudienceProfile() {
		AudienceProfile profile = findActiveAudienceProfileOrThrow();
		return AudienceProfileResponse.from(profile);
	}

	/**
	 * PUT /api/settings/audience-profile：整份覆蓋目前使用中的核心客群設定。
	 * 不新增版本、不切換is_active——直接更新既有那一筆（見類別註解）。
	 */
	@Transactional
	public AudienceProfileResponse updateActiveAudienceProfile(AudienceProfileUpdateRequest request) {
		// 單欄位值域（0~150）已由DTO的@Min／@Max攔截，這裡只驗證跨欄位的大小關係。
		// ageMin > ageMax會讓客群設定變成空區間，任何商品都比對不到。
		if (request.getAgeMin() != null && request.getAgeMax() != null
				&& request.getAgeMin() > request.getAgeMax()) {
			throw new IllegalArgumentException(ValidationMessage.AUDIENCE_AGE_RANGE_INVALID);
		}

		AudienceProfile profile = findActiveAudienceProfileOrThrow();
		profile.setName(request.getName());
		profile.setAgeMin(request.getAgeMin());
		profile.setAgeMax(request.getAgeMax());
		profile.setPriceSensitivity(request.getPriceSensitivity());
		profile.setPreferenceDescription(request.getPreferenceDescription());
		profile.setKeywords(request.getKeywords());
		AudienceProfile saved = audienceProfileRepository.save(profile);
		return AudienceProfileResponse.from(saved);
	}

	/**
	 * 檔期起訖日期關係驗證。
	 *
	 * startDate晚於endDate時，ScoringService.calculateUrgencyFactor()算出的
	 * 剩餘天數會是負數，讓節慶加成的急迫係數完全失真——而節慶加成是直接加在
	 * finalScore上的，錯誤會一路傳到商品排序與審核快照。
	 *
	 * 單一日期的必填由DTO的@NotNull攔截，這裡只處理兩個欄位之間的關係。
	 */
	private void validateCampaignDateRange(LocalDate startDate, LocalDate endDate) {
		if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
			throw new IllegalArgumentException(ValidationMessage.CAMPAIGN_DATE_RANGE_INVALID);
		}
	}

	private AudienceProfile findActiveAudienceProfileOrThrow() {
		return audienceProfileRepository.findByIsActiveTrue().stream().findFirst()
				.orElseThrow(() -> new SystemConfigurationException("尚未設定使用中的核心客群"));
	}

	// ========================= 商品類型設定 =========================

	/**
	 * GET /api/settings/product-types：取得所有商品分類（含系統預設9類與自訂分類）。
	 */
	@Transactional(readOnly = true)
	public List<ProductTypeResponse> getAllProductTypes() {
		// 使用品項數：先前這個統計恆為 null（前端註解明確寫著「後端沒有這個
		// 統計」），導致設定頁「使用品項」欄位永遠顯示「—」，看起來像資料
		// 缺漏。一次 GROUP BY 查出所有品類的使用數量，再用 Map 對照填入，
		// 不對每個品類各自查一次（避免 N+1）。
		Map<Long, Long> usedCountByTypeId = productRepository.countGroupedByProductType().stream()
				.collect(java.util.stream.Collectors.toMap(
						row -> (Long) row[0],
						row -> (Long) row[1]));

		return productTypeRepository.findAll().stream()
				.map(type -> {
					ProductTypeResponse dto = ProductTypeResponse.from(type);
					dto.setUsedCount(usedCountByTypeId.getOrDefault(type.getId(), 0L));
					return dto;
				})
				.toList();
	}

	/**
	 * POST /api/settings/product-types：新增自訂商品分類（isSystemDefault固定為false）。
	 *
	 * <b>刻意不檢查name是否重複（已與團隊確認，非疏漏，與ProductService.createProduct()
	 * 同一個決策範圍）：</b>同名分類可能分屬不同管理脈絡下建立，且屬於管理層低頻、
	 * 少量的操作，人眼就看得出是否重複，由管理層自行判斷，系統不代為阻擋。
	 */
	@Transactional
	/**
	 * POST /api/settings/product-types：新增自訂商品分類（isSystemDefault固定為false）。
	 *
	 * ⚠️ 2026-09-17補上大類／小類支援：
	 * - request.parentId為null → 新增大類，level沿用entity預設值1，
	 *   parentId維持null，直接生成，不需要額外檢查。
	 * - request.parentId有值 → 新增小類：先確認這個id存在、而且本身是
	 *   大類（level=1）——不允許小類底下再掛小類，這個體系只有兩層；
	 *   通過檢查後level設為2、parentId設為指定的大類id。
	 */
	public ProductTypeResponse createProductType(ProductTypeCreateRequest request, String username) {
		Long userId = resolveUserId(username);

		ProductType type = new ProductType();
		type.setName(request.getName());
		type.setDescription(request.getDescription());
		type.setIsSystemDefault(false);
		type.setCreatedBy(userId);

		Long parentId = request.getParentId();
		if (parentId != null) {
			ProductType parent = productTypeRepository.findById(parentId)
					.orElseThrow(() -> new IllegalArgumentException("指定的大類不存在"));
			if (parent.getLevel() != null && parent.getLevel() != 1) {
				throw new IllegalArgumentException("只能選擇大類作為小類的上層分類，不能掛在另一個小類底下");
			}
			type.setParentId(parentId);
			type.setLevel(2);
		}

		ProductType saved = productTypeRepository.save(type);
		return ProductTypeResponse.from(saved);
	}

	/**
	 * PUT /api/settings/product-types/{id}：重新命名既有分類（更新name／description）。
	 *
	 * 不檢查name是否重複：與createProductType()同一個決策範圍，理由相同
	 * （管理層低頻、少量操作，人眼就看得出是否重複，系統不代為阻擋）。
	 *
	 * isSystemDefault／isActive不受此方法影響：前者是分類建立時就固定的身分，
	 * 改名不改變身分本身；後者由disableProductType()/enableProductType()
	 * 專責管理，這裡不重複賦值，避免PUT request若忘記帶正確狀態時意外覆蓋。
	 */
	@Transactional
	public ProductTypeResponse updateProductType(Long id, ProductTypeUpdateRequest request) {
		ProductType type = productTypeRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("商品類型不存在"));
		type.setName(request.getName());
		type.setDescription(request.getDescription());
		ProductType saved = productTypeRepository.save(type);
		return ProductTypeResponse.from(saved);
	}

	/**
	 * PUT /api/settings/product-types/{id}/disable：停用分類（該分類已被品項使用
	 * 時的建議做法）。
	 *
	 * 重複停用已停用的分類不視為錯誤（冪等），與UserService.disableUser()同一套
	 * 判斷原則：結果狀態與呼叫端的意圖一致，沒有理由回報失敗。
	 *
	 * ⚠️ 2026-09-17補上大類→小類的連動停用：原本這裡不管大類小類，一律只
	 * 改這一筆自己的isActive，停用一個大類時，底下的小類完全不受影響、
	 * 繼續維持啟用——一個「已停用」的大類，底下卻掛著看似正常可用的小類，
	 * 對操作人員來說是矛盾的畫面（大類都說不能用了，小類憑什麼還能選）。
	 * 只在停用「大類」（level=1）時才觸發這個連動，停用小類本身不會影響
	 * 任何其他分類。
	 *
	 * 只單向連動（大類→小類），不做反向：enableProductType()重新啟用大類
	 * 時，不會跟著重新啟用小類（見該方法註解）——因為沒辦法區分「這個
	 * 小類是因為大類被停用才跟著停用」還是「這個小類本來就是獨立被停用
	 * 的」，貿然復用可能把使用者原本刻意停用的小類意外復活。
	 */
	@Transactional
	public ProductTypeResponse disableProductType(Long id) {
		ProductType type = productTypeRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("商品類型不存在"));
		type.setIsActive(false);
		ProductType saved = productTypeRepository.save(type);

		if (saved.getLevel() != null && saved.getLevel() == 1) {
			List<ProductType> activeChildren = productTypeRepository
					.findByParentIdAndIsActiveTrueOrderBySortOrderAsc(id);
			for (ProductType child : activeChildren) {
				child.setIsActive(false);
			}
			productTypeRepository.saveAll(activeChildren);
		}

		return ProductTypeResponse.from(saved);
	}

	/**
	 * PUT /api/settings/product-types/{id}/enable：復用（重新啟用）已停用的分類。
	 *
	 * 補上此端點以取代原本「企劃書只定義停用、不提供啟用」的決策：被停用的
	 * 分類若無法復用，管理層誤停用後只能重新新增一筆同名分類，但新分類的id
	 * 與既有品項的product_type_id並不相同，等於沒有真正解決問題，故改為
	 * 提供對稱的enable端點。與disableProductType()同樣採冪等處理。
	 */
	@Transactional
	public ProductTypeResponse enableProductType(Long id) {
		ProductType type = productTypeRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("商品類型不存在"));
		type.setIsActive(true);
		ProductType saved = productTypeRepository.save(type);
		return ProductTypeResponse.from(saved);
	}

	/**
	 * DELETE /api/settings/product-types/{id}：條件式刪除。
	 *
	 * 判定範圍不分品項狀態——即使該分類底下的品項全數為ARCHIVED（已封存），
	 * 仍視為「使用中」而拒絕刪除，避免刪除後歷史品項的分類欄位失去對應資料
	 * （企劃書原文備註）。ProductRepository.existsByProductTypeId()本身就是
	 * 依這個規則設計（不加item_status篩選），直接複用即可。
	 *
	 * 2026-09-17修正：商品只允許掛在小類（level=2，見ProductType.level欄位
	 * 註解），所以大類（level=1）的existsByProductTypeId()恆為false——
	 * 舊版本只檢查這一項，導致「刪除還有小類的大類」這個操作完全沒被擋下，
	 * 一路走到productTypeRepository.deleteById()才被parent_id的DB層FK
	 * 約束（V2 migration的fk_product_type_parent，沒有設定ON DELETE
	 * CASCADE／SET NULL）擋下，拋出DataIntegrityViolationException，
	 * 最終被GlobalExceptionHandler的保底規則包成500「伺服器發生錯誤，
	 * 請稍後再試」，使用者完全看不出真正原因（其實是子類還在，不是伺服器
	 * 壞了）。補上existsByParentId()檢查，主動擋在真正撞到FK之前，回報
	 * 看得懂的原因。
	 */
	@Transactional
	public void deleteProductType(Long id) {
		if (!productTypeRepository.existsById(id)) {
			throw new IllegalArgumentException("商品類型不存在");
		}
		if (productRepository.existsByProductTypeId(id)) {
			throw new IllegalStateException("此商品類型已有品項使用，無法刪除，請改用停用");
		}
		if (productTypeRepository.existsByParentId(id)) {
			throw new IllegalStateException("此大類底下仍有小類，請先刪除或搬移小類，無法直接刪除");
		}
		productTypeRepository.deleteById(id);
	}

	// ========================= 節慶檔期管理 =========================

	/**
	 * GET /api/settings/festive-campaigns：取得所有檔期設定。
	 */
	@Transactional(readOnly = true)
	public List<FestiveCampaignResponse> getAllFestiveCampaigns() {
		return festiveCampaignRepository.findAll().stream().map(this::toFestiveCampaignResponse).toList();
	}

	/**
	 * POST /api/settings/festive-campaigns：新增檔期。campaignCode需唯一，
	 * campaign_status固定從UPCOMING開始（見FestiveCampaignCreateRequest類別註解）。
	 */
	@Transactional
	public FestiveCampaignResponse createFestiveCampaign(FestiveCampaignCreateRequest request) {
		if (festiveCampaignRepository.findByCampaignCode(request.getCampaignCode()).isPresent()) {
			throw new IllegalArgumentException("檔期代碼已存在：" + request.getCampaignCode());
		}
		validateCampaignDateRange(request.getStartDate(), request.getEndDate());

		FestiveCampaign campaign = new FestiveCampaign();
		campaign.setCampaignCode(request.getCampaignCode());
		campaign.setCampaignName(request.getCampaignName());
		campaign.setCategory(request.getCategory());
		campaign.setStartDate(request.getStartDate());
		campaign.setEndDate(request.getEndDate());
		if (request.getPreparationLeadDays() != null) {
			campaign.setPreparationLeadDays(request.getPreparationLeadDays());
		}

		FestiveCampaign saved = festiveCampaignRepository.save(campaign);
		saveTags(saved.getId(), request.getTags());
		return toFestiveCampaignResponse(saved);
	}

	/**
	 * PUT /api/settings/festive-campaigns/{id}：編輯檔期基本資料與標籤。
	 * 標籤整份覆蓋（先刪除該檔期既有全部標籤，再依Request重新寫入）。
	 */
	@Transactional
	public FestiveCampaignResponse updateFestiveCampaign(Long id, FestiveCampaignUpdateRequest request) {
		FestiveCampaign campaign = festiveCampaignRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("檔期不存在"));
		validateCampaignDateRange(request.getStartDate(), request.getEndDate());

		campaign.setCampaignName(request.getCampaignName());
		campaign.setCategory(request.getCategory());
		campaign.setStartDate(request.getStartDate());
		campaign.setEndDate(request.getEndDate());
		if (request.getPreparationLeadDays() != null) {
			campaign.setPreparationLeadDays(request.getPreparationLeadDays());
		}
		FestiveCampaign saved = festiveCampaignRepository.save(campaign);

		festiveCampaignTagRepository.deleteByCampaignId(id);
		saveTags(id, request.getTags());

		return toFestiveCampaignResponse(saved);
	}

	/**
	 * POST /api/settings/festive-campaigns/{id}/manual-status：手動切換檔期狀態
	 * （熔斷清單②備援機制）。status切換campaign_status目標值，overrideEnabled
	 * 切換is_manual_override開關，兩者分開表達（企劃書API總表原文備註）。
	 *
	 * ⚠️ 2026-09-17修正：全專案搜過一輪，找不到任何地方會依日期自動計算
	 * campaign_status——沒有排程工作、沒有其他 setCampaignStatus() 呼叫點，
	 * 「自動判斷」這個概念從頭到尾沒有真正的計算邏輯對應。原本這個方法
	 * 不管 overrideEnabled 是 true 還是 false，一律直接把 request.getStatus()
	 * （前端傳來的、通常就是畫面上當下顯示的舊值）存回去——選「恢復自動
	 * 判斷」时，實際存進去的還是那個舊的手動狀態，只有 is_manual_override
	 * 這個旗標變成 false，狀態文字本身完全沒有跟著重新計算，這就是「恢復
	 * 自動判斷後仍維持手動狀態」的根本原因。
	 *
	 * 這裡補上 resolveAutomaticStatus()：overrideEnabled=false 時，狀態改
	 * 依目前日期、開始/結束日、準備天數即時算一次，不看前端傳來的
	 * status（那個值在這個模式下沒有意義，因為不該由人指定）；
	 * overrideEnabled=true 時才使用 request.getStatus() 這個人工指定值，
	 * 維持原本的手動指定行為。
	 *
	 * ⚠️ 這只解決「這次呼叫當下」重新計算一次——非手動覆蓋的檔期，日期
	 * 過境之後狀態依然不會自動往前推進（例如準備期結束、進入進行中），
	 * 除非又有人手動點一次「恢復自動判斷」或編輯檔期。要做到「每天自動
	 * 更新」需要額外的排程工作（@Scheduled），這是本次沒有做的部分，
	 * 需要先確認是否要新增這個排程，再評估「是否需要手動功能」——如果
	 * 之後真的補上每日排程，手動覆蓋才有明確的存在理由：讓管理者暫時
	 * 蓋過排程的自動判斷結果；如果不打算做排程，這個「自動判斷」目前
	 * 就只等於「這次先幫你算一次，之後不會再變」，需要團隊確認這樣是否
	 * 足夠。
	 */
	@Transactional
	public FestiveCampaignResponse switchManualStatus(Long id, FestiveCampaignManualStatusRequest request) {
		FestiveCampaign campaign = festiveCampaignRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("檔期不存在"));
		boolean overrideEnabled = Boolean.TRUE.equals(request.getOverrideEnabled());
		campaign.setCampaignStatus(overrideEnabled ? request.getStatus() : resolveAutomaticStatus(campaign));
		campaign.setIsManualOverride(overrideEnabled);
		FestiveCampaign saved = festiveCampaignRepository.save(campaign);
		return toFestiveCampaignResponse(saved);
	}

	/**
	 * 依目前日期、檔期起訖日、準備天數計算「應該」是哪個狀態，給
	 * switchManualStatus() 在「恢復自動判斷」時使用。
	 *
	 * 邊界規則（沿用 ScoringService 對 PREPARING 期間的既有定義：
	 * 「準備期＝開始日往前推 preparationLeadDays 天」）：
	 * - 今天 &lt; 開始日 - 準備天數 → UPCOMING（即將開始）
	 * - 開始日 - 準備天數 &lt;= 今天 &lt; 開始日 → PREPARING（準備期）
	 * - 開始日 &lt;= 今天 &lt;= 結束日 → ACTIVE（進行中）
	 * - 今天 &gt; 結束日 → EXPIRED（已結束）
	 *
	 * 準備天數為 null 或 <= 0 時視為 0（沒有準備期，開始日當天直接從
	 * UPCOMING 跳到 ACTIVE），跟 ScoringService.calculateFestivalUrgency()
	 * 對 leadDays 的防禦性處理一致，不要求呼叫端保證這個欄位一定有值。
	 */
	private FestiveCampaignStatus resolveAutomaticStatus(FestiveCampaign campaign) {
		LocalDate today = LocalDate.now();
		LocalDate startDate = campaign.getStartDate();
		LocalDate endDate = campaign.getEndDate();
		long leadDays = campaign.getPreparationLeadDays() != null && campaign.getPreparationLeadDays() > 0
				? campaign.getPreparationLeadDays()
				: 0;
		LocalDate preparationStart = startDate.minusDays(leadDays);

		if (today.isAfter(endDate)) {
			return FestiveCampaignStatus.EXPIRED;
		}
		if (!today.isBefore(startDate)) {
			return FestiveCampaignStatus.ACTIVE;
		}
		if (!today.isBefore(preparationStart)) {
			return FestiveCampaignStatus.PREPARING;
		}
		return FestiveCampaignStatus.UPCOMING;
	}

	/**
	 * ⚠️ 防呆：festive_campaign_tags 對 (campaign_id, tag) 有 UNIQUE 約束
	 * （V2 migration 的 uk_festive_campaign_tags_campaign_tag），但這裡原本
	 * 直接逐筆 save() 前端傳來的 tags，完全沒檢查同一份清單裡有沒有重複的
	 * tag 字串。只要前端（不管是哪個原因——UI bug、使用者手速快連點兩次、
	 * 或單純資料裡本來就帶了重複值）送來兩筆同名標籤，第二筆 insert 就會
	 * 撞上 UNIQUE 約束，丟出 DataIntegrityViolationException，被
	 * GlobalExceptionHandler 的保底規則包成 500「伺服器發生錯誤」，使用者
	 * 完全看不出真正原因（其實是自己不小心存了兩個一樣的標籤，不是伺服器
	 * 壞了）。這裡在寫入前用 LinkedHashMap 依 tag 文字（trim 後）去重，
	 * 同名時保留第一筆出現的 matchTier、丟棄後面重複的——不要讓一個可以
	 * 靜靜處理掉的重複值變成一次看不懂原因的存檔失敗。
	 */
	private void saveTags(Long campaignId, List<FestiveCampaignTagInput> tags) {
		if (tags == null) {
			return;
		}
		Map<String, FestiveCampaignTagInput> dedupedByTag = new LinkedHashMap<>();
		for (FestiveCampaignTagInput tagInput : tags) {
			if (tagInput == null || tagInput.getTag() == null) {
				continue;
			}
			String trimmed = tagInput.getTag().trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			dedupedByTag.putIfAbsent(trimmed, tagInput);
		}
		for (Map.Entry<String, FestiveCampaignTagInput> entry : dedupedByTag.entrySet()) {
			FestiveCampaignTag tag = new FestiveCampaignTag();
			tag.setCampaignId(campaignId);
			tag.setTag(entry.getKey());
			tag.setMatchTier(entry.getValue().getMatchTier());
			festiveCampaignTagRepository.save(tag);
		}
	}

	private FestiveCampaignResponse toFestiveCampaignResponse(FestiveCampaign campaign) {
		List<FestiveCampaignTagView> tags = festiveCampaignTagRepository.findByCampaignId(campaign.getId()).stream()
				.map(FestiveCampaignTagView::from).toList();
		return FestiveCampaignResponse.from(campaign, tags);
	}

	// ========================= 內部輔助方法 =========================

	/** username -&gt; app_users.id；沿用ProductService／ReviewService同樣的慣例。 */
	// ============================================================
	// system_settings 通用讀寫（演算法參數：貝氏收縮 k 值、趨勢半衰期等）
	// ============================================================

	/**
	 * 列出登記表（SystemSettingRegistry）裡全部已知的設定，每筆附上目前
	 * 生效值——資料庫沒有紀錄的 key 一併回傳登記表預設值，讓畫面一開始
	 * 就能顯示「目前實際生效的數字」，不是留白。
	 *
	 * 只回傳登記表裡有的 key，不是 SELECT * FROM system_settings——
	 * 資料庫裡可能存在登記表沒收錄的 key（例如 gemini_calls_2026-08
	 * 這種內部計數器用途的 key），那些不是給使用者調整的參數，不該出現
	 * 在這支給管理層看的設定畫面上。
	 */
	@Transactional(readOnly = true)
	public List<SystemSettingResponse> getSystemSettings() {
		Map<String, SystemSetting> stored = systemSettingRepository.findAll().stream()
				.collect(java.util.stream.Collectors.toMap(SystemSetting::getSettingKey, s -> s));

		return SystemSettingRegistry.all().values().stream()
				.map(meta -> {
					SystemSetting existing = stored.get(meta.key());
					String updatedByName = null;
					if (existing != null && existing.getUpdatedBy() != null) {
						updatedByName = appUserRepository.findById(existing.getUpdatedBy())
								.map(AppUser::getName).orElse(null);
					}
					return SystemSettingResponse.from(meta,
							existing != null ? existing.getSettingValue() : null,
							existing != null ? existing.getUpdatedAt() : null,
							updatedByName);
				})
				.toList();
	}

	/**
	 * 更新單一設定值。型別與範圍驗證對照 SystemSettingRegistry 的中繼資料，
	 * 不管是不是真的透過前端過來的請求都會被擋下——前端的輸入元件限制只是
	 * 體驗優化，這裡才是真正的防線。
	 *
	 * key 不在登記表裡（可能是打錯字，或想調整一個不開放調整的內部 key）
	 * 一律拒絕，不會不明不白地寫入一個沒人管理過的 key。
	 */
	@Transactional
	public SystemSettingResponse updateSystemSetting(String key, String value, String username) {
		SystemSettingRegistry.Metadata meta = SystemSettingRegistry.get(key);
		if (meta == null) {
			throw new IllegalArgumentException("不支援調整的設定項目：" + key);
		}
		validateSettingValue(meta, value);

		SystemSetting setting = systemSettingRepository.findById(key).orElseGet(() -> {
			SystemSetting s = new SystemSetting();
			s.setSettingKey(key);
			return s;
		});
		setting.setSettingValue(value);
		setting.setUpdatedBy(resolveUserId(username));
		SystemSetting saved = systemSettingRepository.save(setting);

		log.info("系統設定已更新：key={} value={} 操作者={}", key, value, username);

		String updatedByName = appUserRepository.findById(saved.getUpdatedBy())
				.map(AppUser::getName).orElse(null);
		return SystemSettingResponse.from(meta, saved.getSettingValue(), saved.getUpdatedAt(), updatedByName);
	}

	/**
	 * 型別與範圍驗證。STRING 型別（例如 supported_temperature_zones）
	 * 不做數值範圍檢查，只確認非空——它的合法值域是「逗號分隔的溫層代碼」，
	 * 這種結構化字串驗證交給前端下拉多選元件保證格式，後端在這裡不重新
	 * 實作一次溫層列舉的解析。
	 */
	private void validateSettingValue(SystemSettingRegistry.Metadata meta, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("設定值不可為空");
		}
		if (meta.dataType() == SystemSettingRegistry.DataType.STRING) {
			return;
		}
		java.math.BigDecimal parsed;
		try {
			parsed = new java.math.BigDecimal(value.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(meta.displayName() + " 必須是數字，目前輸入：" + value);
		}
		if (meta.dataType() == SystemSettingRegistry.DataType.INTEGER
				&& parsed.stripTrailingZeros().scale() > 0) {
			throw new IllegalArgumentException(meta.displayName() + " 必須是整數，目前輸入：" + value);
		}
		if (meta.minValue() != null && parsed.compareTo(meta.minValue()) < 0) {
			throw new IllegalArgumentException(String.format("%s 不可小於 %s，目前輸入：%s",
					meta.displayName(), meta.minValue(), value));
		}
		if (meta.maxValue() != null && parsed.compareTo(meta.maxValue()) > 0) {
			throw new IllegalArgumentException(String.format("%s 不可大於 %s，目前輸入：%s",
					meta.displayName(), meta.maxValue(), value));
		}
	}

	private Long resolveUserId(String username) {
		AppUser user = appUserRepository.findByUsername(username)
				.orElseThrow(() -> new IllegalArgumentException("使用者不存在"));
		return user.getId();
	}
}
