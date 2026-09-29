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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.Product_Selection_260813.common.ApiResponse;
import com.example.Product_Selection_260813.dto.request.DiscoveredItemDismissRequest;
import com.example.Product_Selection_260813.dto.response.DiscoveredItemResponse;
import com.example.Product_Selection_260813.dto.response.DiscoveryRunResponse;
import com.example.Product_Selection_260813.dto.response.DiscoveryStatusResponse;
import com.example.Product_Selection_260813.enums.DiscoveredItemStatus;
import com.example.Product_Selection_260813.service.discovery.DiscoveredItemService;
import com.example.Product_Selection_260813.service.discovery.DiscoveryRunService;

import jakarta.validation.Valid;

/**
 * PTT 新品探索（2026-09-29，第一階段）。
 *
 * <ul>
 * <li>/api/discoveries：探索結果清單與人工處理。清單兩個角色都可讀；略過／復原限操作層——
 * 建立商品是操作層的工作（品項管理只給操作層），決定「不要這個線索」也是。</li>
 * <li>/api/settings/discovery：排程狀態與手動執行，限管理層（比照 /api/settings/trend-crawler，
 * 這是維運外部資料來源與 AI 額度的行為）。</li>
 * </ul>
 * 「建立商品」沒有獨立端點：走既有 POST /api/products，body 帶 discoveredItemId，
 * 見 DiscoveredItemService 類別註解。
 */
@RestController
public class DiscoveryController {

	@Autowired
	private DiscoveredItemService discoveredItemService;

	@Autowired
	private DiscoveryRunService discoveryRunService;

	/**
	 * GET /api/discoveries?status=NEW&recentDays=14&sort=FIT&page=0&size=20
	 * status 預設 NEW；recentDays 語意見 DiscoveredItemService.search()；sort＝FIT（預設）／BUZZ／RECENT。
	 */
	@GetMapping("/api/discoveries")
	public ResponseEntity<ApiResponse<Page<DiscoveredItemResponse>>> search(
			@RequestParam(value = "status", required = false) DiscoveredItemStatus status,
			@RequestParam(value = "recentDays", required = false) Integer recentDays,
			@RequestParam(value = "sort", required = false) DiscoveredItemService.Sort sort,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "20") int size) {
		return ResponseEntity.ok(ApiResponse.success("查詢成功", discoveredItemService.search(status, recentDays, sort, page, size)));
	}

	@PreAuthorize("hasRole('PURCHASER')")
	@PostMapping("/api/discoveries/{id}/dismiss")
	public ResponseEntity<ApiResponse<DiscoveredItemResponse>> dismiss(@PathVariable("id") Long id,
			@Valid @RequestBody(required = false) DiscoveredItemDismissRequest request,
			@AuthenticationPrincipal String username) {
		DiscoveredItemResponse result = discoveredItemService.dismiss(id,
				request == null ? null : request.getReasonCode(), request == null ? null : request.getReason(), username);
		return ResponseEntity.ok(ApiResponse.success("已略過", result));
	}

	@PreAuthorize("hasRole('PURCHASER')")
	@PostMapping("/api/discoveries/{id}/restore")
	public ResponseEntity<ApiResponse<DiscoveredItemResponse>> restore(@PathVariable("id") Long id) {
		return ResponseEntity.ok(ApiResponse.success("已移回待處理", discoveredItemService.restore(id)));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@GetMapping("/api/settings/discovery")
	public ResponseEntity<ApiResponse<DiscoveryStatusResponse>> getStatus() {
		return ResponseEntity.ok(ApiResponse.success("查詢成功", discoveryRunService.getStatus()));
	}

	/** 執行紀錄分頁（2026-09-29）：GET /api/settings/discovery/runs?page=0&size=10，新到舊。 */
	@PreAuthorize("hasRole('MANAGER')")
	@GetMapping("/api/settings/discovery/runs")
	public ResponseEntity<ApiResponse<Page<DiscoveryRunResponse>>> getRuns(
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "10") int size) {
		return ResponseEntity.ok(ApiResponse.success("查詢成功", discoveryRunService.getRuns(page, size)));
	}

	/** 立即執行一次探索（背景執行，立即回 202；畫面輪詢 GET 看狀態）。無法執行時回 409。 */
	@PreAuthorize("hasRole('MANAGER')")
	@PostMapping("/api/settings/discovery/run")
	public ResponseEntity<ApiResponse<DiscoveryStatusResponse>> run(@AuthenticationPrincipal String username) {
		DiscoveryStatusResponse result = discoveryRunService.startManualRun(username);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success("已開始新品探索", result));
	}
}
