package com.example.Product_Selection_260813.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.Product_Selection_260813.common.ApiResponse;
import com.example.Product_Selection_260813.dto.request.TrendCrawlerEnabledRequest;
import com.example.Product_Selection_260813.dto.response.GoogleTrendRunResponse;
import com.example.Product_Selection_260813.dto.response.GoogleTrendSignalResponse;
import com.example.Product_Selection_260813.dto.response.GoogleTrendStatusResponse;
import com.example.Product_Selection_260813.service.GoogleTrendService;

import jakarta.validation.Valid;

/**
 * Google 趨勢參考（SerpApi）：
 * <ul>
 * <li>GET /api/products/{id}/google-trend [操作+管理]：最新一筆，唯讀、不花額度</li>
 * <li>POST /api/products/{id}/google-trend/sync [管理]：立即查詢一個商品，花 1 次額度</li>
 * <li>GET／PUT enabled／POST sync-top /api/settings/google-trends [管理]：控制面板</li>
 * </ul>
 * 會花額度的操作都限定管理層：額度是全站共用的月上限，不能讓每個操作人員都能消耗。
 */
@RestController
public class GoogleTrendController {

	@Autowired
	private GoogleTrendService googleTrendService;

	@GetMapping("/api/products/{id}/google-trend")
	public ResponseEntity<ApiResponse<GoogleTrendSignalResponse>> getLatest(@PathVariable("id") Long id) {
		return ResponseEntity.ok(ApiResponse.success("查詢成功", googleTrendService.getLatest(id)));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PostMapping("/api/products/{id}/google-trend/sync")
	public ResponseEntity<ApiResponse<GoogleTrendSignalResponse>> sync(@PathVariable("id") Long id) {
		return ResponseEntity.ok(ApiResponse.success("Google 趨勢已更新", googleTrendService.syncProduct(id)));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@GetMapping("/api/settings/google-trends")
	public ResponseEntity<ApiResponse<GoogleTrendStatusResponse>> getStatus() {
		return ResponseEntity.ok(ApiResponse.success("查詢成功", googleTrendService.getStatus()));
	}

	/** 執行紀錄分頁（2026-09-29）：GET /api/settings/google-trends/runs?page=0&size=10，新到舊。 */
	@PreAuthorize("hasRole('MANAGER')")
	@GetMapping("/api/settings/google-trends/runs")
	public ResponseEntity<ApiResponse<Page<GoogleTrendRunResponse>>> getRuns(
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "10") int size) {
		return ResponseEntity.ok(ApiResponse.success("查詢成功", googleTrendService.getRuns(page, size)));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/api/settings/google-trends/enabled")
	public ResponseEntity<ApiResponse<GoogleTrendStatusResponse>> setEnabled(
			@Valid @RequestBody TrendCrawlerEnabledRequest request, @AuthenticationPrincipal String username) {
		GoogleTrendStatusResponse result = googleTrendService.setEnabled(request.getEnabled(), username);
		return ResponseEntity.ok(ApiResponse.success(request.getEnabled() ? "Google 趨勢來源已啟用" : "Google 趨勢來源已停用", result));
	}

	/** 立即查詢 PTT 熱度前 N 名；背景執行、立即回 202，畫面輪詢 GET 看進度。停用、無金鑰、額度用完或執行中回 409。 */
	@PreAuthorize("hasRole('MANAGER')")
	@PostMapping("/api/settings/google-trends/sync-top")
	public ResponseEntity<ApiResponse<GoogleTrendStatusResponse>> syncTop(@AuthenticationPrincipal String username) {
		GoogleTrendStatusResponse result = googleTrendService.startManualRun(username);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success("已開始查詢 Google 趨勢", result));
	}
}
