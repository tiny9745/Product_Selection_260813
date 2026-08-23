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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Product_Selection_260813.common.ApiResponse;
import com.example.Product_Selection_260813.dto.request.AudienceProfileUpdateRequest;
import com.example.Product_Selection_260813.dto.request.FestiveCampaignCreateRequest;
import com.example.Product_Selection_260813.dto.request.FestiveCampaignManualStatusRequest;
import com.example.Product_Selection_260813.dto.request.FestiveCampaignUpdateRequest;
import com.example.Product_Selection_260813.dto.request.ProductTypeCreateRequest;
import com.example.Product_Selection_260813.dto.request.RiskOptionCreateRequest;
import com.example.Product_Selection_260813.dto.request.SwitchEvaluationModeRequest;
import com.example.Product_Selection_260813.dto.response.AudienceProfileResponse;
import com.example.Product_Selection_260813.dto.response.EvaluationModeResponse;
import com.example.Product_Selection_260813.dto.response.FestiveCampaignResponse;
import com.example.Product_Selection_260813.dto.response.ProductTypeResponse;
import com.example.Product_Selection_260813.dto.response.RiskOptionResponse;
import com.example.Product_Selection_260813.json.WeightSnapshot;
import com.example.Product_Selection_260813.service.SettingsService;

import jakarta.validation.Valid;

/**
 * 對應 API總表 七、系統設定（十二-13分層決議：SettingsController → SettingsService）。
 *
 * <b>本輪範圍（分批實作，第二批）：</b>核心客群設定（2支）／商品類型設定（4支）／
 * 節慶檔期管理（4支）。加上第一批已完成的評估模式（4支）與人工風險選項的GET
 * （1支），除了POST /api/settings/risk-options（詳見SettingsService類別註解
 * 的文件矛盾說明，本輪刻意不提供），七、系統設定其餘端點皆已完成。
 *
 * 各端點權限逐支對應企劃書標註的角色範圍（[操作+管理]／[僅管理]），
 * 不是整個Controller套同一組權限。
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

	@Autowired
	private SettingsService settingsService;

	// ========================= 評估模式 =========================

	@PreAuthorize("hasRole('MANAGER')")
	@GetMapping("/evaluation-modes")
	public ResponseEntity<ApiResponse<List<EvaluationModeResponse>>> getEvaluationModes() {
		List<EvaluationModeResponse> result = settingsService.getAllEvaluationModes();
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@GetMapping("/evaluation-modes/{id}/factors")
	public ResponseEntity<ApiResponse<WeightSnapshot>> getEvaluationModeFactors(@PathVariable("id") Long id) {
		WeightSnapshot result = settingsService.getEvaluationModeFactors(id);
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	// [操作+管理]，不加@PreAuthorize
	@GetMapping("/evaluation-mode/current")
	public ResponseEntity<ApiResponse<EvaluationModeResponse>> getCurrentEvaluationMode() {
		EvaluationModeResponse result = settingsService.getCurrentEvaluationMode();
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/evaluation-mode/current")
	public ResponseEntity<ApiResponse<EvaluationModeResponse>> switchCurrentEvaluationMode(
			@Valid @RequestBody SwitchEvaluationModeRequest request, @AuthenticationPrincipal String username) {
		EvaluationModeResponse result = settingsService.switchCurrentEvaluationMode(request, username);
		return ResponseEntity.ok(ApiResponse.success("已切換目前生效模式", result));
	}

	// ========================= 人工風險選項 =========================

	@PreAuthorize("hasRole('MANAGER')")
	@GetMapping("/risk-options")
	public ResponseEntity<ApiResponse<List<RiskOptionResponse>>> getRiskOptions() {
		List<RiskOptionResponse> result = settingsService.getAllRiskOptions();
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PostMapping("/risk-options")
	public ResponseEntity<ApiResponse<RiskOptionResponse>> createRiskOption(
			@Valid @RequestBody RiskOptionCreateRequest request, @AuthenticationPrincipal String username) {
		RiskOptionResponse result = settingsService.createRiskOption(request, username);
		return ResponseEntity.ok(ApiResponse.success("新增成功", result));
	}

	// ========================= 核心客群設定 =========================

	@PreAuthorize("hasRole('MANAGER')")
	@GetMapping("/audience-profile")
	public ResponseEntity<ApiResponse<AudienceProfileResponse>> getAudienceProfile() {
		AudienceProfileResponse result = settingsService.getActiveAudienceProfile();
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/audience-profile")
	public ResponseEntity<ApiResponse<AudienceProfileResponse>> updateAudienceProfile(
			@Valid @RequestBody AudienceProfileUpdateRequest request) {
		AudienceProfileResponse result = settingsService.updateActiveAudienceProfile(request);
		return ResponseEntity.ok(ApiResponse.success("已更新核心客群設定", result));
	}

	// ========================= 商品類型設定 =========================

	// [操作+管理]，不加@PreAuthorize
	@GetMapping("/product-types")
	public ResponseEntity<ApiResponse<List<ProductTypeResponse>>> getProductTypes() {
		List<ProductTypeResponse> result = settingsService.getAllProductTypes();
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PostMapping("/product-types")
	public ResponseEntity<ApiResponse<ProductTypeResponse>> createProductType(
			@Valid @RequestBody ProductTypeCreateRequest request, @AuthenticationPrincipal String username) {
		ProductTypeResponse result = settingsService.createProductType(request, username);
		return ResponseEntity.ok(ApiResponse.success("新增成功", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/product-types/{id}/disable")
	public ResponseEntity<ApiResponse<ProductTypeResponse>> disableProductType(@PathVariable("id") Long id) {
		ProductTypeResponse result = settingsService.disableProductType(id);
		return ResponseEntity.ok(ApiResponse.success("已停用", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@DeleteMapping("/product-types/{id}")
	public ResponseEntity<ApiResponse<Void>> deleteProductType(@PathVariable("id") Long id) {
		settingsService.deleteProductType(id);
		return ResponseEntity.ok(ApiResponse.success("刪除成功"));
	}

	// ========================= 節慶檔期管理 =========================

	// [操作+管理]，不加@PreAuthorize
	@GetMapping("/festive-campaigns")
	public ResponseEntity<ApiResponse<List<FestiveCampaignResponse>>> getFestiveCampaigns() {
		List<FestiveCampaignResponse> result = settingsService.getAllFestiveCampaigns();
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PostMapping("/festive-campaigns")
	public ResponseEntity<ApiResponse<FestiveCampaignResponse>> createFestiveCampaign(
			@Valid @RequestBody FestiveCampaignCreateRequest request) {
		FestiveCampaignResponse result = settingsService.createFestiveCampaign(request);
		return ResponseEntity.ok(ApiResponse.success("新增成功", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/festive-campaigns/{id}")
	public ResponseEntity<ApiResponse<FestiveCampaignResponse>> updateFestiveCampaign(@PathVariable("id") Long id,
			@Valid @RequestBody FestiveCampaignUpdateRequest request) {
		FestiveCampaignResponse result = settingsService.updateFestiveCampaign(id, request);
		return ResponseEntity.ok(ApiResponse.success("修改成功", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PostMapping("/festive-campaigns/{id}/manual-status")
	public ResponseEntity<ApiResponse<FestiveCampaignResponse>> switchManualStatus(@PathVariable("id") Long id,
			@Valid @RequestBody FestiveCampaignManualStatusRequest request) {
		FestiveCampaignResponse result = settingsService.switchManualStatus(id, request);
		return ResponseEntity.ok(ApiResponse.success("已切換檔期狀態", result));
	}
}
