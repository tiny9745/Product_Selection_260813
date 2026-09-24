-- =====================================================================
-- V22：submission_count 改為 1 起算（2026-09-24，送審次數 off-by-one 修正）
--
-- 【背景】
-- 建立商品即代表已確認送審（見 ProductCreateRequest 類別註解），但 products.submission_count
-- 原本預設 0，只有 resubmit()（REJECTED→PENDING）才 +1。結果：
--   1. 第一次審核時凍結進 review_records.submission_count 的是 0，畫面顯示「第 0 次送審」；
--   2. 送審轉換率分母 countBySubmissionCountGreaterThan(0) 只算得到「曾重送過」的商品，
--      出現「送審過 84 件、通過 70＋拒絕 15」的矛盾。
-- Java 端 Product.submissionCount 預設值同步改為 1；刪除條件改為 PENDING 且 submission_count=1。
--
-- 【為什麼也改 DB 欄位預設值】
-- JPA 新增時一律帶入 Entity 的值，DB 預設值平常用不到；但手動匯入的測試資料若省略這個欄位，
-- 會拿到 DB 預設值 0 而重現同一個 bug，所以兩邊一致改為 1。
--
-- 【資料回補】
-- 部署流程為 DROP DATABASE＋CREATE DATABASE，新環境執行時兩條 UPDATE 影響 0 筆，無害；
-- 保留是為了未重建的環境（開發機）。WHERE submission_count = 0 限定範圍，已重送過
-- （>=1）的資料不受影響。
-- review_records 是審核快照（原則上不可覆寫），這裡是修正 bug 造成的錯誤序數，不是改寫
-- 審核決策內容；只動 submission_count 一欄，其餘快照欄位不變（2026-09-24 決議）。
-- =====================================================================

ALTER TABLE `products`
  MODIFY COLUMN `submission_count` int NOT NULL DEFAULT '1' COMMENT '第幾次送審（1 起算：建立商品即第 1 次送審，resubmit 時 +1）';

UPDATE `products` SET `submission_count` = 1 WHERE `submission_count` = 0;

UPDATE `review_records` SET `submission_count` = 1 WHERE `submission_count` = 0;
