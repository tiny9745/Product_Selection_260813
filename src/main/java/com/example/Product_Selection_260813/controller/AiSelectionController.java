package com.example.Product_Selection_260813.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Product_Selection_260813.common.ApiResponse;
import com.example.Product_Selection_260813.dto.response.AiAnalysisResponse;
import com.example.Product_Selection_260813.service.AiSelectionService;

/**
 * 對應 API總表 四、評估／趨勢／AI 底下掛在本Controller的兩支端點（十二-13分層決議）：
 * GET /api/products/{id}/ai-analysis、POST /api/products/{id}/ai-analysis/generate，
 * GET為[操作+管理]；POST generate 自 2026-09-24 起為[僅操作]（職責分離，決策 D3）：
 * 產生 AI 分析會寫入 ai_analyses 並消耗 Gemini 配額，屬於選品維護動作，
 * 與 ProductController 寫入端點同一套角色規則。管理層在審核頁看到的 AI 推薦
 * 摘要來自審核詳情 API 的既有快取，不需要呼叫這支。
 *
 * POST透過AiSelectionService呼叫{@link com.example.Product_Selection_260813.service.LlmAnalysisService}
 * 介面（見該介面Java Doc），Controller層完全不受底層是Mock或真實Gemini API
 * 影響——換模型或切回模擬資料，本Controller不需要更動任何一行。
 */
@RestController
public class AiSelectionController {

	@Autowired
	private AiSelectionService aiSelectionService;

	/**
	 * GET /api/products/{id}/ai-analysis：純讀取，取得已快取的AI摘要／推薦原因／
	 * 風險提示；無快取則回傳空值。
	 */
	@GetMapping("/api/products/{id}/ai-analysis")
	public ResponseEntity<ApiResponse<AiAnalysisResponse>> getAiAnalysis(@PathVariable("id") Long id) {
		AiAnalysisResponse result = aiSelectionService.getAiAnalysisResponse(id);
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	/**
	 * POST /api/products/{id}/ai-analysis/generate：觸發生成AI分析並寫入快取。
	 */
	@PreAuthorize("hasRole('PURCHASER')")
	@PostMapping("/api/products/{id}/ai-analysis/generate")
	public ResponseEntity<ApiResponse<AiAnalysisResponse>> generateAiAnalysis(@PathVariable("id") Long id) {
		AiAnalysisResponse result = aiSelectionService.generateAndReturnResponse(id);
		return ResponseEntity.ok(ApiResponse.success("AI分析已生成", result));
	}
}
