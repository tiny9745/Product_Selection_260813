-- =====================================================================
-- V35：PTT 新品探索——已處理標題紀錄
--
-- 每天排程會掃近 N 天（discovery.recent-days，預設 7）的文章，其中大部分標題前一天
-- 已經送過 Gemini。這張表記錄「已成功送過 AI 且結果已寫入」的標題，下次只送新標題，
-- 減少 AI 呼叫量（也減少遇到 Gemini 過載時的損失）。
--
-- title_key：SHA-256(DiscoveryText.normalize(DiscoveryText.cleanTitle(標題)))，固定 64 字元。
-- 清空此表＝下次探索重新處理所有標題（改 prompt 或想重新掃描時使用）。
-- =====================================================================

CREATE TABLE `discovery_processed_titles` (
  `title_key`    char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `processed_at` datetime(6) NOT NULL,
  PRIMARY KEY (`title_key`),
  KEY `idx_discovery_processed_titles_processed_at` (`processed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
