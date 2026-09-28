"""把 build.py 產生的資料寫成 seed_data_full_v6.sql。"""
from __future__ import annotations

import sys
from datetime import date, datetime
from decimal import Decimal

import build as B

OUT = sys.argv[1] if len(sys.argv) > 1 else "seed_data_full_v6.sql"


def lit(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "1" if v else "0"
    if isinstance(v, (int, Decimal)):
        return str(v)
    if isinstance(v, float):
        return repr(v)
    if isinstance(v, datetime):
        return "'" + v.strftime("%Y-%m-%d %H:%M:%S") + "'"
    if isinstance(v, date):
        return "'" + v.isoformat() + "'"
    s = str(v).replace("\\", "\\\\").replace("'", "''").replace("\n", "\\n").replace("\r", "")
    return "'" + s + "'"


def to_json(v):
    """最小 JSON 序列化：Decimal 原樣輸出為數字（不轉 float，避免 0.6 → 0.59999...）。"""
    if v is None:
        return "null"
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, (int, Decimal)):
        return str(v)
    if isinstance(v, float):
        return repr(v)
    if isinstance(v, str):
        out = ['"']
        for ch in v:
            if ch == '"':
                out.append('\\"')
            elif ch == "\\":
                out.append("\\\\")
            elif ch == "\n":
                out.append("\\n")
            elif ord(ch) < 0x20:
                out.append("\\u%04x" % ord(ch))
            else:
                out.append(ch)
        out.append('"')
        return "".join(out)
    if isinstance(v, (list, tuple)):
        return "[" + ", ".join(to_json(x) for x in v) + "]"
    if isinstance(v, dict):
        return "{" + ", ".join(to_json(k) + ": " + to_json(x) for k, x in v.items()) + "}"
    raise TypeError(type(v))


def jlit(v):
    return "NULL" if v is None else lit(to_json(v))


lines = []


def w(s=""):
    lines.append(s)


def insert(table, cols, rows, chunk=100):
    if not rows:
        return
    col_sql = ", ".join("`%s`" % c for c in cols)
    for i in range(0, len(rows), chunk):
        part = rows[i:i + chunk]
        w("INSERT INTO `%s` (%s) VALUES" % (table, col_sql))
        w(",\n".join("(" + ", ".join(r) + ")" for r in part) + ";")
    w()


HEADER = open(__file__.replace("write_sql.py", "seed_header.sql"), encoding="utf-8").read()
w(HEADER.rstrip("\n"))
w()
w("SET NAMES utf8mb4;")
w("SET time_zone = '+08:00';")
w("START TRANSACTION;")
w()

# ---------------------------------------------------------------- app_users
w("-- ============================================================")
w("-- app_users：2 管理層 + 5 操作層（1 位已停用、1 位新帳號待首次登入改密碼）")
w("-- 密碼一律為 %s（BCrypt $2a$10$，Spring BCryptPasswordEncoder 可直接驗證）" % B.PASSWORD)
w("-- ============================================================")
insert("app_users",
       ["id", "username", "password", "name", "role", "enabled", "must_change_password", "created_at", "updated_at",
        "active_session_version"],
       [[lit(u[0]), lit(u[1]), lit(B.PW_HASH), lit(u[2]), lit(u[3]), lit(u[4]), lit(u[5]), lit(u[6]), lit(u[7]),
         lit(u[8])] for u in B.USERS])

# ---------------------------------------------------------------- product_types
w("-- ============================================================")
w("-- product_types：30 小類（9 大類是 V16/V17 migration 建立的系統預設，這裡不重建）")
w("-- ============================================================")
insert("product_types",
       ["id", "parent_id", "level", "sort_order", "name", "description", "is_system_default", "is_active",
        "created_by", "created_at", "updated_at", "default_temperature_zone", "has_shelf_life",
        "default_shelf_life_tier", "return_policy", "shelf_life_threshold_days", "default_moq",
        "required_certification", "default_evaluation_mode_id"],
       [[lit(t[0]), lit(t[2]), "2", lit(t[3]), lit(t[1]), lit("%s - %s" % (B.TYPES[t[2]]["name"], t[1])), "0", "1",
         lit(B.MANAGER01), lit(datetime(2025, 9, 2, 10, 0)), lit(datetime(2025, 9, 2, 10, 0)), lit(t[4]), lit(t[5]),
         lit(t[6]), "NULL", lit(t[7]), lit(t[8]), "NULL", "NULL"] for t in B.SUB_TYPES])

# ---------------------------------------------------------------- risk_options
w("-- ============================================================")
w("-- risk_options：7 筆管理者自訂（is_system_default=0）。id 從 101 起，")
w("-- 避開 V16/V18 的 1~3 與「其他」自由文字選項可能拿到的 AUTO_INCREMENT。")
w("-- 4 筆掛 auto_trigger_code，對應 GateEvaluationService 的 Gate，判定不通過會自動帶入。")
w("-- ============================================================")
insert("risk_options",
       ["id", "name", "description", "alert_keywords", "is_system_default", "is_active", "created_by", "created_at",
        "auto_trigger_code", "category", "is_free_text_option"],
       [[lit(r[0]), lit(r[1]), lit(r[2]), lit(r[3]), "0", lit(r[4]), lit(r[5]), lit(r[6]), lit(r[7]), lit(r[8]), "0"]
        for r in B.RISK_OPTIONS])

# ---------------------------------------------------------------- weather_signal_tag_mappings
w("-- ============================================================")
w("-- weather_signal_tag_mappings：2 筆管理者自訂對照（系統預設 13 筆由 V19 建立）")
w("-- ============================================================")
insert("weather_signal_tag_mappings",
       ["weather_signal_type", "tag", "match_tier", "is_active", "is_system_default", "created_by", "created_at"],
       [[lit(m[1]), lit(m[2]), lit(m[3]), "1", "0", lit(m[4]), lit(m[5])] for m in B.CUSTOM_TAG_MAPPINGS])

# ---------------------------------------------------------------- audience_profiles
w("-- ============================================================")
w("-- audience_profiles：AUDIENCE_MATCH 只取「生效中的第一筆」（id=1）參與計分")
w("-- ============================================================")
insert("audience_profiles",
       ["id", "name", "age_min", "age_max", "price_sensitivity", "preference_description", "keywords", "is_active",
        "version", "created_at", "updated_at"],
       [[lit(a[0]), lit(a[1]), lit(a[2]), lit(a[3]), lit(a[4]), lit(a[5]), lit(a[6]), lit(a[7]), lit(a[8]),
         lit(a[9]), lit(a[10])] for a in B.AUDIENCES])

# ---------------------------------------------------------------- festive campaigns
w("-- ============================================================")
w("-- festive_campaigns：12 節慶 + 4 季節，全部是 V21「每年固定」日期規則（代碼不帶年份）")
w("-- campaign_status 只是資料表存值；節慶／季節的實際狀態由 CampaignOccurrenceResolver 依")
w("-- 「今天」即時推算，不看這欄（除非手動覆蓋且覆蓋週期＝目前週期）。")
w("-- ============================================================")
camp_rows = []
for c in B.CAMPAIGNS:
    occ = B.E.resolve_current_or_next(c, B.TODAY)
    status, _ = B.E.derive_status(c, occ, B.TODAY)
    stored = c.stored_status if c.manual_override else status
    camp_rows.append([
        lit(c.id), lit(c.code), lit(c.name), lit(c.category), lit(c.rule_type),
        lit(c.month if c.rule_type in ("FIXED_DATE", "NTH_WEEKDAY", "LUNAR_DATE") else None),
        lit(c.day if c.rule_type in ("FIXED_DATE", "LUNAR_DATE") else None),
        lit(c.week_ordinal if c.rule_type == "NTH_WEEKDAY" else None),
        lit(c.weekday if c.rule_type == "NTH_WEEKDAY" else None),
        lit(c.solar_term if c.rule_type == "SOLAR_TERM" else None), lit(c.offset),
        lit(c.duration if c.category == "FESTIVAL" else None),
        lit(c.end_month if c.category == "SEASON" else None), lit(c.end_day if c.category == "SEASON" else None),
        lit(c.observed_rule), lit(1 if c.expand_long_weekend else 0), lit(c.lead_days), lit(stored),
        lit(1 if c.manual_override else 0), lit(c.manual_override_cycle),
        lit(datetime(2025, 9, 2, 14, 0)), lit(datetime(2026, 2, 3, 10, 30) if c.manual_override
                                              else datetime(2025, 9, 2, 14, 0))])
insert("festive_campaigns",
       ["id", "campaign_code", "campaign_name", "category", "date_rule_type", "rule_month", "rule_day",
        "rule_week_ordinal", "rule_weekday", "rule_solar_term", "rule_offset_days", "duration_days", "end_month",
        "end_day", "observed_holiday_rule", "expand_long_weekend", "preparation_lead_days", "campaign_status",
        "is_manual_override", "manual_override_cycle", "created_at", "updated_at"], camp_rows)
insert("festive_campaign_tags", ["campaign_id", "tag", "match_tier", "created_at"],
       [[lit(c.id), lit(t), lit(tier), lit(datetime(2025, 9, 2, 14, 0))] for c in B.CAMPAIGNS for t, tier in c.tags])
insert("festive_campaign_regions", ["campaign_id", "region"],
       [[lit(c.id), lit(r)] for c in B.CAMPAIGNS for r in c.regions])
insert("festive_campaign_occurrence_overrides",
       ["campaign_id", "cycle_year", "start_date", "end_date", "note", "updated_by", "updated_at"],
       [[lit(c.id), lit(y), lit(s), lit(e), lit(note), lit(B.MANAGER02), lit(datetime(2026, 9, 10, 15, 0))]
        for c in B.CAMPAIGNS for y, (s, e, note) in c.overrides.items()])

# ---------------------------------------------------------------- products
w("-- ============================================================")
w("-- products：%d 筆（依建立時間排序給 id；再販售參考商品一定比引用者早建立）" % len(B.PRODUCTS))
w("-- ============================================================")
PCOLS = ["id", "product_type_id", "pricing_type", "name", "description", "image_url", "supplier_name", "cost_price",
         "sale_price", "market_price", "campaign_tags", "moq", "supply_stability", "price_competitiveness",
         "target_customer_description", "estimated_purchase_rate", "review_status", "candidate_status",
         "pricing_status", "item_status", "submission_count", "submitted_at", "submitted_by", "created_by",
         "created_at", "updated_at", "updated_by", "temperature_zone", "shelf_life_tier", "supplier_lead_time_tier",
         "package_size_tier", "packing_type", "handling_flags", "certification_flags", "supplier_max_capacity",
         "resale_reference_product_id"]
insert("products", PCOLS, [[lit(p[c]) if c != "image_url" else "NULL" for c in PCOLS] for p in B.PRODUCTS],
       chunk=50)

# ---------------------------------------------------------------- group_buy_records
w("-- ============================================================")
w("-- group_buy_records：%d 筆歷史開團（外部匯入、唯讀），%d 個匯入批次；" % (len(B.GROUP_BUYS), len(B.BATCHES)))
w("-- 第一批 %s 為模擬資料（is_simulated=1，當時尚無成本／市價欄位）。" % B.BATCHES[B.SIM_IMPORT])
w("-- product_id 有值＝已由採購認領到系統內商品（再販售商品的過往開團、核准後實際開團）。")
w("-- ============================================================")
GCOLS = ["id", "product_id", "product_type_id", "external_product_name", "supplier_name", "campaign_start_date",
         "campaign_end_date", "moq_at_time", "sale_price_at_time", "cost_price_at_time", "market_price_at_time",
         "target_quantity", "actual_quantity", "participant_count", "result", "complaint_count", "return_count",
         "is_simulated", "import_batch_id", "imported_at", "imported_by"]
insert("group_buy_records", GCOLS, [[lit(r[c]) for c in GCOLS] for r in B.GROUP_BUYS])

# ---------------------------------------------------------------- trend signals / runs
all_trends = sorted((s for v in B.TRENDS.values() for s in v), key=lambda s: (s["collected_at"], s["product_id"]))
w("-- ============================================================")
w("-- trend_signals：%d 筆。2026-09-15 前為採購手動更新的模擬資料（SIMULATED），" % len(all_trends))
w("-- 之後為每日 02:00 PTT 全商品同步（取不到時改寫 SIMULATED）。已依 AiSuggestionBatchService")
w("-- 的判斷條件核對：只有 AI_SUGGESTED 商品符合「最新熱度 > 70 或連續 3 筆上升」。")
w("-- ============================================================")
TCOLS = ["id", "product_id", "source", "keyword", "trend_score", "popularity_score", "trend_direction",
         "collected_at", "created_at"]
insert("trend_signals", TCOLS,
       [[lit(i), lit(s["product_id"]), lit(s["source"]), lit(s["keyword"]), lit(s["trend_score"]),
         lit(s["popularity_score"]), lit(s["trend_direction"]), lit(s["collected_at"]), lit(s["collected_at"])]
        for i, s in enumerate(all_trends, start=1)], chunk=200)
RCOLS = ["id", "trigger_type", "status", "started_at", "finished_at", "total_count", "real_count", "fallback_count",
         "failed_count", "message", "triggered_by"]
runs = sorted(B.RUNS, key=lambda r: r["started_at"])
insert("trend_sync_runs", RCOLS, [[lit(i)] + [lit(r[c]) for c in RCOLS[1:]] for i, r in enumerate(runs, start=1)])

# ---------------------------------------------------------------- product_evaluations
w("-- ============================================================")
w("-- product_evaluations：每個商品一筆（id＝商品 id），依現行 ScoringService 公式計算。")
w("-- 資料完整度 < 60%% 的 %d 筆只有完整度、沒有分數（與 calculateEvaluation 行為一致）。"
  % sum(1 for e in B.EVALS if e["total_score"] is None))
w("-- 未核准商品的節慶／天氣加成會在應用程式啟動時依「當天」重算（FestivalBoostRefreshListener）。")
w("-- ============================================================")
ECOLS = ["id", "product_id", "evaluation_mode_id", "business_score", "audience_score", "historical_score",
         "purchase_score", "trend_score", "forecast_score", "total_score", "data_completeness", "festival_boost",
         "weather_boost", "matched_campaign_id", "final_score", "calculated_at", "updated_at"]
insert("product_evaluations", ECOLS, [[lit(e[c]) for c in ECOLS] for e in B.EVALS])

# ---------------------------------------------------------------- ai_analyses
ai_sorted = sorted(B.AI, key=lambda a: a["generated_at"])
w("-- ============================================================")
w("-- ai_analyses：%d 筆（reasons 刻意使用 risk_options.alert_keywords 的詞，讓儀表板風險提示有資料）" % len(ai_sorted))
w("-- ============================================================")
insert("ai_analyses", ["id", "product_id", "evaluation_id", "summary", "recommendation", "reasons", "generated_at",
                       "model_name"],
       [[lit(i), lit(a["product_id"]), lit(a["evaluation_id"]), lit(a["summary"]), lit(a["recommendation"]),
         lit(a["reasons"]), lit(a["generated_at"]), lit(a["model_name"])] for i, a in enumerate(ai_sorted, start=1)],
       chunk=50)

# ---------------------------------------------------------------- review_records / risks
w("-- ============================================================")
w("-- review_records：%d 筆審核快照（含重新送審的第 2 次紀錄）。快照 JSON 一律 camelCase、" % len(B.REVIEWS))
w("-- 只含 json/*Snapshot 類別既有的屬性。2026-09-25（V26）之前的紀錄沒有天氣加成，")
w("-- weather_boost_snapshot／weather_boost_detail_snapshot 為 NULL（V26 註解明訂）。")
w("-- ============================================================")
RVCOLS = ["id", "product_id", "reviewer_id", "submission_count", "review_status", "reviewed_at",
          "evaluation_mode_id", "evaluation_mode_name", "evaluation_mode_version", "total_score",
          "festival_boost_snapshot", "weather_boost_snapshot", "weather_boost_detail_snapshot",
          "matched_campaign_snapshot", "final_score_snapshot", "data_completeness", "business_score",
          "audience_score", "historical_score", "purchase_score", "trend_score", "forecast_score",
          "weight_snapshot", "product_snapshot", "ai_summary_snapshot", "trend_snapshot", "review_comment",
          "system_gate_summary", "created_at", "updated_at"]
JSON_COLS = {"weather_boost_detail_snapshot", "matched_campaign_snapshot", "weight_snapshot", "product_snapshot",
             "trend_snapshot"}
insert("review_records", RVCOLS,
       [[jlit(r[c]) if c in JSON_COLS else lit(r[c]) for c in RVCOLS] for r in B.REVIEWS], chunk=25)
insert("review_risks", ["review_id", "risk_option_id", "source", "is_selected", "trigger_reason", "manual_note"],
       [[lit(x[0]), lit(x[1]), lit(x[2]), lit(x[3]), lit(x[4]), lit(x[5])] for x in B.REVIEW_RISKS])
w("-- 「其他」自由文字選項：只在資料庫裡確實存在（name='其他' 且 is_free_text_option=1）時寫入。")
w("-- 目前 V9+V18 在全新資料庫上會把「其他」覆寫成「實際供貨風險」（見交付說明），這時以下")
w("-- 兩句會各插入 0 筆而不是誤掛到 id=1，修正該 migration 後重新匯入即會出現。")
for rid, note in B.FREE_TEXT_NOTES:
    w("INSERT INTO `review_risks` (`review_id`, `risk_option_id`, `source`, `is_selected`, `trigger_reason`, `manual_note`)")
    w("SELECT %d, `id`, 'MANUAL', 1, NULL, %s FROM `risk_options` WHERE `name` = '其他' AND `is_free_text_option` = 1 LIMIT 1;"
      % (rid, lit(note)))
w()

# ---------------------------------------------------------------- export logs
w("-- ============================================================")
w("-- product_export_logs：採購（PURCHASER）匯出審核通過商品 CSV 的紀錄；最近核准的商品刻意未匯出")
w("-- ============================================================")
insert("product_export_logs", ["id", "export_run_id", "product_id", "exported_at", "exported_by"],
       [[lit(i), lit(x[0]), lit(x[1]), lit(x[2]), lit(x[3])] for i, x in enumerate(B.EXPORTS, start=1)])

# ---------------------------------------------------------------- custom fields
w("-- ============================================================")
w("-- 自訂商品屬性：題目與自訂因子由 V15 建立（系統設定），這裡只放適用品類與商品填答")
w("-- ============================================================")
w("INSERT INTO `custom_field_applicable_types` (`field_definition_id`, `root_product_type_id`, `created_at`)")
w("SELECT `id`, 22, '2026-09-21 09:00:00' FROM `custom_field_definitions` WHERE `field_code` = 'ECO_PACKAGING_SCORE' AND `is_active` = 1;")
w("INSERT INTO `custom_field_applicable_types` (`field_definition_id`, `root_product_type_id`, `created_at`)")
w("SELECT `id`, 26, '2026-09-21 09:00:00' FROM `custom_field_definitions` WHERE `field_code` = 'ECO_PACKAGING_SCORE' AND `is_active` = 1;")
w()
vals = []
for pid, code, value, t in B.CUSTOM_VALUES:
    vals.append("  SELECT %d AS product_id, %s AS field_code, %s AS numeric_value, %s AS created_at"
                % (pid, lit(code), value, lit(t)))
w("INSERT INTO `product_custom_field_values` (`product_id`, `field_definition_id`, `numeric_value`, `text_value`, `created_at`)")
w("SELECT v.product_id, d.id, v.numeric_value, NULL, v.created_at FROM (")
w("\n  UNION ALL\n".join(vals))
w(") v JOIN `custom_field_definitions` d ON d.`field_code` = v.field_code AND d.`is_active` = 1;")
w()

# ---------------------------------------------------------------- password reset
w("-- ============================================================")
w("-- password_reset_requests：四種狀態各一筆；purchaser02 目前有一筆待處理申請")
w("-- ============================================================")
insert("password_reset_requests", ["id", "user_id", "status", "requested_at", "handled_at", "handled_by"],
       [[lit(x[0]), lit(x[1]), lit(x[2]), lit(x[3]), lit(x[4]), lit(x[5])] for x in B.PASSWORD_RESETS])

# ---------------------------------------------------------------- google trends
w("-- ============================================================")
w("-- google_trend_runs／google_trend_signals：09-27 管理者手動查詢一次（PTT 熱度前 40 名），")
w("-- 之後停用來源，所以 09-21、09-28 的週一排程都是 SKIPPED（與 google_trends_enabled 預設停用一致）")
w("-- ============================================================")
GRC = ["id", "trigger_type", "status", "started_at", "finished_at", "total_count", "ok_count", "no_data_count",
       "failed_count", "message", "triggered_by"]
insert("google_trend_runs", GRC, [[lit(r[c]) for c in GRC] for r in B.GOOGLE_RUNS])
GSC = ["product_id", "keyword", "status", "direction", "growth_rate", "recent_avg", "baseline_avg", "point_count",
       "collected_at"]
insert("google_trend_signals", ["id"] + GSC,
       [[lit(i)] + [lit(s[c]) for c in GSC] for i, s in enumerate(B.GOOGLE_SIGNALS, start=1)])

# ---------------------------------------------------------------- weather
w("-- ============================================================")
w("-- daily_weather_metrics：四區 %s ~ %s（系統保留窗口 60 天＋未來 14 天預報）。" % (B.WEATHER_FROM, B.WEATHER_TO))
w("-- 早於今天＝歷史（降雨機率為 NULL，比照 Open-Meteo 過去日期），今天起＝預報。")
w("-- 應用程式每日 05:00 同步會以真實資料 upsert 覆蓋同一區同一天的列，不會衝突。")
w("-- ============================================================")
wrows = []
for region in ("NORTH", "CENTRAL", "SOUTH", "EAST"):
    for d, m in sorted(B.TODAY_WEATHER[region].items()):
        pass
# 全窗口（含今天往前 60 天）：今天之前 30 天內的列以今天 05:00 為抓取時間，更早的列停在最後一次在窗口內的那天
import random as _r
from datetime import time as _t, timedelta as _td
wr = _r.Random(B.TODAY.toordinal())
for region in ("NORTH", "CENTRAL", "SOUTH", "EAST"):
    for d, m in sorted(B.WEATHER_TRUTH[region].items()):
        prob = None if d < B.TODAY else B.TODAY_WEATHER[region][d]["prob"]
        last_sync = min(d + _td(days=30), B.TODAY)
        fetched = datetime.combine(last_sync, _t(5, 0, 3))
        wrows.append([lit(region), lit(d), lit(Decimal(str(m["tmax"]))), lit(Decimal(str(m["tmin"]))),
                      lit(Decimal(str(m["hum"]))), lit(Decimal(str(m["rain"]))), lit(prob),
                      lit(Decimal(str(m["wind"]))), lit(fetched)])
insert("daily_weather_metrics",
       ["region", "weather_date", "apparent_temperature_max", "apparent_temperature_min", "humidity_mean",
        "precipitation_sum", "precipitation_probability_max", "wind_speed_max", "fetched_at"], wrows, chunk=150)

w("COMMIT;")
w()
w("-- 種子資料匯入完成")

with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines) + "\n")
print("wrote", OUT, len(lines), "lines")
