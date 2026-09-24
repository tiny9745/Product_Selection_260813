package com.example.Product_Selection_260813.service.crawler;

import static com.example.Product_Selection_260813.service.crawler.PttSearchPageParserTest.entry;
import static com.example.Product_Selection_260813.service.crawler.PttSearchPageParserTest.page;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PttMarketBuzzProviderTest {

	private static final Instant NOW = Instant.parse("2026-09-24T02:00:00Z");
	private static final String KEYWORD = "氣炸鍋";

	private PttClient pttClient;

	@BeforeEach
	void setUp() {
		pttClient = mock(PttClient.class);
	}

	private PttMarketBuzzProvider provider(List<String> boards) {
		return new PttMarketBuzzProvider(pttClient, Clock.fixed(NOW, ZoneId.of("Asia/Taipei")), boards,
				90, 7, 5, 20, 1000);
	}

	/** 距今 daysAgo 天的文章網址。 */
	private static String postAgo(double daysAgo) {
		long epoch = NOW.minusSeconds((long) (Duration.ofDays(1).toSeconds() * daysAgo)).getEpochSecond();
		return "/bbs/Board/M." + epoch + ".A.ABC.html";
	}

	private static Optional<Document> doc(String html) {
		return Optional.of(Jsoup.parse(html));
	}

	@Test
	void 加總各看板窗口內的貼文與推文_窗口外不計() throws IOException {
		when(pttClient.fetchSearchPage("A", KEYWORD, 1)).thenReturn(doc(page(
				entry("10", postAgo(2))       // 近 7 天：1+10
						+ entry("4", postAgo(30))   // 窗口內：1+4
						+ entry("99", postAgo(120))))); // 窗口外：不計
		when(pttClient.fetchSearchPage("B", KEYWORD, 1)).thenReturn(doc(page(entry("", postAgo(5))))); // 近 7 天：1

		MarketBuzzSignal signal = provider(List.of("A", "B")).fetch(KEYWORD, null);

		assertThat(signal.source()).isEqualTo("PTT");
		assertThat(signal.keyword()).isEqualTo(KEYWORD);
		assertThat(signal.discussionVolume()).isEqualTo(17);
		assertThat(signal.popularityScore())
				.isEqualByComparingTo(MarketBuzzNormalizer.popularityScore(17, 20, 1000));
		assertThat(signal.trendScore())
				.isEqualByComparingTo(MarketBuzzNormalizer.trendScore(17, 12, 90, 7));
	}

	@Test
	void 翻頁直到遇到窗口外文章為止() throws IOException {
		String fullRecentPage = IntStream.range(0, 20).mapToObj(i -> entry("", postAgo(1)))
				.collect(Collectors.joining());
		when(pttClient.fetchSearchPage("A", KEYWORD, 1)).thenReturn(doc(page(fullRecentPage)));
		when(pttClient.fetchSearchPage("A", KEYWORD, 2)).thenReturn(doc(page(
				IntStream.range(0, 19).mapToObj(i -> entry("", postAgo(10))).collect(Collectors.joining())
						+ entry("", postAgo(200)))));

		MarketBuzzSignal signal = provider(List.of("A")).fetch(KEYWORD, null);

		assertThat(signal.discussionVolume()).isEqualTo(39);
		verify(pttClient, never()).fetchSearchPage("A", KEYWORD, 3);
	}

	@Test
	void 翻過最後一頁回傳empty時停止() throws IOException {
		String fullPage = IntStream.range(0, 20).mapToObj(i -> entry("", postAgo(1)))
				.collect(Collectors.joining());
		when(pttClient.fetchSearchPage("A", KEYWORD, 1)).thenReturn(doc(page(fullPage)));
		when(pttClient.fetchSearchPage("A", KEYWORD, 2)).thenReturn(Optional.empty());

		assertThat(provider(List.of("A")).fetch(KEYWORD, null).discussionVolume()).isEqualTo(20);
	}

	@Test
	void 單一看板失敗只略過該看板() throws IOException {
		when(pttClient.fetchSearchPage("A", KEYWORD, 1)).thenThrow(new SocketTimeoutException("timeout"));
		when(pttClient.fetchSearchPage("B", KEYWORD, 1)).thenReturn(doc(page(entry("3", postAgo(1)))));

		assertThat(provider(List.of("A", "B")).fetch(KEYWORD, null).discussionVolume()).isEqualTo(4);
	}

	@Test
	void 需要年齡確認的看板略過_不計入也不繞過() throws IOException {
		when(pttClient.fetchSearchPage("Gossiping", KEYWORD, 1)).thenReturn(doc(
				"<script>location='https://www.ptt.cc/ask/over18?from=x'</script>"
						+ page(entry("50", postAgo(1)))));
		when(pttClient.fetchSearchPage("A", KEYWORD, 1)).thenReturn(doc(page(entry("1", postAgo(1)))));

		assertThat(provider(List.of("Gossiping", "A")).fetch(KEYWORD, null).discussionVolume()).isEqualTo(2);
		verify(pttClient, never()).fetchSearchPage(eq("Gossiping"), eq(KEYWORD), eq(2));
	}

	@Test
	void 搜尋成功但沒有討論_是正常的零分結果() throws IOException {
		when(pttClient.fetchSearchPage("A", KEYWORD, 1)).thenReturn(doc(page("")));

		MarketBuzzSignal signal = provider(List.of("A")).fetch(KEYWORD, null);

		assertThat(signal.source()).isEqualTo("PTT");
		assertThat(signal.popularityScore()).isEqualByComparingTo("0");
		assertThat(signal.trendScore()).isEqualByComparingTo("50");
	}

	@Test
	void 所有看板都失敗時拋出例外_交給備援() throws IOException {
		when(pttClient.fetchSearchPage(eq("A"), eq(KEYWORD), anyInt())).thenThrow(new IOException("503"));
		when(pttClient.fetchSearchPage(eq("B"), eq(KEYWORD), anyInt())).thenThrow(new IOException("503"));

		assertThatThrownBy(() -> provider(List.of("A", "B")).fetch(KEYWORD, null))
				.isInstanceOf(MarketBuzzUnavailableException.class);
	}

	@Test
	void 所有看板都需要年齡確認時也視為取不到資料() throws IOException {
		when(pttClient.fetchSearchPage("Gossiping", KEYWORD, 1))
				.thenReturn(doc("<script>location='/ask/over18'</script>"));

		assertThatThrownBy(() -> provider(List.of("Gossiping")).fetch(KEYWORD, null))
				.isInstanceOf(MarketBuzzUnavailableException.class);
	}
}
