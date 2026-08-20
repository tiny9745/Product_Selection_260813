package com.example.Product_Selection_260813.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.FestiveCampaignTag;

public interface FestiveCampaignTagRepository extends JpaRepository<FestiveCampaignTag, Long> {

	// 檔期設定頁「標籤＋分級」清單顯示與編輯（SettingsController／SettingsService負責）
	List<FestiveCampaignTag> findByCampaignId(Long campaignId);

	// Festival Boost比對批次查詢：一次取回多個候選檔期(PREPARING/ACTIVE)底下的所有標籤分級，
	// 避免ScoringService對每個候選檔期各自查一次（見ScoringService.buildMatchedCampaignSnapshot()）
	List<FestiveCampaignTag> findByCampaignIdIn(List<Long> campaignIds);
}
