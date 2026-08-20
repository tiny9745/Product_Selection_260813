package com.example.Product_Selection_260813.enums;

import java.math.BigDecimal;

/**
 * festive_campaign_tags.match_tier：Festival Boost「Match Weight（標籤命中權重係數）」
 * 對應企劃書「節慶加成計分規則」明訂的三個層級：核心命中1.0／一般命中0.6／弱命中0.3。
 *
 * 這裡直接把matchWeight數值帶在enum建構子裡，與UserRole／ProductReviewStatus等既有
 * enum「顯示名稱由前端自行轉譯、後端不夾帶文案」的原則刻意不同：matchWeight不是UI顯示
 * 文字，是規格書明訂、直接參與Festival Boost計算公式的業務常數，讓ScoringService
 * 直接讀取tier.getMatchWeight()，不需要另外寫一份switch/if映射表，兩處各自維護
 * 反而容易對不齊。
 */
public enum FestiveCampaignTagMatchTier {
	CORE(new BigDecimal("1.0")), //
	GENERAL(new BigDecimal("0.6")), //
	WEAK(new BigDecimal("0.3"));

	private final BigDecimal matchWeight;

	private FestiveCampaignTagMatchTier(BigDecimal matchWeight) {
		this.matchWeight = matchWeight;
	}

	public BigDecimal getMatchWeight() {
		return matchWeight;
	}
}
