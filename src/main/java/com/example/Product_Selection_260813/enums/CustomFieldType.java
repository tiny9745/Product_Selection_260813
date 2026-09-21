package com.example.Product_Selection_260813.enums;

/**
 * 自訂商品屬性（動態問卷）的欄位型態。
 *
 * 前三種是數值類，形狀對得上既有計分策略（見 FactorCalculationStrategy）：
 * SCALE_1_5 對應 MANUAL_SCALE，PERCENT_0_1 對應 MANUAL_PERCENT，RAW_NUMBER
 * 對應 TARGET_BAND_NORMALIZE。這個對應關係之後接回計分系統
 * （FactorDataSource 新增「讀動態問卷答案」的資料源類型）時會用到。
 *
 * TEXT 是純文字欄位，不參與計分——2026-09-20 與 Gary 確認「如果加上可寫入
 * 文字＋不列入評分難度不會太高則加入」，這裡就是那個型態。計分系統挑選
 * 資料源時應該只列出前三種，過濾掉 TEXT。
 */
public enum CustomFieldType {

	/** 1~5 人工評分，商品表單畫成星等或 1~5 選擇器。 */
	SCALE_1_5,

	/** 0~1 小數人工估值，商品表單畫成百分比輸入框。 */
	PERCENT_0_1,

	/** 不限範圍的原始數字，商品表單畫成一般數字輸入框。 */
	RAW_NUMBER,

	/** 純文字，不參與計分，商品表單畫成文字輸入框。 */
	TEXT;

	/** 計分系統只認得數值類型態；TEXT 不該出現在資料源候選清單裡。 */
	public boolean isNumeric() {
		return this != TEXT;
	}
}
