-- =====================================================================
-- V37：商品「搜尋關鍵字」（2026-09-30）
--
-- 問題：PTT 熱度同步與 Google 趨勢原本只用規則簡化商品名稱當關鍵字（TrendService.toSearchKeyword()，
-- 只去掉「數字＋單位」與括號內容），「麻豆文旦 10台斤禮盒」「直播補光環燈組」這類名稱會整串送去搜尋，
-- PTT 幾乎搜不到文章 → 熱度 0 → 市場趨勢因子被壓低，但原因是關鍵字，不是市場沒興趣。
--
-- 做法：
--   * 新增 products.search_keyword（選填）。有值時 PTT 熱度同步與 Google 趨勢優先使用；
--     NULL 時沿用原本的規則簡化，既有商品行為不變。
--   * 從 AI 商品雷達建立的商品，關鍵字自動帶入雷達 AI 抽出的 discovered_items.search_keyword。
--   * 回填：已經從 AI 商品雷達建立過的商品，補上當時的關鍵字，下一次熱度同步就會生效。
--
-- 長度與 discovered_items.search_keyword 一致（varchar(100)）。
-- 回填時 updated_at = updated_at：明確保留原值，避免 ON UPDATE CURRENT_TIMESTAMP 把「最後更新時間」
-- 改成 migration 執行時間（使用者並沒有編輯這些商品）。
-- =====================================================================

ALTER TABLE `products`
  ADD COLUMN `search_keyword` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT 'PTT 熱度同步與 Google 趨勢使用的搜尋關鍵字；NULL 時由商品名稱自動簡化'
    AFTER `name`;

UPDATE `products` p
  JOIN `discovered_items` d ON d.`converted_product_id` = p.`id`
   SET p.`search_keyword` = d.`search_keyword`,
       p.`updated_at` = p.`updated_at`
 WHERE p.`search_keyword` IS NULL
   AND d.`search_keyword` <> '';
