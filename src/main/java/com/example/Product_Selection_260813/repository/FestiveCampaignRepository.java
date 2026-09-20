package com.example.Product_Selection_260813.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.FestiveCampaign;
import com.example.Product_Selection_260813.enums.FestiveCampaignStatus;
import com.example.Product_Selection_260813.enums.FestiveCategory;

public interface FestiveCampaignRepository extends JpaRepository<FestiveCampaign, Long> {

    Optional<FestiveCampaign> findByCampaignCode(String campaignCode);

    // Festival Boost命中判定範圍：僅比對PREPARING/ACTIVE的檔期，
    // UPCOMING與EXPIRED不參與計算
    List<FestiveCampaign> findByCampaignStatusIn(List<FestiveCampaignStatus> statuses);

    // Daily Cron自動狀態轉換用：is_manual_override=FALSE的檔期才需要自動判斷，
    // TRUE的檔期由Daily Cron跳過
    List<FestiveCampaign> findByIsManualOverrideFalse();

    // WeatherCampaignSyncService 用：找出「這次同步沒有再被回報、但先前
    // 還在 PREPARING/ACTIVE 的天氣檔期」，用來汰除已經不成立的天氣預測
    // （例如原本預報有雨、最新一次預報改成放晴）。is_manual_override=TRUE
    // 的排除在外，跟 findByIsManualOverrideFalse() 同樣的理由。
    List<FestiveCampaign> findByCategoryAndCampaignStatusInAndIsManualOverrideFalse(
            FestiveCategory category, List<FestiveCampaignStatus> statuses);
}
