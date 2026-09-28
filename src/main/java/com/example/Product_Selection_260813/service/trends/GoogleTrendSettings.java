package com.example.Product_Selection_260813.service.trends;

import java.time.LocalDateTime;
import java.time.YearMonth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.entity.SystemSetting;
import com.example.Product_Selection_260813.repository.SystemSettingRepository;

/**
 * Google 趨勢來源的開關與每月額度，存在 system_settings（比照 TrendCrawlerSettings 與 Gemini 月額度）。
 *
 * <ul>
 * <li>google_trends_enabled：資料庫沒有這筆＝<b>停用</b>（跟 PTT 相反：這是要花額度的外部付費服務，
 * 預設不能自己開始呼叫）</li>
 * <li>serpapi_monthly_limit：沒設定＝200。SerpApi 免費方案每月 250 次，留 50 次給手動查詢與測試</li>
 * <li>serpapi_calls_YYYY-MM：本月已用次數，換月自動是新的一筆，不需要排程重置</li>
 * </ul>
 *
 * 刻意不登記在 SystemSettingRegistry（演算法參數畫面），理由同 TrendCrawlerSettings：
 * 這組設定有自己的控制面板。
 */
@Component
public class GoogleTrendSettings {

	private static final Logger log = LoggerFactory.getLogger(GoogleTrendSettings.class);

	public static final String ENABLED_KEY = "google_trends_enabled";
	public static final String LIMIT_KEY = "serpapi_monthly_limit";
	public static final int DEFAULT_MONTHLY_LIMIT = 200;

	@Autowired
	private SystemSettingRepository systemSettingRepository;

	@Transactional(readOnly = true)
	public boolean isEnabled() {
		return systemSettingRepository.findById(ENABLED_KEY)
				.map(setting -> "true".equalsIgnoreCase(setting.getSettingValue()))
				.orElse(false);
	}

	@Transactional
	public void setEnabled(boolean enabled, Long updatedBy) {
		SystemSetting setting = systemSettingRepository.findById(ENABLED_KEY).orElseGet(() -> {
			SystemSetting created = new SystemSetting();
			created.setSettingKey(ENABLED_KEY);
			return created;
		});
		setting.setSettingValue(Boolean.toString(enabled));
		setting.setUpdatedBy(updatedBy);
		setting.setUpdatedAt(LocalDateTime.now());
		systemSettingRepository.save(setting);
	}

	@Transactional(readOnly = true)
	public int getMonthlyLimit() {
		return systemSettingRepository.findById(LIMIT_KEY)
				.map(SystemSetting::getSettingValue)
				.map(value -> {
					try {
						return Integer.parseInt(value.trim());
					} catch (NumberFormatException e) {
						log.warn("system_settings 裡 {} 的值無法解析為整數，改用預設值 {}", LIMIT_KEY, DEFAULT_MONTHLY_LIMIT);
						return DEFAULT_MONTHLY_LIMIT;
					}
				})
				.orElse(DEFAULT_MONTHLY_LIMIT);
	}

	@Transactional(readOnly = true)
	public int getUsedThisMonth() {
		return systemSettingRepository.findById(currentMonthKey())
				.map(SystemSetting::getSettingValue)
				.map(value -> {
					try {
						return Integer.parseInt(value.trim());
					} catch (NumberFormatException e) {
						return 0;
					}
				})
				.orElse(0);
	}

	public int getRemainingThisMonth() {
		return Math.max(0, getMonthlyLimit() - getUsedThisMonth());
	}

	/** 每次 SerpApi 回 Success（含「查無資料」，SerpApi 也計費）後呼叫。原子 UPSERT，見 repository 說明。 */
	@Transactional
	public void recordCall() {
		systemSettingRepository.incrementCounter(currentMonthKey());
	}

	static String currentMonthKey() {
		return "serpapi_calls_" + YearMonth.now();
	}
}
