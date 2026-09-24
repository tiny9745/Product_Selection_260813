package com.example.Product_Selection_260813.service.campaign;

/**
 * 會影響節慶加成的設定已變更（2026-09-24，方案 2）：天氣同步完成、節慶／季節檔期新增、編輯、
 * 切換狀態、逐年覆寫、地域占比調整。由 FestivalBoostRefreshListener 在交易提交後觸發
 * ScoringService.refreshFestivalBoosts()，讓 product_evaluations 的加成不必等到隔天排程。
 *
 * 用事件而非直接呼叫：發布端（SettingsService、WeatherCampaignSyncService、
 * FestiveCampaignRuleService）不需要依賴 ScoringService，也不會在同一個交易裡重算——
 * 重算失敗不該讓主管的檔期設定跟著回滾。
 *
 * @param reason 觸發原因，只用於 log
 */
public record FestiveCampaignsChangedEvent(String reason) {
}
