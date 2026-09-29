package com.example.Product_Selection_260813.dto.response;

import com.example.Product_Selection_260813.enums.GoogleTrendCoverageReason;

/**
 * 商品是否會被 Google 趨勢每週批次查到（2026-09-30）。
 *
 * 品項詳情「尚未查詢」時用來說明實際原因，取代原本一律寫「每週一會自動查詢」——
 * PTT 熱度為 0、沒有 PTT 資料的非待審商品永遠不會被批次查到，舊文字等於承諾了不會發生的事。
 *
 * @param willBeQueried 若現在執行批次，是否會查到這個商品（與批次使用同一份名單計算）
 * @param reason        原因代碼，供前端判斷或測試
 * @param message       給使用者看的說明，由後端產生，與批次規則同一處維護
 */
public record GoogleTrendCoverageResponse(boolean willBeQueried, GoogleTrendCoverageReason reason, String message) {
}
