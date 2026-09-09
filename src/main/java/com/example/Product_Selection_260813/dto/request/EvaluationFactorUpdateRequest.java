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
 * PUT /api/settings/evaluation-modes/{id}/factors 的 Request Body。
 *
 * 採<b>整份覆蓋</b>語意：必須送出全部七個因子的權重，不接受只送想改的那幾個。
 * 理由是權重之間有「加總必須為 100」的約束——只送部分欄位的話，後端得把
 * 送來的值與資料庫既有值混合後才能驗證加總，而使用者看到的畫面與實際生效的
 * 組合可能不一致。要求整份送出，畫面上算出來的加總就是後端會驗的加總。
 *
 * <b>只有自訂模式可以改</b>。三套固定模式（均衡／衝量／高利潤）的
 * is_editable 為 false，Service 層會拒絕。這個檢查不能省，否則任何人只要
 * 知道均衡型的 id，就能透過這支 API 改掉固定模式，「固定」的設計形同虛設。
 */
public class EvaluationFactorUpdateRequest {

	@NotEmpty(message = ValidationMessage.FACTOR_WEIGHTS_EMPTY)
	@Valid
	private List<FactorWeight> factors;

	public List<FactorWeight> getFactors() {
		return factors;
	}

	public void setFactors(List<FactorWeight> factors) {
		this.factors = factors;
	}

	/**
	 * 單一因子的權重。
	 *
	 * 用 factorCode 而非陣列索引對應因子，是為了讓前端送出的內容自我描述——
	 * 靠索引對應的話，前端少送一個或順序調換都會安靜地把權重套錯因子。
	 */
	public static class FactorWeight {

		@NotBlank(message = ValidationMessage.FACTOR_CODE_BLANK)
		private String factorCode;

		// 單一權重的值域驗證放這裡，「七項加總須為 100」是跨欄位的商業邏輯，
		// 放 SettingsService 攔截。
		@NotNull(message = ValidationMessage.FACTOR_WEIGHT_NULL)
		@DecimalMin(value = "0.00", message = ValidationMessage.FACTOR_WEIGHT_RANGE)
		@DecimalMax(value = "100.00", message = ValidationMessage.FACTOR_WEIGHT_RANGE)
		@Digits(integer = 3, fraction = 2, message = ValidationMessage.FACTOR_WEIGHT_OVER_DIGITS)
		private BigDecimal weight;

		public String getFactorCode() {
			return factorCode;
		}

		public void setFactorCode(String factorCode) {
			this.factorCode = factorCode;
		}

		public BigDecimal getWeight() {
			return weight;
		}

		public void setWeight(BigDecimal weight) {
			this.weight = weight;
		}
	}
}
