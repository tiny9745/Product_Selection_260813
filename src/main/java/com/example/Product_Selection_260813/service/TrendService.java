package com.example.Product_Selection_260813.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.TrendSignal;
import com.example.Product_Selection_260813.enums.ProductItemStatus;
import com.example.Product_Selection_260813.json.TrendSnapshot;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.TrendSignalRepository;
import com.example.Product_Selection_260813.service.crawler.MarketBuzzProvider;
import com.example.Product_Selection_260813.service.crawler.MarketBuzzSignal;
import com.example.Product_Selection_260813.service.crawler.StubMarketBuzzProvider;
import com.example.Product_Selection_260813.service.crawler.TrendCrawlerSettings;

/**
 * 對應 API總表 四、評估／趨勢／AI 底下掛在TrendController的端點：
 * POST /api/products/{id}/trend/sync、GET /api/products/{id}/trend，以及全商品
 * 熱度同步的逐一同步邏輯（排程、執行紀錄、手動觸發見 TrendSyncRunService）。
 *
 * 依十二-13分層決議，本類別只負責「同步趨勢資料」本身（寫入trend_signals），
 * 觸發重算的部分透過呼叫ScoringService完成（單向依賴：TrendService → ScoringService，
 * 不可逆向依賴造成循環）。2026-08-31更新：改為呼叫完整的
 * ScoringService.calculateEvaluation()，確保趨勢資料同步後，六大分項與
 * total_score／final_score都跟著重新計算，而不只是單一欄位更新。
 *
 * <b>2026-09-24：模擬資料改為真實 PTT 討論量。</b>原本 generateSimulatedTrendSignal()
 * 的 Random 邏輯搬到 StubMarketBuzzProvider，本類別改透過 MarketBuzzProvider 介面
 * 取得資料（Spring 預設注入 @Primary 的 PttMarketBuzzProvider），架構比照
 * service/weather/。PTT 整體抓不到資料時退回模擬資料，同步流程不中斷；
 * source 欄位誠實標記實際來源（PTT／SIMULATED），兩者並存於 trend_signals，
 * 每次同步都是新增一筆，不覆寫舊資料。
 *
 * <b>交易邊界：</b>爬 PTT 一個商品要數秒到數十秒（多個看板、每次請求間隔 1 秒），
 * 所以刻意不在 @Transactional 裡爬——先在交易外取得資料，拿到結果後才開交易
 * 寫入 trend_signals 並重算評分，避免長時間佔用資料庫連線。
 */
@Service
public class TrendService {

	private static final Logger log = LoggerFactory.getLogger(TrendService.class);

	/** trend_signals.keyword 欄位長度。 */
	private static final int KEYWORD_MAX_LENGTH = 100;

	/** 商品名稱中的括號內容，例如「(預購)」「【團購價】」。 */
	private static final Pattern BRACKETED = Pattern.compile("[(（\\[【][^)）\\]】]*[)）\\]】]");

	/**
	 * 商品名稱中的規格／數量字樣，例如「500g」「1.5L」「x3入」「12包」。
	 * 單位後面不可緊接英文字母（避免把「10gb」切成「10g」），但允許接 x，
	 * 才能處理「100抽x12包」這種連寫。
	 */
	private static final Pattern SPEC_TOKEN = Pattern.compile(
			"(?i)[x×*]?\\s*\\d+(\\.\\d+)?\\s*(kg|g|ml|l|公克|克|公斤|入|包|盒|罐|瓶|片|顆|組|袋|箱|抽|捲|件)(?![a-z&&[^x]])"
					+ "|[x×*]\\s*\\d+");

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private TrendSignalRepository trendSignalRepository;

	@Autowired
	private ScoringService scoringService;

	/** 預設為 @Primary 的 PttMarketBuzzProvider。 */
	@Autowired
	private MarketBuzzProvider marketBuzzProvider;

	/** 主要來源抓不到資料時的備援。 */
	@Autowired
	@Qualifier("stubMarketBuzzProvider")
	private MarketBuzzProvider fallbackMarketBuzzProvider;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private TrendCrawlerSettings trendCrawlerSettings;

	/**
	 * POST /api/products/{id}/trend/sync：手動同步指定商品的最新市場趨勢／熱門度資料，
	 * 並觸發評估結果重算。
	 *
	 * 回傳同步後的趨勢快照（與review_records.trend_snapshot共用同一個DTO／組裝邏輯，
	 * 直接呼叫ScoringService.buildTrendSnapshot()取得，不重複寫一份映射邏輯）。
	 *
	 * 注意：改接 PTT 後這支 API 需要數秒到數十秒才會回應（見類別註解）。
	 */
	public TrendSnapshot syncTrend(Long productId) {
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new IllegalArgumentException("商品不存在"));
		if (!trendCrawlerSettings.isPttEnabled()) {
			// 停用時不改用模擬資料，理由見 TrendCrawlerSettings 類別說明
			throw new IllegalStateException("PTT 熱度來源目前已停用，請管理者到系統設定啟用後再同步");
		}

		syncProduct(product);

		return scoringService.buildTrendSnapshot(productId);
	}

	/**
	 * GET /api/products/{id}/trend：最新一筆趨勢資料，不觸發爬蟲；尚無資料回傳 null。
	 */
	public TrendSnapshot getLatestTrend(Long productId) {
		if (!productRepository.existsById(productId)) {
			throw new IllegalArgumentException("商品不存在");
		}
		return scoringService.buildTrendSnapshot(productId);
	}

	/**
	 * GET /api/products/{id}/trend/history：最近 30 天的趨勢歷史序列，供
	 * 品項詳情頁畫趨勢圖用。⚠️ 2026-09-25 新增，見 ScoringService.
	 * buildTrendHistory() 的完整說明。
	 */
	public List<com.example.Product_Selection_260813.json.TrendHistoryPoint> getHistory(Long productId) {
		if (!productRepository.existsById(productId)) {
			throw new IllegalArgumentException("商品不存在");
		}
		return scoringService.buildTrendHistory(productId);
	}

	/** 全商品同步的統計結果；processed = 已處理（成功＋備援＋失敗）的商品數。 */
	public record SyncAllResult(int total, int realCount, int fallbackCount, int failedCount) {
		public int processed() {
			return realCount + fallbackCount + failedCount;
		}
	}

	/** 全商品同步的對象：所有未封存商品。 */
	public List<Product> findProductsToSync() {
		return productRepository.findByItemStatus(ProductItemStatus.ACTIVE);
	}

	/**
	 * 依序同步多個商品。排程、執行紀錄、防止重複執行都由 TrendSyncRunService 負責，
	 * 這裡只做「逐一同步＋計數」。
	 *
	 * 單一商品失敗只記錄錯誤，不中斷其他商品。每處理完一個商品前先問 shouldContinue，
	 * 回傳 false 就提早結束（例如執行途中 PTT 來源被停用），已處理的結果照樣保留。
	 *
	 * @param onProgress 每處理完一個商品回報一次目前的統計，供畫面顯示進度
	 */
	public SyncAllResult syncAll(List<Product> products, BooleanSupplier shouldContinue,
			Consumer<SyncAllResult> onProgress) {
		int realCount = 0;
		int fallbackCount = 0;
		int failedCount = 0;
		for (Product product : products) {
			if (!shouldContinue.getAsBoolean()) {
				break;
			}
			try {
				TrendSignal saved = syncProduct(product);
				if (StubMarketBuzzProvider.SOURCE.equals(saved.getSource())) {
					fallbackCount++;
				} else {
					realCount++;
				}
			} catch (RuntimeException e) {
				failedCount++;
				log.error("商品 {}（{}）熱度同步失敗，繼續下一個商品", product.getId(), product.getName(), e);
			}
			onProgress.accept(new SyncAllResult(products.size(), realCount, fallbackCount, failedCount));
		}
		return new SyncAllResult(products.size(), realCount, fallbackCount, failedCount);
	}

	/** 取得熱度（交易外）→ 寫入 trend_signals 並重算評分（交易內）。 */
	private TrendSignal syncProduct(Product product) {
		TrendSignal previous = trendSignalRepository.findFirstByProductIdOrderByCollectedAtDesc(product.getId())
				.orElse(null);
		MarketBuzzSignal buzz = fetchWithFallback(toSearchKeyword(product.getName()), previous);

		TrendSignal signal = new TrendSignal();
		signal.setProductId(product.getId());
		signal.setSource(buzz.source());
		signal.setKeyword(buzz.keyword());
		signal.setTrendScore(buzz.trendScore());
		signal.setPopularityScore(buzz.popularityScore());
		signal.setTrendDirection(buzz.trendDirection());
		signal.setCollectedAt(LocalDateTime.now());

		return new TransactionTemplate(transactionManager).execute(status -> {
			TrendSignal saved = trendSignalRepository.save(signal);
			scoringService.calculateEvaluation(product.getId(), null);
			return saved;
		});
	}

	private MarketBuzzSignal fetchWithFallback(String keyword, TrendSignal previous) {
		try {
			return marketBuzzProvider.fetch(keyword, previous);
		} catch (RuntimeException e) {
			log.warn("取得「{}」市場熱度失敗，改用模擬資料：{}", keyword, e.getMessage());
			return fallbackMarketBuzzProvider.fetch(keyword, previous);
		}
	}

	/**
	 * 商品名稱 → 搜尋關鍵字：去掉括號內容與規格數量字樣（「500g」「x3入」等），
	 * 這些字樣幾乎不會出現在 PTT 討論標題裡，留著只會讓搜尋結果變成 0。
	 * 清完變空字串時退回原始名稱。
	 */
	static String toSearchKeyword(String productName) {
		String original = productName == null ? "" : productName.trim();
		String cleaned = SPEC_TOKEN.matcher(BRACKETED.matcher(original).replaceAll(" ")).replaceAll(" ")
				.replaceAll("\\s+", " ")
				.trim();
		String keyword = cleaned.isEmpty() ? original : cleaned;
		return keyword.length() > KEYWORD_MAX_LENGTH ? keyword.substring(0, KEYWORD_MAX_LENGTH) : keyword;
	}
}
