package com.example.Product_Selection_260813.service.resolver;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.entity.SystemSetting;
import com.example.Product_Selection_260813.repository.SystemSettingRepository;

/**
 * 演算法參數的統一讀取入口。
 *
 * 所有魔術數字（平滑常數、半衰期、分位數、運費）都放 system_settings，不寫死在
 * 程式碼——這些數字之後一定會需要調整，改設定比改程式碼重新部署容易得多。
 *
 * 每個 getter 都帶預設值：設定值缺漏或格式錯誤時記 WARN 並退回預設，不讓整條
 * 計分鏈路因為一筆設定打錯字就整個垮掉。這與既有 GeminiAnalysisServiceImpl
 * 處理 gemini_monthly_limit 的方式一致。
 */
@Component
public class AlgorithmSettings {

	private static final Logger log = LoggerFactory.getLogger(AlgorithmSettings.class);

	// 設定 key
	public static final String KEY_SUPPORTED_TEMPERATURE_ZONES = "supported_temperature_zones";
	public static final String KEY_MOQ_BENCHMARK_PERCENTILE = "moq_benchmark_percentile";
	public static final String KEY_MOQ_SAFETY_FACTOR = "moq_safety_factor";
	public static final String KEY_MOQ_MIN_SAMPLE_SIZE = "moq_min_sample_size";
	public static final String KEY_DEFAULT_MOQ = "default_moq";
	public static final String KEY_SHELF_LIFE_THRESHOLD_DAYS = "shelf_life_threshold_days";
	public static final String KEY_SHRINKAGE_K_CATEGORY = "shrinkage_k_category";
	public static final String KEY_SHRINKAGE_K_PRODUCT = "shrinkage_k_product";
	public static final String KEY_TREND_HALF_LIFE_DAYS = "trend_half_life_days";
	public static final String KEY_NEUTRAL_BASELINE_SCORE = "neutral_baseline_score";

	// 預設值（與 V2 migration 的初值一致）
	private static final int DEFAULT_MOQ_BENCHMARK_PERCENTILE = 75;
	private static final BigDecimal DEFAULT_MOQ_SAFETY_FACTOR = BigDecimal.ONE;
	private static final int DEFAULT_MOQ_MIN_SAMPLE_SIZE = 5;
	private static final int DEFAULT_SHELF_LIFE_THRESHOLD_DAYS = 21;
	private static final int DEFAULT_SHRINKAGE_K_CATEGORY = 10;
	private static final int DEFAULT_SHRINKAGE_K_PRODUCT = 5;
	private static final int DEFAULT_TREND_HALF_LIFE_DAYS = 14;
	private static final BigDecimal DEFAULT_NEUTRAL_BASELINE_SCORE = BigDecimal.valueOf(50);

	private final SystemSettingRepository systemSettingRepository;

	@Autowired
	public AlgorithmSettings(SystemSettingRepository systemSettingRepository) {
		this.systemSettingRepository = systemSettingRepository;
	}

	public int getMoqBenchmarkPercentile() {
		return getInt(KEY_MOQ_BENCHMARK_PERCENTILE, DEFAULT_MOQ_BENCHMARK_PERCENTILE);
	}

	public BigDecimal getMoqSafetyFactor() {
		return getDecimal(KEY_MOQ_SAFETY_FACTOR, DEFAULT_MOQ_SAFETY_FACTOR);
	}

	public int getMoqMinSampleSize() {
		return getInt(KEY_MOQ_MIN_SAMPLE_SIZE, DEFAULT_MOQ_MIN_SAMPLE_SIZE);
	}

	/** MOQ 三層解析的全域保底；未設定時回傳 null（代表確實沒有保底值）。 */
	public Integer getDefaultMoq() {
		return getNullableInt(KEY_DEFAULT_MOQ);
	}

	public int getGlobalShelfLifeThresholdDays() {
		return getInt(KEY_SHELF_LIFE_THRESHOLD_DAYS, DEFAULT_SHELF_LIFE_THRESHOLD_DAYS);
	}

	public int getShrinkageKCategory() {
		return getInt(KEY_SHRINKAGE_K_CATEGORY, DEFAULT_SHRINKAGE_K_CATEGORY);
	}

	public int getShrinkageKProduct() {
		return getInt(KEY_SHRINKAGE_K_PRODUCT, DEFAULT_SHRINKAGE_K_PRODUCT);
	}

	public int getTrendHalfLifeDays() {
		return getInt(KEY_TREND_HALF_LIFE_DAYS, DEFAULT_TREND_HALF_LIFE_DAYS);
	}

	public BigDecimal getNeutralBaselineScore() {
		return getDecimal(KEY_NEUTRAL_BASELINE_SCORE, DEFAULT_NEUTRAL_BASELINE_SCORE);
	}

	/**
	 * 通路支援的溫層清單。
	 *
	 * 目前三種全支援，代表 GATE_TEMPERATURE_ZONE 現階段不會擋掉任何商品。
	 * 保留這個 Gate 是為了冷鏈條件變動時（合約到期、冷凍配送暫停）能立即生效，
	 * 保留成本只是一個設定 key。
	 */
	public List<String> getSupportedTemperatureZones() {
		String raw = getRaw(KEY_SUPPORTED_TEMPERATURE_ZONES);
		if (raw == null || raw.isBlank()) {
			return List.of();
		}
		return Arrays.stream(raw.split(","))
				.map(String::trim)
				.map(String::toUpperCase)
				.filter(s -> !s.isEmpty())
				.toList();
	}

	/** 依材積級距取每件運費估算；未設定時視為 0（不影響毛利計算）。 */
	public BigDecimal getFreightCost(String freightSettingKey) {
		if (freightSettingKey == null) {
			return BigDecimal.ZERO;
		}
		return getDecimal(freightSettingKey, BigDecimal.ZERO);
	}

	// ---------------- 內部工具 ----------------

	private String getRaw(String key) {
		return systemSettingRepository.findById(key)
				.map(SystemSetting::getSettingValue)
				.orElse(null);
	}

	private int getInt(String key, int fallback) {
		Integer v = getNullableInt(key);
		return v != null ? v : fallback;
	}

	private Integer getNullableInt(String key) {
		String raw = getRaw(key);
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException e) {
			log.warn("system_settings 裡 {} 的值「{}」無法解析為整數，改用預設值", key, raw);
			return null;
		}
	}

	private BigDecimal getDecimal(String key, BigDecimal fallback) {
		String raw = getRaw(key);
		if (raw == null || raw.isBlank()) {
			return fallback;
		}
		try {
			return new BigDecimal(raw.trim());
		} catch (NumberFormatException e) {
			log.warn("system_settings 裡 {} 的值「{}」無法解析為數值，改用預設值 {}", key, raw, fallback);
			return fallback;
		}
	}
}
