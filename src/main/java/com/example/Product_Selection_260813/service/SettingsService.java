package com.example.Product_Selection_260813.service;

import java.time.LocalDateTime;
import java.util.List;

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
import com.example.Product_Selection_260813.dto.request.RiskOptionCreateRequest;
import com.example.Product_Selection_260813.dto.request.SwitchEvaluationModeRequest;
import com.example.Product_Selection_260813.dto.response.AudienceProfileResponse;
import com.example.Product_Selection_260813.dto.response.EvaluationModeResponse;
import com.example.Product_Selection_260813.dto.response.FestiveCampaignResponse;
import com.example.Product_Selection_260813.dto.response.FestiveCampaignTagView;
import com.example.Product_Selection_260813.dto.response.ProductTypeResponse;
import com.example.Product_Selection_260813.dto.response.RiskOptionResponse;
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
import com.example.Product_Selection_260813.repository.AudienceProfileRepository;
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
 * <b>本輪範圍（分批實作，第二批）：</b>核心客群設定（2支）／商品類型設定（4支）／
 * 節慶檔期管理（4支）。加上第一批已完成的評估模式（4支）與人工風險選項的GET
 * （1支），七、系統設定除了POST /api/settings/risk-options（見下方說明），
 * 其餘皆已完成。
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
 * 這是配合先前已確認的資料表異動所做的必要調整，不是本輪自行簡化。
 */
@Service
public class SettingsService {

	// 對應SystemSettingRepository註解裡的既定用法與四-14設計取捨
	private static final String CURRENT_EVALUATION_MODE_KEY = "current_evaluation_mode_id";

	@Autowired
	private EvaluationModeRepository evaluationModeRepository;

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
		setting.setUpdatedAt(LocalDateTime.now());
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
	 */
	@Transactional(readOnly = true)
	public List<RiskOptionResponse> getAllRiskOptions() {
		return riskOptionRepository.findAll().stream().map(RiskOptionResponse::from).toList();
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
	public RiskOptionResponse createRiskOption(RiskOptionCreateRequest request, String username) {
		Long userId = resolveUserId(username);

		RiskOption option = new RiskOption();
		option.setName(request.getName());
		option.setDescription(request.getDescription());
		option.setAlertKeywords(request.getAlertKeywords());
		option.setIsSystemDefault(false);
		option.setCreatedBy(userId);

		RiskOption saved = riskOptionRepository.save(option);
		return RiskOptionResponse.from(saved);
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
	 * PUT /api/settings/product-types/{id}/disable：停用分類（該分類已被品項使用
	 * 時的建議做法）。企劃書只定義「停用」這個單向動作，沒有對應的「啟用」端點，
	 * 這裡不額外發明未定義的功能。
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
