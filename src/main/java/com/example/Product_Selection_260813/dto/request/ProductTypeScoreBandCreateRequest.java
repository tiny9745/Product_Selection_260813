package com.example.Product_Selection_260813.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 新增「品類專屬目標區間」的 Request Body。
 *
 * <b>新增這支端點的原因</b>：先前 SettingsController 只有 PUT（更新既有列），
 * 資料庫初始只有兩筆全域列（MARGIN_RATE／DISCOUNT_DEPTH，product_type_id
 * 為 null）。沒有這支端點時，「各商品類型可覆寫」在畫面上永遠是空的，
 * 因為根本沒有入口能建立第一筆品類專屬列。
 *
 * <b>只支援 MANUAL 模式建立</b>：HISTORICAL 模式需要先有歷史開團紀錄才能算，
 * 新建的品類覆寫通常還沒有樣本，先用 MANUAL 建立一組合理的初始值，
 * 之後有足夠歷史資料時，主管可以再透過既有的 PUT 端點切換成 HISTORICAL。
 * 這裡不開放建立時就選 HISTORICAL，避免使用者一建立就撞到「樣本不足」的錯誤。
 *
 * <b>productTypeId 必填且不可為 null</b>：全域列（product_type_id = null）
 * 已經由資料庫種子資料建立好，不會再有人透過這支端點新增另一筆全域列
 * ——Service 層會明確拒絕，避免同一因子出現兩筆 productTypeId 為 null
 * 的列造成 ScoreBandResolver 取值時語意不清。
 */
public class ProductTypeScoreBandCreateRequest {

	@NotNull(message = "商品類型為必填")
	private Long productTypeId;

	@NotBlank(message = "因子代碼為必填")
	private String factorCode;

	@NotNull(message = "下界為必填")
	private BigDecimal lowerBound;

	@NotNull(message = "上界為必填")
	private BigDecimal upperBound;

	public Long getProductTypeId() {
		return productTypeId;
	}

	public void setProductTypeId(Long productTypeId) {
		this.productTypeId = productTypeId;
	}

	public String getFactorCode() {
		return factorCode;
	}

	public void setFactorCode(String factorCode) {
		this.factorCode = factorCode;
	}

	public BigDecimal getLowerBound() {
		return lowerBound;
	}

	public void setLowerBound(BigDecimal lowerBound) {
		this.lowerBound = lowerBound;
	}

	public BigDecimal getUpperBound() {
		return upperBound;
	}

	public void setUpperBound(BigDecimal upperBound) {
		this.upperBound = upperBound;
	}
}
