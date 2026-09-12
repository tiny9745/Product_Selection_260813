package com.example.Product_Selection_260813.constants;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * system_settings 每個 key 的中繼資料（型別、合法範圍、分類、說明文字）。
 *
 * 這張表本身是純粹的 key-value（setting_value 存的是 VARCHAR），沒有型別
 * 欄位——如果前端把每個值都當成不透明字串處理，使用者可以在「平滑常數 k」
 * 欄位填入一段文字、或是一個負數，後端要嘛整個算式壞掉、要嘛安靜地產生
 * 無意義的分數，沒有任何地方會擋下來。
 *
 * 這裡用一個靜態登記表補上型別與範圍資訊，讓：
 * 1. API 回應能附帶「這個值該怎麼驗證」的資訊，前端據此渲染正確的輸入
 *    元件（數字輸入框 vs 下拉選單）並在送出前先做基本檢查
 * 2. 後端 Service 層更新時，用同一份登記表的範圍去驗證送進來的值，
 *    不管是不是真的透過前端過來的請求都會被擋下
 *
 * 不做成資料庫欄位（例如在 system_settings 加 data_type/min_value/max_value
 * 三個新欄位）的理由：這些是程式邏輯的一部分（例如「平滑常數只能是正整數」
 * 是演算法本身的數學限制，不是營運資料），寫在程式碼裡才會跟著版本控制走，
 * 也不需要為了改一個範圍限制就要跑一次資料庫 migration。
 */
public final class SystemSettingRegistry {

	private SystemSettingRegistry() {
	}

	public enum DataType {
		INTEGER, DECIMAL, STRING
	}

	public record Metadata(
			String key,
			String category,
			String displayName,
			String description,
			DataType dataType,
			BigDecimal minValue,
			BigDecimal maxValue,
			String unit,
			String defaultValue) {
	}

	private static final Map<String, Metadata> REGISTRY = new LinkedHashMap<>();

	private static void register(String key, String category, String displayName, String description,
			DataType type, BigDecimal min, BigDecimal max, String unit, String defaultValue) {
		REGISTRY.put(key, new Metadata(key, category, displayName, description, type, min, max, unit, defaultValue));
	}

	static {
		// ---------------- 貝氏收縮 ----------------
		register("shrinkage_k_category", "貝氏收縮", "品類層平滑常數 k",
				"要累積多少筆樣本，品類才會被信任一半以上；數字越大，愈需要更多樣本才會偏離全域平均。",
				DataType.INTEGER, BigDecimal.ONE, BigDecimal.valueOf(100), "次", "10");
		register("shrinkage_k_product", "貝氏收縮", "商品層平滑常數 k",
				"同上，但作用在單一商品自己的歷史上。建議小於品類層 k，否則商品層永遠不會真正發揮作用。",
				DataType.INTEGER, BigDecimal.ONE, BigDecimal.valueOf(100), "次", "5");
		register("neutral_baseline_score", "貝氏收縮", "中性基準分數",
				"完全沒有任何歷史資料時的保底分數，同時也是趨勢資料完全過期時衰減收斂的目標值。",
				DataType.DECIMAL, BigDecimal.ZERO, BigDecimal.valueOf(100), "分", "50");

		// ---------------- 趨勢分析 ----------------
		register("trend_half_life_days", "趨勢分析", "趨勢新鮮度半衰期",
				"趨勢訊號的影響力衰減一半所需的天數，數字越小系統對最新資料的反應越敏感、越快忘記舊資料。",
				DataType.INTEGER, BigDecimal.ONE, BigDecimal.valueOf(365), "天", "14");

		// ---------------- MOQ 可行性判定 ----------------
		register("moq_benchmark_percentile", "MOQ判定", "MOQ可行性基準分位數",
				"用該品類歷史集單量的第幾分位數當作可行性門檻，避免單一離群值（例如一次爆紅）主導判斷。",
				DataType.INTEGER, BigDecimal.ONE, BigDecimal.valueOf(99), "百分位", "75");
		register("moq_safety_factor", "MOQ判定", "MOQ安全係數",
				"基準分位數乘上這個係數才是實際門檻，大於1代表放寬容忍度。",
				DataType.DECIMAL, new BigDecimal("0.1"), BigDecimal.TEN, "倍", "1.0");
		register("moq_min_sample_size", "MOQ判定", "MOQ判定最低樣本數",
				"低於這個樣本數，MOQ可行性一律回報資料不足，不勉強計算。",
				DataType.INTEGER, BigDecimal.ONE, BigDecimal.valueOf(1000), "筆", "5");
		register("default_moq", "MOQ判定", "全域預設MOQ",
				"商品與品類都沒有設定MOQ時的最後保底值。",
				DataType.INTEGER, BigDecimal.ONE, BigDecimal.valueOf(100000), "件", "1");

		// ---------------- 效期與溫層 ----------------
		register("shelf_life_threshold_days", "效期判定", "效期門檻天數（全域）",
				"品類沒有專屬門檻時使用的全域效期天數門檻。",
				DataType.INTEGER, BigDecimal.ONE, BigDecimal.valueOf(3650), "天", "21");
		register("supported_temperature_zones", "溫層判定", "通路支援溫層",
				"逗號分隔，例如 NORMAL,CHILLED,FROZEN。商品溫層不在此清單內會被 Gate 判定為不通過。",
				DataType.STRING, null, null, null, "NORMAL,CHILLED,FROZEN");

		// ---------------- 運費估算 ----------------
		register("freight_cost_xs", "運費估算", "材積 XS 運費估算", "毛利率因子計算用的運費估算基準（極小材積）。",
				DataType.DECIMAL, BigDecimal.ZERO, BigDecimal.valueOf(100000), "元", "0");
		register("freight_cost_s", "運費估算", "材積 S 運費估算", "同上（小材積）。",
				DataType.DECIMAL, BigDecimal.ZERO, BigDecimal.valueOf(100000), "元", "0");
		register("freight_cost_m", "運費估算", "材積 M 運費估算", "同上（中材積）。",
				DataType.DECIMAL, BigDecimal.ZERO, BigDecimal.valueOf(100000), "元", "0");
		register("freight_cost_l", "運費估算", "材積 L 運費估算", "同上（大材積）。",
				DataType.DECIMAL, BigDecimal.ZERO, BigDecimal.valueOf(100000), "元", "0");

		// ---------------- 目標區間（HISTORICAL 模式）----------------
		register("score_band_min_sample_size", "目標區間", "歷史模式最低樣本數",
				"切換目標區間為 HISTORICAL 模式時，低於這個樣本數會直接拒絕計算，改請使用 MANUAL 模式。",
				DataType.INTEGER, BigDecimal.ONE, BigDecimal.valueOf(1000), "筆", "5");
		register("score_band_percentile_lower", "目標區間", "目標區間下界分位數",
				"HISTORICAL 模式計算下界時採用的分位數。",
				DataType.INTEGER, BigDecimal.ZERO, BigDecimal.valueOf(49), "百分位", "10");
		register("score_band_percentile_upper", "目標區間", "目標區間上界分位數",
				"HISTORICAL 模式計算上界時採用的分位數，必須大於下界分位數（畫面與後端皆須驗證）。",
				DataType.INTEGER, BigDecimal.valueOf(51), BigDecimal.valueOf(100), "百分位", "90");
	}

	public static Map<String, Metadata> all() {
		return REGISTRY;
	}

	public static Metadata get(String key) {
		return REGISTRY.get(key);
	}

	public static boolean isKnown(String key) {
		return REGISTRY.containsKey(key);
	}
}
