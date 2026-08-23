package com.example.Product_Selection_260813.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
 * 皆為[操作+管理]。
 *
 * 不需要額外@PreAuthorize：SecurityConfig預設規則「已登入即可」已涵蓋，
 * 與ProductController／TrendController／ScoringController的權限風格一致。
 *
 * POST目前透過AiSelectionService呼叫模擬LLM實作（見AiSelectionService類別註解
 * 的TODO說明），實際串接真實LLM API是另一項獨立任務，Controller層完全不受影響
 * ——未來替換成真實API，本Controller不需要更動任何一行。
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
	@PostMapping("/api/products/{id}/ai-analysis/generate")
	public ResponseEntity<ApiResponse<AiAnalysisResponse>> generateAiAnalysis(@PathVariable("id") Long id) {
		AiAnalysisResponse result = aiSelectionService.generateAndReturnResponse(id);
		return ResponseEntity.ok(ApiResponse.success("AI分析已生成", result));
	}
}
