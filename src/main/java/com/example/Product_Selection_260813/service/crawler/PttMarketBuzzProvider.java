package com.example.Product_Selection_260813.service.crawler;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.entity.TrendSignal;
import com.example.Product_Selection_260813.enums.TrendSignalTrendDirection;
import com.example.Product_Selection_260813.service.crawler.PttSearchPageParser.PttPost;
import com.example.Product_Selection_260813.service.crawler.PttSearchPageParser.PttSearchPage;

/**
 * {@link MarketBuzzProvider} 正式實作：在 PTT 網頁版的數個看板搜尋商品關鍵字，
 * 統計窗口期間（預設 90 天）內的「貼文數＋推文量」當作討論量，再交給
 * MarketBuzzNormalizer 換算成 0~100 分。標 {@code @Primary}，Spring 預設注入這一個；
 * 要暫時停用，把 {@code @Primary} 移到 StubMarketBuzzProvider（見該類別說明）。
 *
 * <b>範圍限制（刻意不做）：</b>不登入、不處理驗證碼、不送 over18 cookie 繞過年齡確認、
 * 不偽裝瀏覽器。需要年齡確認的看板（例如 Gossiping、BuyTogether）直接略過。
 *
 * <b>容錯策略（比照 OpenMeteoWeatherSignalProvider）：</b>單一看板失敗（逾時、看板
 * 不存在）只記錄警告並略過，用其餘看板的結果繼續算；所有看板都沒有成功取得資料時，
 * 才拋 MarketBuzzUnavailableException，由 TrendService 改用模擬資料。
 * 「搜尋成功、但 0 篇討論」是正常結果（分數就是 0），不會觸發備援。
 */
@Component
@Primary
public class PttMarketBuzzProvider implements MarketBuzzProvider {

	private static final Logger log = LoggerFactory.getLogger(PttMarketBuzzProvider.class);

	public static final String SOURCE = "PTT";

	private final PttClient pttClient;
	private final Clock clock;
	private final List<String> boards;
	private final int windowDays;
	private final int recentDays;
	private final int maxPagesPerBoard;
	private final int referenceVolume;
	private final int hotVolume;

	@Autowired
	public PttMarketBuzzProvider(PttClient pttClient,
			@Value("${ptt.boards}") List<String> boards,
			@Value("${ptt.window-days:90}") int windowDays,
			@Value("${ptt.recent-days:7}") int recentDays,
			@Value("${ptt.max-pages-per-board:5}") int maxPagesPerBoard,
			@Value("${ptt.reference-volume:20}") int referenceVolume,
			@Value("${ptt.hot-volume:1000}") int hotVolume) {
		this(pttClient, Clock.systemDefaultZone(), boards, windowDays, recentDays, maxPagesPerBoard,
				referenceVolume, hotVolume);
	}

	/** 測試用：可注入固定時鐘。 */
	PttMarketBuzzProvider(PttClient pttClient, Clock clock, List<String> boards, int windowDays, int recentDays,
			int maxPagesPerBoard, int referenceVolume, int hotVolume) {
		this.pttClient = pttClient;
		this.clock = clock;
		this.boards = boards.stream().map(String::trim).filter(b -> !b.isEmpty()).toList();
		this.windowDays = windowDays;
		this.recentDays = recentDays;
		this.maxPagesPerBoard = maxPagesPerBoard;
		this.referenceVolume = referenceVolume;
		this.hotVolume = hotVolume;
	}

	@Override
	public MarketBuzzSignal fetch(String keyword, TrendSignal previous) {
		long nowEpochSeconds = clock.instant().getEpochSecond();
		long windowSeconds = Duration.ofDays(windowDays).toSeconds();
		long recentSeconds = Duration.ofDays(recentDays).toSeconds();

		int windowVolume = 0;
		int recentVolume = 0;
		int succeededBoards = 0;

		for (String board : boards) {
			try {
				BoardVolume volume = crawlBoard(board, keyword, nowEpochSeconds, windowSeconds, recentSeconds);
				if (volume == null) {
					continue;
				}
				windowVolume += volume.window();
				recentVolume += volume.recent();
				succeededBoards++;
			} catch (IOException e) {
				log.warn("PTT 看板 {} 搜尋「{}」失敗，略過此看板：{}", board, keyword, e.getMessage());
			}
		}

		if (succeededBoards == 0) {
			throw new MarketBuzzUnavailableException("PTT 所有看板都無法取得「" + keyword + "」的搜尋結果");
		}

		BigDecimal popularityScore = MarketBuzzNormalizer.popularityScore(windowVolume, referenceVolume, hotVolume);
		BigDecimal trendScore = MarketBuzzNormalizer.trendScore(windowVolume, recentVolume, windowDays, recentDays);
		TrendSignalTrendDirection direction = MarketBuzzNormalizer.direction(trendScore);

		log.info("PTT 熱度「{}」：{} 個看板，{} 天討論量 {}（近 {} 天 {}）→ 熱度 {}、趨勢 {}（{}）",
				keyword, succeededBoards, windowDays, windowVolume, recentDays, recentVolume,
				popularityScore, trendScore, direction);

		return new MarketBuzzSignal(SOURCE, keyword, trendScore, popularityScore, direction, windowVolume);
	}

	private record BoardVolume(int window, int recent) {
	}

	/**
	 * 由新到舊翻頁，直到遇到窗口外的文章、最後一頁或頁數上限為止。
	 *
	 * @return 看板需要年齡確認時回傳 null（略過，但不算失敗）
	 */
	private BoardVolume crawlBoard(String board, String keyword, long nowEpochSeconds, long windowSeconds,
			long recentSeconds) throws IOException {
		int window = 0;
		int recent = 0;
		for (int page = 1; page <= maxPagesPerBoard; page++) {
			Optional<Document> document = pttClient.fetchSearchPage(board, keyword, page);
			if (document.isEmpty()) {
				break;
			}
			PttSearchPage result = PttSearchPageParser.parse(document.get());
			if (result.over18Gated()) {
				log.info("PTT 看板 {} 需要年齡確認，依規範不繞過，略過此看板", board);
				return null;
			}

			boolean reachedOlderThanWindow = false;
			for (PttPost post : result.posts()) {
				long ageSeconds = nowEpochSeconds - post.postedAtEpochSeconds();
				if (ageSeconds > windowSeconds) {
					reachedOlderThanWindow = true;
					continue;
				}
				// 一篇貼文本身算 1，加上它的推文量
				int volume = 1 + post.pushVolume();
				window += volume;
				if (ageSeconds <= recentSeconds) {
					recent += volume;
				}
			}
			if (reachedOlderThanWindow || result.lastPage()) {
				break;
			}
			if (page == maxPagesPerBoard) {
				log.debug("PTT 看板 {} 搜尋「{}」已達頁數上限 {}，討論量可能被低估", board, keyword, maxPagesPerBoard);
			}
		}
		return new BoardVolume(window, recent);
	}
}
