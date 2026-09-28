-- =====================================================================
-- V30：Google 趨勢（透過 SerpApi）第二資料源——獨立參考資訊，不併入熱度分數
--
-- 刻意另開資料表，不寫進 trend_signals：
--   1. trend_signals 的查詢（單商品最新趨勢、熱度排行榜、AI 建議批次、Gemini prompt）
--      都是「每商品取 collected_at 最新一筆」、不分來源。Google 資料寫進去會被當成
--      最新一筆，而它沒有熱度分數，會讓上述功能顯示錯誤。
--   2. Google Trends 的 0～100 是「相對於該次查詢峰值」的縮放，不同商品之間不可比，
--      不能跟 PTT 討論量換算出的熱度分數相加或平均；只取同一條序列內部的方向與成長率。
--
-- 每次查詢寫一列（包括查無資料 NO_DATA），不覆寫舊資料；呼叫失敗（網路、額度、
-- API 錯誤）不寫這張表，只記在執行紀錄的失敗筆數。
--
-- 開關與額度存在 system_settings（不登記在演算法參數畫面，見 GoogleTrendSettings）：
--   google_trends_enabled（未設定＝停用）
--   serpapi_monthly_limit（未設定＝200；SerpApi 免費方案每月 250 次，留 50 次給手動查詢）
--   serpapi_calls_YYYY-MM（每月呼叫計數，比照 gemini_calls_YYYY-MM）
-- =====================================================================

CREATE TABLE `google_trend_signals` (
  `id`            bigint NOT NULL AUTO_INCREMENT,
  `product_id`    bigint NOT NULL,
  `keyword`       varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '實際送出查詢的關鍵字',
  `status`        enum('OK','NO_DATA') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'NO_DATA=Google 搜尋量不足，無法判斷方向',
  `direction`     enum('UP','DOWN','STABLE') COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'NO_DATA 時為 NULL',
  `growth_rate`   decimal(8,2) DEFAULT NULL COMMENT '近期平均相對基準期平均的成長率（%）；基準期為 0 時為 NULL',
  `recent_avg`    decimal(6,2) DEFAULT NULL COMMENT '近期（最近 7 個完整資料點）平均，0~100 相對值',
  `baseline_avg`  decimal(6,2) DEFAULT NULL COMMENT '基準期（再往前 28 個資料點）平均，0~100 相對值',
  `point_count`   int NOT NULL DEFAULT '0' COMMENT '有效資料點數（不含 Google 標示為未完整的最後一點）',
  `collected_at`  datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_google_trend_signals_product_collected` (`product_id`, `collected_at`),
  CONSTRAINT `fk_google_trend_signals_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Google 趨勢參考資訊（SerpApi），不併入熱度分數';

CREATE TABLE `google_trend_runs` (
  `id`             bigint NOT NULL AUTO_INCREMENT,
  `trigger_type`   enum('SCHEDULED','MANUAL') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SCHEDULED=每週一 04:00 排程；MANUAL=管理層手動觸發',
  `status`         enum('RUNNING','COMPLETED','FAILED','SKIPPED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `started_at`     datetime NOT NULL,
  `finished_at`    datetime DEFAULT NULL,
  `total_count`    int NOT NULL DEFAULT '0' COMMENT '本次要查詢的商品數（PTT 熱度前 N 名，且不超過本月剩餘額度）',
  `ok_count`       int NOT NULL DEFAULT '0' COMMENT '取得趨勢序列的商品數',
  `no_data_count`  int NOT NULL DEFAULT '0' COMMENT 'Google 搜尋量不足的商品數',
  `failed_count`   int NOT NULL DEFAULT '0' COMMENT '呼叫失敗的商品數',
  `message`        varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `triggered_by`   bigint DEFAULT NULL COMMENT '手動觸發的管理者；排程觸發為NULL',
  PRIMARY KEY (`id`),
  KEY `idx_google_trend_runs_started_at` (`started_at`),
  KEY `fk_google_trend_runs_triggered_by` (`triggered_by`),
  CONSTRAINT `fk_google_trend_runs_triggered_by` FOREIGN KEY (`triggered_by`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Google 趨勢批次查詢的執行紀錄';
