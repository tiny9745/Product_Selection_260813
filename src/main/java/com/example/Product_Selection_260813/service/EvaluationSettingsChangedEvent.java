package com.example.Product_Selection_260813.service;

/**
 * 會影響「加權總分」的評分設定已變更（2026-09-24）：切換目前生效模式、調整自訂模式權重、
 * 品類目標區間、自訂計分因子、核心客群、品類設定、演算法參數。
 *
 * 由 EvaluationRecalculationListener 在交易提交後呼叫
 * ScoringService.recalculatePendingEvaluations()，全量重算尚未核准商品的 product_evaluations。
 * 檔期類設定只影響節慶加成，走較輕量的 FestiveCampaignsChangedEvent。
 *
 * @param reason 觸發原因，只用於 log
 */
public record EvaluationSettingsChangedEvent(String reason) {
}
