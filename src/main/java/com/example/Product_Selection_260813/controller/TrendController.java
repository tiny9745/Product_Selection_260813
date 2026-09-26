package com.example.Product_Selection_260813.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Product_Selection_260813.common.ApiResponse;
import com.example.Product_Selection_260813.json.TrendHistoryPoint;
import com.example.Product_Selection_260813.json.TrendSnapshot;
import com.example.Product_Selection_260813.service.TrendService;

/**
 * 對應 API總表 四、評估／趨勢／AI 底下掛在本Controller的端點（十二-13分層決議）：
 * POST /api/products/{id}/trend/sync [操作+管理]，以及 2026-09-25 補上的唯讀端點
 * GET /api/products/{id}/trend [操作+管理]。
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

	/**
	 * GET /api/products/{id}/trend：讀取該商品最新一筆趨勢資料，不觸發爬蟲。
	 *
	 * 2026-09-25新增：改接 PTT 後，每天 02:00 排程會自動寫入趨勢資料，但前端原本
	 * 只能從 POST sync 的回傳拿到趨勢，重新進入詳情頁就看不到已存在的資料。
	 * 這支是純讀取，可以在頁面載入時呼叫；尚無資料時 data 為 null。
	 */
	@GetMapping("/api/products/{id}/trend")
	public ResponseEntity<ApiResponse<TrendSnapshot>> getLatestTrend(@PathVariable("id") Long id) {
		TrendSnapshot result = trendService.getLatestTrend(id);
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	/**
	 * GET /api/products/{id}/trend/history：最近 30 天的趨勢歷史序列，
	 * 依時間正序回傳，供品項詳情頁畫趨勢圖用。
	 *
	 * ⚠️ 2026-09-25 新增：跟上面 getLatestTrend()（只拿最新一筆）刻意
	 * 區隔，這支拿完整序列，見 ScoringService.buildTrendHistory() 說明。
	 */
	@GetMapping("/api/products/{id}/trend/history")
	public ResponseEntity<ApiResponse<List<TrendHistoryPoint>>> getTrendHistory(@PathVariable("id") Long id) {
		List<TrendHistoryPoint> result = trendService.getHistory(id);
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}
}
