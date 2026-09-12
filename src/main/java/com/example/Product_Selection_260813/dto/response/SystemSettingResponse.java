package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.Product_Selection_260813.constants.SystemSettingRegistry;

public class SystemSettingResponse {

	private String key;
	private String category;
	private String displayName;
	private String description;
	private String dataType;
	private BigDecimal minValue;
	private BigDecimal maxValue;
	private String unit;

	/**
	 * 目前生效的值——統一用字串表示，跟資料庫欄位型別一致，由前端依
	 * dataType 決定要 parseInt／parseFloat 還是原樣顯示。
	 *
	 * 若資料庫裡沒有這個 key 的紀錄（例如 score_band_min_sample_size
	 * 這類後來才加的參數，從未被手動調整過），這裡回傳登記表裡的預設值，
	 * 讓畫面一開始就能顯示「目前實際生效的數字」，不是空白——因為即使
	 * 資料庫沒有這筆紀錄，AlgorithmSettings 讀取時一樣會 fallback 到
	 * 同一個預設值，兩邊要顯示一致的數字。
	 */
	private String value;

	/** 資料庫裡是否真的有這筆紀錄；false 代表目前顯示的是登記表預設值。 */
	private boolean hasStoredValue;

	private LocalDateTime updatedAt;
	private String updatedByName;

	public static SystemSettingResponse from(SystemSettingRegistry.Metadata meta, String storedValue,
			LocalDateTime updatedAt, String updatedByName) {
		SystemSettingResponse r = new SystemSettingResponse();
		r.key = meta.key();
		r.category = meta.category();
		r.displayName = meta.displayName();
		r.description = meta.description();
		r.dataType = meta.dataType().name();
		r.minValue = meta.minValue();
		r.maxValue = meta.maxValue();
		r.unit = meta.unit();
		r.hasStoredValue = storedValue != null;
		r.value = storedValue != null ? storedValue : meta.defaultValue();
		r.updatedAt = updatedAt;
		r.updatedByName = updatedByName;
		return r;
	}

	public String getKey() { return key; }
	public String getCategory() { return category; }
	public String getDisplayName() { return displayName; }
	public String getDescription() { return description; }
	public String getDataType() { return dataType; }
	public BigDecimal getMinValue() { return minValue; }
	public BigDecimal getMaxValue() { return maxValue; }
	public String getUnit() { return unit; }
	public String getValue() { return value; }
	public boolean isHasStoredValue() { return hasStoredValue; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public String getUpdatedByName() { return updatedByName; }
}
