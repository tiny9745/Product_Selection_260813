package com.example.Product_Selection_260813.service.crawler;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * PTT 網頁版的 HTTP 存取，只負責「把一頁搜尋結果抓回來」（比照 weather/WeatherClient 的分工）。
 *
 * 禮貌性限制：所有請求共用同一個間隔（ptt.request-delay-ms，預設 1 秒），方法本身
 * synchronized——即使凌晨排程與使用者手動同步同時在跑，對 PTT 的請求速度也不會加倍。
 * User-Agent 誠實標示是本系統的爬蟲，不偽裝成一般瀏覽器；不登入、不帶 over18 cookie。
 */
@Component
public class PttClient {

	private static final String USER_AGENT = "Mozilla/5.0 (compatible; ProductSelection260813-TrendBot/1.0)";

	@Value("${ptt.timeout-seconds:10}")
	private int timeoutSeconds;

	@Value("${ptt.request-delay-ms:1000}")
	private long requestDelayMs;

	private long lastRequestAtMillis = 0;

	/**
	 * 抓取看板搜尋結果的第 page 頁（1＝最新）。
	 *
	 * @return 頁面內容；page &gt; 1 且 PTT 回 404（已經翻過最後一頁）時回傳 empty
	 * @throws IOException 連線失敗、逾時，或第 1 頁就 404（看板不存在）
	 */
	public synchronized Optional<Document> fetchSearchPage(String board, String keyword, int page) throws IOException {
		waitForRateLimit();
		String url = PttSelectors.BASE_URL + String.format(PttSelectors.SEARCH_PATH_FORMAT, board, page,
				URLEncoder.encode(keyword, StandardCharsets.UTF_8));
		try {
			return Optional.of(Jsoup.connect(url)
					.userAgent(USER_AGENT)
					.timeout(timeoutSeconds * 1000)
					.get());
		} catch (HttpStatusException e) {
			if (e.getStatusCode() == 404 && page > 1) {
				return Optional.empty();
			}
			throw e;
		} finally {
			lastRequestAtMillis = System.currentTimeMillis();
		}
	}

	private void waitForRateLimit() throws IOException {
		long waitMillis = lastRequestAtMillis + requestDelayMs - System.currentTimeMillis();
		if (waitMillis <= 0) {
			return;
		}
		try {
			Thread.sleep(waitMillis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("等待 PTT 請求間隔時被中斷", e);
		}
	}
}
