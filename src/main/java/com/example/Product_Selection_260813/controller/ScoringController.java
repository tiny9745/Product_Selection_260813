package com.example.Product_Selection_260813.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.example.Product_Selection_260813.common.ApiResponse;
import com.example.Product_Selection_260813.dto.response.EvaluationResponse;
import com.example.Product_Selection_260813.dto.response.FestivalBoostResponse;
import com.example.Product_Selection_260813.service.ScoringService;

/**
 * 對應 API總表 四、評估／趨勢／AI 底下掛在本Controller的兩支端點（十二-13分層決議）：
 * GET /api/products/{id}/evaluation、GET /api/products/{id}/festival-boost，
 * 皆為[操作+管理]。
 *
 * 不需要額外@PreAuthorize：SecurityConfig預設規則「已登入即可」已涵蓋，
 * 與ProductController／TrendController的權限風格一致。
 *
 * Controller只負責解析Request、轉呼叫ScoringService、決定回應格式——雙軌讀取邏輯
 * （APPROVED讀review_records凍結值／其餘讀product_evaluations即時值）完全封裝在
 * ScoringService內部，Controller不需要也不應該知道這個判斷細節。
 */
@RestController
public class ScoringController {

	@Autowired
	private ScoringService scoringService;

	/**
	 * GET /api/products/{id}/evaluation：取得商品目前評估模式、固定權重、
	 * 各項分數、Base/Final Score。
	 */
	@GetMapping("/api/products/{id}/evaluation")
	public ResponseEntity<ApiResponse<EvaluationResponse>> getEvaluation(@PathVariable("id") Long id) {
		EvaluationResponse result = scoringService.getEvaluation(id);
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	/**
	 * GET /api/products/{id}/festival-boost：取得該商品目前命中的檔期、
	 * Match Weight、Urgency Factor、Festival Boost、Final Score等可解釋性明細。
	 */
	@GetMapping("/api/products/{id}/festival-boost")
	public ResponseEntity<ApiResponse<FestivalBoostResponse>> getFestivalBoost(@PathVariable("id") Long id) {
		FestivalBoostResponse result = scoringService.getFestivalBoostDetail(id);
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}
}
