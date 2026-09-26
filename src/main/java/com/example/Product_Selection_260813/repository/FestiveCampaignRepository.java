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

    // 2026-09-24（V21，決議 D10）：節慶／季節型的狀態改為即時推算，計分改用這兩支取候選：
    // 節慶／季節全取（狀態由 CampaignOccurrenceResolver 推算後再篩），天氣型仍依資料表狀態篩選。
    // findByCampaignStatusIn 保留不刪，避免影響其他可能的呼叫端。
    List<FestiveCampaign> findByCategoryIn(List<FestiveCategory> categories);

    // Daily Cron自動狀態轉換用：is_manual_override=FALSE的檔期才需要自動判斷，
    // TRUE的檔期由Daily Cron跳過
    List<FestiveCampaign> findByIsManualOverrideFalse();

    // V26：天氣檔期移除，原本只給天氣檔期同步／清單用的兩支查詢一併移除。
}
