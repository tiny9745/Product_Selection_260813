package com.example.Product_Selection_260813.service.discovery;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 驗證 AI 抽出的商品（純函式，見 DiscoveryVerifierTest）。
 *
 * <b>為什麼要驗證：</b>LLM 可能捏造標題裡沒有的商品、引用不存在的標題編號，或把一整句話
 * 當成商品名。這裡只保留「mentionText 確實出現在它引用的標題裡」的結果——判斷完全在 Java 端
 * 以字串比對完成，不信任模型的自我聲明。
 *
 * 規則：
 * <ol>
 * <li>canonicalName 正規化後 2～{@value #MAX_NAME_LENGTH} 字；</li>
 * <li>mentionText 正規化後至少 2 字；</li>
 * <li>titleIds 只保留「存在於本批」且「正規化標題包含正規化 mentionText」的編號；</li>
 * <li>保留下來的編號為 0 個 → 整筆丟棄。</li>
 * </ol>
 */
public final class DiscoveryVerifier {

	private DiscoveryVerifier() {
	}

	static final int MIN_NAME_LENGTH = 2;
	static final int MAX_NAME_LENGTH = 60;

	/** AI 回傳的一筆原始結果（未驗證）。 */
	public record ExtractedItem(String canonicalName, String mentionText, String categoryHint, List<String> titleIds) {
	}

	/**
	 * 通過驗證的結果。
	 *
	 * @param normalizedName 去重鍵（canonicalName 正規化）
	 * @param titleIds       確實提到這個商品的標題編號（已排除捏造的引用）
	 */
	public record VerifiedItem(String normalizedName, String canonicalName, String mentionText, String categoryHint,
			Set<String> titleIds) {
	}

	/**
	 * @param titlesById 本批送給 AI 的標題（編號 → 原始標題）
	 * @return 通過驗證的結果；不通過回傳 null
	 */
	public static VerifiedItem verify(ExtractedItem item, Map<String, String> titlesById) {
		if (item == null || item.titleIds() == null) {
			return null;
		}
		String canonical = item.canonicalName() == null ? "" : item.canonicalName().trim();
		String normalizedName = DiscoveryText.normalize(canonical);
		if (normalizedName.length() < MIN_NAME_LENGTH || normalizedName.length() > MAX_NAME_LENGTH) {
			return null;
		}
		String mention = item.mentionText() == null ? "" : item.mentionText().trim();
		String normalizedMention = DiscoveryText.normalize(mention);
		if (normalizedMention.length() < MIN_NAME_LENGTH) {
			return null;
		}

		Set<String> validIds = new LinkedHashSet<>();
		for (String id : item.titleIds()) {
			String title = titlesById.get(id);
			if (title != null && DiscoveryText.normalize(title).contains(normalizedMention)) {
				validIds.add(id);
			}
		}
		if (validIds.isEmpty()) {
			return null;
		}
		String hint = item.categoryHint() == null || item.categoryHint().isBlank() ? null : item.categoryHint().trim();
		return new VerifiedItem(normalizedName, canonical, mention, hint, validIds);
	}
}
