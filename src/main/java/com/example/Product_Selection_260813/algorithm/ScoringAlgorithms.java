package com.example.Product_Selection_260813.algorithm;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 評分演算法的純函式集合（設計文件「三、演算法」）。
 *
 * 這個類別刻意不依賴 Spring、Repository 或任何外部狀態——所有輸入都由參數傳入，
 * 相同輸入必然得到相同輸出。這是為了滿足系統的硬約束：
 *
 * <b>可重現性</b>：同一件商品、同一份資料、同一組權重，不論何時計算、資料庫裡有
 * 多少其他商品，分數必須完全相同。因為審核快照不可覆蓋，三個月後必須能還原
 * 當時的判斷依據。
 *
 * 這個約束否決了 TOPSIS（排序反轉）、熵權法（權重隨資料池變動）、以及任何用
 * 即時資料算 min/max 或標準差的正規化方式——它們的輸出都會隨資料庫內容改變。
 *
 * 所有方法都是 static 且無副作用，可以直接單元測試而不需要 Spring context。
 */
public final class ScoringAlgorithms {

	/** 計算過程統一使用的精度。最終分數才 setScale(2)。 */
	private static final int CALC_SCALE = 6;

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private ScoringAlgorithms() {
		// 純工具類別，不允許實例化
	}

	// =====================================================================
	// 1. 貝氏收縮（設計文件 3.3）
	// =====================================================================

	/**
	 * 向上層先驗值收縮的加權平均，即 IMDb 加權評分採用的 additive smoothing。
	 *
	 * <pre>
	 * 調整後 = (n / (n + k)) × 自己的比率 + (k / (n + k)) × 上層先驗比率
	 * </pre>
	 *
	 * 解決的問題：品類 A 開過 3 次全成團（100%）與品類 B 開過 50 次成團 45 次（90%），
	 * 直接比較 A 會贏，但常識上 B 更可靠——A 只是樣本太少還沒失敗過。
	 *
	 * 收縮後 A=0.785、B=0.870，排序正確。而且沒有門檻、沒有斷崖，樣本從 0 筆到
	 * 100 筆是平滑過渡的——不需要任何「樣本夠不夠」的判斷式。
	 *
	 * k 的意義：要累積多少筆資料，才願意相信一半自己的數字。k=10 表示第 10 筆時
	 * 一半信自己、一半信整體平均。
	 *
	 * @param rawRate 自己的比率（0~1）。n=0 時可為 null。
	 * @param sampleSize 樣本數；0 代表沒有自己的資料，完全採用先驗值
	 * @param priorRate 上層先驗比率（0~1），不可為 null
	 * @param k 平滑常數，必須為正
	 * @return 收縮後的比率（0~1）
	 */
	public static BigDecimal shrink(BigDecimal rawRate, long sampleSize, BigDecimal priorRate, int k) {
		if (priorRate == null) {
			throw new IllegalArgumentException("上層先驗比率不可為 null");
		}
		if (k <= 0) {
			throw new IllegalArgumentException("平滑常數 k 必須為正數，目前為：" + k);
		}
		// 沒有自己的資料就完全採用上層先驗值。這也涵蓋 rawRate 為 null 的情況，
		// 讓呼叫端不必先做 null 判斷。
		if (sampleSize <= 0 || rawRate == null) {
			return priorRate;
		}

		BigDecimal n = BigDecimal.valueOf(sampleSize);
		BigDecimal weight = n.divide(n.add(BigDecimal.valueOf(k)), CALC_SCALE, RoundingMode.HALF_UP);
		return rawRate.multiply(weight)
				.add(priorRate.multiply(BigDecimal.ONE.subtract(weight)))
				.setScale(CALC_SCALE, RoundingMode.HALF_UP);
	}

	// =====================================================================
	// 2. 趨勢新鮮度指數衰減（設計文件 3.5）
	// =====================================================================

	/**
	 * 依資料距今天數對分數做指數衰減，收斂到中性基準分。
	 *
	 * <pre>
	 * freshness = 0.5 ^ (距今天數 / 半衰期)
	 * 調整後    = freshness × 原始分 + (1 − freshness) × 中性基準分
	 * </pre>
	 *
	 * 用指數而非線性衰減的理由：線性（1 − 天數/30）在第 30 天剛好歸零，是一個
	 * 人為的斷崖，讓第 29 天與第 31 天的差異被誇大，而且 30 這個數字沒有依據。
	 * 指數衰減永遠不會真的歸零，而是平滑收斂到中性值，參數意義也好解釋：
	 * 半衰期 14 天 = 兩週後這筆資料的影響力剩一半。
	 *
	 * 這與貝氏收縮是同一個精神：不確定的資訊往中性值靠，確定的資訊保留自己的數字。
	 *
	 * @param rawScore 原始分數（0~100）
	 * @param daysAgo 資料距今天數；負數（時鐘誤差造成的未來時間）一律視為 0
	 * @param halfLifeDays 半衰期天數，必須為正
	 * @param neutralBaseline 中性基準分
	 */
	public static BigDecimal applyFreshnessDecay(BigDecimal rawScore, long daysAgo, int halfLifeDays,
			BigDecimal neutralBaseline) {
		if (rawScore == null) {
			return null;
		}
		if (halfLifeDays <= 0) {
			throw new IllegalArgumentException("半衰期必須為正數，目前為：" + halfLifeDays);
		}
		// 資料時間比現在還新（時鐘誤差、時區問題）時視為今天，不讓 freshness 大於 1
		long effectiveDays = Math.max(0L, daysAgo);

		BigDecimal freshness = BigDecimal
				.valueOf(Math.pow(0.5d, (double) effectiveDays / halfLifeDays))
				.setScale(CALC_SCALE, RoundingMode.HALF_UP);

		return rawScore.multiply(freshness)
				.add(neutralBaseline.multiply(BigDecimal.ONE.subtract(freshness)))
				.setScale(CALC_SCALE, RoundingMode.HALF_UP);
	}

	// =====================================================================
	// 3. 固定目標區間正規化（設計文件 3.1）
	// =====================================================================

	/**
	 * 依固定的上下界把實際值線性映射到 0~100。
	 *
	 * <pre>
	 * score = clamp((實際值 − 下界) / (上界 − 下界) × 100, 0, 100)
	 * </pre>
	 *
	 * 解決的問題：毛利率 30% 在生鮮是好成績、在文具只是普通。直接把毛利率乘 100
	 * 當分數，系統會偏袒高毛利品類，排序被品類主導而非商品本身的優劣。
	 *
	 * 上下界是<b>凍結的設定值</b>，不從即時資料計算——初次設定可用歷史分位數推導，
	 * 之後固定下來。用即時資料算分位數會讓新增商品位移舊商品的分數，違反可重現性。
	 *
	 * @param value 實際值；null 回傳 null，交由呼叫端從加權分母排除（見 3.7 缺漏值策略）
	 * @param lowerBound 對應 0 分的值
	 * @param upperBound 對應 100 分的值，必須大於 lowerBound
	 */
	public static BigDecimal normalizeByBand(BigDecimal value, BigDecimal lowerBound, BigDecimal upperBound) {
		if (value == null) {
			// 缺漏值不給中性分 50——那會讓「資料填齊但條件普通」和「什麼都沒填」
			// 拿到一樣的分數。回傳 null 讓呼叫端從分母排除並重新正規化。
			return null;
		}
		if (lowerBound == null || upperBound == null) {
			throw new IllegalArgumentException("目標區間的上下界不可為 null");
		}
		BigDecimal range = upperBound.subtract(lowerBound);
		if (range.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException(
					"目標區間上界必須大於下界，目前為：" + lowerBound + " ~ " + upperBound);
		}

		BigDecimal score = value.subtract(lowerBound)
				.divide(range, CALC_SCALE, RoundingMode.HALF_UP)
				.multiply(HUNDRED);
		// 夾邊界後統一精度：不這樣做的話，正常值回傳 scale 6、被夾住的值回傳
		// BigDecimal.ZERO（scale 0），同一個方法回傳兩種精度，寫入 DB 或做
		// equals 比較時會出現難查的不一致。
		return clamp(score, BigDecimal.ZERO, HUNDRED).setScale(CALC_SCALE, RoundingMode.HALF_UP);
	}

	// =====================================================================
	// 4. 分位數（設計文件 3.4）
	// =====================================================================

	/**
	 * 線性內插分位數，與 NumPy 預設的 {@code method='linear'} 一致。
	 *
	 * <pre>
	 * 位置 = (p / 100) × (n − 1)
	 * 結果 = 下位值 + 小數部分 × (上位值 − 下位值)
	 * </pre>
	 *
	 * 用於 GATE_MOQ_FEASIBILITY 的集單量基準。不用「歷史最大值」的理由：
	 * 最大值往往是唯一一次爆紅的離群值，用它當基準等於假設每次都能複製那次奇蹟。
	 *
	 * 以設計文件的範例資料 [40,55,60,75,80,90,110,120,150,300] 驗證：
	 * P50=85、P75=117.5、P90=165。
	 *
	 * @param values 樣本值，不可為空。方法內部會排序，不會修改傳入的 List
	 * @param percentile 0~100
	 */
	public static BigDecimal percentile(List<? extends Number> values, double percentile) {
		if (values == null || values.isEmpty()) {
			throw new IllegalArgumentException("計算分位數的樣本不可為空");
		}
		if (percentile < 0d || percentile > 100d) {
			throw new IllegalArgumentException("分位數必須介於 0 至 100，目前為：" + percentile);
		}

		List<Double> sorted = values.stream()
				.map(Number::doubleValue)
				.sorted()
				.toList();
		if (sorted.size() == 1) {
			return BigDecimal.valueOf(sorted.get(0)).setScale(CALC_SCALE, RoundingMode.HALF_UP);
		}

		double position = (percentile / 100d) * (sorted.size() - 1);
		int lowerIndex = (int) Math.floor(position);
		int upperIndex = (int) Math.ceil(position);
		double fraction = position - lowerIndex;

		double result = sorted.get(lowerIndex)
				+ fraction * (sorted.get(upperIndex) - sorted.get(lowerIndex));
		return BigDecimal.valueOf(result).setScale(CALC_SCALE, RoundingMode.HALF_UP);
	}

	// =====================================================================
	// 5. 加權總和（設計文件 3.6 + 3.7 缺漏值策略）
	// =====================================================================

	/**
	 * 加權平均，並自動排除缺漏因子後重新正規化分母。
	 *
	 * <pre>
	 * 總分 = Σ(分項分數 × 權重) / Σ(有值分項的權重)
	 * </pre>
	 *
	 * 缺漏因子（分數為 null）不計入分子也不計入分母，等同於把它的權重按比例
	 * 分配給其餘因子——而不是給它一個中性值 50 混進總分。
	 *
	 * 維持加權總和而不改用 TOPSIS 的理由：可分解（主管能指出不同意哪一項並算出
	 * 影響）、可重現（只依賴這件商品自己的資料）、可快照（每個分項的貢獻都能
	 * 存進審核紀錄）。加權總和「完全可補償」的弱點由 Gate 層負責攔截。
	 *
	 * @param weightedScores 分項分數與權重的配對；分數為 null 代表該因子無資料
	 * @return 加權平均分數；所有因子皆無資料時回傳 null
	 */
	public static BigDecimal weightedAverage(List<WeightedScore> weightedScores) {
		if (weightedScores == null || weightedScores.isEmpty()) {
			return null;
		}

		BigDecimal numerator = BigDecimal.ZERO;
		BigDecimal denominator = BigDecimal.ZERO;
		for (WeightedScore ws : weightedScores) {
			if (ws == null || ws.score() == null || ws.weight() == null) {
				continue;
			}
			if (ws.weight().compareTo(BigDecimal.ZERO) <= 0) {
				// 權重 0 的因子等同於未啟用，跳過（不影響分母）
				continue;
			}
			numerator = numerator.add(ws.score().multiply(ws.weight()));
			denominator = denominator.add(ws.weight());
		}

		if (denominator.compareTo(BigDecimal.ZERO) == 0) {
			// 所有因子都沒有資料。這種商品應該已經被 GATE_DATA_COMPLETENESS 攔下，
			// 這裡回傳 null 而非 0，讓呼叫端明確區分「零分」與「無法計算」。
			return null;
		}
		return numerator.divide(denominator, CALC_SCALE, RoundingMode.HALF_UP);
	}

	/** 加權總和的單一分項。score 為 null 代表該因子無資料，會被排除在分母外。 */
	public record WeightedScore(String factorCode, BigDecimal score, BigDecimal weight) {
	}

	// =====================================================================
	// 6. 共用工具
	// =====================================================================

	/** 把值夾在 [min, max] 區間內。 */
	public static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
		if (value == null) {
			return null;
		}
		return value.max(min).min(max);
	}
}
