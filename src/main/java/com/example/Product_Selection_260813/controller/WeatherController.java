package com.example.Product_Selection_260813.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Product_Selection_260813.common.ApiResponse;
import com.example.Product_Selection_260813.dto.request.WeatherBoostSettingsUpdateRequest;
import com.example.Product_Selection_260813.dto.response.WeatherBoostSettingsResponse;
import com.example.Product_Selection_260813.dto.response.WeatherDataStatusResponse;
import com.example.Product_Selection_260813.dto.response.WeatherSyncResponse;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.service.weather.WeatherBoostService;
import com.example.Product_Selection_260813.service.weather.WeatherDataSyncService;

import jakarta.validation.Valid;

/**
 * 天氣資料與天氣加成設定（V26 改版：天氣不再產生檔期）。
 *
 * <ul>
 * <li>GET  /status：各區資料涵蓋天數與資料更新時間（[操作+管理]，唯讀）。</li>
 * <li>POST /sync：手動觸發一次每日天氣資料同步，與 05:00 排程呼叫同一個方法（[僅管理]）。</li>
 * <li>GET  /boost-settings：歷史／預測比重與加成上限（[操作+管理]，唯讀）。</li>
 * <li>PUT  /boost-settings：更新上述設定（[僅管理]），完成後重算尚未核准商品的加成。</li>
 * </ul>
 * 原本的 GET /signals/preview（預覽天氣檔期訊號）隨天氣檔期一併移除。
 */
@RestController
@RequestMapping("/api/settings/weather")
public class WeatherController {

	@Autowired
	private WeatherDataSyncService weatherDataSyncService;

	@Autowired
	private WeatherBoostService weatherBoostService;

	@Autowired
	private AppUserRepository appUserRepository;

	@GetMapping("/status")
	public ResponseEntity<ApiResponse<WeatherDataStatusResponse>> getStatus() {
		return ResponseEntity.ok(ApiResponse.success("查詢成功", weatherDataSyncService.getStatus()));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PostMapping("/sync")
	public ResponseEntity<ApiResponse<WeatherSyncResponse>> triggerSync() {
		WeatherSyncResponse result = weatherDataSyncService.syncWeatherData();
		return ResponseEntity.ok(ApiResponse.success("天氣資料同步完成", result));
	}

	@GetMapping("/boost-settings")
	public ResponseEntity<ApiResponse<WeatherBoostSettingsResponse>> getBoostSettings() {
		return ResponseEntity.ok(ApiResponse.success("查詢成功", weatherBoostService.getSettings()));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/boost-settings")
	public ResponseEntity<ApiResponse<WeatherBoostSettingsResponse>> updateBoostSettings(
			@Valid @RequestBody WeatherBoostSettingsUpdateRequest request, @AuthenticationPrincipal String username) {
		Long operatorId = appUserRepository.findByUsername(username).map(user -> user.getId()).orElse(null);
		WeatherBoostSettingsResponse result = weatherBoostService.updateSettings(request, operatorId);
		return ResponseEntity.ok(ApiResponse.success("天氣加成設定已更新，尚未核准商品的加成將重新計算", result));
	}
}
