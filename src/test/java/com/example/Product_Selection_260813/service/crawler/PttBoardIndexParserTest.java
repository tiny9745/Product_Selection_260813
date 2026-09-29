package com.example.Product_Selection_260813.service.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import com.example.Product_Selection_260813.service.crawler.PttBoardIndexParser.PttIndexPage;
import com.example.Product_Selection_260813.service.crawler.PttBoardIndexParser.PttIndexPost;

/**
 * HTML 樣本照 www.ptt.cc 看板列表頁（/bbs/{看板}/index.html）的結構製作：
 * 文章列 div.r-ent、置底公告前的 div.r-list-sep、翻頁按鈕 div.btn-group-paging。
 */
class PttBoardIndexParserTest {

	static String entry(String nrec, String href, String title) {
		String nrecHtml = nrec.isEmpty() ? "" : "<span class=\"hl f3\">" + nrec + "</span>";
		String titleHtml = href == null ? "(本文已被刪除) [someone]" : "<a href=\"" + href + "\">" + title + "</a>";
		return """
				<div class="r-ent">
					<div class="nrec">%s</div>
					<div class="title">%s</div>
					<div class="meta"><div class="author">tester</div><div class="date"> 9/28</div></div>
				</div>
				""".formatted(nrecHtml, titleHtml);
	}

	static String page(String previousHref, String entries) {
		String previous = previousHref == null
				? "<a class=\"btn wide disabled\">‹ 上頁</a>"
				: "<a class=\"btn wide\" href=\"" + previousHref + "\">‹ 上頁</a>";
		return """
				<html><body>
				<div class="btn-group btn-group-paging">
					<a class="btn wide" href="/bbs/Lifeismoney/index1.html">最舊</a>
					%s
					<a class="btn wide disabled">下頁 ›</a>
					<a class="btn wide" href="/bbs/Lifeismoney/index.html">最新</a>
				</div>
				<div class="r-list-container action-bar-margin bbs-screen">%s</div>
				</body></html>
				""".formatted(previous, entries);
	}

	private static PttIndexPage parse(String html) {
		return PttBoardIndexParser.parse(Jsoup.parse(html));
	}

	@Test
	void 取出標題推文量與發文時間_找出上一頁() {
		PttIndexPage result = parse(page("/bbs/Lifeismoney/index3999.html",
				entry("12", "/bbs/Lifeismoney/M.1790000000.A.1AB.html", "[情報] 全聯 義美小泡芙 買一送一")
						+ entry("爆", "/bbs/Lifeismoney/M.1790000100.A.2CD.html", "[心得] Dyson V15 吸塵器開箱")));

		assertThat(result.over18Gated()).isFalse();
		assertThat(result.previousPagePath()).isEqualTo("/bbs/Lifeismoney/index3999.html");
		assertThat(result.posts()).containsExactly(
				new PttIndexPost("/bbs/Lifeismoney/M.1790000000.A.1AB.html", "[情報] 全聯 義美小泡芙 買一送一", 12, 1790000000L),
				new PttIndexPost("/bbs/Lifeismoney/M.1790000100.A.2CD.html", "[心得] Dyson V15 吸塵器開箱", 100, 1790000100L));
	}

	@Test
	void 置底公告在分隔線之後_略過() {
		PttIndexPage result = parse(page("/bbs/Food/index10.html",
				entry("3", "/bbs/Food/M.1790000000.A.111.html", "[食記] 台北 好吃拉麵")
						+ "<div class=\"r-list-sep\"></div>"
						+ entry("", "/bbs/Food/M.1600000000.A.222.html", "[公告] 板規")));

		assertThat(result.posts()).extracting(PttIndexPost::title).containsExactly("[食記] 台北 好吃拉麵");
	}

	@Test
	void 已刪除文章沒有連結_略過() {
		PttIndexPage result = parse(page(null, entry("5", null, null)
				+ entry("", "/bbs/Food/M.1790000000.A.333.html", "[問題] 氣炸鍋推薦")));
		assertThat(result.posts()).hasSize(1);
		assertThat(result.previousPagePath()).isNull();
	}

	@Test
	void 上一頁連結格式不符_視為沒有上一頁() {
		PttIndexPage result = parse(page("https://evil.example.com/bbs/Food/index1.html", ""));
		assertThat(result.previousPagePath()).isNull();
	}

	@Test
	void 需要年齡確認的看板_整頁略過() {
		PttIndexPage result = parse("<html><body><script>location='/ask/over18?from=/bbs/Gossiping/index.html'</script></body></html>");
		assertThat(result.over18Gated()).isTrue();
		assertThat(result.posts()).isEmpty();
	}
}
