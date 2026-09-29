-- =====================================================================
-- V34：PTT 新品探索第二階段——適配評分、略過回饋、Google 趨勢交叉驗證
--
-- 【適配評分】AI 分數＋規則判定，刻意不合成加權總分：
--   fit_score           Gemini 依「目前啟用的核心客群＋團購通路條件」給的 0~100 分，附理由與疑慮
--   fit_audience_sig    評分時使用的客群版本（例 "1@3,2@1"＝客群 id@version），客群設定改變就重評
--   temperature_zone    依 product_type_id 解析出的預設溫層（小類→大類繼承）
--   temperature_gate    PASSED／FAILED／INSUFFICIENT_DATA（沿用 GateStatus；FAILED 與資料不足刻意分開）
--
-- 【略過回饋】dismiss_reason_code 讓略過原因可以被程式使用：
--   NOT_A_PRODUCT 的名稱回饋到 AI 抽取階段（不要再抽出類似的），
--   其餘原因回饋到適配評分階段（當作反例）；名稱與已略過項目非常相似的新結果直接排除。
--   V33 之前的略過紀錄沒有代碼，維持 NULL（畫面顯示原文字原因）。
--
-- 【Google 趨勢】沿用 SerpApi 來源與 serpapi_monthly_limit 額度（與商品的 Google 趨勢共用），
--   只查每次適配分最高的前幾項；欄位意義同 google_trend_signals。
-- =====================================================================

ALTER TABLE `discovered_items`
  ADD COLUMN `fit_score`           decimal(5,2) DEFAULT NULL COMMENT 'AI 適配分 0~100；未評分為 NULL' AFTER `buzz_checked_at`,
  ADD COLUMN `fit_reason`          varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'AI 評分理由' AFTER `fit_score`,
  ADD COLUMN `fit_concerns`        varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'AI 列出的疑慮，以「、」分隔' AFTER `fit_reason`,
  ADD COLUMN `fit_audience_sig`    varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '評分時的客群版本 id@version，逗號分隔' AFTER `fit_concerns`,
  ADD COLUMN `fit_model`           varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `fit_audience_sig`,
  ADD COLUMN `fit_evaluated_at`    datetime DEFAULT NULL AFTER `fit_model`,
  ADD COLUMN `temperature_zone`    varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '品類預設溫層（NORMAL/CHILLED/FROZEN），品類未判定為 NULL' AFTER `fit_evaluated_at`,
  ADD COLUMN `temperature_gate`    enum('PASSED','FAILED','INSUFFICIENT_DATA','NOT_APPLICABLE') COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '通路是否支援此溫層' AFTER `temperature_zone`,
  ADD COLUMN `google_status`       enum('OK','NO_DATA') COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '未查為 NULL' AFTER `temperature_gate`,
  ADD COLUMN `google_direction`    enum('UP','DOWN','STABLE') COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `google_status`,
  ADD COLUMN `google_growth_rate`  decimal(8,2) DEFAULT NULL COMMENT '近期相對基準期成長率（%）' AFTER `google_direction`,
  ADD COLUMN `google_checked_at`   datetime DEFAULT NULL AFTER `google_growth_rate`,
  ADD COLUMN `dismiss_reason_code` enum('NOT_A_PRODUCT','NOT_FOR_GROUP_BUY','OUT_OF_SCOPE','SIMILAR_EXISTS','OTHER') COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '略過原因代碼' AFTER `converted_product_id`,
  ADD KEY `idx_discovered_items_status_fit` (`status`, `fit_score`);

ALTER TABLE `discovery_runs`
  ADD COLUMN `similar_dismissed_count` int NOT NULL DEFAULT '0' COMMENT '與已略過項目相似而排除的筆數' AFTER `matched_existing_count`,
  ADD COLUMN `fit_count`               int NOT NULL DEFAULT '0' COMMENT '本次完成適配評分的項目數' AFTER `updated_count`,
  ADD COLUMN `google_count`            int NOT NULL DEFAULT '0' COMMENT '本次查詢 Google 趨勢的項目數' AFTER `fit_count`;
