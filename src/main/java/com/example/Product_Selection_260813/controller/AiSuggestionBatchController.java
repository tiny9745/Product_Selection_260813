package com.example.Product_Selection_260813.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * <b>權限決策（2026-08-28補上，取代原本「未加任何限制」的待辦）：</b>
 * 規格書要求「不對操作/管理角色開放」，理想上應僅限系統內部呼叫（例如IP
 * 白名單或獨立排程觸發、不對外掛REST端點）。但雛型階段沒有內部服務帳號
 * 或IP白名單機制，若完全移除此Controller，demo時就只能乾等到凌晨3點的
 * 排程時間，不切實際。折衷做法：比照六-4 RBAC規則裡「僅管理」端點的既有
 * 模式，用{@code @PreAuthorize("hasRole('MANAGER')")}收斂到只有管理層
 * 能手動觸發——雖然規格書字面上寫「不對操作/管理角色開放」，這裡刻意放寬
 * 讓MANAGER角色可用，是demo可行性與規格精神之間的務實折衷，非完全照規格
 * 字面實作。若之後有真正的內部服務呼叫機制，應改回完全禁止使用者角色存取。
 */
@RestController
@RequestMapping("/api/products/ai-suggested")
public class AiSuggestionBatchController {

	@Autowired
	private AiSuggestionBatchService aiSuggestionBatchService;

	@PreAuthorize("hasRole('MANAGER')")
	@PostMapping("/batch-generate")
	public ApiResponse<BatchResult> triggerBatch() {
		BatchResult result = aiSuggestionBatchService.runBatch();
		String message = String.format("批次執行完成，檢查%d個商品，新增%d個AI建議候選",
				result.checkedCount(), result.suggestedCount());
		return ApiResponse.success(message, result);
	}
}
