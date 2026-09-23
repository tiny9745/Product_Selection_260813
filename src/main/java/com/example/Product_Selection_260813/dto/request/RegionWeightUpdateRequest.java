package com.example.Product_Selection_260813.dto.request;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.example.Product_Selection_260813.constants.ValidationMessage;

/**
 * PUT /api/settings/region-weights 的 Request Body。
 *
 * 採<b>整份覆蓋</b>語意，比照 EvaluationFactorUpdateRequest（七項因子權重編輯）
 * 的既有作法：必須送出全部四區的占比，不接受只送想改的那幾區。理由相同——
 * 占比之間有「加總必須為 100」的約束，只送部分欄位的話，後端得把送來的值
 * 與資料庫既有值混合後才能驗證加總，畫面上算出來的加總可能跟後端實際驗的
 * 不一致。要求整份送出，兩邊看到的加總才會是同一個數字。
 */
public class RegionWeightUpdateRequest {

	@NotEmpty(message = ValidationMessage.REGION_WEIGHTS_EMPTY)
	@Valid
	private List<RegionWeightItem> regionWeights;

	public List<RegionWeightItem> getRegionWeights() {
		return regionWeights;
	}

	public void setRegionWeights(List<RegionWeightItem> regionWeights) {
		this.regionWeights = regionWeights;
	}

	/** 單一區域的占比。用 region 代碼而非陣列索引對應，理由同 EvaluationFactorUpdateRequest.FactorWeight。 */
	public static class RegionWeightItem {

		@NotBlank(message = ValidationMessage.REGION_WEIGHT_REGION_BLANK)
		private String region;

		// 單一占比的值域驗證放這裡，「四區加總須為100」是跨欄位的商業邏輯，放 SettingsService 攔截。
		@NotNull(message = ValidationMessage.REGION_WEIGHT_NULL)
		@DecimalMin(value = "0.00", message = ValidationMessage.REGION_WEIGHT_RANGE)
		@DecimalMax(value = "100.00", message = ValidationMessage.REGION_WEIGHT_RANGE)
		@Digits(integer = 3, fraction = 2, message = ValidationMessage.REGION_WEIGHT_OVER_DIGITS)
		private BigDecimal weightPercentage;

		public String getRegion() {
			return region;
		}

		public void setRegion(String region) {
			this.region = region;
		}

		public BigDecimal getWeightPercentage() {
			return weightPercentage;
		}

		public void setWeightPercentage(BigDecimal weightPercentage) {
			this.weightPercentage = weightPercentage;
		}
	}
}
