package com.example.Product_Selection_260813.service;

import java.util.Map;
import java.util.List;

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
import com.example.Product_Selection_260813.dto.request.RiskOptionUpdateRequest;
import com.example.Product_Selection_260813.dto.request.SwitchEvaluationModeRequest;
import com.example.Product_Selection_260813.dto.response.AudienceProfileResponse;
import com.example.Product_Selection_260813.dto.response.EvaluationModeResponse;
import com.example.Product_Selection_260813.dto.response.FestiveCampaignResponse;
import com.example.Product_Selection_260813.dto.response.FestiveCampaignTagView;
import com.example.Product_Selection_260813.dto.response.ProductTypeResponse;
import com.example.Product_Selection_260813.dto.response.RiskOptionSettingResponse;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.entity.AudienceProfile;
import com.example.Product_Selection_260813.entity.EvaluationMode;
import com.example.Product_Selection_260813.entity.FestiveCampaign;
import com.example.Product_Selection_260813.entity.FestiveCampaignTag;
import com.example.Product_Selection_260813.entity.ProductType;
import com.example.Product_Selection_260813.entity.RiskOption;
import com.example.Product_Selection_260813.entity.SystemSetting;
import com.example.Product_Selection_260813.json.WeightSnapshot;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.example.Product_Selection_260813.algorithm.ScoringAlgorithms;
import com.example.Product_Selection_260813.dto.request.ProductTypeScoreBandUpdateRequest;
import com.example.Product_Selection_260813.dto.response.ProductTypeScoreBandResponse;
import com.example.Product_Selection_260813.entity.ProductTypeScoreBand;
import com.example.Product_Selection_260813.enums.ScoreBandSourceMode;
import com.example.Product_Selection_260813.repository.GroupBuyRecordRepository;
import com.example.Product_Selection_260813.repository.ProductTypeScoreBandRepository;
import com.example.Product_Selection_260813.service.resolver.ScoreBandResolver;

import com.example.Product_Selection_260813.constants.FactorCode;
import com.example.Product_Selection_260813.constants.ValidationMessage;
import com.example.Product_Selection_260813.dto.request.EvaluationFactorUpdateRequest;
import com.example.Product_Selection_260813.entity.EvaluationFactor;
import com.example.Product_Selection_260813.repository.AudienceProfileRepository;
import com.example.Product_Selection_260813.repository.EvaluationFactorRepository;
import com.example.Product_Selection_260813.repository.EvaluationModeRepository;
import com.example.Product_Selection_260813.repository.FestiveCampaignRepository;
import com.example.Product_Selection_260813.repository.FestiveCampaignTagRepository;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.ProductTypeRepository;
import com.example.Product_Selection_260813.repository.RiskOptionRepository;
import com.example.Product_Selection_260813.repository.SystemSettingRepository;

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
	private AppUserRepository appUserRepository;

	@Autowired
	private ScoringService scoringService;

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
		Map<String, BigDecimal> collected = new LinkedHashMap<>();
		for (EvaluationFactorUpdateRequest.FactorWeight fw : request.getFactors()) {
			String code = fw.getFactorCode().trim().toUpperCase();
			if (!FactorCode.ALL.contains(code)) {
				throw new IllegalArgumentException(ValidationMessage.FACTOR_CODE_UNKNOWN + code);
			}
			if (collected.containsKey(code)) {
				throw new IllegalArgumentException(ValidationMessage.FACTOR_CODE_DUPLICATE + code);
			}
			collected.put(code, fw.getWeight());
		}

		List<String> missing = FactorCode.ALL.stream()
				.filter(code -> !collected.containsKey(code))
				.toList();
		if (!missing.isEmpty()) {
			throw new IllegalArgumentException(
					ValidationMessage.FACTOR_CODE_MISSING + String.join("、", missing));
		}
		return collected;
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
		return productTypeRepository.findAll().stream().map(ProductTypeResponse::from).toList();
	}

	/**
	 * POST /api/settings/product-types：新增自訂商品分類（isSystemDefault固定為false）。
	 *
	 * <b>刻意不檢查name是否重複（已與團隊確認，非疏漏，與ProductService.createProduct()
	 * 同一個決策範圍）：</b>同名分類可能分屬不同管理脈絡下建立，且屬於管理層低頻、
	 * 少量的操作，人眼就看得出是否重複，由管理層自行判斷，系統不代為阻擋。
	 */
	@Transactional
	public ProductTypeResponse createProductType(ProductTypeCreateRequest request, String username) {
		Long userId = resolveUserId(username);

		ProductType type = new ProductType();
		type.setName(request.getName());
		type.setDescription(request.getDescription());
		type.setIsSystemDefault(false);
		type.setCreatedBy(userId);

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
	 */
	@Transactional
	public ProductTypeResponse disableProductType(Long id) {
		ProductType type = productTypeRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("商品類型不存在"));
		type.setIsActive(false);
		ProductType saved = productTypeRepository.save(type);
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
	 */
	@Transactional
	public void deleteProductType(Long id) {
		if (!productTypeRepository.existsById(id)) {
			throw new IllegalArgumentException("商品類型不存在");
		}
		if (productRepository.existsByProductTypeId(id)) {
			throw new IllegalStateException("此商品類型已有品項使用，無法刪除，請改用停用");
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
	 */
	@Transactional
	public FestiveCampaignResponse switchManualStatus(Long id, FestiveCampaignManualStatusRequest request) {
		FestiveCampaign campaign = festiveCampaignRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("檔期不存在"));
		campaign.setCampaignStatus(request.getStatus());
		campaign.setIsManualOverride(request.getOverrideEnabled());
		FestiveCampaign saved = festiveCampaignRepository.save(campaign);
		return toFestiveCampaignResponse(saved);
	}

	private void saveTags(Long campaignId, List<FestiveCampaignTagInput> tags) {
		if (tags == null) {
			return;
		}
		for (FestiveCampaignTagInput tagInput : tags) {
			FestiveCampaignTag tag = new FestiveCampaignTag();
			tag.setCampaignId(campaignId);
			tag.setTag(tagInput.getTag());
			tag.setMatchTier(tagInput.getMatchTier());
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
	private Long resolveUserId(String username) {
		AppUser user = appUserRepository.findByUsername(username)
				.orElseThrow(() -> new IllegalArgumentException("使用者不存在"));
		return user.getId();
	}
}
