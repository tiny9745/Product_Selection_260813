-- =====================================================================
-- V25：審核通過商品 CSV 匯出（2026-09 採購儀表板規格 Part B）
--
-- 1. products.submitted_at / submitted_by：真正的「送審時間／送審人」
--    原本系統沒有這兩個欄位，審核頁的「送審時間」是拿 updated_at 代替，
--    任何一次編輯都會改變它。這裡補上後：
--      - 建立商品（含批次新增）＝第一次送審，寫入建立者與當下時間；
--      - 重新送審（REJECTED -> PENDING）寫入重送的人與當下時間；
--      - 其他編輯、審核、匯出都不會動到這兩欄。
--    「送審批次」不另存編號，而是在查詢時由（送審人, 送審日期）推導：
--    同一人、同一個曆日送出的商品就是同一批，批次新增與逐筆新增自然一致。
--
-- 2. product_export_logs：每一次匯出、每一件商品各一列
--    刻意不在 products 加 exported_at：products.updated_at 有
--    ON UPDATE CURRENT_TIMESTAMP（Entity 也有 @UpdateTimestamp），一寫入就會把
--    所有被匯出商品的「最後修改時間」改成匯出當下，污染品項管理的修改時間篩選。
--    獨立成表另一個好處是保留完整稽核軌跡（誰、何時、哪一次匯出了哪些商品）。
--    「未曾匯出」＝ NOT EXISTS 此表的任何一列。
-- =====================================================================

ALTER TABLE `products`
  ADD COLUMN `submitted_at` datetime DEFAULT NULL
      COMMENT '最近一次送審時間（建立＝第一次送審；重新送審時更新）。與 updated_at 不同，編輯不會改變它'
      AFTER `submission_count`,
  ADD COLUMN `submitted_by` bigint DEFAULT NULL
      COMMENT '最近一次送審的使用者；送審批次＝(submitted_by, DATE(submitted_at))'
      AFTER `submitted_at`,
  ADD KEY `idx_products_submission_batch` (`submitted_by`, `submitted_at`),
  ADD CONSTRAINT `fk_products_submitted_by` FOREIGN KEY (`submitted_by`) REFERENCES `app_users` (`id`);

-- 既有資料回填：submission_count = 1 代表只送審過一次，建立當下就是送審時間，
-- 可以準確回填；重新送審過（>= 2）的商品無法得知最後一次重送的時間與人，
-- 維持 NULL，畫面上歸在「（無批次資料）」，不做推測。
-- created_by 為 NULL 的資料同樣不回填：兩欄必須成對，否則會落在批次下拉的兩邊都不是。
-- `updated_at` = `updated_at`：明確指定為原值，避免 ON UPDATE CURRENT_TIMESTAMP
-- 把所有商品的修改時間改成 migration 執行當下。
UPDATE `products`
   SET `submitted_at` = `created_at`,
       `submitted_by` = `created_by`,
       `updated_at` = `updated_at`
 WHERE `submission_count` = 1
   AND `created_by` IS NOT NULL;

CREATE TABLE `product_export_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '匯出紀錄唯一識別碼',
  `export_run_id` char(36) NOT NULL COMMENT '同一次匯出操作的所有商品共用同一個 UUID',
  `product_id` bigint NOT NULL COMMENT '被匯出的商品',
  `exported_at` datetime NOT NULL COMMENT '匯出時間（同一次匯出的每一列相同）',
  `exported_by` bigint NOT NULL COMMENT '執行匯出的使用者',
  PRIMARY KEY (`id`),
  KEY `idx_product_export_logs_product` (`product_id`),
  KEY `idx_product_export_logs_run` (`export_run_id`),
  CONSTRAINT `fk_product_export_logs_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `fk_product_export_logs_exported_by` FOREIGN KEY (`exported_by`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='審核通過商品 CSV 匯出紀錄：每次匯出、每件商品一列，僅新增不修改；「未曾匯出」判斷依據';
