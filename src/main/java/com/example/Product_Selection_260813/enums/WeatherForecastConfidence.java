package com.example.Product_Selection_260813.enums;

import java.math.BigDecimal;

/**
 * 天氣預報可信度分級，依預測距離現在的天數決定（規劃文件第13、21節）。
 *
 * 沿用 FestiveCampaignTagMatchTier「業務常數直接帶在 enum 建構子裡」的既有慣例：
 * confidenceFactor 不是 UI 顯示文字，是直接參與 ScoringService.calculateUrgencyFactor()
 * 計算公式的業務常數，讓呼叫端直接讀 tier.getConfidenceFactor()，不需要另外寫一份
 * switch/if 對照表。
 *
 * 分級門檻（天數）由 WeatherSignalProvider 的實作決定，這裡只定義「分幾級、
 * 每一級的加成係數是多少」，門檻本身不寫死在 enum 裡，避免之後要調整
 * 「幾天算短期／中期／長期」時得改到這個檔案。
 */
public enum WeatherForecastConfidence {
	HIGH(new BigDecimal("1.0")),   // 0～7天：短期預報
	MEDIUM(new BigDecimal("0.7")), // 8～14天：中期預報
	LOW(new BigDecimal("0.4"));    // 15天以上：長期趨勢，非逐日預報

	private final BigDecimal confidenceFactor;

	private WeatherForecastConfidence(BigDecimal confidenceFactor) {
		this.confidenceFactor = confidenceFactor;
	}

	public BigDecimal getConfidenceFactor() {
		return confidenceFactor;
	}
}
