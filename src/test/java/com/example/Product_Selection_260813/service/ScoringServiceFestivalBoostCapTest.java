package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.example.Product_Selection_260813.json.MatchedCampaignSnapshot;

/**
 * 2026-09-29：節慶加成上限改為可調（system_settings.festival_boost_cap）。
 * calculateFestivalBoost() 讀快照記錄的上限，舊快照（boostCap 為 null）沿用當時寫死的 5，
 * 同一份審核快照不論何時重算，結果都一樣。純函式，不需要任何依賴。
 */
class ScoringServiceFestivalBoostCapTest {

	private static MatchedCampaignSnapshot snapshot(String matchWeight, String urgencyFactor, String boostCap) {
		MatchedCampaignSnapshot snapshot = new MatchedCampaignSnapshot();
		snapshot.setMatchWeight(new BigDecimal(matchWeight));
		snapshot.setUrgencyFactor(new BigDecimal(urgencyFactor));
		snapshot.setBoostCap(boostCap == null ? null : new BigDecimal(boostCap));
		return snapshot;
	}

	@Test
	void 快照帶有加成上限時依快照計算() {
		assertThat(ScoringService.calculateFestivalBoost(snapshot("0.6", "0.5", "8"))).isEqualByComparingTo("2.40");
	}

	@Test
	void 舊快照沒有加成上限時沿用5分() {
		assertThat(ScoringService.calculateFestivalBoost(snapshot("0.6", "0.5", null))).isEqualByComparingTo("1.50");
	}

	@Test
	void 上限設為0時等於關閉節慶加成() {
		assertThat(ScoringService.calculateFestivalBoost(snapshot("1.0", "1.0", "0"))).isEqualByComparingTo("0");
	}
}
