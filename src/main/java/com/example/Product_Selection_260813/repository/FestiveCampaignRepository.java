package com.example.Product_Selection_260813.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.FestiveCampaign;
import com.example.Product_Selection_260813.enums.FestiveCampaignStatus;

public interface FestiveCampaignRepository extends JpaRepository<FestiveCampaign, Long> {

    Optional<FestiveCampaign> findByCampaignCode(String campaignCode);

    // Festival Boost命中判定範圍：僅比對PREPARING/ACTIVE的檔期，
    // UPCOMING與EXPIRED不參與計算
    List<FestiveCampaign> findByCampaignStatusIn(List<FestiveCampaignStatus> statuses);

    // Daily Cron自動狀態轉換用：is_manual_override=FALSE的檔期才需要自動判斷，
    // TRUE的檔期由Daily Cron跳過
    List<FestiveCampaign> findByIsManualOverrideFalse();
}
