package com.example.Product_Selection_260813.service.discovery;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PTT 新品探索的文字處理（純函式，無依賴，見 DiscoveryTextTest）。
 *
 * <ul>
 * <li>{@link #normalize(String)}：去重與比對用的正規化——全形轉半形（NFKC）、英文小寫、
 * 去掉空白與標點。「Dyson  V15」「ＤＹＳＯＮ v15」「dyson-v15」都會變成「dysonv15」。</li>
 * <li>{@link #cleanTitle(String)}：去掉「Re: 」「Fw: 」前綴，讓回文與原文合併成同一則標題。</li>
 * <li>{@link #isExcludedTitle(String)}：公告、板務等明顯不是在談商品的文章，不送給 AI。</li>
 * </ul>
 */
public final class DiscoveryText {

	private DiscoveryText() {
	}

	/** 不是在談商品的文章分類（標題開頭的 [xxx]）。只擋「確定不是」的，拿不準的一律交給 AI 判斷。 */
	static final Set<String> EXCLUDED_CATEGORIES = Set.of("公告", "板務", "版務", "申訴", "檢舉", "水桶", "置底", "徵才",
			"活動", "抽獎");

	private static final Pattern REPLY_PREFIX = Pattern.compile("^(?:(?:Re|RE|re|Fw|FW|fw)\\s*[:：]\\s*)+");
	private static final Pattern CATEGORY_TAG = Pattern.compile("^\\[([^\\]]{1,8})\\]");
	/** 保留字母、數字（含 CJK），其餘（空白、標點、符號）全部去掉。 */
	private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

	/** 太短的標題（例如只有「[問題]」）沒有商品資訊可抽。 */
	static final int MIN_TITLE_LENGTH = 6;

	public static String normalize(String text) {
		if (text == null) {
			return "";
		}
		String nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		return NON_WORD.matcher(nfkc).replaceAll("");
	}

	public static String cleanTitle(String title) {
		if (title == null) {
			return "";
		}
		return REPLY_PREFIX.matcher(title.trim()).replaceFirst("").trim();
	}

	public static boolean isExcludedTitle(String cleanedTitle) {
		if (cleanedTitle == null || cleanedTitle.length() < MIN_TITLE_LENGTH) {
			return true;
		}
		Matcher matcher = CATEGORY_TAG.matcher(cleanedTitle);
		return matcher.find() && EXCLUDED_CATEGORIES.contains(matcher.group(1).trim());
	}
}
