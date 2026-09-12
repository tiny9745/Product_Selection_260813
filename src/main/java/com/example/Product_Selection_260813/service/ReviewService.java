package com.example.Product_Selection_260813.service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.service.resolver.MoqResolver;
import com.example.Product_Selection_260813.service.resolver.ResolvedValue;
import com.example.Product_Selection_260813.service.scoring.ProductFactorScorer;
import com.example.Product_Selection_260813.dto.request.ReviewSubmitRequest;
import com.example.Product_Selection_260813.dto.response.ProductResponse;
import com.example.Product_Selection_260813.dto.response.ReviewDetailResponse;
import com.example.Product_Selection_260813.dto.response.ReviewRecordResponse;
import com.example.Product_Selection_260813.constants.ValidationMessage;
import com.example.Product_Selection_260813.dto.response.RiskOptionResponse;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.entity.EvaluationMode;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductEvaluation;
import com.example.Product_Selection_260813.entity.ReviewRecord;
import com.example.Product_Selection_260813.entity.ReviewRisk;
import com.example.Product_Selection_260813.entity.ReviewRiskId;
import com.example.Product_Selection_260813.enums.ProductItemStatus;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;
import com.example.Product_Selection_260813.enums.ReviewRiskSource;
import com.example.Product_Selection_260813.service.gate.GateEvaluationService;
import com.example.Product_Selection_260813.service.gate.GateResult;
import com.example.Product_Selection_260813.json.MatchedCampaignSnapshot;
import com.example.Product_Selection_260813.enums.ReviewRecordReviewStatus;
import com.example.Product_Selection_260813.json.ProductSnapshot;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.ReviewRecordRepository;
import com.example.Product_Selection_260813.repository.ReviewRiskRepository;
import com.example.Product_Selection_260813.entity.RiskOption;
import com.example.Product_Selection_260813.repository.RiskOptionRepository;

/**
 * 對應 API總表 五、選品審核 與 六、審核歷史／版本追蹤：
 *
 * GET  /api/reviews/pending           -&gt; getPendingReviews()<br>
 * GET  /api/reviews/{productId}       -&gt; getReviewDetail()<br>
 * POST /api/reviews                   -&gt; submitReview()<br>
 * GET  /api/reviews/decision-records  -&gt; getDecisionRecords()<br>
 * GET  /api/products/{id}/reviews     -&gt; getProductReviewHistory()
 *
 * 依十二-13分層決議，本類別只管審核流程本身（狀態機、風險勾選、留存快照），
 * 快照的組裝細節（weight_snapshot／trend_snapshot／matched_campaign_snapshot／
 * ai_summary_snapshot）透過呼叫ScoringService／AiSelectionService取得，
 * 不直接注入六、七個Repository。product_snapshot例外——這是Product自身核心資料，
 * 不屬於評分或AI網域，本類別已持有完整Product entity，直接組裝。
 *
 * 例外處理沿用專案既有GlobalExceptionHandler慣例，與ProductService一致：
 * 資源不存在 -&gt; IllegalArgumentException（400）；目前狀態不允許此操作
 * （狀態機不合法轉換、併發衝突）-&gt; IllegalStateException（409）。
 */
@Service
public class ReviewService {

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ReviewRecordRepository reviewRecordRepository;

	@Autowired
	private ReviewRiskRepository reviewRiskRepository;

	@Autowired
	private RiskOptionRepository riskOptionRepository;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private ScoringService scoringService;

	@Autowired
	private AiSelectionService aiSelectionService;

	// Gate 判定。由 ReviewService 注入而非 ScoringService 內部呼叫，
	// 是為了避免兩個 Service 互相依賴形成循環。
	@Autowired
	private GateEvaluationService gateEvaluationService;

	@Autowired
	private MoqResolver moqResolver;

	// 只用來取運費估算——快照要留下當時採用的運費，
	// 否則事後拿成本價與售價重算會對不上快照裡的毛利率分數。
	@Autowired
	private ProductFactorScorer productFactorScorer;

	// ========================= 查詢 =========================

	/**
	 * GET /api/reviews/pending：待審清單，預設「未審核＋使用中」。
	 */
	@Transactional(readOnly = true)
	public Page<ProductResponse> getPendingReviews(Pageable pageable) {
		Page<Product> page = productRepository
				.findByReviewStatusAndItemStatus(ProductReviewStatus.PENDING, ProductItemStatus.ACTIVE, pageable);

		// 批次查一次 createdBy 對應的姓名，避免在 .map() 裡逐筆查詢（N+1）。
		// app_users 是使用者帳號本身的資料，不屬於評分／AI 網域，不算跨越
		// 類別 Java Doc 講的十二-13分層邊界（該邊界只規範 Scoring／Trend／AiSelection）。
		Set<Long> createdByIds = page.getContent().stream()
				.map(Product::getCreatedBy)
				.filter(id -> id != null)
				.collect(Collectors.toSet());
		// ⚠️ 同 ProductService.resolveCreatedByNames() 的說明：
		// Collectors.toMap 遇到 value 為 null 會直接拋 NullPointerException，
		// 用 requireNonNullElse 擋掉，避免一筆髒資料（姓名為 null）拖垮整支清單 API。
		Map<Long, String> createdByNameById = createdByIds.isEmpty() ? Map.of()
				: appUserRepository.findAllById(createdByIds).stream()
						.collect(Collectors.toMap(
								AppUser::getId,
								user -> Objects.requireNonNullElse(user.getName(), "")));

		return page.map(product -> ProductResponse.from(product)
				.withCreatedByName(
						product.getCreatedBy() == null ? null : createdByNameById.get(product.getCreatedBy())));
	}

	/**
	 * GET /api/reviews/{productId}：管理進行審核所需的完整資訊（含節慶加成明細）。
	 *
	 * 這裡查詢的是「目前即時資料」，不是Snapshot——Snapshot只在submitReview()
	 * 審核當下才會凍結寫入review_records，審核之前管理層看到的永遠是最新狀態。
	 */
	@Transactional(readOnly = true)
	public ReviewDetailResponse getReviewDetail(Long productId) {
		Product product = findProductOrThrow(productId);

		Optional<ProductEvaluation> evaluationOpt = scoringService.getCurrentEvaluation(productId);
		Long evaluationModeId = evaluationOpt.map(ProductEvaluation::getEvaluationModeId).orElse(null);
		Optional<EvaluationMode> evaluationModeOpt = scoringService.getEvaluationMode(evaluationModeId);

		MatchedCampaignSnapshot matchedCampaign = scoringService.buildMatchedCampaignSnapshot(product);
		// orElseGet 而非 orElse(null)：evaluationOpt 為空理論上不該發生
		// （商品透過 createProduct()/updateProduct() 建立時就會觸發
		// calculateEvaluation() 寫入這筆快取），但如果因為任何原因這筆
		// 快取還沒寫入（例如資料庫直接匯入、或極端的時序競態），與其讓
		// 完整度靜默變成 null、審核頁顯示一個空白的「—」看起來像資料
		// 缺漏，不如即時算一次——這個計算只讀商品自己的欄位，不依賴
		// 任何外部狀態，重新算一次的成本很低，值得當作防禦性備援。
		BigDecimal dataCompleteness = evaluationOpt.map(ProductEvaluation::getDataCompleteness)
				.orElseGet(() -> scoringService.calculateDataCompleteness(product));
		GateResult.Summary gateSummary = gateEvaluationService.evaluate(product, dataCompleteness, matchedCampaign);

		// Gate 判定不通過的項目，對應的風險選項預先標記為系統帶入。
		// 只標記不代表已勾選——最終是否成立由主管決定，這裡只是提供建議。
		Map<Long, String> triggerReasons = resolveGateTriggerReasons(gateSummary);
		List<RiskOptionResponse> availableRiskOptions = riskOptionRepository.findByIsActiveTrue().stream()
				.map(option -> {
					RiskOptionResponse dto = RiskOptionResponse.from(option);
					String reason = triggerReasons.get(option.getId());
					dto.setAutoTriggered(reason != null);
					dto.setTriggerReason(reason);
					return dto;
				}).toList();

		return ReviewDetailResponse.build(ProductResponse.from(product), product.getSubmissionCount(),
				evaluationOpt.orElse(null), evaluationModeOpt.orElse(null),
				scoringService.buildWeightSnapshot(evaluationModeId), matchedCampaign,
				aiSelectionService.getLatestAnalysis(productId).orElse(null), availableRiskOptions,
				gateSummary);
	}

	/**
	 * GET /api/products/{id}/reviews：單一商品歷次送審與審核結果。
	 */
	@Transactional(readOnly = true)
	public List<ReviewRecordResponse> getProductReviewHistory(Long productId) {
		if (!productRepository.existsById(productId)) {
			throw new IllegalArgumentException("商品不存在");
		}
		return reviewRecordRepository.findByProductIdOrderByReviewedAtDesc(productId).stream()
				.map(record -> ReviewRecordResponse.from(record, getRiskOptionIds(record.getId()))).toList();
	}

	/**
	 * GET /api/reviews/decision-records：跨商品的審核紀錄彙總查詢頁。
	 */
	@Transactional(readOnly = true)
	public Page<ReviewRecordResponse> getDecisionRecords(Pageable pageable) {
		return reviewRecordRepository.findAllByOrderByReviewedAtDesc(pageable)
				.map(record -> ReviewRecordResponse.from(record, getRiskOptionIds(record.getId())));
	}

	// ========================= 提交審核 =========================

	/**
	 * POST /api/reviews：管理提交人工風險評估、審核留言及核准／拒絕結果。
	 *
	 * 執行順序刻意如下，確保review_records只保留「真正生效」的審核結果：
	 * 1. 讀取快照來源資料（唯讀查詢，不影響併發正確性，即使之後衝突了也只是白算一次）
	 * 2. 條件式UPDATE products.review_status（WHERE review_status='PENDING'）
	 *    ——影響筆數0代表已被他人審核過，直接409、不寫入任何審核紀錄
	 * 3. 條件式UPDATE成功後，才寫入review_records與review_risks
	 *
	 * 若先寫入review_records、最後才做條件式UPDATE，一旦UPDATE失敗（409），
	 * 就會留下一筆「與products.review_status實際狀態對不上」的孤兒審核紀錄，
	 * 這裡的順序刻意避免這個問題，寫法與精神沿用ProductService.resubmit()。
	 */
	@Transactional
	public ReviewRecordResponse submitReview(ReviewSubmitRequest request, String username) {
		Product product = findProductOrThrow(request.getProductId());

		if (product.getReviewStatus() != ProductReviewStatus.PENDING) {
			throw new IllegalStateException("僅未審核商品可提交審核結果");
		}

		validateRejectionReason(request);

		Long reviewerId = resolveUserId(username);

		Optional<ProductEvaluation> evaluationOpt = scoringService.getCurrentEvaluation(product.getId());
		Long evaluationModeId = evaluationOpt.map(ProductEvaluation::getEvaluationModeId).orElse(null);
		Optional<EvaluationMode> evaluationModeOpt = scoringService.getEvaluationMode(evaluationModeId);

		// Gate 判定：與分數計算同時取得，讓快照留下的是「審核當下」的判定結果。
		// 不在查詢時重算——之後品類屬性或設定值改了，重算的結果會與當時不同，
		// 而審核紀錄必須能還原當時的判斷依據。
		MatchedCampaignSnapshot matchedCampaign = scoringService.buildMatchedCampaignSnapshot(product);
		// orElseGet 而非 orElse(null)：evaluationOpt 為空理論上不該發生
		// （商品透過 createProduct()/updateProduct() 建立時就會觸發
		// calculateEvaluation() 寫入這筆快取），但如果因為任何原因這筆
		// 快取還沒寫入（例如資料庫直接匯入、或極端的時序競態），與其讓
		// 完整度靜默變成 null、審核頁顯示一個空白的「—」看起來像資料
		// 缺漏，不如即時算一次——這個計算只讀商品自己的欄位，不依賴
		// 任何外部狀態，重新算一次的成本很低，值得當作防禦性備援。
		BigDecimal dataCompleteness = evaluationOpt.map(ProductEvaluation::getDataCompleteness)
				.orElseGet(() -> scoringService.calculateDataCompleteness(product));
		GateResult.Summary gateSummary = gateEvaluationService.evaluate(product, dataCompleteness, matchedCampaign);
		Map<Long, String> gateTriggerReasons = resolveGateTriggerReasons(gateSummary);

		ReviewRecord record = new ReviewRecord();
		record.setProductId(product.getId());
		record.setReviewerId(reviewerId);
		record.setSubmissionCount(product.getSubmissionCount());
		record.setReviewStatus(request.getReviewStatus());
		record.setReviewedAt(LocalDateTime.now());
		record.setReviewComment(request.getReviewComment());

		evaluationModeOpt.ifPresent(mode -> {
			record.setEvaluationModeId(mode.getId());
			record.setEvaluationModeName(mode.getModeName());
			record.setEvaluationModeVersion(mode.getVersion());
		});

		evaluationOpt.ifPresent(evaluation -> {
			record.setTotalScore(evaluation.getTotalScore());
			record.setFestivalBoostSnapshot(evaluation.getFestivalBoost());
			record.setFinalScoreSnapshot(evaluation.getFinalScore());
			record.setDataCompleteness(evaluation.getDataCompleteness());
			record.setBusinessScore(evaluation.getBusinessScore());
			record.setAudienceScore(evaluation.getAudienceScore());
			record.setHistoricalScore(evaluation.getHistoricalScore());
			record.setPurchaseScore(evaluation.getPurchaseScore());
			record.setTrendScore(evaluation.getTrendScore());
			record.setForecastScore(evaluation.getForecastScore());
		});

		record.setMatchedCampaignSnapshot(matchedCampaign);
		record.setSystemGateSummary(gateSummary.toDisplaySummary());
		record.setWeightSnapshot(scoringService.buildWeightSnapshot(evaluationModeId));
		record.setTrendSnapshot(scoringService.buildTrendSnapshot(product.getId()));
		record.setProductSnapshot(buildProductSnapshot(product));
		record.setAiSummarySnapshot(aiSelectionService.buildAiSummarySnapshot(product.getId()));

		int updated = productRepository.conditionalUpdateReviewStatus(product.getId(), ProductReviewStatus.PENDING,
				toProductReviewStatus(request.getReviewStatus()));
		if (updated == 0) {
			throw new IllegalStateException("商品狀態已被異動，請重新整理後再試");
		}

		ReviewRecord saved = reviewRecordRepository.save(record);
		List<Long> riskOptionIds = saveReviewRisks(saved.getId(), request.getRiskOptionIds(),
				request.getSystemSuggestedRiskOptionIds(), gateTriggerReasons);

		return ReviewRecordResponse.from(saved, riskOptionIds);
	}

	// ========================= 內部輔助方法 =========================


	/**
	 * 把 Gate 判定不通過的項目對應到 risk_options，取得「風險選項 id -&gt; 判定原因」。
	 *
	 * 只處理 FAILED——資料不足與不適用都不代表確定有風險，不該自動勾選風險選項。
	 * 尤其是資料不足：把它自動勾成風險，等於因為採購沒填欄位就替商品扣了一筆
	 * 風險紀錄，這對商品不公平，也會讓風險清單失去意義。
	 *
	 * 找不到對應 risk_option 的 Gate 會被略過（不拋錯）——auto_trigger_code 是
	 * 設定資料，漏設一筆不該讓整筆審核送不出去，只是少了自動勾選的便利。
	 */
	private Map<Long, String> resolveGateTriggerReasons(GateResult.Summary summary) {
		Map<Long, String> reasons = new LinkedHashMap<>();
		for (GateResult failed : summary.failedResults()) {
			riskOptionRepository.findByAutoTriggerCode(failed.gateCode())
					.forEach(option -> reasons.put(option.getId(), failed.reason()));
		}
		return reasons;
	}

	private Product findProductOrThrow(Long id) {
		return productRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("商品不存在"));
	}

	/** username -&gt; app_users.id；沿用ProductService.resolveUserId()同樣的慣例。 */
	private Long resolveUserId(String username) {
		AppUser user = appUserRepository.findByUsername(username)
				.orElseThrow(() -> new IllegalArgumentException("使用者不存在"));
		return user.getId();
	}

	/**
	 * ReviewRecordReviewStatus{APPROVED,REJECTED}與ProductReviewStatus
	 * {PENDING,REJECTED,APPROVED}是兩個獨立的enum（即使成員名稱相同，Java不會自動轉換），
	 * 條件式UPDATE需要的是products.review_status所用的ProductReviewStatus，故需明確映射。
	 */
	private ProductReviewStatus toProductReviewStatus(ReviewRecordReviewStatus status) {
		return switch (status) {
		case APPROVED -> ProductReviewStatus.APPROVED;
		case REJECTED -> ProductReviewStatus.REJECTED;
		};
	}

	/**
	 * 組出審核當下的商品快照。
	 *
	 * <b>這是整套設計裡唯一「現在不做、之後補不回來」的地方。</b>
	 * 審核紀錄不可覆蓋，漏掉的欄位無法回頭補齊——三個月後想查「當初為什麼
	 * 這個 Gate 不通過」，如果快照裡沒有溫層、效期這些欄位，就只能撈到商品
	 * 今天的值，而那可能已經被編輯過了。
	 *
	 * 因此除了商品自己的欄位，還要存兩類「解析後的結果」：
	 * <ul>
	 * <li>resolvedMoq / moqSource：三層解析後實際生效的值與來源。品類預設
	 *     之後可能被改，只存商品層原始值（可能是 null）無法還原當時判斷。</li>
	 * <li>freightCostEstimate：毛利率是扣掉運費後才正規化的，而運費來自
	 *     system_settings，之後會被調整。不存的話，事後拿成本價與售價重算
	 *     會對不上快照裡的分數。</li>
	 * </ul>
	 */
	private ProductSnapshot buildProductSnapshot(Product product) {
		ProductSnapshot snapshot = new ProductSnapshot();
		snapshot.setName(product.getName());
		snapshot.setPricingType(product.getPricingType() != null ? product.getPricingType().name() : null);
		snapshot.setCostPrice(product.getCostPrice());
		snapshot.setSalePrice(product.getSalePrice());
		// 折扣深度是計分因子，公式用到市價；不存的話這一項分數無法重現
		snapshot.setMarketPrice(product.getMarketPrice());
		snapshot.setCampaignTags(product.getCampaignTags());
		snapshot.setSupplyStability(product.getSupplyStability());
		snapshot.setPriceCompetitiveness(product.getPriceCompetitiveness());
		snapshot.setTargetCustomerDescription(product.getTargetCustomerDescription());
		snapshot.setEstimatedPurchaseRate(product.getEstimatedPurchaseRate());

		// moq 存商品層原始值（可能為 null，代表當時是繼承品類），
		// resolvedMoq 存實際生效的數字。兩者並存才能還原「當時用的是多少、
		// 而且那個數字是誰給的」。
		snapshot.setMoq(product.getMoq());
		ResolvedValue<Integer> resolvedMoq = moqResolver.resolve(product);
		snapshot.setResolvedMoq(resolvedMoq.value());
		snapshot.setMoqSource(resolvedMoq.source().name());

		// Gate 判定所依據的商品屬性
		snapshot.setTemperatureZone(product.getTemperatureZone());
		snapshot.setShelfLifeTier(product.getShelfLifeTier());
		snapshot.setSupplierLeadTimeTier(product.getSupplierLeadTimeTier());
		snapshot.setPackageSizeTier(product.getPackageSizeTier());
		snapshot.setPackingType(product.getPackingType());
		snapshot.setHandlingFlags(product.getHandlingFlags());
		snapshot.setCertificationFlags(product.getCertificationFlags());
		snapshot.setSupplierMaxCapacity(product.getSupplierMaxCapacity());
		snapshot.setResaleReferenceProductId(product.getResaleReferenceProductId());

		// 當時採用的運費估算，供事後重現毛利率計算
		snapshot.setFreightCostEstimate(productFactorScorer.estimateFreightCost(product));

		return snapshot;
	}

	/**
	 * 寫入review_risks（多對多）。複合主鍵天生避免重複勾選寫入（見ReviewRisk.java），
	 * 這裡仍先distinct()一次是為了避免對同一組合鍵save()兩次觸發不必要的重複UPDATE語句
	 * （JPA對已存在的複合主鍵save()會走UPDATE而非INSERT，雖然結果正確但多一次往返）。
	 */
	/**
	 * 退件時至少要留下一項可追溯的理由：勾選風險項目或填寫審核備註，兩者至少一項。
	 *
	 * riskOptionIds／reviewComment在DTO層刻意不加@NotEmpty／@NotBlank
	 * （見ReviewSubmitRequest類別註解）——核准時本來就可能兩者皆空，那是合法的。
	 * 但「退件卻不說明任何理由」對送審人沒有任何幫助，也讓審核紀錄失去稽核價值，
	 * 因此這是依審核結果而定的商業邏輯，只能放在Service層判斷，不能用DTO註解表達。
	 */
	private void validateRejectionReason(ReviewSubmitRequest request) {
		if (request.getReviewStatus() != ReviewRecordReviewStatus.REJECTED) {
			return;
		}
		boolean hasRisk = request.getRiskOptionIds() != null && !request.getRiskOptionIds().isEmpty();
		boolean hasComment = request.getReviewComment() != null && !request.getReviewComment().isBlank();
		if (!hasRisk && !hasComment) {
			throw new IllegalArgumentException(ValidationMessage.REVIEW_REJECT_REASON_REQUIRED);
		}
	}

	/**
	 * 寫入審核風險項目，區分「系統帶入」與「主管勾選」。
	 *
	 * 四種組合都必須被記錄：
	 * <table>
	 * <tr><td>SYSTEM_AUTO + isSelected=true </td><td>系統判定，主管保留</td></tr>
	 * <tr><td>SYSTEM_AUTO + isSelected=false</td><td><b>系統判定，主管推翻</b></td></tr>
	 * <tr><td>MANUAL + isSelected=true      </td><td>主管自行勾選</td></tr>
	 * <tr><td>MANUAL + isSelected=false     </td><td>不寫入（無此風險）</td></tr>
	 * </table>
	 *
	 * 第二種是稽核價值最高的一筆——「系統說有問題，但主管認為可以」。
	 * 如果只寫入主管最終勾選的清單，這筆資訊會完全消失，事後就分不出
	 * 「系統沒建議」和「系統建議了但被推翻」。
	 *
	 * @param selectedIds 主管最終勾選的（含他保留下來的系統建議項）
	 * @param systemSuggested 系統原本建議的（Gate 判定不通過而預先勾選的）
	 * @param triggerReasons riskOptionId -&gt; Gate 判定原因，供系統帶入項留存說明
	 * @return 主管最終勾選的 id 清單，供回應使用
	 */
	private List<Long> saveReviewRisks(Long reviewId, List<Long> selectedIds,
			List<Long> systemSuggested, Map<Long, String> triggerReasons) {
		List<Long> selected = selectedIds == null ? List.of() : selectedIds.stream().distinct().toList();
		List<Long> suggested = systemSuggested == null ? List.of() : systemSuggested.stream().distinct().toList();

		// 兩份清單的聯集才是要寫入的範圍——被主管取消的系統建議項也要留下紀錄
		LinkedHashSet<Long> allIds = new LinkedHashSet<>();
		allIds.addAll(selected);
		allIds.addAll(suggested);
		if (allIds.isEmpty()) {
			return List.of();
		}

		for (Long riskOptionId : allIds) {
			boolean isSelected = selected.contains(riskOptionId);
			boolean isSystemSuggested = suggested.contains(riskOptionId);

			// 停用檢查只套用在「主管實際勾選」的項目。
			// 系統建議但被取消的項目不檢查——那筆紀錄的用途是保留稽核軌跡，
			// 若因為選項剛好被停用而拋錯，反而會讓整筆審核送不出去。
			RiskOption riskOption = riskOptionRepository.findById(riskOptionId)
					.orElseThrow(() -> new IllegalArgumentException("人工風險選項不存在：" + riskOptionId));
			if (isSelected && !Boolean.TRUE.equals(riskOption.getIsActive())) {
				throw new IllegalArgumentException(
						ValidationMessage.REVIEW_RISK_OPTION_INACTIVE + riskOption.getName());
			}

			ReviewRisk reviewRisk = new ReviewRisk();
			reviewRisk.setId(new ReviewRiskId(reviewId, riskOptionId));
			reviewRisk.setSource(isSystemSuggested ? ReviewRiskSource.SYSTEM_AUTO : ReviewRiskSource.MANUAL);
			reviewRisk.setIsSelected(isSelected);
			if (isSystemSuggested && triggerReasons != null) {
				reviewRisk.setTriggerReason(triggerReasons.get(riskOptionId));
			}
			reviewRiskRepository.save(reviewRisk);
		}
		return selected;
	}

	/**
	 * 查詢審核紀錄實際成立的風險項目。
	 *
	 * 只回傳 isSelected = true 的——被主管推翻的系統建議項雖然留在資料庫，
	 * 但它不是「這次審核認定的風險」，不該出現在風險清單裡。
	 * 要看完整軌跡（含被推翻的項目）請另外查 review_risks。
	 */
	private List<Long> getRiskOptionIds(Long reviewId) {
		return reviewRiskRepository.findById_ReviewId(reviewId).stream()
				.filter(risk -> Boolean.TRUE.equals(risk.getIsSelected()))
				.map(risk -> risk.getId().getRiskOptionId())
				.toList();
	}
}