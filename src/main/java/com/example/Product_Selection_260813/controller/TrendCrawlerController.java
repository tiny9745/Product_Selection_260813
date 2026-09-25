package com.example.Product_Selection_260813.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
import com.example.Product_Selection_260813.dto.request.TrendCrawlerEnabledRequest;
import com.example.Product_Selection_260813.dto.response.TrendCrawlerStatusResponse;
import com.example.Product_Selection_260813.service.TrendSyncRunService;

import jakarta.validation.Valid;

/**
 * 系統設定「爬蟲排程控制」：PTT 熱度來源開關、立即同步全部商品、執行紀錄。
 * 比照 WeatherController 放在 /api/settings/ 底下，三支都限定 MANAGER——
 * 這是操作自動化排程與外部資料來源的維運行為，不是一般客群可見的功能。
 */
@RestController
@RequestMapping("/api/settings/trend-crawler")
public class TrendCrawlerController {

	@Autowired
	private TrendSyncRunService trendSyncRunService;

	@PreAuthorize("hasRole('MANAGER')")
	@GetMapping
	public ResponseEntity<ApiResponse<TrendCrawlerStatusResponse>> getStatus() {
		return ResponseEntity.ok(ApiResponse.success("查詢成功", trendSyncRunService.getStatus()));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/enabled")
	public ResponseEntity<ApiResponse<TrendCrawlerStatusResponse>> setEnabled(
			@Valid @RequestBody TrendCrawlerEnabledRequest request, @AuthenticationPrincipal String username) {
		TrendCrawlerStatusResponse result = trendSyncRunService.setEnabled(request.getEnabled(), username);
		return ResponseEntity.ok(ApiResponse.success(request.getEnabled() ? "PTT 熱度來源已啟用" : "PTT 熱度來源已停用", result));
	}

	/**
	 * 立即同步全部未封存商品。同步在背景執行，這裡立即回 202；畫面輪詢 GET 看進度。
	 * PTT 來源停用中或已有同步在執行時回 409。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@PostMapping("/sync-all")
	public ResponseEntity<ApiResponse<TrendCrawlerStatusResponse>> syncAll(@AuthenticationPrincipal String username) {
		TrendCrawlerStatusResponse result = trendSyncRunService.startManualRun(username);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success("已開始同步全部商品", result));
	}
}
