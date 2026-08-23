package com.example.Product_Selection_260813.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Product_Selection_260813.common.ApiResponse;
import com.example.Product_Selection_260813.dto.response.DashboardConversionRateResponse;
import com.example.Product_Selection_260813.dto.response.DashboardRecommendationItem;
import com.example.Product_Selection_260813.dto.response.DashboardRiskAlertItem;
import com.example.Product_Selection_260813.dto.response.DashboardStatisticsResponse;
import com.example.Product_Selection_260813.service.DashboardService;

/**
 * 對應 API總表「2. 儀表板」四支端點，皆為[操作+管理]，不加@PreAuthorize
 * （SecurityConfig預設規則「已登入即可」已涵蓋）。
 *
 * 企劃書十二-13的Controller對應表沒有列出本Controller／DashboardService
 * （該表本身的遺漏，四支端點在API總表裡都有完整定義，非待決議項目），
 * 依專案既有命名慣例補上，不影響其餘Controller的既有分工。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

	@Autowired
	private DashboardService dashboardService;

	/**
	 * GET /api/dashboard/statistics：取得商品總數、待審核數、通過數、拒絕數等統計。
	 */
	@GetMapping("/statistics")
	public ResponseEntity<ApiResponse<DashboardStatisticsResponse>> getStatistics() {
		DashboardStatisticsResponse result = dashboardService.getStatistics();
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	/**
	 * GET /api/dashboard/recommendations：依Final Score取得推薦分數較高的
	 * 前10項商品，含重新入榜標籤。
	 */
	@GetMapping("/recommendations")
	public ResponseEntity<ApiResponse<List<DashboardRecommendationItem>>> getRecommendations() {
		List<DashboardRecommendationItem> result = dashboardService.getRecommendations();
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	/**
	 * GET /api/dashboard/risk-alerts：依AI風險提示文字關鍵字比對，取得需要
	 * 特別注意的商品。
	 */
	@GetMapping("/risk-alerts")
	public ResponseEntity<ApiResponse<List<DashboardRiskAlertItem>>> getRiskAlerts() {
		List<DashboardRiskAlertItem> result = dashboardService.getRiskAlerts();
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	/**
	 * GET /api/dashboard/conversion-rate：計算選品轉換率。
	 */
	@GetMapping("/conversion-rate")
	public ResponseEntity<ApiResponse<DashboardConversionRateResponse>> getConversionRate() {
		DashboardConversionRateResponse result = dashboardService.getConversionRate();
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}
}
