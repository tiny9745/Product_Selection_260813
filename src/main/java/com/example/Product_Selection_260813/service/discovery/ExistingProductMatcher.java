package com.example.Product_Selection_260813.service.discovery;

import java.util.List;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;

/**
 * 判斷探索到的商品是不是系統裡已經有的商品（純函式，見 ExistingProductMatcherTest）。
 * 已有的商品交給現有的熱度同步流程，不重複列為「新品」。
 *
 * 比對一律用 {@link DiscoveryText#normalize(String)} 之後的字串，兩種情況視為相同：
 * <ol>
 * <li>Jaro-Winkler 相似度 ≥ {@value #SIMILARITY_THRESHOLD}（與 ProductSimilarityService 同一套演算法，
 * 對「開頭相同、後面多了規格字樣」的名稱給高分）；</li>
 * <li>一方包含另一方，且短的那方長度至少是長的 {@value #CONTAINMENT_MIN_RATIO} 倍——避免既有商品叫
 * 「氣炸鍋」時，把「飛利浦氣炸鍋」這種更具體的新品也吃掉。</li>
 * </ol>
 * 兩個門檻都是初始值，沒有用真實資料校準過；太鬆會漏掉新品、太嚴會把既有商品當新品，
 * 之後看探索結果再調整這兩個常數即可。
 */
public final class ExistingProductMatcher {

	static final double SIMILARITY_THRESHOLD = 0.92;
	static final double CONTAINMENT_MIN_RATIO = 0.6;

	private static final JaroWinklerSimilarity JARO_WINKLER = new JaroWinklerSimilarity();

	private ExistingProductMatcher() {
	}

	/** @param normalizedProductNames 既有商品名稱，已經過 DiscoveryText.normalize */
	public static boolean matchesAny(String normalizedName, List<String> normalizedProductNames) {
		if (normalizedName == null || normalizedName.isEmpty()) {
			return false;
		}
		for (String productName : normalizedProductNames) {
			if (matches(normalizedName, productName)) {
				return true;
			}
		}
		return false;
	}

	static boolean matches(String a, String b) {
		if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
			return false;
		}
		if (a.equals(b)) {
			return true;
		}
		String shorter = a.length() <= b.length() ? a : b;
		String longer = shorter == a ? b : a;
		if (longer.contains(shorter) && (double) shorter.length() / longer.length() >= CONTAINMENT_MIN_RATIO) {
			return true;
		}
		return JARO_WINKLER.apply(a, b) >= SIMILARITY_THRESHOLD;
	}
}
