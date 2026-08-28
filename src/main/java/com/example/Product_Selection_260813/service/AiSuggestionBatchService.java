package com.example.Product_Selection_260813.service;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.entity.TrendSignal;
import com.example.Product_Selection_260813.enums.ProductCandidateStatus;
import com.example.Product_Selection_260813.enums.TrendSignalTrendDirection;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.TrendSignalRepository;

/**
 * AI主動選品批次規則。
 *
 * 對應規格書七、新增功能精簡化設計：
 * 「沿用模擬市場資料集，用單一閾值規則（熱度分數>70 或 連續3天呈上升趨勢，
 * 依Daily Cron同步結果判斷）產生AI_SUGGESTED候選，操作層確認後轉CANDIDATE」。
 *
 * 這是唯一產生candidate_status=AI_SUGGESTED的來源（規格書QA4：手動新增品項
 * 一律直接是CANDIDATE，不會是AI_SUGGESTED，避免語意矛盾）。
 *
 * 判斷依據：TrendSignal.popularityScore（熱度分數），對應資料庫欄位
 * popularity_score，不是trendScore（趨勢分數，語意不同，見四、trend_signals表）。
 *
 * 目前種子資料每個商品僅有1-2筆記錄，尚無法覆蓋「連續3天」情境測試，
 * 該分支邏輯已依規格正確實作，待隊友之後補充多天期種子資料即可驗證。
 */
@Service
public class AiSuggestionBatchService {

	private static final Logger log = LoggerFactory.getLogger(AiSuggestionBatchService.class);

	private static final BigDecimal POPULARITY_THRESHOLD = BigDecimal.valueOf(70);
	private static final int CONSECUTIVE_UP_DAYS_REQUIRED = 3;

	@Autowired
	private TrendSignalRepository trendSignalRepository;

	@Autowired
	private ProductRepository productRepository;

	/**
	 * 對應規格書「依Daily Cron同步結果判斷」，每天凌晨3點跑一次。
	 * demo／開發階段可改用AiSuggestionBatchController的手動觸發端點，
	 * 不需要真的等排程時間到才能驗證效果。
	 */
	@Scheduled(cron = "0 0 3 * * *")
	public void runDailyBatch() {
		log.info("開始執行AI主動選品批次規則（Daily Cron）");
		BatchResult result = runBatch();
		log.info("AI主動選品批次規則執行完畢：檢查{}個商品，新增{}個AI_SUGGESTED候選",
				result.checkedCount(), result.suggestedCount());
	}

	/**
	 * 執行一次完整批次判斷，回傳統計結果。
	 * 供@Scheduled排程與手動觸發端點（AiSuggestionBatchController）共用同一份邏輯，
	 * 避免兩處各寫一份規則導致之後改規則時漏改。
	 */
	@Transactional
	public BatchResult runBatch() {
		List<Long> productIds = trendSignalRepository.findDistinctProductIds();
		int suggestedCount = 0;

		for (Long productId : productIds) {
			if (shouldSuggest(productId)) {
				boolean updated = markAsSuggested(productId);
				if (updated) {
					suggestedCount++;
				}
			}
		}

		return new BatchResult(productIds.size(), suggestedCount);
	}

	/**
	 * 判斷單一商品是否符合AI主動選品條件。
	 * 規則：熱度分數>70 或 連續3天呈上升趨勢——兩者符合其一即可，非同時滿足。
	 */
	private boolean shouldSuggest(Long productId) {
		List<TrendSignal> recentSignals = trendSignalRepository
				.findTop3ByProductIdOrderByCollectedAtDesc(productId);

		if (recentSignals.isEmpty()) {
			return false;
		}

		// 條件1：最新一筆熱度分數>70
		TrendSignal latest = recentSignals.get(0);
		if (latest.getPopularityScore() != null
				&& latest.getPopularityScore().compareTo(POPULARITY_THRESHOLD) > 0) {
			return true;
		}

		// 條件2：連續3天呈上升趨勢。資料筆數不足3筆時，尚無足夠歷史資料判斷「連續」，
		// 此條件直接視為不成立，不對不足的資料做任何推測或補值。
		if (recentSignals.size() < CONSECUTIVE_UP_DAYS_REQUIRED) {
			return false;
		}

		return recentSignals.stream()
				.limit(CONSECUTIVE_UP_DAYS_REQUIRED)
				.allMatch(signal -> signal.getTrendDirection() == TrendSignalTrendDirection.UP);
	}

	/**
	 * 將商品標記為AI_SUGGESTED。
	 * 只處理目前非AI_SUGGESTED的商品，避免每次批次執行都重複寫入同一筆、
	 * 讓suggestedCount統計失真。已審核通過/拒絕/CANDIDATE的商品若符合條件，
	 * 一樣會被改成AI_SUGGESTED——規格書未排除這個情境，暫依規格字面實作；
	 * 若demo後發現這樣會誤動到已審核商品的狀態，屬於Phase 2可再收斂的範圍。
	 */
	private boolean markAsSuggested(Long productId) {
		return productRepository.findById(productId)
				.map(product -> {
					if (product.getCandidateStatus() == ProductCandidateStatus.AI_SUGGESTED) {
						return false;
					}
					product.setCandidateStatus(ProductCandidateStatus.AI_SUGGESTED);
					productRepository.save(product);
					log.debug("商品ID={}（{}）符合AI主動選品條件，標記為AI_SUGGESTED", productId, product.getName());
					return true;
				})
				.orElse(false);
	}

	/**
	 * 批次執行結果統計，供Controller回傳給前端顯示執行摘要。
	 */
	public record BatchResult(int checkedCount, int suggestedCount) {
	}
}
