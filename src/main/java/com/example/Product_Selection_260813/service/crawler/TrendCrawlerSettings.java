package com.example.Product_Selection_260813.service.crawler;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.entity.SystemSetting;
import com.example.Product_Selection_260813.repository.SystemSettingRepository;

/**
 * PTT 來源的啟用／停用開關，存在 system_settings（key = ptt_crawler_enabled）。
 *
 * 刻意不登記在 SystemSettingRegistry：那份登記表是「演算法參數」設定畫面的資料來源，
 * 這個開關有自己的控制區塊（系統設定 → 爬蟲排程控制），混在演算法參數裡反而難找。
 * 資料庫沒有這筆紀錄時視為啟用（預設行為與加入開關前相同）。
 *
 * <b>停用時的行為：</b>不會改用模擬資料替代——模擬資料是隨機漫步，每晚跑一次會讓
 * 「連續 3 天上升」隨機成立，AI 主動選品會被雜訊觸發。所以停用＝完全不同步：
 * 每日排程記錄為「已略過」、手動同步（單一商品或全部）回 409。
 * 模擬資料只在「PTT 啟用中、但這次抓不到」時當備援。
 */
@Component
public class TrendCrawlerSettings {

	public static final String ENABLED_KEY = "ptt_crawler_enabled";

	@Autowired
	private SystemSettingRepository systemSettingRepository;

	@Transactional(readOnly = true)
	public boolean isPttEnabled() {
		return systemSettingRepository.findById(ENABLED_KEY)
				.map(setting -> !"false".equalsIgnoreCase(setting.getSettingValue()))
				.orElse(true);
	}

	@Transactional
	public void setPttEnabled(boolean enabled, Long updatedBy) {
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
}
