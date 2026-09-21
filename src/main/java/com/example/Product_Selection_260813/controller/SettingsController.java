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
import com.example.Product_Selection_260813.dto.request.CustomFieldDefinitionCreateRequest;
import com.example.Product_Selection_260813.dto.request.CustomFieldDefinitionUpdateRequest;
import com.example.Product_Selection_260813.dto.request.FactorDefinitionCreateRequest;
import com.example.Product_Selection_260813.dto.request.FactorDefinitionUpdateRequest;
import com.example.Product_Selection_260813.dto.response.CustomFieldDefinitionResponse;
import com.example.Product_Selection_260813.dto.response.FactorDefinitionResponse;
import com.example.Product_Selection_260813.dto.request.FestiveCampaignCreateRequest;
import com.example.Product_Selection_260813.dto.request.FestiveCampaignManualStatusRequest;
import com.example.Product_Selection_260813.dto.request.FestiveCampaignUpdateRequest;
import com.example.Product_Selection_260813.dto.request.ProductTypeCreateRequest;
import com.example.Product_Selection_260813.dto.request.ProductTypeUpdateRequest;
//import com.example.Product_Selection_260813.dto.request.ProductTypeUpdateRequest;
import com.example.Product_Selection_260813.dto.request.RiskOptionCreateRequest;
import com.example.Product_Selection_260813.dto.request.RiskOptionUpdateRequest;
import com.example.Product_Selection_260813.dto.request.SwitchEvaluationModeRequest;
import com.example.Product_Selection_260813.dto.request.SystemSettingUpdateRequest;
import com.example.Product_Selection_260813.dto.response.AudienceProfileResponse;
import com.example.Product_Selection_260813.dto.response.EvaluationModeResponse;
import com.example.Product_Selection_260813.dto.response.FestiveCampaignResponse;
import com.example.Product_Selection_260813.dto.response.ProductTypeResponse;
import com.example.Product_Selection_260813.dto.response.RiskOptionSettingResponse;
import com.example.Product_Selection_260813.dto.response.SystemSettingResponse;
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

	// ========================= 自訂計分因子 =========================

	@PreAuthorize("hasRole('MANAGER')")
	@GetMapping("/factor-definitions")
	public ResponseEntity<ApiResponse<List<FactorDefinitionResponse>>> getFactorDefinitions() {
		List<FactorDefinitionResponse> result = settingsService.listFactorDefinitions();
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	/**
	 * 新增自訂計分因子。新增後不會自動加進任何評估模式的權重配置——
	 * 要讓某個自訂模式開始採計，還需要另外呼叫上面的
	 * PUT /evaluation-modes/{id}/factors，見 FactorDefinitionCreateRequest 類別註解。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@PostMapping("/factor-definitions")
	public ResponseEntity<ApiResponse<FactorDefinitionResponse>> createFactorDefinition(
			@Valid @RequestBody FactorDefinitionCreateRequest request,
			@AuthenticationPrincipal String username) {
		FactorDefinitionResponse result = settingsService.createFactorDefinition(request, username);
		return ResponseEntity.ok(ApiResponse.success("自訂因子已新增", result));
	}

	/**
	 * 編輯自訂計分因子。回傳的是新版本（新id）的資料，不是被取代的舊版本——
	 * 前端應以回應內容取代畫面上原本這一列，見 SettingsService.
	 * updateFactorDefinition() 類別註解的版本鏈設計說明。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/factor-definitions/{id}")
	public ResponseEntity<ApiResponse<FactorDefinitionResponse>> updateFactorDefinition(
			@PathVariable("id") Long id,
			@Valid @RequestBody FactorDefinitionUpdateRequest request,
			@AuthenticationPrincipal String username) {
		FactorDefinitionResponse result = settingsService.updateFactorDefinition(id, request, username);
		return ResponseEntity.ok(ApiResponse.success("自訂因子已更新", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/factor-definitions/{id}/disable")
	public ResponseEntity<ApiResponse<FactorDefinitionResponse>> disableFactorDefinition(
			@PathVariable("id") Long id, @AuthenticationPrincipal String username) {
		FactorDefinitionResponse result = settingsService.disableFactorDefinition(id, username);
		return ResponseEntity.ok(ApiResponse.success("已停用", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/factor-definitions/{id}/enable")
	public ResponseEntity<ApiResponse<FactorDefinitionResponse>> enableFactorDefinition(
			@PathVariable("id") Long id, @AuthenticationPrincipal String username) {
		FactorDefinitionResponse result = settingsService.enableFactorDefinition(id, username);
		return ResponseEntity.ok(ApiResponse.success("已啟用", result));
	}

	// ========================= 自訂商品屬性（動態問卷） =========================

	@PreAuthorize("hasRole('MANAGER')")
	@GetMapping("/custom-field-definitions")
	public ResponseEntity<ApiResponse<List<CustomFieldDefinitionResponse>>> getCustomFieldDefinitions() {
		List<CustomFieldDefinitionResponse> result = settingsService.listCustomFieldDefinitions();
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PostMapping("/custom-field-definitions")
	public ResponseEntity<ApiResponse<CustomFieldDefinitionResponse>> createCustomFieldDefinition(
			@Valid @RequestBody CustomFieldDefinitionCreateRequest request,
			@AuthenticationPrincipal String username) {
		CustomFieldDefinitionResponse result = settingsService.createCustomFieldDefinition(request, username);
		return ResponseEntity.ok(ApiResponse.success("自訂商品屬性已新增", result));
	}

	/**
	 * 編輯自訂商品屬性題目。回傳的是新版本（新id）的資料。若這個題目正被某些
	 * 生效中的因子綁定，服務層會一併把這些因子改綁到新版本；回應內容裡看不到
	 * 「哪些因子被改綁」的清單，如需要可另外呼叫 GET /factor-definitions 確認
	 * （因子清單的customFieldDefinitionId會反映最新綁定）。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/custom-field-definitions/{id}")
	public ResponseEntity<ApiResponse<CustomFieldDefinitionResponse>> updateCustomFieldDefinition(
			@PathVariable("id") Long id,
			@Valid @RequestBody CustomFieldDefinitionUpdateRequest request,
			@AuthenticationPrincipal String username) {
		CustomFieldDefinitionResponse result = settingsService.updateCustomFieldDefinition(id, request, username);
		return ResponseEntity.ok(ApiResponse.success("自訂商品屬性已更新", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/custom-field-definitions/{id}/disable")
	public ResponseEntity<ApiResponse<CustomFieldDefinitionResponse>> disableCustomFieldDefinition(
			@PathVariable("id") Long id, @AuthenticationPrincipal String username) {
		CustomFieldDefinitionResponse result = settingsService.disableCustomFieldDefinition(id, username);
		return ResponseEntity.ok(ApiResponse.success("已停用", result));
	}

	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/custom-field-definitions/{id}/enable")
	public ResponseEntity<ApiResponse<CustomFieldDefinitionResponse>> enableCustomFieldDefinition(
			@PathVariable("id") Long id, @AuthenticationPrincipal String username) {
		CustomFieldDefinitionResponse result = settingsService.enableCustomFieldDefinition(id, username);
		return ResponseEntity.ok(ApiResponse.success("已啟用", result));
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

	// ========================= 系統設定（演算法參數）=========================

	/**
	 * 列出登記表（SystemSettingRegistry）裡全部已知的演算法參數（貝氏收縮 k 值、
	 * 趨勢新鮮度半衰期等），每筆附上目前生效值與型別／範圍中繼資料。
	 *
	 * ⚠️ 補上此端點前，SettingsService.getSystemSettings()／updateSystemSetting()
	 * 業務邏輯早已完成，只是從未被 Controller 掛上路由，導致前端「演算法參數」
	 * 分頁一直打到不存在的端點、收到非 ApiResponse 格式的 404，被前端錯誤處理
	 * 邏輯退化顯示成「伺服器發生錯誤，請稍後再試」。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@GetMapping("/system-settings")
	public ResponseEntity<ApiResponse<List<SystemSettingResponse>>> getSystemSettings() {
		List<SystemSettingResponse> result = settingsService.getSystemSettings();
		return ResponseEntity.ok(ApiResponse.success("查詢成功", result));
	}

	/**
	 * 更新單一演算法參數。key 是路徑參數，不是 body 欄位；型別與範圍驗證
	 * 交給 SettingsService 對照 SystemSettingRegistry 的中繼資料做。
	 */
	@PreAuthorize("hasRole('MANAGER')")
	@PutMapping("/system-settings/{key}")
	public ResponseEntity<ApiResponse<SystemSettingResponse>> updateSystemSetting(
			@PathVariable("key") String key,
			@Valid @RequestBody SystemSettingUpdateRequest request,
			@AuthenticationPrincipal String username) {
		SystemSettingResponse result = settingsService.updateSystemSetting(key, request.getValue(), username);
		return ResponseEntity.ok(ApiResponse.success("已更新演算法參數", result));
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
