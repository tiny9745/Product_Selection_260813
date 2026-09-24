package com.example.Product_Selection_260813.enums;

/**
 * 節慶檔期的補假規則（V21）。
 *
 * <ul>
 * <li>NONE：不計算補假日。</li>
 * <li>TW_STATUTORY：依「政府機關配合紀念日與節日補假及調整放假處理要點」第 3 點——
 * 放假日逢星期六者於前一個上班日補假，逢星期日者於次一個上班日補假。
 * 補假日撞到檔期內或其他補假日時，沿用同一方向繼續找（週六往前、週日往後），
 * 2026-09-24 決議。除夕及春節「得」前得後補假屬年度公告選擇，無法用規則推導，
 * 以逐年覆寫表（festive_campaign_occurrence_overrides）處理。</li>
 * </ul>
 */
public enum ObservedHolidayRule {
	NONE,
	TW_STATUTORY
}
