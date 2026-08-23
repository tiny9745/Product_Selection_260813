package com.example.Product_Selection_260813.dto.response;

import java.math.BigDecimal;

import com.example.Product_Selection_260813.json.MatchedCampaignSnapshot;

/**
 * GET /api/products/{id}/festival-boost：該商品目前命中的檔期、Match Weight、
 * Urgency Factor、Festival Boost、Final Score等可解釋性明細。
 *
 * dataSource與雙軌讀取邏輯說明同EvaluationResponse——festival_boost／final_score
 * 同樣是product_evaluations.final_score欄位規則涵蓋的範圍，APPROVED商品一併
 * 改讀review_records的凍結快照，不是本端點另外自訂的規則。
 *
 * matchedCampaign為null代表未命中任何檔期（Festival Boost=0），前端對應
 * 「未命中檔期時不顯示此區塊」的UI規則。
 */
public class FestivalBoostResponse {

	private String dataSource;

	private MatchedCampaignSnapshot matchedCampaign;

	private BigDecimal festivalBoost;

	private BigDecimal finalScore;

	public String getDataSource() {
		return dataSource;
	}

	public void setDataSource(String dataSource) {
		this.dataSource = dataSource;
	}

	public MatchedCampaignSnapshot getMatchedCampaign() {
		return matchedCampaign;
	}

	public void setMatchedCampaign(MatchedCampaignSnapshot matchedCampaign) {
		this.matchedCampaign = matchedCampaign;
	}

	public BigDecimal getFestivalBoost() {
		return festivalBoost;
	}

	public void setFestivalBoost(BigDecimal festivalBoost) {
		this.festivalBoost = festivalBoost;
	}

	public BigDecimal getFinalScore() {
		return finalScore;
	}

	public void setFinalScore(BigDecimal finalScore) {
		this.finalScore = finalScore;
	}
}
