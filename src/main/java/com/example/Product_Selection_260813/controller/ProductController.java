package com.example.Product_Selection_260813.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.Product_Selection_260813.common.ApiResponse;
import com.example.Product_Selection_260813.dto.request.ProductCreateRequest;
import com.example.Product_Selection_260813.dto.request.ProductUpdateRequest;
import com.example.Product_Selection_260813.dto.response.ProductResponse;
import com.example.Product_Selection_260813.enums.ProductCandidateStatus;
import com.example.Product_Selection_260813.enums.ProductItemStatus;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;
import com.example.Product_Selection_260813.service.ProductService;

import jakarta.validation.Valid;

/**
 * 對應 API總表 三、品項管理，全部端點皆為[操作+管理]皆可存取——SecurityConfig
 * 對所有非登入路徑的預設規則就是「已登入即可」，不需要額外加@PreAuthorize
 * （與/api/reviews等[僅管理]端點不同，那些才需要方法層級的角色限制）。
 *
 * Controller只負責：解析Request、轉呼叫ProductService、決定HTTP狀態碼／回應格式，
 * 不含任何業務規則判斷——所有規則（欄位鎖定、狀態機、資料完整度門檻等）都在
 * ProductService，這裡維持跟AuthController一致的薄Controller風格。
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

	@Autowired
	private ProductService productService;

	/**
	 * GET /api/products：品項管理主清單。 支援關鍵字、審核狀態、品項狀態、候選狀態、商品類型篩選，以及分頁/排序參數。
	 *
	 * candidateStatus不帶時，ProductService.searchProducts()內部會預設帶入CANDIDATE
	 * （見ProductService類別註解），Controller這裡刻意保留null傳遞的可能性， 不在這層就寫死預設值，業務預設值只該有一個地方定義。
	 */
	@GetMapping
	public ResponseEntity<ApiResponse<Page<ProductResponse>>> search(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) ProductReviewStatus reviewStatus,
			@RequestParam(required = false) ProductItemStatus itemStatus,
			@RequestParam(required = false) ProductCandidateStatus candidateStatus,
			@RequestParam(required = false) Long productTypeId, @PageableDefault(size = 20) Pageable pageable) {
		Page<ProductResponse> result = productService.searchProducts(reviewStatus, itemStatus, candidateStatus,
				productTypeId, keyword, pageable);
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	/**
	 * GET /api/products/ai-suggested：AI建議清單（candidate_status=AI_SUGGESTED）。
	 */
	@GetMapping("/ai-suggested")
	public ResponseEntity<ApiResponse<Page<ProductResponse>>> searchAiSuggested(
			@PageableDefault(size = 20) Pageable pageable) {
		Page<ProductResponse> result = productService.searchAiSuggested(pageable);
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	/**
	 * GET /api/products/{id}：商品核心資料。
	 * 評估／趨勢／AI／風險等聚合資訊由前端另外呼叫ScoringController／TrendController／
	 * AiSelectionController取得（見十二-13分層決議），此端點不在Controller層做跨Service組裝，
	 * 避免ProductController反過來依賴其他領域的Service造成耦合。
	 */
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Long id) {
		ProductResponse result = productService.getProduct(id);
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	/**
	 * POST /api/products：手動新增品項，直接為正式候選（CANDIDATE）。
	 */
	@PostMapping
	public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductCreateRequest request,
			@AuthenticationPrincipal String username) {
		ProductResponse result = productService.createProduct(request, username);
		return ResponseEntity.ok(ApiResponse.success("新增成功", result));
	}

	/**
	 * PUT /api/products/{id}：整份覆蓋更新，欄位鎖定規則見ProductService。
	 */
	@PutMapping("/{id}")
	public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(@PathVariable Long id,
			@Valid @RequestBody ProductUpdateRequest request, @AuthenticationPrincipal String username) {
		ProductResponse result = productService.updateProduct(id, request, username);
		return ResponseEntity.ok(ApiResponse.success("修改成功", result));
	}

	/**
	 * DELETE /api/products/{id}：僅限未審核且尚未送審過的商品。
	 */
	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
		productService.deleteProduct(id);
		return ResponseEntity.ok(ApiResponse.success("刪除成功"));
	}

	/**
	 * POST /api/products/{id}/resubmit：REJECTED -&gt; PENDING，submission_count+1。
	 */
	@PostMapping("/{id}/resubmit")
	public ResponseEntity<ApiResponse<ProductResponse>> resubmit(@PathVariable Long id) {
		ProductResponse result = productService.resubmit(id);
		return ResponseEntity.ok(ApiResponse.success("已重新送審", result));
	}

	/**
	 * POST /api/products/{id}/archive：(APPROVED或REJECTED) 且 ACTIVE -&gt; ARCHIVED。
	 */
	@PostMapping("/{id}/archive")
	public ResponseEntity<ApiResponse<ProductResponse>> archive(@PathVariable Long id) {
		ProductResponse result = productService.archive(id);
		return ResponseEntity.ok(ApiResponse.success("已封存", result));
	}

	/**
	 * POST /api/products/{id}/restore：APPROVED 且 ARCHIVED -&gt; ACTIVE。
	 */
	@PostMapping("/{id}/restore")
	public ResponseEntity<ApiResponse<ProductResponse>> restore(@PathVariable Long id) {
		ProductResponse result = productService.restore(id);
		return ResponseEntity.ok(ApiResponse.success("已復用", result));
	}

	/**
	 * POST /api/products/{id}/promote-to-candidate：AI_SUGGESTED -&gt; CANDIDATE。
	 */
	@PostMapping("/{id}/promote-to-candidate")
	public ResponseEntity<ApiResponse<ProductResponse>> promoteToCandidate(@PathVariable Long id) {
		ProductResponse result = productService.promoteToCandidate(id);
		return ResponseEntity.ok(ApiResponse.success("已加入候選", result));
	}
}
