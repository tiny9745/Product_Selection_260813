package com.example.Product_Selection_260813.service.crawler;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.example.Product_Selection_260813.enums.TrendSignalTrendDirection;

/**
 * 把 PTT 原始討論量轉成 0~100 分。純函式、不碰網路與資料庫，方便單元測試把邊界行為釘死。
 *
 * <b>為什麼不能直接用討論則數當分數：</b>AiSuggestionBatchService 的「熱度 &gt;70 標記
 * AI_SUGGESTED」門檻，是照原本模擬資料「基準 50、±5 波動」的分佈校準的。PTT 真實
 * 討論量是長尾分佈——2026-09-24 以 7 個預設看板、90 天窗口實測：
 * <pre>
 *   0      鳳梨酥、滴雞精、電風扇、藍芽耳機、月餅
 *   4~6    氣炸鍋、洗碗精
 *   19~41  雞胸肉、泡麵、咖啡豆、除濕機、牙膏、洗衣精
 *   58     衛生紙
 *   145    蛋捲
 *   298    行動電源
 *   1466   iPhone（30 天窗口即達此量）
 * </pre>
 * 線性換算（討論量 × 係數）不論係數怎麼調，都只能在「一般商品全部趨近 0」與
 * 「稍有討論就爆表」之間二選一，所以改用分段對數：
 * <ul>
 * <li>0 → 0 分；referenceVolume（預設 20，約為實測的一般商品中位數）→ 50 分；
 *     hotVolume（預設 1000）以上 → 100 分。
 * <li>0~reference、reference~hot 兩段各自用對數內插，壓縮長尾。
 * </ul>
 * 套用預設值後：牙膏/洗衣精(41)≈59、衛生紙(58)≈63、蛋捲(145)≈75、行動電源(298)≈84，
 * 一般商品落在 40~60，約 66 以上的討論量才會超過 70 分門檻。
 * 兩個錨點都可以用 ptt.reference-volume／ptt.hot-volume 調整，不用改程式。
 */
public final class MarketBuzzNormalizer {

	private MarketBuzzNormalizer() {
	}

	private static final double MID_SCORE = 50.0;
	private static final double MAX_SCORE = 100.0;

	/** 趨勢分數：近期討論速度每是窗口平均的 2 倍，加 TREND_POINTS_PER_DOUBLING 分。 */
	private static final double TREND_POINTS_PER_DOUBLING = 15.0;

	/** 趨勢分數平滑常數：避免「平常 0 篇、這週 1 篇」這種極小樣本被放大成暴漲。 */
	private static final double TREND_SMOOTHING = 5.0;

	/** trendScore 高於此值視為 UP、低於 DOWN_THRESHOLD 視為 DOWN，中間為 STABLE。 */
	public static final BigDecimal UP_THRESHOLD = new BigDecimal("55");
	public static final BigDecimal DOWN_THRESHOLD = new BigDecimal("45");

	/**
	 * 熱度分數（popularity_score）：討論量的分段對數正規化，見類別註解。
	 */
	public static BigDecimal popularityScore(int volume, int referenceVolume, int hotVolume) {
		if (referenceVolume <= 0 || hotVolume <= referenceVolume) {
			throw new IllegalArgumentException("必須 0 < referenceVolume < hotVolume");
		}
		if (volume <= 0) {
			return scale(0);
		}
		if (volume >= hotVolume) {
			return scale(MAX_SCORE);
		}
		double score;
		if (volume <= referenceVolume) {
			score = MID_SCORE * Math.log1p(volume) / Math.log1p(referenceVolume);
		} else {
			score = MID_SCORE + (MAX_SCORE - MID_SCORE)
					* Math.log((1.0 + volume) / (1.0 + referenceVolume))
					/ Math.log((1.0 + hotVolume) / (1.0 + referenceVolume));
		}
		return scale(score);
	}

	/**
	 * 趨勢分數（trend_score）：最近 recentDays 天的討論量，跟「整個窗口平均分攤到
	 * recentDays 天應有的量」比。50 分＝速度持平；高於 50＝近期升溫；低於 50＝退燒。
	 * 窗口內完全沒有討論時無從判斷趨勢，回傳中性的 50 分。
	 *
	 * @param windowVolume 整個統計窗口的討論量（含最近 recentDays 天）
	 * @param recentVolume 最近 recentDays 天的討論量
	 */
	public static BigDecimal trendScore(int windowVolume, int recentVolume, int windowDays, int recentDays) {
		if (windowDays <= 0 || recentDays <= 0 || recentDays > windowDays) {
			throw new IllegalArgumentException("必須 0 < recentDays <= windowDays");
		}
		if (windowVolume <= 0) {
			return scale(MID_SCORE);
		}
		double expectedRecent = windowVolume * (double) recentDays / windowDays;
		double ratio = (recentVolume + TREND_SMOOTHING) / (expectedRecent + TREND_SMOOTHING);
		double score = MID_SCORE + TREND_POINTS_PER_DOUBLING * (Math.log(ratio) / Math.log(2));
		return scale(Math.max(0, Math.min(MAX_SCORE, score)));
	}

	/**
	 * 趨勢方向：直接由本次 trendScore 判斷，不跟上一筆資料比較。
	 *
	 * 刻意不沿用模擬資料「跟上一筆比大小」的做法：上一筆可能是 SIMULATED 資料，
	 * 拿隨機值跟真實值比沒有意義；而且 PTT 統計窗口是滾動的 90 天，每天重疊 89 天，
	 * 逐日比大小會被微小雜訊主導。以「近期速度是否明顯高於平均」判斷，
	 * AiSuggestionBatchService 的「連續 3 筆 UP」才真的代表連續幾天持續升溫。
	 */
	public static TrendSignalTrendDirection direction(BigDecimal trendScore) {
		if (trendScore.compareTo(UP_THRESHOLD) >= 0) {
			return TrendSignalTrendDirection.UP;
		}
		if (trendScore.compareTo(DOWN_THRESHOLD) <= 0) {
			return TrendSignalTrendDirection.DOWN;
		}
		return TrendSignalTrendDirection.STABLE;
	}

	private static BigDecimal scale(double value) {
		return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
	}
}
