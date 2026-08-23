package com.example.Product_Selection_260813.common.exception;

/**
 * 系統設定資料異常：伺服器端的設定資料本身不完整或不一致，
 * 導致功能無法正常運作（例如system_settings查無current_evaluation_mode_id、
 * 設定值格式錯誤、指向一個不存在的evaluation_mode等）。
 *
 * <b>為什麼不沿用IllegalStateException：</b>GlobalExceptionHandler把
 * IllegalStateException統一對應成409 Conflict，那是給「資源目前狀態不允許
 * 此操作」用的語意（例如商品已審核通過所以不能再送審、商品類型已被引用所以
 * 不能刪除）——這類情況使用者「換個對象或改變狀態後重試」就能成功。
 *
 * 但本例外代表的是伺服器設定壞掉，使用者端不管怎麼重試、換什麼參數都不可能
 * 成功，必須由維運人員修正資料庫設定才行，語意上屬於500 Internal Server Error。
 * 回409會誤導前端以為「換個做法再試一次就好」，也會讓監控系統把這種需要
 * 人為介入的嚴重問題誤判為正常的業務衝突而不告警。
 */
public class SystemConfigurationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public SystemConfigurationException(String message) {
		super(message);
	}
}
