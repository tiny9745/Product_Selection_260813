-- =====================================================================
-- V23：PTT 熱度全商品同步的執行紀錄（系統設定「爬蟲排程控制」區塊使用）
--
-- 每次「全商品同步」寫一列：每日 02:00 排程（SCHEDULED）或管理層在系統設定
-- 按「立即同步全部商品」（MANUAL）。單一商品的「立即更新」不寫這張表——那是
-- 使用者當下就看得到結果的單筆操作，trend_signals 本身已經留有紀錄。
--
-- 執行中先寫入 status=RUNNING，結束後回填 finished_at 與各項筆數；應用程式在
-- 執行途中被關掉時，這一列會停在 RUNNING，下次啟動時由
-- TrendSyncRunService 改成 FAILED（見該類別說明），不會永遠顯示「執行中」。
--
-- PTT 來源開關存在 system_settings（key = ptt_crawler_enabled），不在這張表。
-- =====================================================================

CREATE TABLE `trend_sync_runs` (
  `id`             bigint NOT NULL AUTO_INCREMENT,
  `trigger_type`   enum('SCHEDULED','MANUAL') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SCHEDULED=每日02:00排程；MANUAL=管理層手動觸發',
  `status`         enum('RUNNING','COMPLETED','FAILED','SKIPPED') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SKIPPED=PTT來源停用中，排程未執行',
  `started_at`     datetime NOT NULL,
  `finished_at`    datetime DEFAULT NULL,
  `total_count`    int NOT NULL DEFAULT '0' COMMENT '本次要同步的未封存商品數',
  `real_count`     int NOT NULL DEFAULT '0' COMMENT '成功取得 PTT 真實資料的商品數',
  `fallback_count` int NOT NULL DEFAULT '0' COMMENT 'PTT 取不到、改用模擬資料的商品數',
  `failed_count`   int NOT NULL DEFAULT '0' COMMENT '同步失敗（連模擬資料都沒寫入）的商品數',
  `message`        varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '補充說明，例如略過原因或中斷原因',
  `triggered_by`   bigint DEFAULT NULL COMMENT '手動觸發的管理者；排程觸發為NULL',
  PRIMARY KEY (`id`),
  KEY `idx_trend_sync_runs_started_at` (`started_at`),
  KEY `fk_trend_sync_runs_triggered_by` (`triggered_by`),
  CONSTRAINT `fk_trend_sync_runs_triggered_by` FOREIGN KEY (`triggered_by`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='PTT 熱度全商品同步的執行紀錄';
