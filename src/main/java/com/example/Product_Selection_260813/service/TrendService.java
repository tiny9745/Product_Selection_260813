package com.example.Product_Selection_260813.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.TrendSignal;
import com.example.Product_Selection_260813.enums.TrendSignalTrendDirection;
import com.example.Product_Selection_260813.json.TrendSnapshot;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.TrendSignalRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 對應 API總表 四、評估／趨勢／AI 底下唯一掛在TrendController的端點：
 * POST /api/products/{id}/trend/sync。
 *
 * 依十二-13分層決議，本類別只負責「同步趨勢資料」本身（寫入trend_signals），
 * 觸發重算的部分透過呼叫ScoringService完成（單向依賴：TrendService → ScoringService，
 * 不可逆向依賴造成循環）。2026-08-31更新：改為呼叫完整的
 * ScoringService.calculateEvaluation()（取代原本只更新trend_score的
 * updateTrendScoreFromLatestSignal()），確保趨勢資料同步後，六大分項與
 * total_score／final_score都跟著重新計算，而不只是單一欄位更新。
 *
 * 「取得最新市場趨勢／熱門度資料」在六週雛型階段採模擬市場資料集、非即時爬蟲
 * （企劃書十二-2備註已明確界定此範圍，非本類別自行簡化），故本類別不串接任何
 * 外部趨勢資料API，以「上一筆趨勢資料為基準、加上隨機小幅波動」模擬新一筆
 * 趨勢資料，來源欄位固定標記為SIMULATED，與seed資料的GOOGLE_TRENDS等真實來源
 * 值明確區隔，未來要串接真實API時，只需替換generateSimulatedTrendSignal()
 * 這個方法的實作內容，呼叫方（Controller／syncTrend()本身的流程）不需要更動。
 */
@Service
public class TrendService {

	// 無趨勢歷史紀錄時的起始基準分數，模擬用途，非任何實際市場資料
	private static final BigDecimal DEFAULT_BASELINE_SCORE = new BigDecimal("50.00");

	// 每次同步的隨機波動範圍：±(FLUCTUATION_RANGE/2)分
	private static final double FLUCTUATION_RANGE = 10.0;

	private static final BigDecimal SCORE_MIN = BigDecimal.ZERO;
	private static final BigDecimal SCORE_MAX = new BigDecimal("100.00");

	private final Random random = new Random();

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private TrendSignalRepository trendSignalRepository;

	@Autowired
	private ScoringService scoringService;

	/**
	 * POST /api/products/{id}/trend/sync：手動同步指定商品的最新市場趨勢／熱門度資料，
	 * 並觸發評估結果的局部更新（見類別註解與ScoringService.updateTrendScoreFromLatestSignal()）。
	 *
	 * 回傳同步後的趨勢快照（與review_records.trend_snapshot共用同一個DTO／組裝邏輯，
	 * 直接呼叫ScoringService.buildTrendSnapshot()取得，不重複寫一份映射邏輯）。
	 */
	@Transactional
	public TrendSnapshot syncTrend(Long productId) {
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new IllegalArgumentException("商品不存在"));

		TrendSignal newSignal = generateSimulatedTrendSignal(product);
		trendSignalRepository.save(newSignal);

		scoringService.calculateEvaluation(productId, null);

		return scoringService.buildTrendSnapshot(productId);
	}

	private TrendSignal generateSimulatedTrendSignal(Product product) {
		Optional<TrendSignal> previous = trendSignalRepository.findFirstByProductIdOrderByCollectedAtDesc(product.getId());

		String keyword = previous.map(TrendSignal::getKeyword).orElse(product.getName());
		BigDecimal previousTrendScore = previous.map(TrendSignal::getTrendScore).orElse(DEFAULT_BASELINE_SCORE);
		BigDecimal previousPopularityScore = previous.map(TrendSignal::getPopularityScore).orElse(DEFAULT_BASELINE_SCORE);

		BigDecimal newTrendScore = fluctuate(previousTrendScore);
		BigDecimal newPopularityScore = fluctuate(previousPopularityScore);

		TrendSignal signal = new TrendSignal();
		signal.setProductId(product.getId());
		signal.setSource("SIMULATED");
		signal.setKeyword(keyword);
		signal.setTrendScore(newTrendScore);
		signal.setPopularityScore(newPopularityScore);
		signal.setTrendDirection(determineTrendDirection(previousTrendScore, newTrendScore));
		signal.setCollectedAt(LocalDateTime.now());
		return signal;
	}

	private BigDecimal fluctuate(BigDecimal base) {
		double delta = (random.nextDouble() - 0.5) * FLUCTUATION_RANGE;
		BigDecimal result = base.add(BigDecimal.valueOf(delta)).setScale(2, RoundingMode.HALF_UP);
		if (result.compareTo(SCORE_MIN) < 0) {
			return SCORE_MIN;
		}
		if (result.compareTo(SCORE_MAX) > 0) {
			return SCORE_MAX;
		}
		return result;
	}

	private TrendSignalTrendDirection determineTrendDirection(BigDecimal previous, BigDecimal current) {
		int comparison = current.compareTo(previous);
		if (comparison > 0) {
			return TrendSignalTrendDirection.UP;
		}
		if (comparison < 0) {
			return TrendSignalTrendDirection.DOWN;
		}
		return TrendSignalTrendDirection.STABLE;
	}
}
