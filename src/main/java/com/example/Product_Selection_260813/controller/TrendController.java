package com.example.Product_Selection_260813.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Product_Selection_260813.common.ApiResponse;
import com.example.Product_Selection_260813.json.TrendSnapshot;
import com.example.Product_Selection_260813.service.TrendService;

/**
 * 對應 API總表 四、評估／趨勢／AI 底下唯一掛在本Controller的端點（十二-13分層決議）：
 * POST /api/products/{id}/trend/sync [操作+管理]。
 *
 * 不需要額外@PreAuthorize：[操作+管理]皆可存取，SecurityConfig預設規則
 * 「已登入即可」已涵蓋，與ProductController其餘端點的權限風格一致。
 */
@RestController
public class TrendController {

	@Autowired
	private TrendService trendService;

	/**
	 * POST /api/products/{id}/trend/sync：手動同步指定商品的最新市場趨勢／熱門度資料，
	 * 並觸發評估結果的局部更新。
	 */
	@PostMapping("/api/products/{id}/trend/sync")
	public ResponseEntity<ApiResponse<TrendSnapshot>> syncTrend(@PathVariable("id") Long id) {
		TrendSnapshot result = trendService.syncTrend(id);
		return ResponseEntity.ok(ApiResponse.success("趨勢資料已同步", result));
	}
}
