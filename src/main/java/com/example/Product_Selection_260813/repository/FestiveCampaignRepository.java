package com.example.Product_Selection_260813.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    List<FestiveCampaign> findByCategoryAndCampaignStatusIn(FestiveCategory category,
            List<FestiveCampaignStatus> statuses);

    // Daily Cron自動狀態轉換用：is_manual_override=FALSE的檔期才需要自動判斷，
    // TRUE的檔期由Daily Cron跳過
    List<FestiveCampaign> findByIsManualOverrideFalse();

    // WeatherCampaignSyncService 用：找出「這次同步沒有再被回報、但先前
    // 還在 PREPARING/ACTIVE 的天氣檔期」，用來汰除已經不成立的天氣預測
    // （例如原本預報有雨、最新一次預報改成放晴）。is_manual_override=TRUE
    // 的排除在外，跟 findByIsManualOverrideFalse() 同樣的理由。
    List<FestiveCampaign> findByCategoryAndCampaignStatusInAndIsManualOverrideFalse(
            FestiveCategory category, List<FestiveCampaignStatus> statuses);

    // 2026-09-24「天氣連動 › 目前的天氣檔期」用：指定狀態的檔期，加上所有手動覆蓋中的檔期。
    // 手動覆蓋的列不論狀態都要列出（含被主管設成 EXPIRED 的），否則主管無法在畫面上「恢復自動判斷」。
    // 天氣檔期每次同步可能依窗口起始日新增一列，歷史列會持續累積，所以在 DB 端過濾，不整表撈回再篩。
    @Query("SELECT c FROM FestiveCampaign c WHERE c.category = :category"
            + " AND (c.campaignStatus IN :statuses OR c.isManualOverride = true)"
            + " ORDER BY c.startDate ASC, c.id ASC")
    List<FestiveCampaign> findCurrentOrManualByCategory(@Param("category") FestiveCategory category,
            @Param("statuses") List<FestiveCampaignStatus> statuses);
}
