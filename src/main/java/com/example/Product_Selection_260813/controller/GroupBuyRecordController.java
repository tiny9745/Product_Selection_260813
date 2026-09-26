package com.example.Product_Selection_260813.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.Product_Selection_260813.common.ApiResponse;
import com.example.Product_Selection_260813.dto.request.ClaimGroupBuyRecordsRequest;
import com.example.Product_Selection_260813.dto.response.GroupBuyImportResult;
import com.example.Product_Selection_260813.dto.response.GroupBuyRecordClaimCandidateResponse;
import com.example.Product_Selection_260813.dto.response.GroupBuyRecordResponse;
import com.example.Product_Selection_260813.enums.UserRole;
import com.example.Product_Selection_260813.security.AuthenticatedUserRole;
import com.example.Product_Selection_260813.service.GroupBuyRecordService;

import jakarta.validation.Valid;

/**
 * 歷史開團紀錄。
 *
 * <b>刻意沒有單筆新增／編輯／刪除端點</b>，這與其他 Controller 的 CRUD 慣例不一致。
 * 原因是系統邊界：選品系統的職責到審核為止，不負責審核之後的開團執行。
 * 開團結果由外部系統產生後批次匯入，系統內視為唯讀的參考資料。
 *
 * 只有一種資料寫入路徑：整批匯入（另有「認領」只回填 product_id，不改紀錄
 * 內容），沒有單筆操作，這樣資料的來源永遠可追溯到某一次匯入。請勿為了
 * 「補齊 CRUD」而新增端點。
 *
 * 2026-09-23 分支整併：移除原本的整批回退（DELETE /batch/{batchId}）。
 * 理由見 GroupBuyRecordService 對應位置的說明。
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
	 * 但成本資訊依角色揭露（2026-09 職責分層）：PURCHASER 拿到的 costPriceAtTime／marginRate
	 * 固定為 null，MANAGER 才有值，見 GroupBuyRecordResponse 類別說明。
	 * 這支原本沒有任何角色判斷，操作層可直接看到每筆成本價，屬資訊揭露範圍問題。
	 */
	@GetMapping
	public ResponseEntity<ApiResponse<List<GroupBuyRecordResponse>>> list(
			@RequestParam(name = "productTypeId", required = false) Long productTypeId,
			@RequestParam(name = "productId", required = false) Long productId,
			Authentication authentication) {
		UserRole viewerRole = AuthenticatedUserRole.of(authentication);
		List<GroupBuyRecordResponse> result;
		if (productId != null) {
			result = groupBuyRecordService.findByProduct(productId, viewerRole);
		} else if (productTypeId != null) {
			result = groupBuyRecordService.findByProductType(productTypeId, viewerRole);
		} else {
			result = groupBuyRecordService.findAll(viewerRole);
		}
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	/**
	 * GET /api/group-buy-records/unlinked-candidates：認領歷史紀錄的候選查詢。
	 *
	 * 唯讀查詢，不修改任何資料。[操作+管理] 皆可存取——與建立商品本身的權限
	 * 一致，認領本來就是「新增／編輯商品」流程裡的一個步驟，不該比建立商品
	 * 本身的權限還嚴格。
	 */
	@GetMapping("/unlinked-candidates")
	public ResponseEntity<ApiResponse<List<GroupBuyRecordClaimCandidateResponse>>> searchUnlinkedCandidates(
			@RequestParam("productTypeId") Long productTypeId,
			@RequestParam("name") String name,
			@RequestParam(value = "supplierName", required = false) String supplierName) {
		List<GroupBuyRecordClaimCandidateResponse> result = groupBuyRecordService
				.searchUnlinkedCandidates(productTypeId, name, supplierName);
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	/**
	 * POST /api/group-buy-records/claim：把選定的歷史紀錄連結到指定商品。
	 *
	 * ⚠️ 這是 group_buy_records「不提供單筆編輯」原則下的窄範圍例外，見
	 * GroupBuyRecordService.claimRecords() 類別註解。[操作+管理] 皆可存取，
	 * 理由同上；權限邊界是否要收緊到僅管理，待你視實際使用狀況決定。
	 */
	@PostMapping("/claim")
	public ResponseEntity<ApiResponse<Void>> claim(@Valid @RequestBody ClaimGroupBuyRecordsRequest request) {
		groupBuyRecordService.claimRecords(request);
		return ResponseEntity.ok(ApiResponse.success("已連結 " + request.getGroupBuyRecordIds().size() + " 筆歷史紀錄"));
	}
}
