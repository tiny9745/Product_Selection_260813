-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: localhost    Database: product_selection_260813
-- Empty schema (structure only, no data)
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `ai_analyses`
--

DROP TABLE IF EXISTS `ai_analyses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_analyses` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'AI分析結果唯一識別碼',
  `product_id` bigint NOT NULL COMMENT '對應被分析的商品',
  `evaluation_id` bigint DEFAULT NULL COMMENT 'AI分析所依據的評估結果',
  `summary` text COLLATE utf8mb4_unicode_ci COMMENT 'AI產生的商品分析摘要',
  `recommendation` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'AI產生的推薦方向',
  `reasons` text COLLATE utf8mb4_unicode_ci COMMENT 'AI推薦或評論的主要理由（含風險提示，引導使用risk_options.alert_keywords詞彙）',
  `generated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'AI分析產生時間',
  `model_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '使用的AI模型名稱，例如gpt-5.6-luna',
  PRIMARY KEY (`id`),
  KEY `fk_ai_analyses_evaluation` (`evaluation_id`),
  KEY `idx_ai_analyses_product_generated` (`product_id`,`generated_at` DESC),
  CONSTRAINT `fk_ai_analyses_evaluation` FOREIGN KEY (`evaluation_id`) REFERENCES `product_evaluations` (`id`),
  CONSTRAINT `fk_ai_analyses_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI分析結果表：同一商品可能累積多筆記錄，「目前有效版本」規則統一為ORDER BY generated_at DESC LIMIT 1，不新增is_current欄位';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `app_users`
--

DROP TABLE IF EXISTS `app_users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_users` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '使用者唯一識別碼',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登入帳號',
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'BCrypt加密後密碼，絕不存明文',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '顯示名稱',
  `role` enum('PURCHASER','MANAGER') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '使用者角色：PURCHASER=操作／MANAGER=管理，顯示名稱由前端轉譯',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '帳號是否可登入',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_users_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系統使用者表：僅兩種角色，操作(PURCHASER)與管理(MANAGER)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `audience_profiles`
--

DROP TABLE IF EXISTS `audience_profiles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audience_profiles` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '核心客群設定唯一識別碼',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '核心客群名稱',
  `age_min` int DEFAULT NULL COMMENT '核心客群最低年齡',
  `age_max` int DEFAULT NULL COMMENT '核心客群最高年齡',
  `price_sensitivity` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客群價格敏感程度',
  `preference_description` text COLLATE utf8mb4_unicode_ci COMMENT '核心客群消費偏好描述',
  `keywords` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用於商品與客群匹配的關鍵字',
  `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '此客群設定是否使用中（版本切換邏輯本階段不實作，欄位僅預留）',
  `version` int NOT NULL DEFAULT '1' COMMENT '客群設定版本（本階段僅預留，不實作版本切換API）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='核心客群設定表：作為核心客群匹配度計算依據，version/is_active本階段僅預留欄位';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `evaluation_factors`
--

DROP TABLE IF EXISTS `evaluation_factors`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `evaluation_factors` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '評估因子唯一識別碼',
  `evaluation_mode_id` bigint NOT NULL COMMENT '所屬評估模式版本',
  `factor_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '評估因子系統識別代碼',
  `factor_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '評估因子顯示名稱',
  `category` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '大分類：BUSINESS商業條件／AUDIENCE客群匹配／HISTORY歷史銷售／FORECAST預測人氣',
  `weight` decimal(5,2) NOT NULL COMMENT '固定權重（唯讀展示，不提供調整介面）',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '因子說明',
  `sort_order` int DEFAULT NULL COMMENT '前端顯示順序',
  PRIMARY KEY (`id`),
  KEY `idx_evaluation_factors_mode` (`evaluation_mode_id`),
  CONSTRAINT `fk_evaluation_factors_mode` FOREIGN KEY (`evaluation_mode_id`) REFERENCES `evaluation_modes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='評估因子表：每個評估模式版本底下的固定權重明細，唯讀展示於品項詳情頁/審核頁';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `evaluation_modes`
--

DROP TABLE IF EXISTS `evaluation_modes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `evaluation_modes` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '評估模式版本唯一識別碼',
  `mode_code` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '評估模式代碼：BALANCED=均衡／VOLUME=衝量／PROFIT=高利潤',
  `mode_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '評估模式顯示名稱',
  `version` int NOT NULL COMMENT '模式版本，權重修改不覆蓋既有資料而是建新版本',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '模式用途與特性說明',
  `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '此版本是否可使用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_evaluation_modes_code_version` (`mode_code`,`version`),
  KEY `fk_evaluation_modes_created_by` (`created_by`),
  CONSTRAINT `fk_evaluation_modes_created_by` FOREIGN KEY (`created_by`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='評估模式版本表：3套固定模式(均衡/衝量/高利潤)，權重唯讀展示不可調整';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- 系統預設資料：3 套固定評估模式（均衡／衝量／高利潤）
--
-- 這三套模式是系統評分功能運作的必要前提（ScoringService 在商品未指定
-- 專屬模式時會退回讀取 system_settings.current_evaluation_mode_id 指向的
-- 這幾筆之一），不是選配的示範資料，因此直接放進 schema migration，
-- 確保任何全新建立的資料庫在 V1 完成後這三套模式就已經存在。
--
-- 具體的因子權重明細（evaluation_factors）留給 V3 定義，這裡只建立
-- 模式本身的 metadata——因子結構後續會被 V3 整個取代，V1 若同時 seed
-- 因子資料只會在 V3 立刻被刪除重建，沒有必要。
--
INSERT INTO `evaluation_modes` (`mode_code`, `mode_name`, `version`, `description`, `is_active`) VALUES
  ('BALANCED', '均衡模式',   1, '四大分類權重平均分配', 1),
  ('VOLUME',   '衝量模式',   1, '偏重核心客群匹配與預測人氣，適合衝銷量', 1),
  ('PROFIT',   '高利潤模式', 1, '偏重商業條件（毛利率），適合追求利潤', 1);


--
-- Table structure for table `festive_campaign_tags`
--

DROP TABLE IF EXISTS `festive_campaign_tags`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `festive_campaign_tags` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '唯一識別碼',
  `campaign_id` bigint NOT NULL COMMENT '對應檔期(festive_campaigns)',
  `tag` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '標籤內容，例：bbq',
  `match_tier` enum('CORE','GENERAL','WEAK') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '標籤命中權重層級：核心命中(1.0)／一般命中(0.6)／弱命中(0.3)，對應節慶加成計分規則',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_festive_campaign_tags_campaign_tag` (`campaign_id`,`tag`),
  KEY `idx_festive_campaign_tags_tag` (`tag`),
  CONSTRAINT `fk_festive_campaign_tags_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `festive_campaigns` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='檔期標籤命中權重明細表：Match Weight的資料依據，取代原target_tags純文字欄位無法分級的缺口';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `festive_campaigns`
--

DROP TABLE IF EXISTS `festive_campaigns`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `festive_campaigns` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '檔期唯一識別碼',
  `campaign_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '系統識別代碼(例：MIDAUTUMN_2026)',
  `campaign_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '檔期顯示名稱(例：中秋節)',
  `category` enum('FESTIVAL','SEASON') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'FESTIVAL=節慶(固定日期型，如中秋節/雙11)／SEASON=季節(區間型，如夏季換季)',
  `start_date` date NOT NULL COMMENT '檔期開始日期',
  `end_date` date NOT NULL COMMENT '檔期結束日期',
  `preparation_lead_days` int NOT NULL DEFAULT '30' COMMENT '備戰提前天數',
  `campaign_status` enum('UPCOMING','PREPARING','ACTIVE','EXPIRED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UPCOMING' COMMENT '檔期狀態，由Daily Cron依start_date/end_date/preparation_lead_days自動判斷轉換',
  `is_manual_override` tinyint(1) NOT NULL DEFAULT '0' COMMENT '手動覆蓋旗標：TRUE時Daily Cron跳過該筆自動判斷，管理層手動改回FALSE才恢復自動判斷',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_festive_campaigns_code` (`campaign_code`),
  KEY `idx_festive_campaigns_status` (`campaign_status`),
  CONSTRAINT `chk_festive_campaigns_date_range` CHECK ((`end_date` >= `start_date`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='檔期與節慶特徵表：驅動Festival Boost計分，狀態機UPCOMING→PREPARING→ACTIVE→EXPIRED';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product_evaluations`
--

DROP TABLE IF EXISTS `product_evaluations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_evaluations` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商品評估結果唯一識別碼',
  `product_id` bigint NOT NULL COMMENT '對應被評估的商品',
  `evaluation_mode_id` bigint NOT NULL COMMENT '此次評估使用的評估模式版本',
  `business_score` decimal(5,2) DEFAULT NULL COMMENT '商品商業條件分數',
  `audience_score` decimal(5,2) DEFAULT NULL COMMENT '核心客群匹配度分數',
  `historical_score` decimal(5,2) DEFAULT NULL COMMENT '歷史銷售資料分數',
  `purchase_score` decimal(5,2) DEFAULT NULL COMMENT '預估購買分數',
  `trend_score` decimal(5,2) DEFAULT NULL COMMENT '市場趨勢／熱門度分數',
  `forecast_score` decimal(5,2) DEFAULT NULL COMMENT '預測人氣聚合分數＝(purchase_score+trend_score)/2，對應evaluation_factors.category=FORECAST',
  `total_score` decimal(5,2) DEFAULT NULL COMMENT '綜合加權分數（Base Score）',
  `data_completeness` decimal(5,2) DEFAULT NULL COMMENT '資料完整程度，不列入加權，僅顯示用',
  `festival_boost` decimal(5,2) DEFAULT '0.00' COMMENT '節慶加成分數，未命中檔期時為0',
  `matched_campaign_id` bigint DEFAULT NULL COMMENT '命中的檔期ID，未命中為NULL',
  `final_score` decimal(5,2) DEFAULT NULL COMMENT '最終分數＝total_score＋festival_boost，已審核通過商品的此值改讀review_records的Snapshot凍結值',
  `calculated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '評估結果計算時間',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '評估結果最後更新時間',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_evaluations_product_id` (`product_id`),
  KEY `fk_product_evaluations_mode` (`evaluation_mode_id`),
  KEY `fk_product_evaluations_campaign` (`matched_campaign_id`),
  KEY `idx_product_evaluations_product` (`product_id`),
  CONSTRAINT `fk_product_evaluations_campaign` FOREIGN KEY (`matched_campaign_id`) REFERENCES `festive_campaigns` (`id`),
  CONSTRAINT `fk_product_evaluations_mode` FOREIGN KEY (`evaluation_mode_id`) REFERENCES `evaluation_modes` (`id`),
  CONSTRAINT `fk_product_evaluations_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品評估結果表：即時值。APPROVED商品的凍結值改讀review_records的Snapshot欄位（雙軌讀取邏輯）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product_types`
--

DROP TABLE IF EXISTS `product_types`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_types` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商品類型唯一識別碼',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商品類型名稱',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商品類型說明',
  `is_system_default` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否為系統預設商品類型（9類，seed資料寫入）',
  `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否可被新增／編輯品項選擇（條件式刪除失敗時改用此欄位停用）',
  `created_by` bigint DEFAULT NULL COMMENT '建立商品類型的使用者',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_product_types_created_by` (`created_by`),
  CONSTRAINT `fk_product_types_created_by` FOREIGN KEY (`created_by`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品類型表：9類系統預設＋管理層可自訂新增，條件式刪除保護已被使用的分類';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `products`
--

DROP TABLE IF EXISTS `products`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `products` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商品唯一識別碼',
  `product_type_id` bigint NOT NULL COMMENT '對應商品類型(product_types)',
  `pricing_type` enum('NEW','RESALE') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'QA1商品分流：NEW=新品／RESALE=再販售',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商品名稱',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '商品基本說明',
  `image_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商品圖片位置',
  `supplier_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供應商名稱',
  `cost_price` decimal(10,2) DEFAULT NULL COMMENT '商品成本價格。新品(NEW)可為空，待議價完成後回填【QA1】',
  `sale_price` decimal(10,2) DEFAULT NULL COMMENT '預計銷售價格。新品(NEW)可為空【QA1】',
  `market_price` decimal(10,2) DEFAULT NULL COMMENT '市售價格（消費者一般通路購買價）【十四-1新增】。僅RESALE商品填寫，NEW商品不適用；不進評分公式，僅供品項詳情頁「市價與團購價比較」呈現折扣率(QA2)',
  `campaign_tags` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '節慶標籤，逗號分隔字串(例："bbq,gift")，與festive_campaign_tags.tag做交集比對',
  `moq` int DEFAULT NULL COMMENT '最低訂購量',
  `supply_stability` decimal(5,2) DEFAULT NULL COMMENT '供應穩定性評估資料',
  `price_competitiveness` decimal(5,2) DEFAULT NULL COMMENT '商品價格競爭力評估資料',
  `target_customer_description` text COLLATE utf8mb4_unicode_ci COMMENT '商品適合客群的補充描述',
  `estimated_purchase_rate` decimal(5,2) DEFAULT NULL COMMENT '預估消費者購買商品的可能性',
  `review_status` enum('PENDING','REJECTED','APPROVED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '審核狀態：PENDING未審核／REJECTED已審核拒絕／APPROVED已審核通過(不代表已銷售，見系統定位)',
  `candidate_status` enum('AI_SUGGESTED','CANDIDATE') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CANDIDATE' COMMENT 'QA4候選狀態：AI_SUGGESTED=AI建議(非正式候選)／CANDIDATE=正式候選(進入評分排行)。手動新增品項預設CANDIDATE',
  `pricing_status` enum('PENDING_PRICING','PRICED') COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'QA1訂價狀態，僅NEW商品有值，RESALE固定留空(NULL)。PENDING_PRICING=待訂價／PRICED=已完成議價定價，Service層於PUT時檢查cost_price與sale_price皆非NULL即自動轉為PRICED',
  `item_status` enum('ACTIVE','ARCHIVED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '品項是否啟用：ACTIVE使用中／ARCHIVED已封存',
  `submission_count` int NOT NULL DEFAULT '0' COMMENT '第幾次送審',
  `created_by` bigint DEFAULT NULL COMMENT '建立商品的使用者',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL COMMENT '最後修改商品的使用者',
  PRIMARY KEY (`id`),
  KEY `fk_products_created_by` (`created_by`),
  KEY `fk_products_updated_by` (`updated_by`),
  KEY `idx_products_review_status` (`review_status`),
  KEY `idx_products_item_status` (`item_status`),
  KEY `idx_products_candidate_status` (`candidate_status`),
  KEY `idx_products_product_type` (`product_type_id`),
  CONSTRAINT `fk_products_created_by` FOREIGN KEY (`created_by`) REFERENCES `app_users` (`id`),
  CONSTRAINT `fk_products_product_type` FOREIGN KEY (`product_type_id`) REFERENCES `product_types` (`id`),
  CONSTRAINT `fk_products_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品資料表：系統核心表，pricing_type/candidate_status/review_status/item_status四組狀態欄位語意各自獨立，開發與查詢時不可混淆';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `review_records`
--

DROP TABLE IF EXISTS `review_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `review_records` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '審核紀錄唯一識別碼',
  `product_id` bigint NOT NULL COMMENT '對應被審核的商品',
  `reviewer_id` bigint NOT NULL COMMENT '實際執行審核的管理人員',
  `submission_count` int NOT NULL COMMENT '第幾次送審',
  `review_status` enum('APPROVED','REJECTED') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '本次審核結果',
  `reviewed_at` datetime NOT NULL COMMENT '審核完成時間',
  `evaluation_mode_id` bigint DEFAULT NULL COMMENT '當時使用的評估模式版本',
  `evaluation_mode_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '評估模式名稱快照',
  `evaluation_mode_version` int DEFAULT NULL COMMENT '評估模式版本快照',
  `total_score` decimal(5,2) DEFAULT NULL COMMENT '當時的綜合加權分數(Base Score)快照',
  `festival_boost_snapshot` decimal(5,2) DEFAULT NULL COMMENT '當時的節慶加成分數快照【新增】',
  `matched_campaign_snapshot` json DEFAULT NULL COMMENT '當時命中的檔期資訊快照【新增】，例：{"campaign_id":12,"campaign_name":"中秋節","matched_tags":["bbq"],"match_weight":1.0,"urgency_factor":0.85}',
  `final_score_snapshot` decimal(5,2) DEFAULT NULL COMMENT '當時的最終分數快照【新增】',
  `data_completeness` decimal(5,2) DEFAULT NULL COMMENT '當時的資料完整度快照',
  `business_score` decimal(5,2) DEFAULT NULL COMMENT '當時商品商業條件分數快照',
  `audience_score` decimal(5,2) DEFAULT NULL COMMENT '當時核心客群匹配度分數快照',
  `historical_score` decimal(5,2) DEFAULT NULL COMMENT '當時歷史銷售資料分數快照',
  `purchase_score` decimal(5,2) DEFAULT NULL COMMENT '當時預估購買分數快照',
  `trend_score` decimal(5,2) DEFAULT NULL COMMENT '當時市場趨勢分數快照',
  `forecast_score` decimal(5,2) DEFAULT NULL COMMENT '當時預測人氣聚合分數快照【新增】',
  `weight_snapshot` json DEFAULT NULL COMMENT '本次審核當下使用的完整固定權重',
  `product_snapshot` json DEFAULT NULL COMMENT '本次審核當下的商品核心資料，例：{"name":"...","pricing_type":"RESALE","cost_price":80,"sale_price":120,"campaign_tags":"bbq,gift","moq":50,"supply_stability":4.2,"price_competitiveness":3.8,"target_customer_description":"...","estimated_purchase_rate":0.65}',
  `ai_summary_snapshot` text COLLATE utf8mb4_unicode_ci COMMENT '本次審核時管理所看到的AI摘要',
  `trend_snapshot` json DEFAULT NULL COMMENT '本次審核時使用的趨勢資料',
  `review_comment` text COLLATE utf8mb4_unicode_ci COMMENT '管理本次審核的補充意見與決策依據，用途邊界：記錄選品判斷依據，非銷售條款或議價條件',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_review_records_reviewer` (`reviewer_id`),
  KEY `fk_review_records_mode` (`evaluation_mode_id`),
  KEY `idx_review_records_product` (`product_id`),
  CONSTRAINT `fk_review_records_mode` FOREIGN KEY (`evaluation_mode_id`) REFERENCES `evaluation_modes` (`id`),
  CONSTRAINT `fk_review_records_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `fk_review_records_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='審核紀錄表：每次送審的完整快照（含節慶加成快照），是決策紀錄列表與APPROVED商品Final Score凍結的資料來源，不可覆蓋既有紀錄';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `review_risks`
--

DROP TABLE IF EXISTS `review_risks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `review_risks` (
  `review_id` bigint NOT NULL COMMENT '對應一次審核紀錄',
  `risk_option_id` bigint NOT NULL COMMENT '對應本次審核所選擇的人工風險',
  PRIMARY KEY (`review_id`,`risk_option_id`),
  KEY `fk_review_risks_risk_option` (`risk_option_id`),
  CONSTRAINT `fk_review_risks_review` FOREIGN KEY (`review_id`) REFERENCES `review_records` (`id`),
  CONSTRAINT `fk_review_risks_risk_option` FOREIGN KEY (`risk_option_id`) REFERENCES `risk_options` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='review_records與risk_options的多對多關聯表，承接審核時「人工風險評估（複選）」的勾選結果';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `risk_options`
--

DROP TABLE IF EXISTS `risk_options`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `risk_options` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '人工風險選項唯一識別碼',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '風險類型名稱',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '風險類型說明',
  `alert_keywords` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'AI風險提示關鍵字引導詞【新增】。寫入AI Prompt引導其用詞，非系統事後隨機比對；系統對ai_analyses文字內容做字串比對取用AI已做出的判斷結果',
  `is_system_default` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否為系統預設風險',
  `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '目前是否可被管理選擇',
  `created_by` bigint DEFAULT NULL COMMENT '建立風險選項的使用者',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_risk_options_created_by` (`created_by`),
  CONSTRAINT `fk_risk_options_created_by` FOREIGN KEY (`created_by`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人工風險選項表：本階段僅有讀取(GET)API，新增/調整一律採直接改DB seed script（QA5已知限制）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `system_settings`
--

DROP TABLE IF EXISTS `system_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_settings` (
  `setting_key` varchar(100) NOT NULL,
  `setting_value` varchar(255) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`setting_key`),
  KEY `fk_system_settings_updated_by` (`updated_by`),
  CONSTRAINT `fk_system_settings_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `trend_signals`
--

DROP TABLE IF EXISTS `trend_signals`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trend_signals` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '趨勢資料唯一識別碼',
  `product_id` bigint NOT NULL COMMENT '對應商品',
  `source` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '趨勢資料來源(例：GOOGLE_TRENDS)',
  `keyword` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '實際查詢的趨勢關鍵字',
  `trend_score` decimal(5,2) DEFAULT NULL COMMENT '市場趨勢分數',
  `popularity_score` decimal(5,2) DEFAULT NULL COMMENT '市場熱門度分數',
  `trend_direction` enum('UP','DOWN','STABLE') COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '市場趨勢方向',
  `collected_at` datetime NOT NULL COMMENT '實際取得外部趨勢資料的時間',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '資料寫入系統的時間',
  PRIMARY KEY (`id`),
  KEY `idx_trend_signals_product` (`product_id`),
  CONSTRAINT `fk_trend_signals_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='趨勢資料表：六週雛型版本使用模擬市場資料集，僅保留單一來源欄位，不分Google Trends/YouTube/IG/PTT各自獨立表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Empty schema dump complete
