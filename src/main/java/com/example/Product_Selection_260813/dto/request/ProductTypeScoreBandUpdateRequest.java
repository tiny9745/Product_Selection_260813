package com.example.Product_Selection_260813.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;

/**
 * 目標區間更新的 Request Body。
 *
 * <b>依 sourceMode 決定必填欄位不同</b>，沿用專案既有的「NEW／RESALE 顯示
 * 不同必填欄位」同一種設計模式：
 * <ul>
 * <li>{@code sourceMode=MANUAL}：lowerBound／upperBound 必填（若未提供，
 *     Service 層會沿用資料庫裡目前的值，符合「手動設定預設值為固定值」）</li>
 * <li>{@code sourceMode=HISTORICAL}：lowerBound／upperBound 一律忽略，
 *     由 Service 從歷史開團紀錄重新計算，即使有送也不會被採用</li>
 * </ul>
 *
 * 因此這裡的 lowerBound／upperBound 都不加 {@code @NotNull}——單欄位的
 * 必填與否取決於另一個欄位的值，這種跨欄位條件式驗證放在 Service 層判斷，
 * 不是 DTO 註解能表達的規則。
 */
public class ProductTypeScoreBandUpdateRequest {

	@NotBlank(message = "來源模式不可為空")
	private String sourceMode;

	/** 僅 MANUAL 模式使用；HISTORICAL 模式下這個欄位會被忽略。 */
	private BigDecimal lowerBound;

	/** 僅 MANUAL 模式使用；HISTORICAL 模式下這個欄位會被忽略。 */
	private BigDecimal upperBound;

	public String getSourceMode() {
		return sourceMode;
	}

	public void setSourceMode(String sourceMode) {
		this.sourceMode = sourceMode;
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
