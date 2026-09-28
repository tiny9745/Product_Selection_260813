"""
組裝 seed_data_full_v6.sql 的全部資料（由 write_sql.py 匯入並輸出 SQL）。

  python3 write_sql.py [輸出路徑]

- 決定性：固定亂數種子，重跑產出完全相同的 SQL。
- 系統預設（V1~V30 migration 建立）一律從本機已套用 migration 的資料庫讀取，
  不在這裡重寫一份，避免兩邊數字對不上。
- 所有分數、快照皆由 engine.py（移植自後端 Java）計算。
"""
from __future__ import annotations

import json
import random
import re
import subprocess
import sys
import uuid
from collections import defaultdict
from datetime import date, datetime, time, timedelta
from decimal import Decimal as D

import bcrypt

import engine as E
from catalog import CATALOG, TCD

REF_DB = "product_selection_260813"
NOW = datetime(2026, 9, 28, 8, 0, 0)       # 種子資料的「現在」：09-28 02:00 PTT 同步、05:00 天氣同步之後
TODAY = NOW.date()
V26_DATE = date(2026, 9, 25)               # 天氣加成上線日：之前的審核快照沒有天氣加成（V26 註解明訂為 NULL）
PTT_START = date(2026, 9, 15)              # PTT 全商品每日同步開始的日子
FULL_RECALC_AT = datetime(2026, 9, 25, 14, 20, 0)  # 管理者調整核心客群 → 全量重算未核准商品
rng = random.Random(20260928)


def ref_query(sql):
    out = subprocess.run(["mysql", "-N", "-B", REF_DB, "-e", sql], capture_output=True, text=True, check=True).stdout
    return [line.split("\t") for line in out.strip().splitlines() if line]


# ============================================================================
# 系統預設（唯讀，來自 migration）
# ============================================================================
# V32 起大類不再綁定評估模式（NULL）→ 依 ScoringService.resolveEvaluationModeId() 退回全域的目前生效模式
MODE_OF_ROOT = {int(r[0]): (int(r[1]) if r[1] != "NULL" else None) for r in ref_query(
    "SELECT id, default_evaluation_mode_id FROM product_types WHERE is_system_default=1")}
CURRENT_MODE_ID = int(ref_query(
    "SELECT setting_value FROM system_settings WHERE setting_key='current_evaluation_mode_id'")[0][0])
MODES = {int(r[0]): (r[1], r[2], int(r[3])) for r in ref_query(
    "SELECT id, mode_code, mode_name, version FROM evaluation_modes")}
FACTOR_ROWS = defaultdict(list)
for r in ref_query("SELECT evaluation_mode_id, factor_code, factor_name, category, weight FROM evaluation_factors "
                   "ORDER BY evaluation_mode_id, sort_order"):
    FACTOR_ROWS[int(r[0])].append((r[1], r[2], r[3], D(r[4])))
WEIGHTS = {m: {code: w for code, _, _, w in rows} for m, rows in FACTOR_ROWS.items()}
BANDS = {r[0]: (D(r[1]), D(r[2])) for r in ref_query(
    "SELECT factor_code, lower_bound, upper_bound FROM product_type_score_bands WHERE product_type_id IS NULL")}
REGION_WEIGHTS = {r[0]: D(r[1]) for r in ref_query("SELECT region, weight_percentage FROM region_weights")}
SYSTEM_TAG_MAPPINGS = [(r[0], r[1], r[2]) for r in ref_query(
    "SELECT weather_signal_type, tag, match_tier FROM weather_signal_tag_mappings WHERE is_active=1")]
ROOT_TYPES = {}
for r in ref_query("SELECT id, name, default_temperature_zone, has_shelf_life, default_shelf_life_tier, "
                   "shelf_life_threshold_days, default_moq FROM product_types WHERE is_system_default=1"):
    ROOT_TYPES[int(r[0])] = dict(id=int(r[0]), name=r[1], parent_id=None, default_temperature_zone=r[2],
                                 has_shelf_life=int(r[3]), default_shelf_life_tier=r[4],
                                 shelf_life_threshold_days=None if r[5] == "NULL" else int(r[5]),
                                 default_moq=None if r[6] == "NULL" else int(r[6]))

# ============================================================================
# product_types：30 小類（沿用 v5 的 id／名稱／屬性，V16 註解與既有文件都以這組 id 為準）
# ============================================================================
SUB_TYPES = [
    # id, 名稱, 父類, 排序, 溫層, 有效期, 效期級距, 門檻天數, 預設MOQ
    (2, "肉品", 1, 1, "CHILLED", 1, "D8_30", 5, 10), (3, "海鮮水產", 1, 2, "CHILLED", 1, "D8_30", 5, 10),
    (4, "蛋奶豆製品", 1, 3, "CHILLED", 1, "D8_30", 5, 10), (6, "蔬菜", 5, 1, "NORMAL", 1, "D8_30", 7, 20),
    (7, "水果", 5, 2, "NORMAL", 1, "D8_30", 7, 20), (8, "米糧雜糧", 5, 3, "NORMAL", 1, "D8_30", 7, 20),
    (10, "冷凍調理食品", 9, 1, "FROZEN", 1, "D90_PLUS", 14, 15), (11, "冷凍點心", 9, 2, "FROZEN", 1, "D90_PLUS", 14, 15),
    (12, "冰品", 9, 3, "FROZEN", 1, "D90_PLUS", 14, 15), (14, "伴手禮糕餅", 13, 1, "NORMAL", 1, "D31_90", 30, 30),
    (15, "醬料南北貨", 13, 2, "NORMAL", 1, "D31_90", 30, 30), (16, "特色加工品", 13, 3, "NORMAL", 1, "D31_90", 30, 30),
    (18, "零食餅乾", 17, 1, "NORMAL", 1, "D90_PLUS", 21, 50), (19, "飲品沖泡", 17, 2, "NORMAL", 1, "D90_PLUS", 21, 50),
    (20, "調味料", 17, 3, "NORMAL", 1, "D90_PLUS", 21, 50), (21, "保健食品", 17, 4, "NORMAL", 1, "D90_PLUS", 21, 50),
    (23, "清潔用品", 22, 1, "NORMAL", 0, "NA", None, 50), (24, "紙品衛生", 22, 2, "NORMAL", 0, "NA", None, 50),
    (25, "個人清潔美妝", 22, 3, "NORMAL", 0, "NA", None, 50), (27, "廚房用品", 26, 1, "NORMAL", 0, "NA", None, 20),
    (28, "收納整理", 26, 2, "NORMAL", 0, "NA", None, 20), (29, "家用紡織", 26, 3, "NORMAL", 0, "NA", None, 20),
    (30, "五金工具", 26, 4, "NORMAL", 0, "NA", None, 20), (32, "手機周邊", 31, 1, "NORMAL", 0, "NA", None, 5),
    (33, "電腦周邊", 31, 2, "NORMAL", 0, "NA", None, 5), (34, "影音配件", 31, 3, "NORMAL", 0, "NA", None, 5),
    (35, "小型家電", 31, 4, "NORMAL", 0, "NA", None, 5), (37, "文具事務", 36, 1, "NORMAL", 0, "NA", None, 20),
    (38, "寵物用品", 36, 2, "NORMAL", 0, "NA", None, 20), (39, "運動休閒", 36, 3, "NORMAL", 0, "NA", None, 20),
]
TYPES = dict(ROOT_TYPES)
for tid, name, parent, sort, zone, has_shelf, tier, th, moq in SUB_TYPES:
    TYPES[tid] = dict(id=tid, name=name, parent_id=parent, sort=sort, default_temperature_zone=zone,
                      has_shelf_life=has_shelf, default_shelf_life_tier=tier, shelf_life_threshold_days=th,
                      default_moq=moq)


def root_of(tid):
    return TYPES[tid]["parent_id"] or tid


# ============================================================================
# app_users
# ============================================================================
PASSWORD = "Passw0rd!2025"
# 固定 salt：讓每次產生的 SQL 完全相同（種子帳號的測試密碼，不是正式密碼原則）
PW_HASH = bcrypt.hashpw(PASSWORD.encode(), b"$2a$10$SeedV6.ProductSelecti.").decode()
USERS = [
    # id, username, name, role, enabled, must_change, created_at, updated_at, session_version
    (1, "manager01", "林建宏", "MANAGER", 1, 0, datetime(2025, 9, 1, 9, 0), datetime(2025, 9, 1, 9, 0), 186),
    (2, "purchaser01", "陳怡君", "PURCHASER", 1, 0, datetime(2025, 9, 1, 9, 10), datetime(2026, 8, 7, 8, 52), 241),
    (3, "purchaser02", "黃冠廷", "PURCHASER", 1, 0, datetime(2025, 9, 1, 9, 15), datetime(2026, 6, 18, 11, 30), 198),
    (4, "purchaser03", "吳雅婷", "PURCHASER", 1, 0, datetime(2025, 9, 8, 10, 0), datetime(2026, 3, 2, 10, 21), 173),
    (5, "manager02", "張淑芬", "MANAGER", 1, 0, datetime(2025, 12, 1, 9, 0), datetime(2025, 12, 1, 9, 0), 92),
    (6, "purchaser04", "李承翰", "PURCHASER", 0, 0, datetime(2025, 9, 15, 9, 30), datetime(2026, 3, 31, 17, 45), 58),
    (7, "purchaser05", "周佳穎", "PURCHASER", 1, 1, datetime(2026, 9, 26, 16, 20), datetime(2026, 9, 26, 16, 20), 0),
]
USER_NAME = {u[0]: u[2] for u in USERS}
MANAGER01, MANAGER02 = 1, 5


def active_purchasers(dt):
    ids = [2, 3, 4]
    if datetime(2025, 9, 15) <= dt < datetime(2026, 3, 31, 17, 45):
        ids.append(6)
    return ids


def reviewer_at(dt):
    if dt >= datetime(2025, 12, 1, 9, 0) and rng.random() < 0.35:
        return MANAGER02
    return MANAGER01


# ============================================================================
# 核心客群（第一筆生效者驅動 AUDIENCE_MATCH）
# ============================================================================
AUDIENCES = [
    (1, "核心家庭客群", 28, 45, "MEDIUM", "重視食材來源與商品品質的小家庭與雙薪家庭",
     "家庭,新鮮,品質,安全,方便,健康,小孩", 1, 1, datetime(2025, 9, 2, 9, 30), FULL_RECALC_AT),
    (2, "上班族便利客群", 25, 40, "MEDIUM", "追求快速方便、重視時間成本的上班族群",
     "方便,快速,即食,外食,省時", 1, 1, datetime(2025, 9, 2, 9, 40), datetime(2025, 9, 2, 9, 40)),
    (3, "健身健康客群", 22, 38, "LOW", "注重蛋白質攝取、成分標示與運動表現的健身族群",
     "健身,蛋白質,運動,低卡,無添加", 1, 1, datetime(2025, 11, 12, 11, 0), datetime(2025, 11, 12, 11, 0)),
    (4, "銀髮保健客群", 55, 75, "HIGH", "重視保健與日常照護、對價格較敏感的銀髮族群",
     "保健,養生,高齡,安心,照護", 1, 1, datetime(2026, 1, 8, 9, 0), datetime(2026, 1, 8, 9, 0)),
    (5, "年輕潮流客群（已停用）", 18, 28, "HIGH", "重視外觀設計與話題性的年輕族群；2026 Q2 起改由上班族便利客群涵蓋，故停用",
     "潮流,設計感,話題,限量", 0, 1, datetime(2025, 9, 20, 14, 0), datetime(2026, 4, 10, 16, 0)),
]
AUDIENCE_KEYWORDS = list(dict.fromkeys(k.strip().lower() for k in re.split(r"[,、\s]+", AUDIENCES[0][6]) if k.strip()))

# ============================================================================
# 節慶／季節檔期（V21 日期規則；代碼不得帶年份）
# ============================================================================
C = E.Campaign
CAMPAIGNS = [
    C(1, "LUNAR_NEW_YEAR", "春節年貨", "FESTIVAL", "LUNAR_DATE", month=1, day=1, offset=-1, duration=6,
      observed_rule="TW_STATUTORY", expand_long_weekend=True, lead_days=45,
      tags=[("過年", "CORE"), ("禮品", "GENERAL"), ("囤貨", "WEAK")]),
    C(2, "LANTERN_FESTIVAL", "元宵節", "FESTIVAL", "LUNAR_DATE", month=1, day=15, duration=1, lead_days=21,
      tags=[("元宵", "CORE"), ("湯圓", "CORE")]),
    C(3, "VALENTINES_DAY", "西洋情人節", "FESTIVAL", "FIXED_DATE", month=2, day=14, duration=1, lead_days=21,
      manual_override=True, manual_override_cycle=2026, stored_status="EXPIRED",
      tags=[("情人節", "CORE"), ("禮品", "WEAK")]),
    C(4, "QINGMING", "清明連假", "FESTIVAL", "SOLAR_TERM", solar_term="QINGMING", offset=-1, duration=3,
      observed_rule="TW_STATUTORY", lead_days=21, tags=[("清明", "CORE"), ("出遊", "GENERAL")]),
    C(5, "MOTHERS_DAY", "母親節", "FESTIVAL", "NTH_WEEKDAY", month=5, week_ordinal=2, weekday=7, offset=-7,
      duration=8, lead_days=30, tags=[("母親節", "CORE"), ("禮品", "GENERAL")]),
    C(6, "DRAGON_BOAT", "端午節", "FESTIVAL", "LUNAR_DATE", month=5, day=5, offset=-3, duration=4,
      observed_rule="TW_STATUTORY", lead_days=30, tags=[("端午", "CORE"), ("粽子", "CORE"), ("禮品", "WEAK")]),
    C(7, "FATHERS_DAY", "父親節", "FESTIVAL", "FIXED_DATE", month=8, day=8, duration=1, lead_days=21,
      tags=[("父親節", "CORE"), ("禮品", "GENERAL")]),
    C(8, "MID_AUTUMN", "中秋節", "FESTIVAL", "LUNAR_DATE", month=8, day=15, offset=-7, duration=8,
      observed_rule="TW_STATUTORY", lead_days=45, tags=[("中秋", "CORE"), ("烤肉", "GENERAL"), ("禮品", "GENERAL")],
      overrides={}),
    C(9, "NATIONAL_DAY", "國慶連假", "FESTIVAL", "FIXED_DATE", month=10, day=10, duration=1,
      observed_rule="TW_STATUTORY", expand_long_weekend=True, lead_days=14,
      tags=[("出遊", "CORE"), ("烤肉", "WEAK")]),
    C(10, "DOUBLE_ELEVEN", "雙11購物節", "FESTIVAL", "FIXED_DATE", month=11, day=11, offset=-10, duration=11,
      lead_days=21, tags=[("雙11", "CORE"), ("囤貨", "GENERAL")]),
    C(11, "WINTER_SOLSTICE", "冬至", "FESTIVAL", "SOLAR_TERM", solar_term="DONGZHI", duration=1, lead_days=14,
      tags=[("冬至", "CORE"), ("湯圓", "GENERAL")]),
    C(12, "CHRISTMAS", "聖誕跨年", "FESTIVAL", "FIXED_DATE", month=12, day=25, offset=-5, duration=12,
      lead_days=30, tags=[("聖誕", "CORE"), ("禮品", "GENERAL")]),
    C(13, "SUMMER_COOLING", "夏季消暑", "SEASON", "FIXED_DATE", month=6, day=1, end_month=8, end_day=31,
      lead_days=21, tags=[("消暑", "CORE"), ("冰品", "CORE"), ("涼感", "GENERAL")]),
    C(14, "BACK_TO_SCHOOL", "開學季", "SEASON", "FIXED_DATE", month=8, day=10, end_month=9, end_day=10,
      lead_days=21, tags=[("開學", "CORE"), ("文具", "GENERAL")]),
    C(15, "AUTUMN_WINTER", "秋冬換季", "SEASON", "FIXED_DATE", month=9, day=23, end_month=11, end_day=30,
      lead_days=14, regions=["NORTH", "CENTRAL", "EAST"],
      tags=[("秋冬", "CORE"), ("火鍋", "GENERAL"), ("保暖", "GENERAL")]),
    C(16, "WINTER_WARMTH", "冬季保暖", "SEASON", "FIXED_DATE", month=12, day=1, end_month=2, end_day=28,
      lead_days=21, regions=["NORTH", "CENTRAL", "EAST"], tags=[("保暖", "CORE"), ("火鍋", "CORE")]),
]
# 逐年覆寫示範：2027 春節依人事總處公告的連假日期（規則推算不出的調整放假）
CAMPAIGNS[0].overrides[2027] = (date(2027, 2, 5), date(2027, 2, 14),
                                "示範資料：依行政院人事行政總處公告之 2027 春節連假（含調整放假）覆寫期間")
CAMPAIGN_BY_ID = {c.id: c for c in CAMPAIGNS}


def tags_of(s):
    return [t.strip() for t in (s or "").split(",") if t.strip()]


# ============================================================================
# 天氣：合成四區每日氣象（2026-07-30 ~ 2026-10-11，保留窗口 60 天＋預報 14 天）
# ============================================================================
WEATHER_FROM = TODAY - timedelta(days=60)
WEATHER_TO = TODAY + timedelta(days=13)
REGION_PROFILE = {
    #          (8月體感高溫, 10月初體感高溫, 基礎濕度, 10月濕度, 午後雷陣雨機率)
    "NORTH": (37.0, 27.5, 72.0, 81.0, 0.30),
    "CENTRAL": (37.5, 32.5, 70.0, 72.0, 0.25),
    "SOUTH": (38.2, 34.8, 74.0, 76.0, 0.35),
    "EAST": (35.8, 30.0, 77.0, 82.0, 0.30),
}
TYPHOON_DAYS = {date(2026, 9, 16): (1.0, 1.0), date(2026, 9, 17): (0.7, 0.8)}   # 颱風外圍環流（雨量、風）
FRONT_START = date(2026, 10, 2)                                                  # 東北季風鋒面（預報）


def synth_weather():
    wr = random.Random(7)
    series = {}
    span = (WEATHER_TO - WEATHER_FROM).days
    for region, (t_aug, t_oct, h_base, h_oct, storm_p) in REGION_PROFILE.items():
        days = {}
        for i in range(span + 1):
            d = WEATHER_FROM + timedelta(days=i)
            frac = min(max((d - date(2026, 8, 15)).days / 50.0, 0.0), 1.0)
            tmax = t_aug + (t_oct - t_aug) * frac + wr.gauss(0, 1.1)
            hum = h_base + (h_oct - h_base) * frac + wr.gauss(0, 3.5)
            rain = 0.0
            wind = max(8.0, wr.gauss(20, 6))
            if wr.random() < storm_p * (1.0 - 0.6 * frac):
                rain = round(wr.uniform(3, 28), 1)
                hum += 5
            if d in TYPHOON_DAYS and region in ("NORTH", "EAST"):
                r_f, w_f = TYPHOON_DAYS[d]
                rain = round(wr.uniform(70, 140) * r_f, 1)
                wind = wr.uniform(58, 78) * w_f
                hum = 90 + wr.uniform(0, 5)
                tmax -= 4
            if d >= FRONT_START and region in ("NORTH", "EAST"):
                tmax -= 2.5
                hum += 4
                if wr.random() < 0.65:
                    rain = round(wr.uniform(6, 32), 1)
            hum = min(hum, 97.0)
            tmin = tmax - wr.uniform(6.5, 9.0)
            days[d] = {"tmax": round(tmax, 2), "tmin": round(tmin, 2), "hum": round(hum, 2),
                       "rain": round(rain, 2), "wind": round(wind, 2)}
        series[region] = days
    return series


WEATHER_TRUTH = synth_weather()


def forecast_probability(rain, wr):
    if rain >= 40:
        return round(wr.uniform(80, 95))
    if rain >= 5:
        return round(wr.uniform(60, 90))
    if rain > 0:
        return round(wr.uniform(30, 55))
    return round(wr.uniform(5, 25))


def weather_view(as_of_day, fetched_on):
    """模擬某一天 05:00 同步後資料庫裡看得到的天氣列（歷史無降雨機率、預報有）。"""
    wr = random.Random(as_of_day.toordinal())
    fetched = datetime.combine(fetched_on, time(5, 0, 3))
    view = {}
    for region, days in WEATHER_TRUTH.items():
        v = {}
        for d, m in days.items():
            if d < as_of_day - timedelta(days=30) or d > as_of_day + timedelta(days=13):
                continue
            row = dict(m)
            row["prob"] = None if d < as_of_day else forecast_probability(m["rain"], wr)
            row["fetched_at"] = fetched
            v[d] = row
        view[region] = v
    return view


# 天氣標籤對照：系統預設 + 種子資料新增的兩筆管理者自訂（created_by 有值、is_system_default=0）
CUSTOM_TAG_MAPPINGS = [
    (1, "HOT", "防曬", "GENERAL", MANAGER01, datetime(2026, 9, 25, 16, 5)),
    (2, "RAINY", "除濕", "WEAK", MANAGER02, datetime(2026, 9, 26, 10, 40)),
]


def tag_weights(include_custom_since=None):
    tw = defaultdict(dict)
    for t, tag, tier in SYSTEM_TAG_MAPPINGS:
        tw[t][tag] = max(tw[t].get(tag, D(0)), E.TIER_WEIGHT[tier])
    for _, t, tag, tier, _, created in CUSTOM_TAG_MAPPINGS:
        if include_custom_since is None or created <= include_custom_since:
            tw[t][tag] = max(tw[t].get(tag, D(0)), E.TIER_WEIGHT[tier])
    return tw


# ============================================================================
# 商品
# ============================================================================
SPEC_TOKEN = re.compile(r"(?i)[x×*]?\s*\d+(\.\d+)?\s*(kg|g|ml|l|公克|克|公斤|入|包|盒|罐|瓶|片|顆|組|袋|箱|抽|捲|件)(?![a-wyz])"
                        r"|[x×*]\s*\d+")
BRACKETED = re.compile(r"[(（\[【][^)）\]】]*[)）\]】]")


def search_keyword(name):
    cleaned = re.sub(r"\s+", " ", SPEC_TOKEN.sub(" ", BRACKETED.sub(" ", name))).strip()
    return (cleaned or name)[:100]


FRESH_ROOTS = {1, 5, 9}
CERT = {1: "CAS優良食品", 5: "產銷履歷", 9: "HACCP", 13: "HACCP", 17: "HACCP", 22: "SGS檢驗合格"}
DEFAULT_PKG = {1: "S", 5: "M", 9: "S", 13: "S", 17: "S", 22: "M", 26: "M", 31: "S", 36: "S"}
PRICE_Q = D("0.01")


def business_time(d, lo=9, hi=18):
    while d.weekday() >= 5:
        d += timedelta(days=1)
    return datetime.combine(d, time(rng.randint(lo, hi - 1), rng.randint(0, 59), rng.randint(0, 59)))


def lead_tier_for(supplier, pricing_type):
    if "股份有限公司" in supplier:
        tiers = ["D3", "D4_7", "D4_7"]
    elif "有限公司" in supplier:
        tiers = ["D4_7", "D4_7", "D8_14"]
    else:
        tiers = ["D4_7", "D8_14", "D8_14"]
    if pricing_type == "NEW":
        tiers = [t if t != "D3" else "D4_7" for t in tiers]
    return rng.choice(tiers)


def build_products():
    entries = list(CATALOG)
    rng.shuffle(entries)
    # 參考商品必須比引用它的再販售商品早建立：把有 ref 的放到最後
    entries.sort(key=lambda e: 1 if e[10].get("ref") else 0)
    n = len(entries)
    start, end = datetime(2025, 10, 1), datetime(2026, 9, 26)
    products = []
    for k, e in enumerate(entries):
        frac = (k / (n - 1)) ** 0.6
        day = (start + (end - start) * frac).date()
        created = business_time(day)
        while created > NOW - timedelta(hours=14) or created.weekday() >= 5:
            created -= timedelta(days=1)
        sub, kind, name, supplier, cost, sale, market, moq, tags, tcd, extra = e
        products.append(dict(
            product_type_id=sub, pricing_type="NEW" if kind == "N" else "RESALE", name=name,
            supplier_name=supplier, cost_price=D(cost).quantize(PRICE_Q), sale_price=D(sale).quantize(PRICE_Q),
            market_price=None if market is None else D(market).quantize(PRICE_Q), moq=moq,
            campaign_tags=tags or None, target_customer_description=TCD[tcd], extra=extra, created_at=created,
            description=extra.get("desc") or "%s，由%s供貨。" % (name, supplier)))
    products.sort(key=lambda p: p["created_at"])
    for i, p in enumerate(products, start=1):
        p["id"] = i
    by_name = {p["name"]: p for p in products}
    for p in products:
        ref = p["extra"].get("ref")
        p["resale_reference_product_id"] = by_name[ref]["id"] if ref else None
        if ref:
            assert by_name[ref]["id"] < p["id"] and by_name[ref]["product_type_id"] == p["product_type_id"]
    return products


PRODUCTS = build_products()
PBY = {p["id"]: p for p in PRODUCTS}


def assign_lifecycle():
    for p in PRODUCTS:
        c = p["created_at"]
        p["created_by"] = rng.choice(active_purchasers(c))
        if c < datetime(2026, 8, 15):
            path = rng.choices(["A", "R", "RA", "RR", "RP", "P"], [50, 24, 10, 3, 5, 8])[0]
        elif c < datetime(2026, 9, 12):
            path = rng.choices(["A", "R", "RP", "P"], [26, 12, 8, 54])[0]
        else:
            path = rng.choices(["A", "P"], [12, 88])[0]
        p["path"] = path
        p["reviews"] = []           # [(time, status, reviewer, version)]
        p["resubmit_at"] = None
        p["resubmit_by"] = None
        p["archive_at"] = None
        t1 = business_time((c + timedelta(days=rng.randint(1, 9))).date())
        if path != "P":
            if t1 >= NOW:
                path = p["path"] = "P"
            else:
                p["reviews"].append([t1, "APPROVED" if path == "A" else "REJECTED", reviewer_at(t1), 1])
        if path in ("RA", "RR", "RP"):
            rs = business_time((t1 + timedelta(days=rng.randint(3, 16))).date())
            if rs >= NOW:
                path = p["path"] = "R"
            else:
                p["resubmit_at"] = rs
                p["resubmit_by"] = p["created_by"] if p["created_by"] in active_purchasers(rs) \
                    else rng.choice(active_purchasers(rs))
                if path in ("RA", "RR"):
                    t2 = business_time((rs + timedelta(days=rng.randint(1, 6))).date())
                    if t2 >= NOW:
                        path = p["path"] = "RP"
                    else:
                        p["reviews"].append([t2, "APPROVED" if path == "RA" else "REJECTED", reviewer_at(t2), 2])
        final = p["reviews"][-1][1] if p["reviews"] and path not in ("RP",) else "PENDING"
        p["review_status"] = final
        p["submission_count"] = 2 if p["resubmit_at"] else 1
        # 封存：只能是 APPROVED／REJECTED（ProductService.archive）
        if final in ("APPROVED", "REJECTED") and p["reviews"][-1][0] < datetime(2026, 5, 1):
            if rng.random() < (0.18 if final == "APPROVED" else 0.35):
                p["archive_at"] = business_time((p["reviews"][-1][0] + timedelta(days=rng.randint(20, 90))).date())
                if p["archive_at"] >= NOW:
                    p["archive_at"] = None
        p["item_status"] = "ARCHIVED" if p["archive_at"] else "ACTIVE"
    # V26（2026-09-25）上線當天下午審核 3 件，讓決策紀錄裡有「含天氣加成快照」的新格式紀錄
    late = [p for p in PRODUCTS if p["path"] == "P" and datetime(2026, 9, 14) <= p["created_at"] < datetime(2026, 9, 23)]
    for p, status, hour in zip(late[:3], ["APPROVED", "REJECTED", "APPROVED"], [15, 16, 17]):
        t = datetime(2026, 9, 25, hour, rng.randint(0, 50), rng.randint(0, 59))
        p["path"] = "A" if status == "APPROVED" else "R"
        p["reviews"] = [[t, status, reviewer_at(t), 1]]
        p["review_status"] = status


assign_lifecycle()


def assign_attributes():
    for p in PRODUCTS:
        root = root_of(p["product_type_id"])
        path = p["path"]
        if path in ("A", "RA"):
            ss, pc, rate = rng.randint(3, 5), rng.randint(3, 5), rng.uniform(0.45, 0.86)
        elif path in ("R", "RR"):
            ss, pc, rate = rng.randint(1, 3), rng.randint(1, 3), rng.uniform(0.18, 0.5)
        else:
            ss, pc, rate = rng.randint(2, 5), rng.randint(2, 5), rng.uniform(0.3, 0.8)
        p["supply_stability"], p["price_competitiveness"] = ss, pc
        p["estimated_purchase_rate"] = D(rate).quantize(PRICE_Q)
        # Gate 相關屬性
        sub = TYPES[p["product_type_id"]]
        ex = p["extra"]
        p["temperature_zone"] = sub["default_temperature_zone"]
        p["shelf_life_tier"] = ex.get("shelf", sub["default_shelf_life_tier"])
        p["supplier_lead_time_tier"] = ex.get("lead") or lead_tier_for(p["supplier_name"], p["pricing_type"])
        p["package_size_tier"] = ex.get("pkg", DEFAULT_PKG[root])
        p["packing_type"] = "REPACK" if root in FRESH_ROOTS else "WHOLE_CARTON"
        p["handling_flags"] = ex.get("handling")
        p["certification_flags"] = CERT.get(root)
        p["supplier_max_capacity"] = (p["moq"] or 30) * rng.choice([6, 8, 10, 12])
        p["pricing_status"] = None
        p["v1"] = None
    # 再販售商品：價格與市價都有；新品：市價一律 NULL（ProductService 驗證）
    # 尚在早期階段的待審商品：刻意缺漏部分資料（資料完整度 < 60% 不計分）
    pending = [p for p in PRODUCTS if p["path"] == "P"]
    rng.shuffle(pending)
    for p in pending[:6]:          # 資料未齊：完整度低於門檻
        p["campaign_tags"] = None
        p["moq"] = None
        p["price_competitiveness"] = None
        if p["pricing_type"] == "NEW":
            p["target_customer_description"] = None
            p["cost_price"] = p["sale_price"] = None
        else:
            p["market_price"] = None
            p["cost_price"] = None
        p["temperature_zone"] = p["shelf_life_tier"] = p["supplier_lead_time_tier"] = None
        p["package_size_tier"] = p["packing_type"] = p["handling_flags"] = p["certification_flags"] = None
        p["supplier_max_capacity"] = None
        p["estimated_purchase_rate"] = None
    for p in pending[6:12]:        # 新品尚未議價：沒有成本／售價（完整度不受影響，但毛利率因子為 null）
        if p["pricing_type"] == "NEW":
            p["cost_price"] = p["sale_price"] = None
    for p in PRODUCTS:
        if p["pricing_type"] == "NEW":
            p["pricing_status"] = "PRICED" if (p["cost_price"] is not None and p["sale_price"] is not None) \
                else "PENDING_PRICING"
    # 退件後重送：第一版有具體問題，第二版（現值）已修正
    for p in PRODUCTS:
        if not p["resubmit_at"]:
            continue
        problem = rng.choice(["margin", "tcd", "moq", "supply"])
        v1 = {}
        if problem == "margin" and p["cost_price"] is not None:
            v1["cost_price"] = (p["sale_price"] * D("0.93")).quantize(PRICE_Q)
            p["v1_problem"] = "margin"
        elif problem == "tcd" and p["pricing_type"] == "NEW":
            v1.update(target_customer_description=None, campaign_tags=None, price_competitiveness=None,
                      moq=None)
            p["v1_problem"] = "data"
        elif problem == "moq":
            v1["moq"] = (p["moq"] or 30) * 4
            p["v1_problem"] = "moq"
        else:
            v1["supply_stability"] = 1
            p["v1_problem"] = "supply"
        p["v1"] = v1
    # RR（重送後仍被拒）：第二版問題沒有完全解決
    for p in PRODUCTS:
        if p["path"] == "RR":
            p["supply_stability"] = min(p["supply_stability"], 2)


assign_attributes()


def fields_at(p, t):
    """商品在時間 t 的欄位內容（重送前使用第一版）。"""
    f = {k: p[k] for k in ("id", "name", "product_type_id", "pricing_type", "supplier_name", "cost_price",
                           "sale_price", "market_price", "campaign_tags", "moq", "supply_stability",
                           "price_competitiveness", "target_customer_description", "estimated_purchase_rate",
                           "resale_reference_product_id", "temperature_zone", "shelf_life_tier",
                           "supplier_lead_time_tier", "package_size_tier", "packing_type", "handling_flags",
                           "certification_flags", "supplier_max_capacity")}
    if p["v1"] and (p["resubmit_at"] is None or t < p["resubmit_at"]):
        f.update(p["v1"])
    return f


def status_at(p, t):
    """(review_status, 是否待審) 於時間 t。"""
    if t < p["created_at"]:
        return None
    reviews = p["reviews"]
    if not reviews or t < reviews[0][0]:
        return "PENDING"
    if p["resubmit_at"] and t >= p["resubmit_at"]:
        if len(reviews) > 1 and t >= reviews[1][0]:
            return reviews[1][1]
        return "PENDING"
    return reviews[0][1]


def active_at(p, t):
    return p["created_at"] <= t and (p["archive_at"] is None or t < p["archive_at"])


# ============================================================================
# 歷史開團紀錄（group_buy_records）
# ============================================================================
GB_PROFILE = {
    # 子類: (中位集單量, 成團率, 筆數, 客訴係數)
    2: (130, .82, 18, 1.2), 3: (95, .74, 14, 1.4), 4: (160, .85, 14, 1.0), 6: (110, .80, 14, 1.0),
    7: (100, .78, 16, 1.3), 8: (150, .88, 14, .6), 10: (190, .84, 18, .8), 11: (160, .82, 14, .8),
    12: (140, .76, 12, .9), 14: (120, .80, 16, .9), 15: (70, .66, 12, .7), 16: (90, .72, 10, .8),
    18: (230, .86, 16, .5), 19: (180, .83, 14, .5), 20: (120, .78, 12, .5), 21: (60, .62, 12, .6),
    23: (210, .88, 14, .4), 24: (250, .90, 12, .3), 25: (110, .74, 12, .8), 27: (55, .64, 10, .9),
    28: (50, .66, 10, .9), 29: (45, .60, 10, 1.1), 30: (22, .45, 6, 1.0), 32: (60, .70, 10, 1.2),
    33: (35, .58, 8, 1.3), 34: (20, .50, 7, 1.6), 35: (28, .60, 10, 1.5), 37: (150, .84, 10, .3),
    38: (90, .78, 12, .6), 39: (70, .70, 12, .8),
}
EXTRA_NAMES = {
    2: ["台灣豬里肌燒肉片", "國產雞腿排 10片", "牛五花火鍋片"], 3: ["白帶魚切片", "智利鮭魚菲力", "天使紅蝦"],
    4: ["溫泉蛋 10入", "嫩豆腐 家庭號", "鮮奶酪 6入"], 6: ["有機小松菜", "筊白筍 家庭號", "產銷履歷地瓜葉"],
    7: ["花蓮西瓜", "大湖草莓 禮盒", "台東釋迦 5台斤"], 8: ["花蓮富里米 3kg", "發芽玄米 2kg", "台灣小米"],
    10: ["三杯雞調理包", "冷凍炒飯 5入", "麻油雞湯"], 11: ["芋泥球 20入", "冷凍湯包", "紅豆鬆餅"],
    12: ["紅豆牛奶冰棒", "鳳梨冰沙杯", "芋頭冰淇淋"], 14: ["太陽餅 12入", "綠豆椪禮盒", "蜂蜜蛋糕"],
    15: ["蝦米 300g", "香菇禮盒", "沙茶醬 2罐"], 16: ["客家菜脯", "米粉湯 5入", "花生糖禮盒"],
    18: ["海鹽蘇打餅", "芝麻脆片", "牛軋餅 20入"], 19: ["阿薩姆奶茶粉", "黑糖薑茶", "高山烏龍茶包"],
    20: ["蔭油膏 2瓶", "胡麻醬", "純釀米醋"], 21: ["魚油膠囊", "鈣片 60錠", "B群 60錠"],
    23: ["小蘇打粉 2kg", "地板清潔劑", "洗手乳 補充包"], 24: ["平版衛生紙", "口罩 50入", "舒潔濕巾"],
    25: ["牙膏 6入", "沐浴乳 家庭號", "卸妝棉"], 27: ["電鍋內鍋蒸架", "矽膠料理匙組", "鑄鐵鍋 20cm"],
    28: ["鞋櫃收納盒", "摺疊收納籃", "衣架 30入"], 29: ["記憶枕", "抗菌毛巾 6入", "竹蓆"],
    30: ["水平儀捲尺組", "工具掛板"], 32: ["手機支架", "無線充電盤"], 33: ["機械式鍵盤", "筆電支架"],
    34: ["頸掛式喇叭", "麥克風收音組"], 35: ["果汁機", "電動牙刷", "吸塵器"], 37: ["資料夾 20入", "便條紙"],
    38: ["寵物飲水機", "貓抓板"], 39: ["跳繩", "健身彈力帶", "運動水壺"],
}
IMPORT_DATES = [date(2025, 10, 6), date(2025, 11, 5), date(2025, 12, 4), date(2026, 1, 6), date(2026, 2, 5),
                date(2026, 3, 5), date(2026, 4, 7), date(2026, 5, 6), date(2026, 6, 4), date(2026, 7, 6),
                date(2026, 8, 5), date(2026, 9, 4), date(2026, 9, 22)]
SIM_IMPORT = date(2025, 9, 5)


def batch_id():
    return "GBR-" + uuid.UUID(int=rng.getrandbits(128)).hex[:8].upper()


BATCHES = {SIM_IMPORT: batch_id()}
for d in IMPORT_DATES:
    BATCHES[d] = batch_id()


def import_date_for(end_day):
    if end_day < date(2025, 9, 1):
        return SIM_IMPORT
    for d in IMPORT_DATES:
        if d >= end_day + timedelta(days=3):
            return d
    return None


def import_time(d):
    return datetime.combine(d, time(10, rng.randint(0, 50), rng.randint(0, 59)))


def gb_record(sub, name, supplier, start_day, moq, sale, claim_pid=None, claimed_at=None, q_scale=None):
    q_med, p_ful, _, cfac = GB_PROFILE[sub]
    q_med = q_scale or q_med
    dur = rng.randint(3, 10)
    end_day = start_day + timedelta(days=dur)
    imp = import_date_for(end_day)
    if imp is None or import_time(imp) > NOW:
        return None
    r = rng.random()
    if r < 0.07:
        result = "CANCELLED"
        actual = rng.randint(0, max(1, int(moq * 0.3)))
    else:
        want_fulfilled = rng.random() < p_ful
        if want_fulfilled:
            actual = max(moq, int(rng.lognormvariate(0, 0.35) * q_med))
            result = "FULFILLED"
        else:
            actual = rng.randint(max(1, int(moq * 0.35)), max(2, moq - 1))
            result = "FAILED"
    simulated = imp == SIM_IMPORT
    cost = None if simulated else (sale * D(rng.uniform(0.55, 0.78))).quantize(PRICE_Q)
    market = None if simulated or rng.random() < 0.4 else (sale * D(rng.uniform(1.15, 1.5))).quantize(PRICE_Q)
    complaints = 0
    returns = 0
    if result == "FULFILLED":
        lam = actual * 0.012 * cfac
        complaints = sum(1 for _ in range(int(lam * 3) + 1) if rng.random() < 0.33)
        returns = sum(1 for _ in range(complaints) if rng.random() < 0.5)
    importer = MANAGER01 if imp < date(2025, 12, 1) or rng.random() < 0.6 else MANAGER02
    return dict(product_id=claim_pid, product_type_id=sub, external_product_name=name, supplier_name=supplier,
                campaign_start_date=start_day, campaign_end_date=end_day, moq_at_time=moq,
                sale_price_at_time=(sale * D(rng.uniform(0.95, 1.05))).quantize(PRICE_Q),
                target_quantity=int(round(moq * rng.uniform(1.3, 2.6), -1)) or moq, actual_quantity=actual,
                participant_count=None if result == "CANCELLED" else max(1, int(actual * rng.uniform(0.55, 0.9))),
                result=result, complaint_count=complaints, return_count=returns, is_simulated=1 if simulated else 0,
                import_batch_id=BATCHES[imp], imported_at=import_time(imp), imported_by=importer,
                cost_price_at_time=cost, market_price_at_time=market,
                claimed_at=claimed_at or import_time(imp))


def build_group_buys():
    recs = []
    sub_prices = defaultdict(list)
    for p in PRODUCTS:
        sub_prices[p["product_type_id"]].append((p["sale_price"] or D(300), p["moq"] or 30, p["supplier_name"]))
    span_start = date(2024, 10, 1)
    for sub, (q_med, p_ful, count, _) in GB_PROFILE.items():
        names = [e[2] for e in CATALOG if e[0] == sub and e[1] == "N"] + EXTRA_NAMES[sub]
        for _ in range(count):
            sale, moq, supplier = rng.choice(sub_prices[sub])
            day = span_start + timedelta(days=rng.randint(0, (date(2026, 9, 10) - span_start).days))
            moq_t = max(5, int(q_med * rng.uniform(0.35, 0.75)))
            r = gb_record(sub, rng.choice(names), supplier if rng.random() < 0.5 else None, day, moq_t,
                          sale * D(rng.uniform(0.8, 1.2)))
            if r:
                r["claimed_at"] = None
                r["product_id"] = None
                recs.append(r)
    # 再販售商品：自己過去的開團紀錄，於建立商品時由採購認領（product_id = 本商品）
    for p in PRODUCTS:
        if p["pricing_type"] != "RESALE" or p["resale_reference_product_id"]:
            continue
        claim = rng.random() < 0.75
        for _ in range(rng.randint(3, 6)):
            day = p["created_at"].date() - timedelta(days=rng.randint(40, 360))
            moq_t = p["moq"] or 30
            r = gb_record(p["product_type_id"], p["name"], p["supplier_name"], day, moq_t,
                          p["sale_price"] or D(300), q_scale=int(moq_t * rng.uniform(1.4, 2.6)))
            if r and r["imported_at"] < p["created_at"]:
                if claim:
                    r["product_id"] = p["id"]
                    r["claimed_at"] = p["created_at"] + timedelta(minutes=rng.randint(5, 40))
                else:
                    r["product_id"] = None
                    r["claimed_at"] = None
                recs.append(r)
    # 已核准商品核准後實際開團，匯入後由採購認領；少數尚未認領（示範「待認領」清單）
    for p in PRODUCTS:
        if p["review_status"] != "APPROVED":
            continue
        approved_at = p["reviews"][-1][0]
        for _ in range(rng.randint(0, 3)):
            day = approved_at.date() + timedelta(days=rng.randint(7, 60))
            if p["archive_at"] and datetime.combine(day, time()) > p["archive_at"]:
                continue
            moq_t = p["moq"] or 30
            r = gb_record(p["product_type_id"], p["name"], p["supplier_name"], day, moq_t,
                          p["sale_price"] or D(300), q_scale=int(moq_t * rng.uniform(1.6, 3.0)))
            if not r:
                continue
            if rng.random() < 0.8:
                r["product_id"] = p["id"]
                r["claimed_at"] = r["imported_at"] + timedelta(days=rng.randint(1, 3), hours=rng.randint(1, 5))
                if r["claimed_at"] > NOW:
                    r["product_id"], r["claimed_at"] = None, None
            else:
                r["product_id"], r["claimed_at"] = None, None
            recs.append(r)
    recs.sort(key=lambda r: (r["imported_at"], r["campaign_start_date"], r["external_product_name"]))
    for i, r in enumerate(recs, start=1):
        r["id"] = i
    return recs


GROUP_BUYS = build_group_buys()

# ============================================================================
# 趨勢資料（trend_signals）與 PTT 同步執行紀錄（trend_sync_runs）
# ============================================================================
SEASON_TREND = {"保暖": 9, "秋冬": 7, "火鍋": 7, "除濕": 4, "雨具": 5, "防水": 3, "雙11": 5, "出遊": 3,
                "消暑": -8, "冰品": -9, "涼感": -6, "開學": -7, "中秋": -10}
AI_PICKS = set()


def pick_ai_products():
    cands = [p for p in PRODUCTS if p["path"] == "P" and p["created_at"] < datetime(2026, 9, 12)
             and p["campaign_tags"] and p["cost_price"] is not None]
    rng.shuffle(cands)
    for p in cands[:6]:
        AI_PICKS.add(p["id"])


pick_ai_products()


def base_popularity(p):
    if p["id"] in AI_PICKS:
        # PTT 上線前熱度普通；PTT 上線後才爆紅（見 ptt_signal），避免好幾個月前就被標成 AI 建議
        return rng.uniform(45, 60), 0.6
    tags = tags_of(p["campaign_tags"])
    slope = sum(SEASON_TREND.get(t, 0) for t in tags) / 10.0
    return rng.uniform(28, 62), max(-1.2, min(1.2, slope))


TRENDS = defaultdict(list)
RUNS = []


def clamp(v, lo=0.0, hi=100.0):
    return max(lo, min(hi, v))


def is_pending_candidate(p, t):
    return status_at(p, t) == "PENDING" and p["id"] not in AI_PICKS


def add_signal(p, t, source, pop, trend, direction):
    TRENDS[p["id"]].append(dict(product_id=p["id"], source=source, keyword=search_keyword(p["name"]),
                                trend_score=D(trend).quantize(PRICE_Q), popularity_score=D(pop).quantize(PRICE_Q),
                                trend_direction=direction, collected_at=t))


def simulated_signal(p, t, state):
    prev_t, prev_p = state.get("trend", 50.0), state.get("pop", 50.0)
    new_t = clamp(round(prev_t + (rng.random() - 0.5) * 10.0, 2))
    new_p = clamp(round(prev_p + (rng.random() - 0.5) * 10.0 + state["slope"] * 0.8, 2))
    if is_pending_candidate(p, t) or (p["id"] in AI_PICKS and t.date() < PTT_START):
        new_p = min(new_p, 68.4)
    if p["id"] in AI_PICKS and t.date() >= PTT_START:
        new_p = max(new_p, 70.6)          # 已被標記的 AI 建議商品：維持符合條件，建議原因才重建得出來
    direction = "UP" if new_t > prev_t else ("DOWN" if new_t < prev_t else "STABLE")
    if direction == "UP" and is_pending_candidate(p, t) and state.get("ups", 0) >= 2:
        new_t, direction = prev_t, "STABLE"
    state.update(trend=new_t, pop=new_p, ups=state.get("ups", 0) + 1 if direction == "UP" else 0)
    add_signal(p, t, "SIMULATED", new_p, new_t, direction)


def ptt_signal(p, t, state, day_index):
    pop = clamp(round(state["base"] + state["slope"] * day_index + rng.gauss(0, 3.0), 2))
    trend = clamp(round(50 + state["slope"] * 7 + rng.gauss(0, 5.5), 2))
    if p["id"] in AI_PICKS:
        pop = clamp(round(min(92.0, 72.0 + 0.9 * day_index + rng.gauss(0, 2.0)), 2), 70.6)
        trend = clamp(round(56 + rng.random() * 8, 2))
    if is_pending_candidate(p, t):
        pop = min(pop, 68.4)
    direction = "UP" if trend >= 55 else ("DOWN" if trend <= 45 else "STABLE")
    if direction == "UP" and is_pending_candidate(p, t) and state.get("ups", 0) >= 2:
        trend, direction = 52.0 + rng.random() * 2, "STABLE"
    state.update(trend=trend, pop=pop, ups=state.get("ups", 0) + 1 if direction == "UP" else 0)
    add_signal(p, t, "PTT", pop, trend, direction)


def build_trends():
    states = {}
    for p in PRODUCTS:
        base, slope = base_popularity(p)
        states[p["id"]] = {"base": base, "slope": slope, "pop": base, "trend": 50.0}
    # 1. PTT 上線前：採購在品項詳情頁手動「更新趨勢」（模擬資料），約六成商品、每 7~14 天一次
    for p in PRODUCTS:
        if rng.random() > 0.6:
            continue
        t = p["created_at"] + timedelta(days=rng.randint(1, 5))
        stop = datetime.combine(PTT_START, time(0, 0))
        while t < stop and active_at(p, t):
            bt = business_time(t.date())
            if bt >= stop or not active_at(p, bt) or bt >= NOW:
                break
            simulated_signal(p, bt, states[p["id"]])
            t += timedelta(days=rng.randint(7, 14))
    # 2. 2026-09-15 起每日 02:00 PTT 全商品同步（findByItemStatus(ACTIVE)，依 id 順序）
    day = PTT_START
    while day <= TODAY:
        start = datetime.combine(day, time(2, 0, 0))
        run_specs = [("SCHEDULED", start, None)]
        if day == date(2026, 9, 22):
            run_specs.append(("MANUAL", datetime.combine(day, time(15, 32, 10)), MANAGER01))
        for trigger, started, who in run_specs:
            if day == date(2026, 9, 20) and trigger == "SCHEDULED":
                RUNS.append(dict(trigger_type=trigger, status="SKIPPED", started_at=started, finished_at=started,
                                 total_count=0, real_count=0, fallback_count=0, failed_count=0,
                                 message="PTT 來源已停用，本次排程未執行", triggered_by=None))
                continue
            targets = [p for p in PRODUCTS if active_at(p, started)]
            interrupted = day == date(2026, 9, 18)
            cut = int(len(targets) * 0.42) if interrupted else len(targets)
            t = started
            real = fallback = failed = 0
            for idx, p in enumerate(targets[:cut]):
                t += timedelta(seconds=rng.randint(14, 26))
                roll = rng.random()
                st = states[p["id"]]
                if roll < 0.02:
                    failed += 1
                    continue
                if roll < 0.16:
                    fallback += 1
                    simulated_signal(p, t, st)
                else:
                    real += 1
                    ptt_signal(p, t, st, (day - PTT_START).days)
            if interrupted:
                RUNS.append(dict(trigger_type=trigger, status="FAILED", started_at=started,
                                 finished_at=datetime.combine(day, time(9, 12, 44)), total_count=len(targets),
                                 real_count=0, fallback_count=0, failed_count=0,
                                 message="應用程式在同步途中關閉，本次未完成", triggered_by=who))
            else:
                RUNS.append(dict(trigger_type=trigger, status="COMPLETED", started_at=started,
                                 finished_at=t + timedelta(seconds=3), total_count=len(targets), real_count=real,
                                 fallback_count=fallback, failed_count=failed, message=None, triggered_by=who))
        day += timedelta(days=1)
    for pid in TRENDS:
        TRENDS[pid].sort(key=lambda s: s["collected_at"])


build_trends()


def ai_flag_time(p):
    """AI 主動選品批次（每日 03:00）第一次符合條件的時間。"""
    sigs = TRENDS.get(p["id"], [])
    for i, s in enumerate(sigs):
        last3 = sigs[max(0, i - 2):i + 1][::-1]
        hot = s["popularity_score"] > 70
        ups = len(last3) == 3 and all(x["trend_direction"] == "UP" for x in last3)
        if hot or ups:
            flag = datetime.combine(s["collected_at"].date(), time(3, 0, 0))
            if flag <= s["collected_at"]:
                flag += timedelta(days=1)
            return flag if flag <= NOW else None
    return None


for p in PRODUCTS:
    p["candidate_status"] = "CANDIDATE"
    p["ai_flagged_at"] = None
    if p["id"] in AI_PICKS:
        f = ai_flag_time(p)
        if f:
            p["candidate_status"] = "AI_SUGGESTED"
            p["ai_flagged_at"] = f
        else:
            AI_PICKS.discard(p["id"])

# ============================================================================
# 評分：last_calc_time / 評估值
# ============================================================================
CTX = E.ScoringContext(bands=BANDS, audience_keywords=AUDIENCE_KEYWORDS, group_buys=GROUP_BUYS, trends=TRENDS)


def mode_of(p):
    """與 ScoringService.resolveEvaluationModeId() 相同：大類有綁定就用綁定，否則用目前生效模式。"""
    bound = MODE_OF_ROOT[root_of(p["product_type_id"])]
    return bound if bound is not None else CURRENT_MODE_ID


def last_calc_time(p, t):
    """t 當下 product_evaluations 裡那一筆是何時算的（建立／編輯、每次趨勢同步、全量重算都會重算）。"""
    cands = [p["created_at"]]
    if p["resubmit_at"] and p["resubmit_at"] <= t:
        cands.append(p["resubmit_at"])
    for s in TRENDS.get(p["id"], []):
        if s["collected_at"] <= t:
            cands.append(s["collected_at"])
    if FULL_RECALC_AT <= t and status_at(p, FULL_RECALC_AT) not in (None, "APPROVED"):
        cands.append(FULL_RECALC_AT)
    return max(c for c in cands if c <= t)


def evaluation_at(p, t):
    calc = last_calc_time(p, t)
    return E.evaluate(fields_at(p, calc), WEIGHTS[mode_of(p)], CTX, calc), calc


# ============================================================================
# 風險選項（種子資料自訂的 7 筆；id 刻意從 101 起，避開系統預設與「其他」可能的 AUTO_INCREMENT）
# ============================================================================
RISK_OPTIONS = [
    (101, "食品保存期限風險", "生鮮／食品類效期不足時由 Gate 自動帶入", "過期、保存期限、腐壞、冷鏈", 1, MANAGER01,
     datetime(2025, 9, 3, 10, 0), "GATE_SHELF_LIFE", "QUALITY"),
    (102, "集單量門檻風險", "最低訂購量高於品類歷史集單基準時由 Gate 自動帶入", "集單困難、門檻過高、成團率低", 1, MANAGER01,
     datetime(2025, 9, 3, 10, 5), "GATE_MOQ_FEASIBILITY", "SUPPLY"),
    (103, "檔期備貨時程風險", "距檔期開始天數短於供應商前置期時由 Gate 自動帶入", "備貨不及、交期延誤、趕不上檔期", 1, MANAGER01,
     datetime(2025, 9, 3, 10, 10), "GATE_LEAD_TIME", "SUPPLY"),
    (104, "資料不完整風險", "資料完整度未達 60% 時由 Gate 自動帶入", "資料不足、資訊不完整、待補資料", 1, MANAGER01,
     datetime(2025, 9, 3, 10, 15), "GATE_DATA_COMPLETENESS", "DATA"),
    (105, "商品易碎與運損風險", "玻璃、陶瓷、酥餅等易碎品的配送破損風險", "易碎、破損、運損", 1, MANAGER01,
     datetime(2025, 10, 20, 15, 30), None, "BUSINESS"),
    (106, "價格競爭力不足風險", "團購價與市售通路相比缺乏優勢", "價格偏高、比價劣勢、毛利過低", 1, MANAGER02,
     datetime(2026, 1, 12, 11, 0), None, "BUSINESS"),
    (107, "包材環保疑慮（已停用）", "2026 Q2 起改以自訂屬性「環保包裝評級」評估，停用此選項", "過度包裝、塑膠包材", 0, MANAGER01,
     datetime(2025, 11, 3, 9, 20), None, "QUALITY"),
]
GATE_RISK = {r[7]: r[0] for r in RISK_OPTIONS if r[7] and r[4]}
FREE_TEXT_NOTES = []   # (review_id, note)

# ============================================================================
# AI 分析
# ============================================================================
AI = []


def ai_text(p, f, ev, t, gates):
    sub = TYPES[f["product_type_id"]]["name"]
    parts = ["「%s」屬於%s，由%s供貨。" % (f["name"], sub, f["supplier_name"])]
    margin = None
    if f["cost_price"] is not None and f["sale_price"] is not None:
        margin = (f["sale_price"] - f["cost_price"]) / f["sale_price"] * 100
        parts.append("以團購價 %s 元、成本 %s 元估算，毛利率約 %.1f%%" % (f["sale_price"], f["cost_price"], margin))
        if f["market_price"] is not None:
            disc = (f["market_price"] - f["sale_price"]) / f["market_price"] * 100
            parts[-1] += "，相對市售價折扣約 %.0f%%" % disc
        parts[-1] += "。"
    else:
        parts.append("目前尚未完成議價，毛利率無法評估。")
    sig = E.latest_trend(p["id"], CTX, t)
    if sig:
        dir_txt = {"UP": "上升", "DOWN": "下滑", "STABLE": "持平"}[sig["trend_direction"]]
        src = "PTT 討論量" if sig["source"] == "PTT" else "模擬資料"
        parts.append("近期市場熱度 %s 分（%s），趨勢%s。" % (sig["popularity_score"], src, dir_txt))
    tags = tags_of(f["campaign_tags"])
    if tags:
        parts.append("標籤：%s。" % "、".join(tags))
    summary = "".join(parts)
    reasons = []
    failed = {g[0] for g in gates if g[1] == "FAILED"}
    if margin is not None and margin >= 32:
        reasons.append("毛利結構健康，扣除運費後仍有合理利潤空間")
    if margin is not None and margin < 15:
        reasons.append("毛利過低，扣除運費後利潤有限，建議重新議價")
    if ev["audience"] is not None and ev["audience"] >= 40:
        reasons.append("商品描述與核心家庭客群高度相關")
    if f["supply_stability"] is not None and f["supply_stability"] <= 2:
        reasons.append("供應商近期曾有缺貨、交期延遲紀錄，供應不穩")
    if "GATE_SHELF_LIFE" in failed:
        reasons.append("保存期限短，冷鏈配送時間需嚴格控管，恐有腐壞客訴風險")
    if "GATE_MOQ_FEASIBILITY" in failed:
        reasons.append("集單門檻高於品類歷史水準，成團率低")
    if "GATE_LEAD_TIME" in failed:
        reasons.append("供應商前置期長，可能備貨不及、趕不上檔期")
    if "GATE_DATA_COMPLETENESS" in failed:
        reasons.append("商品資料不足，部分評分因子無法計算，建議補齊後再評估")
    if sig and sig["trend_direction"] == "DOWN":
        reasons.append("討論熱度下降，需留意需求下滑與季節性風險")
    if sig and sig["popularity_score"] > 70:
        reasons.append("市場討論熱度高，具話題性")
    if f.get("handling_flags") and "FRAGILE" in f["handling_flags"]:
        reasons.append("屬易碎品，配送需加強包裝以降低破損")
    if ev["historical"] is not None and ev["historical"] < 60:
        reasons.append("同品類歷史成團率偏低")
    if not reasons:
        reasons.append("各項指標表現中等，無明顯風險訊號")
    total = ev["total"]
    if total is None or failed & {"GATE_DATA_COMPLETENESS"}:
        rec = "建議暫緩，待資料補齊後再評估"
    elif failed or total < 48:
        rec = "建議暫緩或拒絕"
    elif total >= 64:
        rec = "建議優先上架"
    else:
        rec = "建議先核准，上架時機另議"
    return summary, rec, "\n".join("・" + r for r in reasons)


def add_ai(p, t):
    f = fields_at(p, t)
    ev, _ = evaluation_at(p, t)
    matched = E.match_campaign(set(tags_of(f["campaign_tags"])), CAMPAIGNS, t.date(), REGION_WEIGHTS)
    gates = E.evaluate_gates(f, TYPES, GROUP_BUYS, ev["data_completeness"], matched, t.date(), t)
    s, rec, reasons = ai_text(p, f, ev, t, gates)
    AI.append(dict(product_id=p["id"], evaluation_id=p["id"], summary=s, recommendation=rec, reasons=reasons,
                   generated_at=t, model_name="gemini-3.6-flash"))


def format_ai_snapshot(a):
    parts = []
    for label, content in ((None, a["summary"]), ("【推薦方向】", a["recommendation"]),
                           ("【理由與風險提示】", a["reasons"])):
        if content:
            parts.append((label or "") + content)
    return "\n\n".join(parts) if parts else None


def latest_ai(pid, t):
    cands = [a for a in AI if a["product_id"] == pid and a["generated_at"] <= t]
    return max(cands, key=lambda a: a["generated_at"]) if cands else None


for p in PRODUCTS:
    reviewed = bool(p["reviews"])
    if reviewed or rng.random() < 0.55:
        t = p["created_at"] + timedelta(hours=rng.randint(1, 30))
        limit = p["reviews"][0][0] - timedelta(minutes=30) if reviewed else NOW - timedelta(hours=1)
        if t < limit:
            add_ai(p, t)
    if p["resubmit_at"]:
        t = p["resubmit_at"] + timedelta(hours=rng.randint(1, 20))
        limit = p["reviews"][1][0] - timedelta(minutes=30) if len(p["reviews"]) > 1 else NOW - timedelta(hours=1)
        if t < limit:
            add_ai(p, t)
    if not reviewed and p["id"] in AI_PICKS and p["ai_flagged_at"]:
        t = p["ai_flagged_at"] + timedelta(hours=rng.randint(6, 30))
        if t < NOW:
            add_ai(p, t)

# ============================================================================
# 審核紀錄與快照
# ============================================================================
REVIEWS = []
REVIEW_RISKS = []
APPROVE_COMMENTS = [
    "毛利與歷史成團率皆在水準之上，同意上架。",
    "檔期前備貨時間充足，核准；請採購與供應商確認出貨排程。",
    "屬常態回購品項，核准列入常態清單。",
    "價格具競爭力，客群匹配度佳，同意。",
    "核准。首檔建議先以較低 MOQ 試水溫。",
    None,
]
APPROVE_WITH_OVERRIDE = {
    "GATE_MOQ_FEASIBILITY": "系統提示集單門檻偏高，但已與供應商談妥可分批出貨，風險可控，核准。",
    "GATE_SHELF_LIFE": "效期偏短，已要求供應商改為產地直送、到貨即出，核准。",
    "GATE_LEAD_TIME": "供應商承諾檔期前加開產線，前置期風險可接受，核准。",
}
REJECT_BY_PROBLEM = {
    "margin": "毛利率過低，扣除運費後幾乎無利潤，請重新議價後再送審。",
    "data": "資料不完整（缺少目標客群、標籤與 MOQ），請補齊後重新送審。",
    "moq": "MOQ 過高，歷史同類商品很少達到此集單量，請與供應商協調降低門檻。",
    "supply": "供應商近三個月曾兩度斷貨，供應風險過高，請確認備貨能力後再送審。",
}
REJECT_GENERIC = [
    "同類商品近期已有兩檔在進行，市場重疊，本次先不核准。",
    "市售通路常態促銷價與團購價差距不大，價格優勢不足。",
    "品質客訴風險偏高，暫不考慮。",  # 保留供日後擴充；目前依條件挑選時不使用
    "熱度下滑且季節性明顯，錯過最佳檔期，本次不核准。",
]
APPROVE_AFTER_FIX = {
    "margin": "重新議價後毛利已改善至合理區間，核准。",
    "data": "資料已補齊，評分與客群匹配皆合格，核准。",
    "moq": "MOQ 已下修，集單可行性合理，核准。",
    "supply": "供應商已提出備貨計畫並加簽交期條款，核准。",
}
OTHER_NOTES = ["供應商尚未提供最新一期的第三方檢驗報告，需補件確認。",
               "同一供應商另一商品上月有延遲出貨紀錄，需持續觀察。"]
other_used = 0


def weight_snapshot(mode_id, f, t):
    """ScoringService.buildWeightSnapshot(modeId, product)：2026-09-29 起審核送出改用含商品情境的版本，
    目標區間與歷史樣本數（審核當下）一併凍結。"""
    code, name, version = MODES[mode_id]
    _, cat_n, prod_n, includes_sim = E.history_score(f, CTX, t)
    return {
        "modeCode": code, "modeName": name, "version": version,
        "factors": [{"factorCode": fc, "factorName": fn, "category": cat, "weight": w, "strategyCode": None,
                     "strategyParams": None, "dataSourceCode": None, "customFieldCode": None}
                    for fc, fn, cat, w in FACTOR_ROWS[mode_id]],
        "shrinkageKCategory": 10, "shrinkageKProduct": 5, "trendHalfLifeDays": 14,
        "historySampleSizeCategory": cat_n, "historySampleSizeProduct": prod_n,
        "historyIncludesSimulated": includes_sim,
        "scoreBands": {code_: [lo, hi] for code_, (lo, hi) in BANDS.items() if code_ in ("MARGIN_RATE", "DISCOUNT_DEPTH")},
    }


def product_snapshot(f):
    moq, source = E.resolve_moq(f, TYPES)
    return {
        "name": f["name"], "pricingType": f["pricing_type"], "costPrice": f["cost_price"],
        "salePrice": f["sale_price"], "marketPrice": f["market_price"], "campaignTags": f["campaign_tags"],
        "moq": f["moq"], "supplyStability": f["supply_stability"], "priceCompetitiveness": f["price_competitiveness"],
        "targetCustomerDescription": f["target_customer_description"],
        "estimatedPurchaseRate": f["estimated_purchase_rate"], "resolvedMoq": moq, "moqSource": source,
        "temperatureZone": f["temperature_zone"], "shelfLifeTier": f["shelf_life_tier"],
        "supplierLeadTimeTier": f["supplier_lead_time_tier"], "packageSizeTier": f["package_size_tier"],
        "packingType": f["packing_type"], "handlingFlags": f["handling_flags"],
        "certificationFlags": f["certification_flags"], "supplierMaxCapacity": f["supplier_max_capacity"],
        "freightCostEstimate": D("0"), "resaleReferenceProductId": f["resale_reference_product_id"],
    }


def trend_snapshot(pid, t):
    s = E.latest_trend(pid, CTX, t)
    if not s:
        return None
    return {"source": s["source"], "keyword": s["keyword"], "trendScore": s["trend_score"],
            "popularityScore": s["popularity_score"], "trendDirection": s["trend_direction"],
            "collectedAt": s["collected_at"].isoformat()}


def build_reviews():
    global other_used
    events = []
    for p in PRODUCTS:
        for idx, (t, status, reviewer, version) in enumerate(p["reviews"]):
            events.append((t, p, idx, status, reviewer))
    events.sort(key=lambda e: e[0])
    for rid, (t, p, idx, status, reviewer) in enumerate(events, start=1):
        f = fields_at(p, t)
        ev, calc = evaluation_at(p, t)
        mode_id = mode_of(p)
        tags = set(tags_of(f["campaign_tags"]))
        matched = E.match_campaign(tags, CAMPAIGNS, t.date(), REGION_WEIGHTS)
        fb = E.festival_boost(matched)
        weather = None
        if t.date() >= V26_DATE:
            view = weather_view(t.date(), t.date())
            weather = E.weather_boost(tags, t.date(), view, tag_weights(t), REGION_WEIGHTS)
        wb = weather["weatherBoost"] if weather else None
        gates = E.evaluate_gates(f, TYPES, GROUP_BUYS, ev["data_completeness"], matched, t.date(), t)
        failed = [g for g in gates if g[1] == "FAILED"]
        submission = idx + 1
        # 審核意見與風險
        comment = None
        manual = []
        suggested = {GATE_RISK[g[0]]: g[2] for g in failed if g[0] in GATE_RISK}
        keep = set(suggested)
        problem = p.get("v1_problem")
        if status == "APPROVED":
            if submission == 2 and problem:
                comment = APPROVE_AFTER_FIX[problem]
            elif failed:
                g = failed[0][0]
                comment = APPROVE_WITH_OVERRIDE.get(g, "系統判定的風險已確認可控，核准。")
                keep = set() if rng.random() < 0.8 else keep
            else:
                lead_ok = any(g[0] == "GATE_LEAD_TIME" and g[1] == "PASSED" for g in gates)
                pool = [c for c in APPROVE_COMMENTS if lead_ok or c is None or "檔期" not in c]
                comment = rng.choice(pool)
            if rng.random() < 0.12:
                manual.append(3)
        else:
            if submission == 1 and problem:
                comment = REJECT_BY_PROBLEM[problem]
                manual += {"margin": [106], "data": [], "moq": [], "supply": [1]}[problem]
            elif failed:
                labels = {"GATE_SHELF_LIFE": "效期太短，團購配送週期無法保證到貨品質，暫不考慮。",
                          "GATE_MOQ_FEASIBILITY": REJECT_BY_PROBLEM["moq"],
                          "GATE_LEAD_TIME": "供應商前置期太長，趕不上檔期，本檔不核准。",
                          "GATE_DATA_COMPLETENESS": "資料完整度未達門檻，請補齊商品資料後再送審。"}
                comment = labels[failed[0][0]]
            elif submission == 2:
                comment = "重新送審後問題仍未完全解決：供應商備貨能力未見改善，維持不核准。"
            else:
                sig0 = E.latest_trend(p["id"], CTX, t)
                pool = [REJECT_GENERIC[0]]
                if f["price_competitiveness"] is not None and f["price_competitiveness"] <= 2:
                    pool.append(REJECT_GENERIC[1])
                if f["supply_stability"] is not None and f["supply_stability"] <= 2:
                    pool.append(REJECT_BY_PROBLEM["supply"])
                if sig0 and sig0["trend_direction"] == "DOWN":
                    pool.append(REJECT_GENERIC[3])
                if ev["historical"] is not None and ev["historical"] < 65:
                    pool.append("同品類歷史成團率偏低，集單風險高，本次先不核准。")
                comment = rng.choice(pool)
            if f["supply_stability"] is not None and f["supply_stability"] <= 2 and 1 not in manual:
                manual.append(1)
            if f["price_competitiveness"] is not None and f["price_competitiveness"] <= 2 and 106 not in manual:
                manual.append(106)
            if f.get("handling_flags") and "FRAGILE" in f["handling_flags"]:
                manual.append(105)
            sig = E.latest_trend(p["id"], CTX, t)
            if sig and sig["trend_direction"] == "DOWN":
                manual.append(3)
            if not manual and not keep:
                manual.append(2)
        use_other = status == "REJECTED" and other_used < 2 and rng.random() < 0.15
        if use_other:
            FREE_TEXT_NOTES.append((rid, OTHER_NOTES[other_used]))
            other_used += 1
        for opt, reason in suggested.items():
            REVIEW_RISKS.append((rid, opt, "SYSTEM_AUTO", 1 if opt in keep else 0, reason, None))
        for opt in dict.fromkeys(manual):
            if opt in suggested:
                continue
            REVIEW_RISKS.append((rid, opt, "MANUAL", 1, None, None))
        ai = latest_ai(p["id"], t)
        REVIEWS.append(dict(
            id=rid, product_id=p["id"], reviewer_id=reviewer, submission_count=submission, review_status=status,
            reviewed_at=t, evaluation_mode_id=mode_id, evaluation_mode_name=MODES[mode_id][1],
            evaluation_mode_version=MODES[mode_id][2], total_score=ev["total"], festival_boost_snapshot=fb,
            weather_boost_snapshot=wb, weather_boost_detail_snapshot=weather, matched_campaign_snapshot=matched,
            final_score_snapshot=E.compose_final(ev["total"], fb, wb), data_completeness=ev["data_completeness"],
            business_score=ev["business"], audience_score=ev["audience"], historical_score=ev["historical"],
            purchase_score=ev["purchase"], trend_score=ev["trend"], forecast_score=ev["forecast"],
            weight_snapshot=weight_snapshot(mode_id, f, t), product_snapshot=product_snapshot(f),
            ai_summary_snapshot=format_ai_snapshot(ai) if ai else None, trend_snapshot=trend_snapshot(p["id"], t),
            review_comment=comment, system_gate_summary=E.gate_summary_text(gates),
            created_at=t, updated_at=t, gates=gates))


build_reviews()

# ============================================================================
# product_evaluations（每個商品一筆，id = 商品 id）
# ============================================================================
EVALS = []
TODAY_WEATHER = weather_view(TODAY, TODAY)
TODAY_TAG_WEIGHTS = tag_weights()
for p in PRODUCTS:
    ev, calc = evaluation_at(p, NOW)
    tags = set(tags_of(fields_at(p, NOW)["campaign_tags"]))
    matched = wb = None
    fb = D("0.00")
    wbv = D("0.00")
    if ev["total"] is not None:
        boost_day = calc.date()
        matched = E.match_campaign(tags, CAMPAIGNS, boost_day, REGION_WEIGHTS)
        fb = E.festival_boost(matched)
        if boost_day >= V26_DATE:
            view = TODAY_WEATHER if boost_day == TODAY else weather_view(boost_day, boost_day)
            wbv = E.weather_boost(tags, boost_day, view, tag_weights(calc), REGION_WEIGHTS)["weatherBoost"]
    EVALS.append(dict(
        id=p["id"], product_id=p["id"], evaluation_mode_id=mode_of(p), business_score=ev["business"],
        audience_score=ev["audience"], historical_score=ev["historical"], purchase_score=ev["purchase"],
        trend_score=ev["trend"], forecast_score=ev["forecast"], total_score=ev["total"],
        data_completeness=ev["data_completeness"], festival_boost=fb, weather_boost=wbv,
        matched_campaign_id=matched["campaignId"] if matched else None,
        final_score=E.compose_final(ev["total"], fb, wbv), calculated_at=calc, updated_at=calc))

# ============================================================================
# 其他：匯出紀錄、自訂屬性、密碼重設、Google 趨勢
# ============================================================================
EXPORTS = []
EXPORT_RUNS = [(datetime(2025, 12, 15, 10, 32, 5), 2), (datetime(2026, 2, 10, 14, 5, 40), 3),
               (datetime(2026, 4, 20, 9, 48, 12), 2), (datetime(2026, 6, 30, 16, 20, 33), 4),
               (datetime(2026, 8, 18, 11, 2, 9), 3), (datetime(2026, 9, 15, 15, 40, 51), 2)]
exported = set()
for when, who in EXPORT_RUNS:
    run_id = str(uuid.UUID(int=rng.getrandbits(128), version=4))
    batch = [p for p in PRODUCTS if status_at(p, when) == "APPROVED" and p["id"] not in exported
             and (p["archive_at"] is None or when < p["archive_at"])]
    if when == EXPORT_RUNS[-1][0]:
        batch = batch[: max(1, len(batch) // 2)]
    again = [p for p in PRODUCTS if p["id"] in exported and status_at(p, when) == "APPROVED"]
    batch += rng.sample(again, min(len(again), 1))
    for p in batch:
        EXPORTS.append((run_id, p["id"], when, who))
        exported.add(p["id"])

CUSTOM_VALUES = []
ECO_START = datetime(2026, 9, 21, 9, 0)
for p in PRODUCTS:
    root = root_of(p["product_type_id"])
    base_t = max(p["created_at"], ECO_START) + timedelta(minutes=rng.randint(5, 600))
    if base_t > NOW:
        continue
    if root in (22, 26) and p["item_status"] == "ACTIVE" and rng.random() < 0.7:
        CUSTOM_VALUES.append((p["id"], "ECO_PACKAGING_SCORE", D(rng.randint(1, 5)), base_t))
    if root in (31, 17, 36) and p["item_status"] == "ACTIVE" and rng.random() < 0.55:
        CUSTOM_VALUES.append((p["id"], "SOCIAL_BUZZ_MENTIONS", D(int(rng.lognormvariate(3.4, 1.0))),
                              base_t + timedelta(minutes=3)))

PASSWORD_RESETS = [
    (1, 4, "COMPLETED", datetime(2026, 3, 2, 9, 12, 30), datetime(2026, 3, 2, 10, 5, 12), MANAGER01),
    (2, 3, "REJECTED", datetime(2026, 6, 18, 8, 55, 1), datetime(2026, 6, 18, 11, 30, 44), MANAGER02),
    (3, 2, "CANCELLED", datetime(2026, 8, 7, 8, 40, 19), datetime(2026, 8, 7, 8, 52, 3), None),
    (4, 3, "PENDING", datetime(2026, 9, 27, 21, 15, 48), None, None),
]

GOOGLE_RUNS = []
GOOGLE_SIGNALS = []
GOOGLE_RUNS.append(dict(id=1, trigger_type="SCHEDULED", status="SKIPPED", started_at=datetime(2026, 9, 21, 4, 0, 0),
                        finished_at=datetime(2026, 9, 21, 4, 0, 0), total_count=0, ok_count=0, no_data_count=0,
                        failed_count=0, message="Google 趨勢來源已停用，本次排程未執行", triggered_by=None))
g_start = datetime(2026, 9, 27, 14, 5, 22)
latest_ptt = []
for p in PRODUCTS:
    sigs = [s for s in TRENDS.get(p["id"], []) if s["collected_at"] <= g_start]
    if sigs and sigs[-1]["popularity_score"] > 0 and active_at(p, g_start):
        latest_ptt.append((sigs[-1]["popularity_score"], p))
latest_ptt.sort(key=lambda x: (-x[0], x[1]["id"]))
targets = [p for _, p in latest_ptt[:40]]
t = g_start
ok = nod = fail = 0
for p in targets:
    t += timedelta(seconds=rng.randint(3, 6))
    roll = rng.random()
    if roll < 0.05:
        fail += 1
        continue
    kw = search_keyword(p["name"])
    if roll < 0.27:
        nod += 1
        GOOGLE_SIGNALS.append(dict(product_id=p["id"], keyword=kw, status="NO_DATA", direction=None,
                                   growth_rate=None, recent_avg=None, baseline_avg=None,
                                   point_count=rng.randint(3, 12), collected_at=t))
        continue
    ok += 1
    baseline = D(rng.uniform(12, 55)).quantize(PRICE_Q)
    recent = (baseline * D(rng.uniform(0.6, 1.7))).quantize(PRICE_Q)
    growth = ((recent - baseline) * 100 / baseline).quantize(PRICE_Q)
    direction = "UP" if growth >= 15 else ("DOWN" if growth <= -15 else "STABLE")
    GOOGLE_SIGNALS.append(dict(product_id=p["id"], keyword=kw, status="OK", direction=direction, growth_rate=growth,
                               recent_avg=recent, baseline_avg=baseline, point_count=rng.randint(88, 91),
                               collected_at=t))
GOOGLE_RUNS.append(dict(id=2, trigger_type="MANUAL", status="COMPLETED", started_at=g_start,
                        finished_at=t + timedelta(seconds=2), total_count=len(targets), ok_count=ok,
                        no_data_count=nod, failed_count=fail, message=None, triggered_by=MANAGER01))
GOOGLE_RUNS.append(dict(id=3, trigger_type="SCHEDULED", status="SKIPPED", started_at=datetime(2026, 9, 28, 4, 0, 0),
                        finished_at=datetime(2026, 9, 28, 4, 0, 0), total_count=0, ok_count=0, no_data_count=0,
                        failed_count=0, message="Google 趨勢來源已停用，本次排程未執行", triggered_by=None))

# ============================================================================
# 商品最終欄位：submitted_at／updated_at／updated_by
# ============================================================================
for p in PRODUCTS:
    if p["resubmit_at"]:
        p["submitted_at"], p["submitted_by"] = p["resubmit_at"], p["resubmit_by"]
    else:
        p["submitted_at"], p["submitted_by"] = p["created_at"], p["created_by"]
    touches = [p["created_at"]] + [r[0] for r in p["reviews"]]
    updated_by = p["created_by"]
    if p["resubmit_at"]:
        touches.append(p["resubmit_at"])
        updated_by = p["resubmit_by"]
    if p["archive_at"]:
        touches.append(p["archive_at"])
    if p["ai_flagged_at"]:
        touches.append(p["ai_flagged_at"])
    p["updated_at"] = max(touches)
    p["updated_by"] = updated_by
    if p["archive_at"]:
        p["updated_by"] = p["created_by"] if p["created_by"] in active_purchasers(p["archive_at"]) \
            else rng.choice(active_purchasers(p["archive_at"]))
