package com.example.Product_Selection_260813.enums;

/**
 * 自訂計分因子（factor_definitions）可選用的運算邏輯。
 *
 * 對照既有七個因子的計算邏輯，形狀其實只有五種，但目前只實作了其中三種：
 * <ul>
 * <li>{@link #MANUAL_SCALE}——比照 SUPPLY_STABILITY（人工 1~5 評分 ×固定倍率）</li>
 * <li>{@link #MANUAL_PERCENT}——比照 PURCHASE_RATE（人工估值 ×100）</li>
 * <li>{@link #TARGET_BAND_NORMALIZE}——比照 MARGIN_RATE／DISCOUNT_DEPTH（依品類目標區間正規化）</li>
 * </ul>
 * HISTORY_FULFILLMENT 的巢狀貝氏收縮、TREND_HEAT 的指數衰減這兩種形狀刻意不開放
 * 給自訂因子選用（2026-09-20 與 Gary 確認）：兩者都綁定特定形狀的既有查詢
 * （成團次數/總次數、時間序列訊號），套用到一個全新因子上前，工程師仍需要先接一段
 * 新查詢，不是單純選一個運算邏輯就能通——這跟「不用改程式碼就能新增因子」的目標
 * 衝突，所以維持既有七個因子的寫死用法，不透過這裡開放。
 *
 * AUDIENCE_MATCH 的關鍵字命中率原本也評估過開放（見前一輪的方案比較），但實作
 * 時發現它的輸入形狀是「文字＋關鍵字清單」，跟這裡另外三種「數值→數值轉換」的
 * {@link com.example.Product_Selection_260813.service.scoring.FactorCalculationStrategy}
 * 介面形狀不同，且目前系統只有一份客群關鍵字設定可比對，沒有第二個「既有欄位」
 * 可以真的拿來當新因子的資料源。先不勉強塞進同一個介面，等真的有需求、且能明確
 * 定義「跟誰的關鍵字清單比對」時再設計，避免為了「四種」硬湊一個沒有實際用途的空殼。
 */
public enum FactorStrategyCode {

	/** 人工評分（1~5）× 固定倍率，倍率預設 20，可透過 strategyParams 的 "scale" 覆寫。 */
	MANUAL_SCALE,

	/** 人工估值（0~1 小數）× 固定倍率，倍率預設 100，可透過 strategyParams 的 "scale" 覆寫。 */
	MANUAL_PERCENT,

	/** 依品類的固定目標區間正規化，區間資料沿用既有「設定 > 目標區間」頁面管理。 */
	TARGET_BAND_NORMALIZE;
}
