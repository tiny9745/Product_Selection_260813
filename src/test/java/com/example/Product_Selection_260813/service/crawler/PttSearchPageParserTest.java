package com.example.Product_Selection_260813.service.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.example.Product_Selection_260813.service.crawler.PttSearchPageParser.PttPost;
import com.example.Product_Selection_260813.service.crawler.PttSearchPageParser.PttSearchPage;

/**
 * HTML 樣本照 2026-09-24 www.ptt.cc 搜尋結果頁的實際結構製作。PTT 改版導致
 * PttSelectors 失效時，這裡會先壞——爬蟲本身不會報錯，只會悄悄抓到 0 篇。
 */
class PttSearchPageParserTest {

	static String entry(String nrec, String href) {
		String nrecHtml = nrec.isEmpty() ? "" : "<span class=\"hl f3\">" + nrec + "</span>";
		String titleHtml = href == null ? "(本文已被刪除) [someone]" : "<a href=\"" + href + "\">[情報] 測試</a>";
		return """
				<div class="r-ent">
					<div class="nrec">%s</div>
					<div class="title">%s</div>
					<div class="meta"><div class="author">tester</div><div class="date"> 9/24</div></div>
				</div>
				""".formatted(nrecHtml, titleHtml);
	}

	static String page(String entries) {
		return "<html><body><div class=\"r-list-container action-bar-margin bbs-screen\">" + entries
				+ "</div></body></html>";
	}

	private static PttSearchPage parse(String html) {
		return PttSearchPageParser.parse(Jsoup.parse(html));
	}

	@Nested
	class Parse {

		@Test
		void 取出發文時間與推文量() {
			PttSearchPage result = parse(page(
					entry("31", "/bbs/Lifeismoney/M.1750310171.A.4BE.html")
							+ entry("", "/bbs/Lifeismoney/M.1770899824.A.4BB.html")));

			assertThat(result.over18Gated()).isFalse();
			assertThat(result.posts()).containsExactly(
					new PttPost(1750310171L, 31),
					new PttPost(1770899824L, 0));
		}

		@Test
		void 已刪除文章沒有連結_略過() {
			PttSearchPage result = parse(page(entry("5", null) + entry("2", "/bbs/Food/M.1770000000.A.123.html")));
			assertThat(result.posts()).containsExactly(new PttPost(1770000000L, 2));
		}

		@Test
		void 不足二十篇為最後一頁_滿二十篇不是() {
			assertThat(parse(page(entry("1", "/bbs/Food/M.1770000000.A.123.html"))).lastPage()).isTrue();

			String twenty = IntStream.range(0, 20)
					.mapToObj(i -> entry("1", "/bbs/Food/M.17700000" + (10 + i) + ".A.123.html"))
					.collect(Collectors.joining());
			PttSearchPage full = parse(page(twenty));
			assertThat(full.posts()).hasSize(20);
			assertThat(full.lastPage()).isFalse();
		}

		@Test
		void 沒有搜尋結果() {
			PttSearchPage result = parse(page(""));
			assertThat(result.posts()).isEmpty();
			assertThat(result.lastPage()).isTrue();
		}

		@Test
		void 需要年齡確認的看板_整頁略過() {
			String html = "<html><head><script>if (document.cookie.indexOf('over18=1') === -1) {"
					+ "location = 'https://www.ptt.cc/ask/over18?from=' + encodeURIComponent(location.pathname);}"
					+ "</script></head><body>" + entry("10", "/bbs/Gossiping/M.1770000000.A.123.html")
					+ "</body></html>";
			PttSearchPage result = parse(html);
			assertThat(result.over18Gated()).isTrue();
			assertThat(result.posts()).isEmpty();
		}
	}

	@Nested
	class ParsePushVolume {

		@Test
		void 各種推文數格式() {
			assertThat(PttSearchPageParser.parsePushVolume("")).isZero();
			assertThat(PttSearchPageParser.parsePushVolume(" 12 ")).isEqualTo(12);
			assertThat(PttSearchPageParser.parsePushVolume("爆")).isEqualTo(100);
			assertThat(PttSearchPageParser.parsePushVolume("X3")).isEqualTo(30);
			assertThat(PttSearchPageParser.parsePushVolume("XX")).isEqualTo(100);
			assertThat(PttSearchPageParser.parsePushVolume("??")).isZero();
		}
	}
}
