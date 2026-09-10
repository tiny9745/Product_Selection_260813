package com.example.Product_Selection_260813.enums;

/**
 * 目標區間的資料來源模式。
 *
 * 兩者並存、可切換，不是「先建議後確認」的兩階段流程：
 * <ul>
 * <li>{@code HISTORICAL}：由系統從歷史開團紀錄（group_buy_records）算出
 *     建議區間，切換當下計算一次並凍結寫入 lower_bound／upper_bound——
 *     不是每次評分都重新算，否則違反系統的可重現性硬約束。</li>
 * <li>{@code MANUAL}：由主管直接輸入固定數字。切換到這個模式且未提供
 *     新數字時，沿用目前的值，不清空。</li>
 * </ul>
 *
 * 兩種模式在「評分時怎麼被讀取」這件事上完全一樣——{@code ScoreBandResolver}
 * 只讀 lower_bound／upper_bound，不理會 source_mode 是哪一種。這個欄位純粹
 * 是給畫面顯示「這組區間是算出來的還是手動填的」，以及供之後重新計算時判斷
 * 要不要提示「這是歷史模式，要重新抓一次最新資料嗎」。
 */
public enum ScoreBandSourceMode {
	HISTORICAL,
	MANUAL
}
