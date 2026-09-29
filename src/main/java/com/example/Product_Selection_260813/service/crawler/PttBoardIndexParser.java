package com.example.Product_Selection_260813.service.crawler;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 解析 PTT 看板文章列表頁（/bbs/{看板}/index.html）——2026-09-29，PTT 新品探索用。
 *
 * 比照 {@link PttSearchPageParser}：只做 HTML → 資料的轉換，不發網路請求，可以用固定的
 * HTML 樣本做單元測試；selector 全部來自 {@link PttSelectors}。
 *
 * 與搜尋頁的差異見 PttSelectors「看板文章列表」區塊：要讀標題文字、要略過置底公告、
 * 要找出「‹ 上頁」的連結。
 */
public final class PttBoardIndexParser {

	private PttBoardIndexParser() {
	}

	/**
	 * @param postPath             文章路徑（/bbs/{看板}/M.xxx.A.yyy.html），同時是去重的鍵
	 * @param title                列表上的標題（含分類標籤，例如「[情報] ...」）
	 * @param pushVolume           淨推文量（換算規則同 PttSearchPageParser.parsePushVolume）
	 * @param postedAtEpochSeconds 發文時間（Unix 秒），取自文章路徑
	 */
	public record PttIndexPost(String postPath, String title, int pushVolume, long postedAtEpochSeconds) {
	}

	/**
	 * @param posts            本頁一般文章（不含置底公告、已刪除文章）
	 * @param over18Gated      看板需要「已滿18歲」確認；為 true 時 posts 為空，呼叫端應略過整個看板
	 * @param previousPagePath 「‹ 上頁」的路徑；已是最舊一頁或連結格式不符時為 null
	 */
	public record PttIndexPage(List<PttIndexPost> posts, boolean over18Gated, String previousPagePath) {
	}

	public static PttIndexPage parse(Document document) {
		if (document.html().contains(PttSelectors.OVER18_MARKER)) {
			return new PttIndexPage(List.of(), true, null);
		}

		List<PttIndexPost> posts = new ArrayList<>();
		for (Element element : document.select(PttSelectors.ENTRY_OR_SEPARATOR)) {
			if (element.hasClass("r-list-sep")) {
				break; // 分隔線之後是置底公告
			}
			Element link = element.selectFirst(PttSelectors.TITLE_LINK);
			if (link == null) {
				continue; // 已刪除的文章
			}
			String path = link.attr("href");
			Matcher matcher = PttSelectors.POST_TIMESTAMP.matcher(path);
			if (!matcher.find()) {
				continue;
			}
			String title = link.text().trim();
			if (title.isEmpty()) {
				continue;
			}
			Element pushElement = element.selectFirst(PttSelectors.PUSH_COUNT);
			int pushVolume = PttSearchPageParser.parsePushVolume(pushElement == null ? "" : pushElement.text());
			posts.add(new PttIndexPost(path, title, pushVolume, Long.parseLong(matcher.group(1))));
		}
		return new PttIndexPage(posts, false, findPreviousPagePath(document));
	}

	/** 只接受符合看板列表頁格式的路徑，其餘（沒有按鈕、按鈕停用、奇怪的網址）一律視為沒有上一頁。 */
	private static String findPreviousPagePath(Document document) {
		for (Element link : document.select(PttSelectors.PAGING_LINKS)) {
			if (!link.text().contains(PttSelectors.PREVIOUS_PAGE_TEXT)) {
				continue;
			}
			String href = link.attr("href");
			return PttSelectors.INDEX_PAGE_PATH.matcher(href).matches() ? href : null;
		}
		return null;
	}
}
