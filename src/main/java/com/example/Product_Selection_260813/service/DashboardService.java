package com.example.Product_Selection_260813.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.dto.response.DashboardConversionRateResponse;
import com.example.Product_Selection_260813.dto.response.DashboardRecommendationItem;
import com.example.Product_Selection_260813.dto.response.DashboardRiskAlertItem;
import com.example.Product_Selection_260813.dto.response.DashboardStatisticsResponse;
import com.example.Product_Selection_260813.entity.AiAnalysis;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ReviewRecord;
import com.example.Product_Selection_260813.entity.RiskOption;
import com.example.Product_Selection_260813.enums.ProductCandidateStatus;
import com.example.Product_Selection_260813.enums.ProductItemStatus;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;
import com.example.Product_Selection_260813.enums.ReviewRecordReviewStatus;
import com.example.Product_Selection_260813.repository.AiAnalysisRepository;
import com.example.Product_Selection_260813.repository.ProductEvaluationRepository;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.ReviewRecordRepository;
import com.example.Product_Selection_260813.repository.RiskOptionRepository;

/**
 * 對應 API總表「2. 儀表板」四支端點。企劃書十二-13的Controller對應表本身
 * 沒有列出DashboardController／DashboardService（該表遺漏，非本類別自行
 * 判斷不需要），這裡依照專案既有的Controller/Service命名慣例補上。
 *
 * 四支端點彼此獨立，不共用內部狀態，唯一共同點是都只做唯讀查詢與聚合計算，
 * 不寫入任何資料表。
 */
@Service
public class DashboardService {

	// GET /api/dashboard/recommendations 固定回傳前10筆（企劃書明訂「前10項商品」）
	private static final int RECOMMENDATIONS_LIMIT = 10;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductEvaluationRepository productEvaluationRepository;

	@Autowired
	private ReviewRecordRepository reviewRecordRepository;

	@Autowired
	private AiAnalysisRepository aiAnalysisRepository;

	@Autowired
	private RiskOptionRepository riskOptionRepository;

	/**
	 * GET /api/dashboard/statistics：商品總數、待審核數、通過數、拒絕數。
	 */
	@Transactional(readOnly = true)
	public DashboardStatisticsResponse getStatistics() {
		DashboardStatisticsResponse response = new DashboardStatisticsResponse();
		response.setTotalProducts(productRepository.count());
		response.setPendingCount(productRepository.countByReviewStatus(ProductReviewStatus.PENDING));
		response.setApprovedCount(productRepository.countByReviewStatus(ProductReviewStatus.APPROVED));
		response.setRejectedCount(productRepository.countByReviewStatus(ProductReviewStatus.REJECTED));
		return response;
	}

	/**
	 * GET /api/dashboard/recommendations：依Final Score取前10項商品，含重新入榜標籤。
	 *
	 * 範圍限定review_status=PENDING（仍待審核，「不代表系統自動核准」）、
	 * candidate_status=CANDIDATE、item_status=ACTIVE（企劃書QA4：只有CANDIDATE
	 * 狀態商品才會出現在推薦清單）。已核准／已拒絕（未重新送審）的商品不會出現在
	 * 這裡——已核准的不需要再被「推薦」，已拒絕且未重新送審的則要等操作人員
	 * 重新送審變回PENDING才會再次出現。
	 */
	@Transactional(readOnly = true)
	public List<DashboardRecommendationItem> getRecommendations() {
		List<Product> topProducts = productRepository.findTopRecommendations(ProductReviewStatus.PENDING,
				ProductCandidateStatus.CANDIDATE, ProductItemStatus.ACTIVE, PageRequest.of(0, RECOMMENDATIONS_LIMIT));

		return topProducts.stream().map(this::toRecommendationItem).toList();
	}

	private DashboardRecommendationItem toRecommendationItem(Product product) {
		DashboardRecommendationItem item = new DashboardRecommendationItem();
		item.setProductId(product.getId());
		item.setProductName(product.getName());
		item.setSubmissionCount(product.getSubmissionCount());
		// product 物件已在記憶體裡（findTopRecommendations 查出來的），
		// 不需要為了 productTypeId 多查一次。
		item.setProductTypeId(product.getProductTypeId());

		productEvaluationRepository.findByProductId(product.getId())
				.ifPresent(evaluation -> {
					item.setFinalScore(evaluation.getFinalScore());
					// dataCompleteness 跟 finalScore 來自同一個已查出的 evaluation 物件，
					// 多讀一個既有欄位，不會多一次查詢。
					item.setDataCompleteness(evaluation.getDataCompleteness());
				});

		// submissionCount>1代表曾經歷過「拒絕→重新送審」，才附加重新入榜標籤
		if (product.getSubmissionCount() != null && product.getSubmissionCount() > 1) {
			item.setReentryLabel("曾被拒絕．第" + product.getSubmissionCount() + "次送審");
			findLastRejectionComment(product.getId()).ifPresent(item::setLastRejectionComment);
		}
		return item;
	}

	private Optional<String> findLastRejectionComment(Long productId) {
		return reviewRecordRepository.findByProductIdOrderByReviewedAtDesc(productId).stream()
				.filter(record -> record.getReviewStatus() == ReviewRecordReviewStatus.REJECTED)
				.findFirst()
				.map(ReviewRecord::getReviewComment);
	}

	/**
	 * GET /api/dashboard/risk-alerts：依AI風險提示文字關鍵字比對，取得需要特別
	 * 注意的商品。
	 *
	 * 資料來源為每個商品「最新一筆」ai_analyses的summary／reasons文字，
	 * 命中risk_options.alert_keywords（逗號分隔關鍵字組，僅比對is_active=TRUE
	 * 的風險選項）其中任一詞即列入。不限定review_status——這是提醒管理層
	 * 「這個商品的AI分析裡有風險字眼」的一般性示警，不是只給尚未決定的商品看。
	 */
	@Transactional(readOnly = true)
	public List<DashboardRiskAlertItem> getRiskAlerts() {
		List<String> keywords = riskOptionRepository.findByIsActiveTrue().stream()
				.map(RiskOption::getAlertKeywords)
				.filter(k -> k != null && !k.isBlank())
				// 實測seed資料裡alert_keywords用全形頓號「、」分隔（例："缺貨、斷貨、供應不穩"），
				// 不是常見的半形逗號，用split(",")完全切不開，會把整組字串當成一個關鍵字，
				// 比對邏輯形同虛設。這裡同時支援「、」與「,」兩種分隔符號，避免未來新增
				// 風險選項時不管用哪種標點寫入都能正確處理，不假設單一固定格式。
				.flatMap(k -> Arrays.stream(k.split("[、,]")))
				.map(String::trim)
				.filter(k -> !k.isEmpty())
				.distinct()
				.toList();

		if (keywords.isEmpty()) {
			return List.of();
		}

		Map<Long, AiAnalysis> latestAnalysisByProduct = aiAnalysisRepository.findAll().stream()
				.collect(Collectors.toMap(AiAnalysis::getProductId, a -> a,
						(existing, candidate) -> isNewer(candidate, existing) ? candidate : existing));

		List<DashboardRiskAlertItem> alerts = new ArrayList<>();
		for (AiAnalysis analysis : latestAnalysisByProduct.values()) {
			String combinedText = joinNonNull(analysis.getSummary(), analysis.getReasons());
			List<String> matched = keywords.stream().filter(combinedText::contains).toList();
			if (matched.isEmpty()) {
				continue;
			}
			productRepository.findById(analysis.getProductId()).ifPresent(product -> {
				DashboardRiskAlertItem item = new DashboardRiskAlertItem();
				item.setProductId(product.getId());
				item.setProductName(product.getName());
				item.setMatchedKeywords(matched);
				item.setAiReasons(analysis.getReasons());
				alerts.add(item);
			});
		}
		return alerts;
	}

	private boolean isNewer(AiAnalysis candidate, AiAnalysis existing) {
		LocalDateTime candidateTime = candidate.getGeneratedAt();
		LocalDateTime existingTime = existing.getGeneratedAt();
		if (candidateTime == null || existingTime == null) {
			return candidateTime != null;
		}
		int cmp = candidateTime.compareTo(existingTime);
		if (cmp != 0) {
			return cmp > 0;
		}
		// generated_at精確度僅到秒，撞秒時以id較大者（寫入較晚）為準，
		// 與AiAnalysisRepository.findFirstByProductIdOrderByGeneratedAtDescIdDesc()
		// 的排序規則保持一致。
		return candidate.getId() > existing.getId();
	}

	private String joinNonNull(String... parts) {
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part != null) {
				sb.append(part).append('\n');
			}
		}
		return sb.toString();
	}

	/**
	 * GET /api/dashboard/conversion-rate：選品轉換率（見DashboardConversionRateResponse
	 * 類別註解的公式說明）。
	 */
	@Transactional(readOnly = true)
	public DashboardConversionRateResponse getConversionRate() {
		long approvedCount = productRepository.countByReviewStatus(ProductReviewStatus.APPROVED);
		long submittedCount = productRepository.countBySubmissionCountGreaterThan(0);

		DashboardConversionRateResponse response = new DashboardConversionRateResponse();
		response.setApprovedCount(approvedCount);
		response.setSubmittedCount(submittedCount);

		if (submittedCount == 0) {
			response.setRatePercentage(null);
		} else {
			BigDecimal rate = BigDecimal.valueOf(approvedCount)
					.divide(BigDecimal.valueOf(submittedCount), 4, RoundingMode.HALF_UP)
					.multiply(BigDecimal.valueOf(100))
					.setScale(2, RoundingMode.HALF_UP);
			response.setRatePercentage(rate);
		}
		return response;
	}
}