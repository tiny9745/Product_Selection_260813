package com.example.Product_Selection_260813.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.Product_Selection_260813.common.ApiResponse;
import com.example.Product_Selection_260813.dto.response.GroupBuyImportResult;
import com.example.Product_Selection_260813.dto.response.GroupBuyRecordResponse;
import com.example.Product_Selection_260813.service.GroupBuyRecordService;

/**
 * 歷史開團紀錄。
 *
 * <b>刻意沒有單筆新增／編輯／刪除端點</b>，這與其他 Controller 的 CRUD 慣例不一致。
 * 原因是系統邊界：選品系統的職責到審核為止，不負責審核之後的開團執行。
 * 開團結果由外部系統產生後批次匯入，系統內視為唯讀的參考資料。
 *
 * 只有兩種寫入路徑：整批匯入、整批回退。兩者都是批次級別，沒有單筆操作，
 * 這樣資料的來源永遠可追溯到某一次匯入。請勿為了「補齊 CRUD」而新增端點。
 */
@RestController
@RequestMapping("/api/group-buy-records")
public class GroupBuyRecordController {

	private final GroupBuyRecordService groupBuyRecordService;

	@Autowired
	public GroupBuyRecordController(GroupBuyRecordService groupBuyRecordService) {
		this.groupBuyRecordService = groupBuyRecordService;
	}

	/**
	 * CSV 匯入。
	 *
	 * 回傳 200 而非 400 即使匯入失敗——失敗結果（哪幾列有錯）本身就是使用者要看的
	 * 資料，包在 ApiResponse.data 裡讓前端能逐列標示，比丟一個 400 錯誤訊息有用。
	 * 真正的請求錯誤（沒選檔案、檔案讀不到）才由 Service 拋例外轉成 400。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@PostMapping("/import")
	public ResponseEntity<ApiResponse<GroupBuyImportResult>> importCsv(
			@RequestParam("file") MultipartFile file,
			@AuthenticationPrincipal String username) {
		GroupBuyImportResult result = groupBuyRecordService.importFromCsv(file, username);
		String message = result.success()
				? "匯入完成，共 " + result.importedRows() + " 筆"
				: "匯入未執行：有 " + result.errors().size() + " 處資料問題，修正後請重新上傳";
		return ResponseEntity.ok(ApiResponse.success(message, result));
	}

	/**
	 * 唯讀查詢。可依品類或商品篩選，皆不指定時回傳全部。
	 *
	 * 操作與管理角色都可讀——採購需要看到歷史成團狀況才能判斷自己的預估合不合理。
	 */
	@GetMapping
	public ResponseEntity<ApiResponse<List<GroupBuyRecordResponse>>> list(
			@RequestParam(name = "productTypeId", required = false) Long productTypeId,
			@RequestParam(name = "productId", required = false) Long productId) {
		List<GroupBuyRecordResponse> result;
		if (productId != null) {
			result = groupBuyRecordService.findByProduct(productId);
		} else if (productTypeId != null) {
			result = groupBuyRecordService.findByProductType(productTypeId);
		} else {
			result = groupBuyRecordService.findAll();
		}
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	/** 整批回退。匯入錯誤時使用，不需要逐筆刪除。 */
	@PreAuthorize("hasRole('MANAGER')")
	@DeleteMapping("/batch/{batchId}")
	public ResponseEntity<ApiResponse<Void>> deleteBatch(@PathVariable("batchId") String batchId) {
		int deleted = groupBuyRecordService.deleteBatch(batchId);
		return ResponseEntity.ok(ApiResponse.success("已回退批次 " + batchId + "，共刪除 " + deleted + " 筆"));
	}
}
