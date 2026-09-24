package com.example.Product_Selection_260813.service.crawler;

import java.math.BigDecimal;

import com.example.Product_Selection_260813.enums.TrendSignalTrendDirection;

/**
 * 單一商品一次市場熱度查詢的結果，由 TrendService 轉成一筆 trend_signals 紀錄。
 *
 * @param source          寫進 trend_signals.source 的來源標記（PTT／SIMULATED），誠實反映資料實際從哪來
 * @param keyword         實際拿去搜尋的關鍵字
 * @param trendScore      趨勢分數 0~100，50＝近期討論速度與窗口平均相同
 * @param popularityScore 熱度分數 0~100，AiSuggestionBatchService 以 &gt;70 判斷是否標記 AI_SUGGESTED
 * @param trendDirection  趨勢方向，AiSuggestionBatchService 以「連續3筆 UP」判斷
 * @param discussionVolume 統計窗口內的原始討論量（貼文＋推文）；模擬資料沒有這個值，為 null。只供記錄與除錯用，不入庫
 */
public record MarketBuzzSignal(
		String source,
		String keyword,
		BigDecimal trendScore,
		BigDecimal popularityScore,
		TrendSignalTrendDirection trendDirection,
		Integer discussionVolume) {
}
