package com.example.Product_Selection_260813.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.Product_Selection_260813.common.ApiResponse;
import com.example.Product_Selection_260813.dto.request.ReviewSubmitRequest;
import com.example.Product_Selection_260813.dto.response.ProductResponse;
import com.example.Product_Selection_260813.dto.response.ReviewDetailResponse;
import com.example.Product_Selection_260813.dto.response.ReviewRecordResponse;
import com.example.Product_Selection_260813.service.ReviewService;

import jakarta.validation.Valid;

/**
 * 對應 API總表 五、選品審核（[僅管理]）與 六、審核歷史／版本追蹤（[操作+管理]）。
 *
 * 不使用類別層級@RequestMapping：五、選品審核的端點都在/api/reviews/*下，
 * 但六、審核歷史/api/products/{id}/reviews巢狀在/api/products/*路徑下
 * （語意上仍屬於審核網域、由ReviewService負責，見十二-13分層決議），
 * 兩者路徑前綴不同，故各方法自行標明完整路徑，不假裝套用同一個類別前綴。
 *
 * [僅管理]端點以@PreAuthorize("hasRole('MANAGER')")逐支落實RBAC（六-4決議），
 * 不僅靠前端選單隱藏；GET /api/products/{id}/reviews為[操作+管理]，
 * SecurityConfig預設規則「已登入即可」已涵蓋，刻意不加額外角色限制。
 */
@RestController
public class ReviewController {

	@Autowired
	private ReviewService reviewService;

	/**
	 * GET /api/reviews/pending：待審清單，預設「未審核＋使用中」。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@GetMapping("/api/reviews/pending")
	public ResponseEntity<ApiResponse<Page<ProductResponse>>> getPendingReviews(
			@PageableDefault(size = 20) Pageable pageable) {
		Page<ProductResponse> result = reviewService.getPendingReviews(pageable);
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	/**
	 * GET /api/reviews/{productId}：取得管理進行審核所需的完整資訊（含節慶加成明細）。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@GetMapping("/api/reviews/{productId}")
	public ResponseEntity<ApiResponse<ReviewDetailResponse>> getReviewDetail(@PathVariable Long productId) {
		ReviewDetailResponse result = reviewService.getReviewDetail(productId);
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	/**
	 * POST /api/reviews：提交人工風險評估、審核留言及核准／拒絕結果。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@PostMapping("/api/reviews")
	public ResponseEntity<ApiResponse<ReviewRecordResponse>> submitReview(
			@Valid @RequestBody ReviewSubmitRequest request, @AuthenticationPrincipal String username) {
		ReviewRecordResponse result = reviewService.submitReview(request, username);
		return ResponseEntity.ok(ApiResponse.success("審核已送出", result));
	}

	/**
	 * GET /api/reviews/decision-records：跨商品的審核紀錄彙總查詢頁。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@GetMapping("/api/reviews/decision-records")
	public ResponseEntity<ApiResponse<Page<ReviewRecordResponse>>> getDecisionRecords(
			@PageableDefault(size = 20) Pageable pageable) {
		Page<ReviewRecordResponse> result = reviewService.getDecisionRecords(pageable);
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	/**
	 * GET /api/products/{id}/reviews：查詢單一商品歷次送審與審核結果。
	 */
	@GetMapping("/api/products/{id}/reviews")
	public ResponseEntity<ApiResponse<List<ReviewRecordResponse>>> getProductReviewHistory(@PathVariable Long id) {
		List<ReviewRecordResponse> result = reviewService.getProductReviewHistory(id);
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}
}
