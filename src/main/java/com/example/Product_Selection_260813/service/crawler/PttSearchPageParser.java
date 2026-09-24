package com.example.Product_Selection_260813.service.crawler;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 解析 PTT 看板搜尋結果頁。只做 HTML → 資料的轉換，不發網路請求，
 * 所以可以拿固定的 HTML 樣本做單元測試（見 PttSearchPageParserTest）。
 * selector 全部來自 {@link PttSelectors}。
 */
public final class PttSearchPageParser {

	private PttSearchPageParser() {
	}

	/**
	 * @param postedAtEpochSeconds 發文時間（Unix 秒），取自文章網址
	 * @param pushVolume           推文量，已換算成非負整數（見 parsePushVolume）
	 */
	public record PttPost(long postedAtEpochSeconds, int pushVolume) {
	}

	/**
	 * @param posts        本頁可辨識發文時間的文章（已刪除文章不含在內）
	 * @param over18Gated  看板需要「已滿18歲」確認；為 true 時 posts 一律為空，呼叫端應略過整個看板
	 * @param lastPage     本頁已是最後一頁（不足 20 篇）
	 */
	public record PttSearchPage(List<PttPost> posts, boolean over18Gated, boolean lastPage) {
	}

	public static PttSearchPage parse(Document document) {
		if (document.html().contains(PttSelectors.OVER18_MARKER)) {
			return new PttSearchPage(List.of(), true, true);
		}

		List<Element> entries = document.select(PttSelectors.POST_ENTRY);
		List<PttPost> posts = new ArrayList<>();
		for (Element entry : entries) {
			Element link = entry.selectFirst(PttSelectors.TITLE_LINK);
			if (link == null) {
				continue;
			}
			Matcher matcher = PttSelectors.POST_TIMESTAMP.matcher(link.attr("href"));
			if (!matcher.find()) {
				continue;
			}
			Element pushElement = entry.selectFirst(PttSelectors.PUSH_COUNT);
			int pushVolume = parsePushVolume(pushElement == null ? "" : pushElement.text());
			posts.add(new PttPost(Long.parseLong(matcher.group(1)), pushVolume));
		}
		return new PttSearchPage(posts, false, entries.size() < PttSelectors.POSTS_PER_PAGE);
	}

	/**
	 * 列表上的推文數是「淨推數」（推 − 噓），不是留言總數；要拿到精確留言數得逐篇點開，
	 * 請求量會暴增數十倍，對 PTT 不友善。這裡以淨推數近似討論熱度：
	 * 空白＝0；數字照用；「爆」＝100；「X1~X9」＝10~90；「XX」＝100。
	 * 被噓也算熱度（大家在討論），所以噓文同樣換算成正數。無法辨識的內容視為 0。
	 */
	static int parsePushVolume(String text) {
		String value = text == null ? "" : text.trim();
		if (value.isEmpty()) {
			return 0;
		}
		if (PttSelectors.PUSH_BURST.equals(value) || PttSelectors.PUSH_BOO_BURST.equals(value)) {
			return 100;
		}
		if (value.startsWith(PttSelectors.PUSH_BOO_PREFIX)) {
			String digits = value.substring(PttSelectors.PUSH_BOO_PREFIX.length());
			return digits.matches("\\d") ? Integer.parseInt(digits) * 10 : 0;
		}
		return value.matches("\\d{1,3}") ? Integer.parseInt(value) : 0;
	}
}
