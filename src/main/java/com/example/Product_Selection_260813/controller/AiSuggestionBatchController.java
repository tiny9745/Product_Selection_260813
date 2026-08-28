package com.example.Product_Selection_260813.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Product_Selection_260813.common.ApiResponse;
import com.example.Product_Selection_260813.service.AiSuggestionBatchService;
import com.example.Product_Selection_260813.service.AiSuggestionBatchService.BatchResult;

/**
 * AI主動選品批次規則的手動觸發端點。
 *
 * 對應規格書三-3：POST /api/products/ai-suggested/batch-generate
 * [系統內部排程專用]——僅供Daily Cron排程觸發，非人工操作入口，
 * 不對操作/管理角色開放，此為AI_SUGGESTED狀態的唯一產生來源。
 *
 * 正式排程由AiSuggestionBatchService的@Scheduled（每日凌晨3點）負責，
 * 本端點僅供demo／開發／測試階段不用等待排程時間即可立即驗證效果。
 *
 * ⚠️ 待辦：目前此端點未加任何權限限制，任何人皆可呼叫觸發。正式提交前
 * 需與團隊確認是否要比照規格書「系統內部排程專用」的定位加上存取限制
 * （例如僅允許內部IP、或乾脆移除此Controller改由排程器獨立觸發），
 * 避免被任意呼叫（雖然此端點本身不呼叫LLM、不產生API費用，但仍應收斂
 * 存取範圍，符合六-4 RBAC設計精神）。
 */
@RestController
@RequestMapping("/api/products/ai-suggested")
public class AiSuggestionBatchController {

	@Autowired
	private AiSuggestionBatchService aiSuggestionBatchService;

	@PostMapping("/batch-generate")
	public ApiResponse<BatchResult> triggerBatch() {
		BatchResult result = aiSuggestionBatchService.runBatch();
		String message = String.format("批次執行完成，檢查%d個商品，新增%d個AI建議候選",
				result.checkedCount(), result.suggestedCount());
		return ApiResponse.success(message, result);
	}
}
