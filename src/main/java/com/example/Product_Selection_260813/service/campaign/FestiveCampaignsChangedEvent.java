package com.example.Product_Selection_260813.service.campaign;

/**
 * 會影響加成的設定已變更（2026-09-24，方案 2）：節慶／季節檔期新增、編輯、切換狀態、逐年覆寫、
 * 地域占比調整；V26 起另含天氣資料同步、天氣加成設定、天氣標籤對照變更。由 FestivalBoostRefreshListener
 * 在交易提交後觸發 ScoringService.refreshFestivalBoosts()（同時重算節慶與天氣加成），
 * 讓 product_evaluations 的加成不必等到隔天排程。
 * 類別名稱沿用既有命名，未改名以免牽動所有發布端。
 *
 * 用事件而非直接呼叫：發布端（SettingsService、WeatherDataSyncService、WeatherBoostService、
 * FestiveCampaignRuleService）不需要依賴 ScoringService，也不會在同一個交易裡重算——
 * 重算失敗不該讓主管的檔期設定跟著回滾。
 *
 * @param reason 觸發原因，只用於 log
 */
public record FestiveCampaignsChangedEvent(String reason) {
}
