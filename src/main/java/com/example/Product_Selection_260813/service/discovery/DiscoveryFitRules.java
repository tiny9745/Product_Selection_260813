package com.example.Product_Selection_260813.service.discovery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.Product_Selection_260813.entity.AudienceProfile;
import com.example.Product_Selection_260813.entity.DiscoveredItem;
import com.example.Product_Selection_260813.enums.GateStatus;
import com.example.Product_Selection_260813.enums.TemperatureZone;

/**
 * PTT 新品探索第二階段的規則（純函式，無依賴，見 DiscoveryFitRulesTest）。
 *
 * <b>適配評分＝AI 分數＋規則判定，刻意不合成加權總分：</b>
 * <ul>
 * <li>AI 適配分（0~100）：客群與團購適合度這種判斷只有 AI 做得到，但它是意見，不是事實，
 * 所以必附理由與疑慮，並記錄評分時用的客群版本（{@link #audienceSignature}），可追蹤、可重評。</li>
 * <li>溫層判定（{@link #temperatureGate}）：規則可以確定的事就不交給 AI。沿用 GateStatus，
 * FAILED 與 INSUFFICIENT_DATA 刻意分開（前者是確定不能出貨，後者只是品類沒判定出來）。</li>
 * </ul>
 * 若要合成一個總分就得決定權重，那是沒有依據的數字；兩者並列顯示、清單排序時 FAILED 沉到最後，
 * 已經足夠讓採購人員判斷。
 */
public final class DiscoveryFitRules {

	private DiscoveryFitRules() {
	}

	static final int MAX_REASON_LENGTH = 300;
	static final int MAX_CONCERNS = 3;
	static final int MAX_CONCERN_LENGTH = 30;
	static final int MAX_SIGNATURE_LENGTH = 100;

	/** 送給 AI 評分的一個項目（id 由呼叫端產生）。 */
	public record FitCandidate(String id, String name, String categoryHint, List<String> sampleTitles) {
	}

	/** AI 回傳的一筆評分（未驗證）。 */
	public record FitResult(String id, int score, String reason, List<String> concerns) {
	}

	/**
	 * 通路溫層判定。
	 *
	 * @param zone      品類預設溫層（NORMAL/CHILLED/FROZEN）；品類未判定或品類沒設定為 null
	 * @param supported 通路支援的溫層（AlgorithmSettings.getSupportedTemperatureZones）
	 */
	public static GateStatus temperatureGate(String zone, List<String> supported) {
		if (zone == null || zone.isBlank() || supported == null || supported.isEmpty()) {
			return GateStatus.INSUFFICIENT_DATA;
		}
		return supported.contains(zone.trim().toUpperCase()) ? GateStatus.PASSED : GateStatus.FAILED;
	}

	/**
	 * 評分時使用的客群版本，例 "1@3,2@1"（客群 id@version，依 id 排序）。
	 * 客群設定每次修改 version 都會 +1，簽章不同就代表該重新評分。沒有啟用的客群時為 ""。
	 */
	public static String audienceSignature(List<AudienceProfile> profiles) {
		String signature = profiles.stream()
				.filter(Objects::nonNull)
				.sorted(Comparator.comparing(AudienceProfile::getId, Comparator.nullsLast(Comparator.naturalOrder())))
				.map(profile -> profile.getId() + "@" + profile.getVersion())
				.collect(Collectors.joining(","));
		return signature.length() > MAX_SIGNATURE_LENGTH ? signature.substring(0, MAX_SIGNATURE_LENGTH) : signature;
	}

	/** 從沒評過、或評分後客群設定變了，才需要（重新）評分——不讓每天重複花 AI 額度評同一件東西。 */
	public static boolean needsFitEvaluation(DiscoveredItem item, String currentSignature) {
		return item.getFitEvaluatedAt() == null || !Objects.equals(item.getFitAudienceSig(), currentSignature);
	}

	/**
	 * 給 AI 的評分背景：核心客群＋通路條件。客群欄位都可能沒填，沒填的就不寫，
	 * 不讓模型拿「未知」去推測。
	 */
	public static String describeContext(List<AudienceProfile> profiles, List<String> supportedZones) {
		StringBuilder sb = new StringBuilder("【通路】零庫存團購：集單後由供應商出貨給消費者。");
		if (supportedZones != null && !supportedZones.isEmpty()) {
			sb.append("可配送溫層：").append(supportedZones.stream().map(DiscoveryFitRules::zoneLabel)
					.collect(Collectors.joining("、"))).append("。");
		}
		sb.append('\n');
		if (profiles.isEmpty()) {
			sb.append("【核心客群】尚未設定，請以一般台灣家庭消費者評估，並在 concerns 註明「客群未設定」。\n");
			return sb.toString();
		}
		for (AudienceProfile profile : profiles) {
			sb.append("【核心客群】").append(profile.getName());
			if (profile.getAgeMin() != null || profile.getAgeMax() != null) {
				sb.append("；年齡 ").append(profile.getAgeMin() == null ? "" : profile.getAgeMin()).append("～")
						.append(profile.getAgeMax() == null ? "" : profile.getAgeMax()).append(" 歲");
			}
			if (profile.getPriceSensitivity() != null) {
				sb.append("；價格敏感度：").append(profile.getPriceSensitivity().getPriceSensitivityStatus());
			}
			if (profile.getPreferenceDescription() != null && !profile.getPreferenceDescription().isBlank()) {
				sb.append("；偏好：").append(profile.getPreferenceDescription().trim());
			}
			if (profile.getKeywords() != null && !profile.getKeywords().isBlank()) {
				sb.append("；關鍵字：").append(profile.getKeywords().trim());
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	private static String zoneLabel(String code) {
		try {
			return TemperatureZone.valueOf(code).getLabel();
		} catch (IllegalArgumentException e) {
			return code;
		}
	}

	/** 驗證後的評分，可以直接寫回 DiscoveredItem。 */
	public record VerifiedFit(int score, String reason, String concerns) {
	}

	/**
	 * 驗證 AI 回傳的評分：id 必須是這批送出去的、分數夾在 0~100、理由與疑慮裁切長度。
	 * 同一個 id 回了兩次只採第一筆。沒有回到的 id 就是沒評到，下次再評（不補預設分數）。
	 *
	 * @return 送出去的 id → 驗證後的評分
	 */
	public static Map<String, VerifiedFit> verifyFit(List<FitResult> results, Set<String> sentIds) {
		Map<String, VerifiedFit> verified = new java.util.LinkedHashMap<>();
		for (FitResult result : results) {
			if (result == null || result.id() == null || !sentIds.contains(result.id())
					|| verified.containsKey(result.id())) {
				continue;
			}
			int score = Math.max(0, Math.min(100, result.score()));
			String reason = truncate(result.reason(), MAX_REASON_LENGTH);
			List<String> concerns = new ArrayList<>();
			for (String concern : result.concerns() == null ? List.<String>of() : result.concerns()) {
				if (concern != null && !concern.isBlank() && concerns.size() < MAX_CONCERNS) {
					concerns.add(truncate(concern.trim().replace("、", "／"), MAX_CONCERN_LENGTH));
				}
			}
			verified.put(result.id(), new VerifiedFit(score, reason, concerns.isEmpty() ? null
					: truncate(String.join("、", concerns), MAX_REASON_LENGTH)));
		}
		return verified;
	}

	private static String truncate(String value, int max) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
	}
}
