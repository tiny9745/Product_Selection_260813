package com.example.Product_Selection_260813.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.dto.request.ReviewSubmitRequest;
import com.example.Product_Selection_260813.dto.response.ProductResponse;
import com.example.Product_Selection_260813.dto.response.ReviewDetailResponse;
import com.example.Product_Selection_260813.dto.response.ReviewRecordResponse;
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
import com.example.Product_Selection_260813.enums.ReviewRecordReviewStatus;
import com.example.Product_Selection_260813.json.ProductSnapshot;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.ReviewRecordRepository;
import com.example.Product_Selection_260813.repository.ReviewRiskRepository;
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
		Map<Long, String> createdByNameById = createdByIds.isEmpty() ? Map.of()
				: appUserRepository.findAllById(createdByIds).stream()
						.collect(Collectors.toMap(AppUser::getId, AppUser::getName));

		return page.map(product -> ProductResponse.from(product)
				.withCreatedByName(createdByNameById.get(product.getCreatedBy())));
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

		List<RiskOptionResponse> availableRiskOptions = riskOptionRepository.findByIsActiveTrue().stream()
				.map(RiskOptionResponse::from).toList();

		return ReviewDetailResponse.build(ProductResponse.from(product), product.getSubmissionCount(),
				evaluationOpt.orElse(null), evaluationModeOpt.orElse(null),
				scoringService.buildWeightSnapshot(evaluationModeId), scoringService.buildMatchedCampaignSnapshot(product),
				aiSelectionService.getLatestAnalysis(productId).orElse(null), availableRiskOptions);
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

		Long reviewerId = resolveUserId(username);

		Optional<ProductEvaluation> evaluationOpt = scoringService.getCurrentEvaluation(product.getId());
		Long evaluationModeId = evaluationOpt.map(ProductEvaluation::getEvaluationModeId).orElse(null);
		Optional<EvaluationMode> evaluationModeOpt = scoringService.getEvaluationMode(evaluationModeId);

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

		record.setMatchedCampaignSnapshot(scoringService.buildMatchedCampaignSnapshot(product));
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
		List<Long> riskOptionIds = saveReviewRisks(saved.getId(), request.getRiskOptionIds());

		return ReviewRecordResponse.from(saved, riskOptionIds);
	}

	// ========================= 內部輔助方法 =========================

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

	private ProductSnapshot buildProductSnapshot(Product product) {
		ProductSnapshot snapshot = new ProductSnapshot();
		snapshot.setName(product.getName());
		snapshot.setPricingType(product.getPricingType() != null ? product.getPricingType().name() : null);
		snapshot.setCostPrice(product.getCostPrice());
		snapshot.setSalePrice(product.getSalePrice());
		snapshot.setCampaignTags(product.getCampaignTags());
		snapshot.setMoq(product.getMoq());
		snapshot.setSupplyStability(product.getSupplyStability());
		snapshot.setPriceCompetitiveness(product.getPriceCompetitiveness());
		snapshot.setTargetCustomerDescription(product.getTargetCustomerDescription());
		snapshot.setEstimatedPurchaseRate(product.getEstimatedPurchaseRate());
		return snapshot;
	}

	/**
	 * 寫入review_risks（多對多）。複合主鍵天生避免重複勾選寫入（見ReviewRisk.java），
	 * 這裡仍先distinct()一次是為了避免對同一組合鍵save()兩次觸發不必要的重複UPDATE語句
	 * （JPA對已存在的複合主鍵save()會走UPDATE而非INSERT，雖然結果正確但多一次往返）。
	 */
	private List<Long> saveReviewRisks(Long reviewId, List<Long> riskOptionIds) {
		if (riskOptionIds == null || riskOptionIds.isEmpty()) {
			return List.of();
		}

		List<Long> distinctIds = riskOptionIds.stream().distinct().toList();
		for (Long riskOptionId : distinctIds) {
			if (!riskOptionRepository.existsById(riskOptionId)) {
				throw new IllegalArgumentException("人工風險選項不存在：" + riskOptionId);
			}
			ReviewRisk reviewRisk = new ReviewRisk();
			reviewRisk.setId(new ReviewRiskId(reviewId, riskOptionId));
			reviewRiskRepository.save(reviewRisk);
		}
		return distinctIds;
	}

	private List<Long> getRiskOptionIds(Long reviewId) {
		return reviewRiskRepository.findById_ReviewId(reviewId).stream().map(risk -> risk.getId().getRiskOptionId())
				.toList();
	}
}