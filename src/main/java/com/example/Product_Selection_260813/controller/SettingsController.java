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
import com.example.Product_Selection_260813.dto.response.ProductTypeScoreBandResponse;
import com.example.Product_Selection_260813.dto.request.AudienceProfileUpdateRequest;
import com.example.Product_Selection_260813.dto.request.ProductTypeScoreBandCreateRequest;
import com.example.Product_Selection_260813.dto.request.ProductTypeScoreBandUpdateRequest;
import com.example.Product_Selection_260813.dto.request.EvaluationFactorUpdateRequest;
import com.example.Product_Selection_260813.dto.request.FestiveCampaignCreateRequest;
import com.example.Product_Selection_260813.dto.request.FestiveCampaignManualStatusRequest;
import com.example.Product_Selection_260813.dto.request.FestiveCampaignUpdateRequest;
import com.example.Product_Selection_260813.dto.request.ProductTypeCreateRequest;
import com.example.Product_Selection_260813.dto.request.ProductTypeUpdateRequest;
//import com.example.Product_Selection_260813.dto.request.ProductTypeUpdateRequest;
import com.example.Product_Selection_260813.dto.request.RiskOptionCreateRequest;
import com.example.Product_Selection_260813.dto.request.RiskOptionUpdateRequest;
import com.example.Product_Selection_260813.dto.request.SwitchEvaluationModeRequest;
import com.example.Product_Selection_260813.dto.response.AudienceProfileResponse;
import com.example.Product_Selection_260813.dto.response.EvaluationModeResponse;
import com.example.Product_Selection_260813.dto.response.FestiveCampaignResponse;
import com.example.Product_Selection_260813.dto.response.ProductTypeResponse;
import com.example.Product_Selection_260813.dto.response.RiskOptionSettingResponse;
import com.example.Product_Selection_260813.json.WeightSnapshot;
import com.example.Product_Selection_260813.service.SettingsService;

import jakarta.validation.Valid;

/**
 * 對應 API總表 七、系統設定（十二-13分層決議：SettingsController → SettingsService）。
 *
 * <b>本輪範圍（分批實作，第二批）：</b>核心客群設定（2支）／商品類型設定的
 * update與enable（2支）／人工風險選項的停用與復用（2支）。加上先前已完成的
 * 評估模式（4支）、人工風險選項的GET／POST（2支）、商品類型的GET／POST／
 * disable（3支）與節慶檔期管理（4支），七、系統設定端點皆已完成（詳見
 * SettingsService類別註解關於「停用只單向」決策推翻的說明）。
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

	/**
	 * 更新自訂模式的權重。
	 *
	 * 只有 is_editable = true 的模式可以改；三套固定模式會被 Service 層拒絕。
	 * 這個檢查必須在後端做，不能只靠前端不顯示編輯按鈕——有人直接呼叫 API
	 * 就繞過去了，而權重被改掉不會有任何錯誤訊息，只會讓所有商品的分數
	 * 安靜地變成另一組數字。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/evaluation-modes/{id}/factors")
	public ResponseEntity<ApiResponse<WeightSnapshot>> updateEvaluationModeFactors(
			@PathVariable("id") Long id,
			@Valid @RequestBody EvaluationFactorUpdateRequest request,
			@AuthenticationPrincipal String username) {
		WeightSnapshot result = settingsService.updateEvaluationModeFactors(id, request, username);
		return ResponseEntity.ok(ApiResponse.success(
				"權重已更新。本次調整僅影響之後新送審的商品，已完成審核的紀錄不會變動", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@GetMapping("/evaluation-modes/{id}/factors")
	public ResponseEntity<ApiResponse<WeightSnapshot>> getEvaluationModeFactors(@PathVariable("id") Long id) {
		WeightSnapshot result = settingsService.getEvaluationModeFactors(id);
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	// ========================= 目標區間 =========================

	/**
	 * 新增「品類專屬」目標區間。只支援 MANUAL 模式建立，理由見
	 * ProductTypeScoreBandCreateRequest 類別註解；已存在的品類×因子組合
	 * 會被拒絕（400），請改用下方 PUT 端點編輯既有列。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@PostMapping("/product-type-score-bands")
	public ResponseEntity<ApiResponse<ProductTypeScoreBandResponse>> createProductTypeScoreBand(
			@Valid @RequestBody ProductTypeScoreBandCreateRequest request,
			@AuthenticationPrincipal String username) {
		ProductTypeScoreBandResponse result = settingsService.createProductTypeScoreBand(request, username);
		return ResponseEntity.ok(ApiResponse.success("已新增品類專屬目標區間", result));
	}

	/**
	 * 更新目標區間。sourceMode=MANUAL 時 body 需帶 lowerBound／upperBound；
	 * sourceMode=HISTORICAL 時這兩個欄位會被忽略，由後端從歷史開團紀錄重新
	 * 計算並凍結——見 SettingsService.updateProductTypeScoreBand() 的完整說明。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/product-type-score-bands/{id}")
	public ResponseEntity<ApiResponse<ProductTypeScoreBandResponse>> updateProductTypeScoreBand(
			@PathVariable("id") Long id,
			@Valid @RequestBody ProductTypeScoreBandUpdateRequest request,
			@AuthenticationPrincipal String username) {
		ProductTypeScoreBandResponse result = settingsService.updateProductTypeScoreBand(id, request, username);
		return ResponseEntity.ok(ApiResponse.success("目標區間已更新", result));
	}

	@GetMapping("/product-type-score-bands")
	public ResponseEntity<ApiResponse<List<ProductTypeScoreBandResponse>>> getProductTypeScoreBands() {
		List<ProductTypeScoreBandResponse> result = settingsService.getProductTypeScoreBands();
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
	public ResponseEntity<ApiResponse<List<RiskOptionSettingResponse>>> getRiskOptions() {
		List<RiskOptionSettingResponse> result = settingsService.getAllRiskOptions();
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PostMapping("/risk-options")
	public ResponseEntity<ApiResponse<RiskOptionSettingResponse>> createRiskOption(
			@Valid @RequestBody RiskOptionCreateRequest request, @AuthenticationPrincipal String username) {
		RiskOptionSettingResponse result = settingsService.createRiskOption(request, username);
		return ResponseEntity.ok(ApiResponse.success("新增成功", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/risk-options/{id}")
	public ResponseEntity<ApiResponse<RiskOptionSettingResponse>> updateRiskOption(@PathVariable("id") Long id,
			@Valid @RequestBody RiskOptionUpdateRequest request) {
		RiskOptionSettingResponse result = settingsService.updateRiskOption(id, request);
		return ResponseEntity.ok(ApiResponse.success("修改成功", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/risk-options/{id}/disable")
	public ResponseEntity<ApiResponse<RiskOptionSettingResponse>> disableRiskOption(@PathVariable("id") Long id) {
		RiskOptionSettingResponse result = settingsService.disableRiskOption(id);
		return ResponseEntity.ok(ApiResponse.success("已停用", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/risk-options/{id}/enable")
	public ResponseEntity<ApiResponse<RiskOptionSettingResponse>> enableRiskOption(@PathVariable("id") Long id) {
		RiskOptionSettingResponse result = settingsService.enableRiskOption(id);
		return ResponseEntity.ok(ApiResponse.success("已復用", result));
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
	@PutMapping("/product-types/{id}")
	public ResponseEntity<ApiResponse<ProductTypeResponse>> updateProductType(@PathVariable("id") Long id,
			@Valid @RequestBody ProductTypeUpdateRequest request) {
		ProductTypeResponse result = settingsService.updateProductType(id, request);
		return ResponseEntity.ok(ApiResponse.success("修改成功", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/product-types/{id}/disable")
	public ResponseEntity<ApiResponse<ProductTypeResponse>> disableProductType(@PathVariable("id") Long id) {
		ProductTypeResponse result = settingsService.disableProductType(id);
		return ResponseEntity.ok(ApiResponse.success("已停用", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/product-types/{id}/enable")
	public ResponseEntity<ApiResponse<ProductTypeResponse>> enableProductType(@PathVariable("id") Long id) {
		ProductTypeResponse result = settingsService.enableProductType(id);
		return ResponseEntity.ok(ApiResponse.success("已復用", result));
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
