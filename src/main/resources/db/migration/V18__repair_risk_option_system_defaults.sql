-- =====================================================================
-- V18：修復 V16 對 risk_options 3 筆系統預設風險選項「表面成功、實際
-- 沒生效」的問題——跟 V17__repair_product_type_system_defaults.sql
-- 是同一個 bug class，這裡補上 V17 沒有涵蓋到的另一半。
--
-- 【問題還原，與 product_types 完全同構】
-- V16__seed_product_type_and_risk_option_defaults.sql 對 risk_options
-- 這 3 筆一般風險選項（id=1~3），同樣用「INSERT ... SELECT ...
-- WHERE NOT EXISTS (SELECT 1 FROM risk_options WHERE id = X)」寫法，
-- 一樣只檢查 id 存不存在、不檢查 is_system_default 現值是否正確。
-- 如果這 3 個 id 在 V16 實際套用當下，已經被舊版
-- seed_data_full_v4.sql（一般 INSERT，未帶 is_system_default 欄位、
-- 隱性落成 0）佔用，V16 對這 3 筆同樣會靜默跳過，is_system_default
-- 從頭到尾不會被改成 1。
--
-- 【跟 product_types 案例的差異：為什麼這裡沒有直接炸出 FK 錯誤】
-- product_types 的 9 筆大類被 30 筆小類的 parent_id 外鍵參照，
-- clear_seed_data_v6.sql 誤刪後重新匯入 seed data 會立刻在 INSERT
-- 小類時因為外鍵找不到父層而報錯（Error Code 1452），問題會被立刻
-- 發現。risk_options 這 3 筆沒有其他表用外鍵參照它們（review_risks
-- 雖然直接寫死 risk_option_id=1/2/3，但 review_risks 本身資料是
-- seed_data_full_v5.sql 早於 risk_options 判斷邏輯清空重灌，順序上
-- 不會在這裡卡外鍵），所以 clear_seed_data_v6.sql 的
-- `DELETE FROM risk_options WHERE is_system_default = 0` 如果誤判
-- 這 3 筆為一般假資料一併刪除，不會讓應用程式啟動失敗，只會靜默讓
-- review_risks 裡引用 risk_option_id=1/2/3 的既有假資料變成懸空外鍵
-- （或者下次重新匯入 seed_data_full_v5.sql 時，因為 v5 已經不再插入
-- id=1~3 這幾筆，被清空後就再也不會被任何腳本補回來，形同這 3 個
-- 系統預設風險選項從資料庫裡永久消失）——問題比 product_types 那個
-- 案例更隱蔽，沒有報錯訊息可循，因此同樣需要修復，不能因為它沒有
-- 讓應用程式啟動失敗就當作沒發生過。
--
-- 【修復方式：跟 V17 同一套 ON DUPLICATE KEY UPDATE】
-- 不管這 3 個 id 當下是「不存在」還是「存在但欄位跟預期不符」，都用
-- 同一條語句處理到底。alert_keywords／auto_trigger_code／category 這幾欄
-- 也一併強制覆寫回系統預設應有的值，理由與 V17 對 product_types 各業務
-- 欄位的處理一致：這些欄位是系統預設身分的一部分，不是使用者可能已經
-- 客製化過、需要保留現值的欄位。
--
-- 【is_active 為什麼還是強制覆寫成 1，即使這代表可能覆蓋管理層的手動停用】
-- 跟 V17 的取捨一致：這個風險目前發生的機率極低（專案仍在雛型階段，
-- 這 3 筆系統預設風險選項被誤判為假資料清空、重灌後又被這支 migration
-- 修復，中間管理層剛好手動停用過其中一筆的機率可忽略），且沒有更嚴謹的
-- 判斷依據可以區分「is_active=0 是管理層刻意停用」還是「is_active=0
-- 只是清空後從未被任何腳本正確重建過」，保守覆寫成系統預設的生效狀態，
-- 與 V17 對 product_types 的做法保持一致，不在兩支互相修復的 migration
-- 之間採不同標準。
--
-- 【冪等性】ON DUPLICATE KEY UPDATE 依定義冪等，重複執行只會把這 3 筆
-- 列的欄位再覆寫成同樣的值。
--
-- 【created_at／created_by 為什麼沒有覆寫】理由與 V17 對 product_types
-- 的處理完全一致：若這 3 筆本來就存在，保留原本的 created_at 較符合
-- 事實；只有 id 目前不存在、真的走 INSERT 分支時，created_at 才會寫入
-- NOW()。
-- =====================================================================

INSERT INTO `risk_options`
  (`id`, `name`, `description`, `alert_keywords`, `is_system_default`, `is_active`,
   `created_by`, `created_at`, `auto_trigger_code`, `category`)
VALUES
  (1, '實際供貨風險', NULL, '缺貨、斷貨、供應不穩、交期延遲、停產', 1, 1, NULL, NOW(), NULL, 'SUPPLY'),
  (2, '商品品質與客訴風險', NULL, '客訴、退貨、瑕疵、品質不穩、客訴率高', 1, 1, NULL, NOW(), NULL, 'QUALITY'),
  (3, '市場不確定性與需求變動風險', NULL, '需求下滑、競品增加、退燒、熱度下降、季節性風險', 1, 1, NULL, NOW(), NULL, 'MARKET')
AS `new`
ON DUPLICATE KEY UPDATE
  `name` = `new`.`name`,
  `description` = `new`.`description`,
  `alert_keywords` = `new`.`alert_keywords`,
  `is_system_default` = `new`.`is_system_default`,
  `is_active` = `new`.`is_active`,
  `auto_trigger_code` = `new`.`auto_trigger_code`,
  `category` = `new`.`category`;
