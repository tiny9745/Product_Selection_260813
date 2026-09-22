package com.example.Product_Selection_260813.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Product_Selection_260813.common.ApiResponse;
import com.example.Product_Selection_260813.dto.response.WeatherSyncResponse;
import com.example.Product_Selection_260813.dto.weather.WeatherSignal;
import com.example.Product_Selection_260813.service.weather.WeatherCampaignSyncService;
import com.example.Product_Selection_260813.service.weather.WeatherSignalProvider;

/**
 * 天氣訊號功能的維運端點。不在企劃書「API總表」原本的範圍內——天氣同步
 * 原本設計成純背景排程（見WeatherCampaignSyncService類別註解），不需要
 * 任何人手動觸發也能運作。這裡補上這兩支端點單純是維運／測試需求：
 *
 * <ol>
 * <li>{@code POST /sync}：WeatherCampaignSyncService.syncWeatherCampaigns()
 *     原本只被{@code @Scheduled(cron="0 0 5 * * *")}呼叫，開發或校準門檻時
 *     只能乾等到每天05:00，或改cron表達式再重啟應用程式。這支端點呼叫
 *     「同一支」service方法（不是另外寫一套簡化版同步邏輯），讓Postman
 *     可以隨時手動觸發一次完整同步，回傳值也跟排程實際會做的事完全一致。
 * <li>{@code GET /signals/preview}：只呼叫WeatherSignalProvider.getActiveSignals()，
 *     不寫入資料庫。{@link com.example.Product_Selection_260813.service.weather.WeatherNormalizer}
 *     的分類門檻是初版預設值（見該類別Javadoc），校準門檻常數時如果每次
 *     都要真的upsert進festive_campaigns才能看結果，會很重、也會污染既有
 *     檔期資料；這支端點讓你能先看「這次跟Open-Meteo要到的資料，分類出來
 *     長什麼樣子」，確認沒問題後再呼叫上面的{@code POST /sync}落地。
 * </ol>
 *
 * <b>權限：</b>兩支都限定{@code MANAGER}角色——這是操作自動化排程與外部
 * API串接的維運行為，不是一般客群可見的查詢功能。對比
 * {@code GET /api/settings/festive-campaigns}本身不加{@code @PreAuthorize}，
 * 是因為那支查的是已經生效、給操作端也要看的檔期資料，性質不同，不是
 * 這裡漏掉權限設定。
 */
@RestController
@RequestMapping("/api/settings/weather")
public class WeatherController {

	@Autowired
	private WeatherCampaignSyncService weatherCampaignSyncService;

	@Autowired
	private WeatherSignalProvider weatherSignalProvider;

	/**
	 * 手動觸發一次完整的天氣檔期同步。呼叫的是WeatherCampaignSyncService
	 * 唯一的同步方法，跟每天05:00排程觸發時執行的邏輯、Transactional邊界
	 * 完全相同，只是觸發時機從cron換成HTTP請求——避免「手動觸發」與
	 * 「排程觸發」兩條路徑行為對不起來。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@PostMapping("/sync")
	public ResponseEntity<ApiResponse<WeatherSyncResponse>> triggerSync() {
		WeatherSyncResponse result = weatherCampaignSyncService.syncWeatherCampaigns();
		return ResponseEntity.ok(ApiResponse.success("天氣檔期同步完成", result));
	}

	/**
	 * 預覽目前會分類出的天氣訊號，不寫入資料庫。想看落地後實際建立／更新的
	 * 檔期，請先呼叫上面的{@code POST /sync}，再查
	 * {@code GET /api/settings/festive-campaigns}（category=WEATHER的部分）。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@GetMapping("/signals/preview")
	public ResponseEntity<ApiResponse<List<WeatherSignal>>> previewSignals() {
		List<WeatherSignal> signals = weatherSignalProvider.getActiveSignals();
		return ResponseEntity.ok(ApiResponse.success("查詢成功（未寫入資料庫）", signals));
	}
}
