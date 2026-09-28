"""
種子資料產生器的「計算引擎」：逐一移植後端 Java 的計分／檔期／天氣／Gate 邏輯，
讓種子資料裡的 product_evaluations、review_records 快照與系統自己算出來的結果一致。

移植來源（Product_Selection_260813 後端）：
  - ScoringService.calculateEvaluation / calculateDataCompleteness / composeFinalScore
  - ProductFactorScorer（七因子）、HistoricalScoreCalculator、ScoringAlgorithms
  - CampaignOccurrenceResolver / CampaignUrgencyCalculator / RegionCoverageCalculator
  - LunarCalendarService / SolarTermCalculator（讀同一份 classpath:campaign/*.csv）
  - WeatherNormalizer.classifyDay / WeatherBoostService.evaluate
  - GateEvaluationService（五個 Gate）
"""
from __future__ import annotations

import csv
import math
import os
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta
from decimal import Decimal, ROUND_HALF_UP

HERE = os.path.dirname(os.path.abspath(__file__))
D = Decimal
HUNDRED = D(100)


def q(value, places=2):
    """BigDecimal.setScale(places, HALF_UP)。"""
    if value is None:
        return None
    return D(value).quantize(D(1).scaleb(-places), rounding=ROUND_HALF_UP)


def div(a, b, places=6):
    return (D(a) / D(b)).quantize(D(1).scaleb(-places), rounding=ROUND_HALF_UP)


# ---------------------------------------------------------------------------
# 農曆／節氣（LunarCalendarService / SolarTermCalculator）
# ---------------------------------------------------------------------------
_LUNAR = {}
_SOLAR = {}


def _load_calendars():
    with open(os.path.join(HERE, "lunar-calendar-2000-2099.csv"), encoding="utf-8") as f:
        for row in csv.reader(f):
            if not row or row[0].startswith("#"):
                continue
            year, new_year, leap, pattern = int(row[0]), date.fromisoformat(row[1]), int(row[2]), row[3]
            _LUNAR[year] = (new_year, leap, [30 if c == "L" else 29 for c in pattern])
    with open(os.path.join(HERE, "solar-terms-2000-2099.csv"), encoding="utf-8") as f:
        for row in csv.reader(f):
            if not row or row[0].startswith("#"):
                continue
            _SOLAR[int(row[0])] = {"QINGMING": date.fromisoformat(row[1]), "DONGZHI": date.fromisoformat(row[2])}


_load_calendars()


def lunar_to_gregorian(cycle_year, month, day):
    new_year, leap, lengths = _LUNAR[cycle_year]
    index = month - 1 + (1 if leap > 0 and month > leap else 0)
    before = sum(lengths[:index])
    return new_year + timedelta(days=before + min(day, lengths[index]) - 1)


# ---------------------------------------------------------------------------
# 節慶／季節檔期（CampaignOccurrenceResolver 等）
# ---------------------------------------------------------------------------
@dataclass
class Campaign:
    id: int
    code: str
    name: str
    category: str                 # FESTIVAL / SEASON
    rule_type: str                # FIXED_DATE / NTH_WEEKDAY / LUNAR_DATE / SOLAR_TERM
    month: int | None = None
    day: int | None = None
    week_ordinal: int | None = None
    weekday: int | None = None    # ISO 1=Mon..7=Sun
    solar_term: str | None = None
    offset: int = 0
    duration: int | None = None   # FESTIVAL
    end_month: int | None = None  # SEASON
    end_day: int | None = None
    observed_rule: str = "NONE"
    expand_long_weekend: bool = False
    lead_days: int = 30
    manual_override: bool = False
    manual_override_cycle: int | None = None
    stored_status: str = "UPCOMING"
    regions: list = field(default_factory=list)
    tags: list = field(default_factory=list)       # [(tag, tier)]
    overrides: dict = field(default_factory=dict)  # cycle -> (start, end, note)


def _clamp_day(year, month, day):
    import calendar
    return date(year, month, min(day, calendar.monthrange(year, month)[1]))


def _is_weekend(d):
    return d.weekday() >= 5


def _tw_observed_days(start, end):
    observed = set()
    d = start
    while d <= end:
        if d.weekday() == 5:
            step = -1
        elif d.weekday() == 6:
            step = 1
        else:
            d += timedelta(days=1)
            continue
        cand = d + timedelta(days=step)
        while _is_weekend(cand) or (start <= cand <= end) or cand in observed:
            cand += timedelta(days=step)
        observed.add(cand)
        d += timedelta(days=1)
    return sorted(observed)


def _expand_long_weekend(start, end, observed):
    holidays = set(observed)
    d = start
    while d <= end:
        holidays.add(d)
        d += timedelta(days=1)
    low, high = min(holidays), max(holidays)
    while _is_weekend(low - timedelta(days=1)) or (low - timedelta(days=1)) in holidays:
        low -= timedelta(days=1)
    while _is_weekend(high + timedelta(days=1)) or (high + timedelta(days=1)) in holidays:
        high += timedelta(days=1)
    return low, high


def _anchor(c: Campaign, cycle):
    if c.rule_type == "FIXED_DATE":
        return _clamp_day(cycle, c.month, c.day)
    if c.rule_type == "NTH_WEEKDAY":
        first = date(cycle, c.month, 1)
        target = c.weekday - 1  # python weekday 0=Mon
        if c.week_ordinal == -1:
            import calendar
            last = date(cycle, c.month, calendar.monthrange(cycle, c.month)[1])
            while last.weekday() != target:
                last -= timedelta(days=1)
            return last
        d = first
        while d.weekday() != target:
            d += timedelta(days=1)
        return d + timedelta(weeks=c.week_ordinal - 1)
    if c.rule_type == "LUNAR_DATE":
        return lunar_to_gregorian(cycle, c.month, c.day)
    if c.rule_type == "SOLAR_TERM":
        return _SOLAR[cycle][c.solar_term]
    raise ValueError(c.rule_type)


@dataclass
class Occurrence:
    cycle: int
    start: date
    end: date
    overridden: bool


def compute_occurrence(c: Campaign, cycle) -> Occurrence:
    if cycle in c.overrides:
        s, e, _ = c.overrides[cycle]
        return Occurrence(cycle, s, e, True)
    anchor = _anchor(c, cycle) + timedelta(days=c.offset)
    if c.category == "SEASON":
        end_year = cycle + 1 if (c.end_month * 100 + c.end_day) < (c.month * 100 + c.day) else cycle
        return Occurrence(cycle, anchor, _clamp_day(end_year, c.end_month, c.end_day), False)
    start = anchor
    end = anchor + timedelta(days=c.duration - 1)
    observed = _tw_observed_days(start, end) if c.observed_rule == "TW_STATUTORY" else []
    if c.expand_long_weekend:
        start, end = _expand_long_weekend(start, end, observed)
    return Occurrence(cycle, start, end, False)


def resolve_current_or_next(c: Campaign, today: date):
    cands = [compute_occurrence(c, y) for y in (today.year - 1, today.year, today.year + 1)]
    cands = [o for o in cands if o.end >= today]
    return min(cands, key=lambda o: o.start) if cands else None


def derive_status(c: Campaign, occ: Occurrence, today: date):
    if c.manual_override and c.manual_override_cycle == occ.cycle and c.stored_status:
        return c.stored_status, "MANUAL"
    lead = c.lead_days if c.lead_days and c.lead_days > 0 else 0
    if today < occ.start - timedelta(days=lead):
        return "UPCOMING", "AUTO"
    if today < occ.start:
        return "PREPARING", "AUTO"
    if today <= occ.end:
        return "ACTIVE", "AUTO"
    return "EXPIRED", "AUTO"


def region_coverage(regions, weights):
    if not regions:
        return D("1.0000")
    total = sum((weights[r] for r in dict.fromkeys(regions) if r in weights), D(0))
    ratio = q(total / HUNDRED, 4)
    return D("1.0000") if ratio > 1 else ratio


TIER_WEIGHT = {"CORE": D("1.0"), "GENERAL": D("0.6"), "WEAK": D("0.3")}
BOOST_CAP = D(5)


def urgency(c: Campaign, status, occ_start, today, coverage):
    if status == "ACTIVE":
        tf = D(1)
    elif status != "PREPARING":
        tf = D(0)
    elif c.category == "SEASON":
        tf = D("0.20")
    else:
        lead = c.lead_days if c.lead_days and c.lead_days > 0 else 1
        remaining = max((occ_start - today).days, 0)
        ratio = q(D(remaining) / D(lead), 4)
        tf = min(max(D(1) - ratio, D(0)), D(1))
    cov = coverage if coverage is not None else D(1)
    return q(tf, 2), cov, q(tf * cov, 2)


def match_campaign(product_tags, campaigns, today, region_weights):
    """ScoringService.matchCampaign()：回傳 MatchedCampaignSnapshot 的 dict 或 None。"""
    if not product_tags:
        return None
    best = None
    best_boost = D(0)
    for c in campaigns:
        occ = resolve_current_or_next(c, today)
        if occ is None:
            continue
        status, _ = derive_status(c, occ, today)
        if status not in ("PREPARING", "ACTIVE"):
            continue
        regions = list(c.regions) if c.category == "SEASON" else []
        cov = region_coverage(regions, region_weights)
        matched = [t for t, _ in c.tags if t in product_tags]
        if not matched:
            continue
        # 同一檔期取命中標籤中最高等級（同等級取先出現者，與 Java 迴圈 > 比較一致）
        best_tier_w = D(0)
        for t, tier in c.tags:
            if t in product_tags and TIER_WEIGHT[tier] > best_tier_w:
                best_tier_w = TIER_WEIGHT[tier]
        tf, cov2, urg = urgency(c, status, occ.start, today, cov)
        boost = best_tier_w * urg * BOOST_CAP
        if boost > best_boost:
            best_boost = boost
            best = {
                "campaignId": c.id,
                "campaignName": c.name,
                "matchedTags": matched,
                "matchWeight": best_tier_w,
                "urgencyFactor": urg,
                "category": c.category,
                "cycleYear": occ.cycle,
                "occurrenceStartDate": occ.start.isoformat(),
                "occurrenceEndDate": occ.end.isoformat(),
                "occurrenceOverridden": occ.overridden,
                "regions": regions,
                "regionCoverageRatio": cov2,
                "timeFactor": tf,
            }
    return best


def festival_boost(snapshot):
    if snapshot is None:
        return D("0.00")
    return q(snapshot["matchWeight"] * snapshot["urgencyFactor"] * BOOST_CAP, 2)


def compose_final(total, fb, wb):
    if total is None:
        return None
    return q(D(total) + (fb or 0) + (wb or 0), 2)


# ---------------------------------------------------------------------------
# 天氣（WeatherNormalizer / WeatherBoostService）
# ---------------------------------------------------------------------------
def classify_day(m, observed):
    types = []
    tmax, tmin, hum = m.get("tmax"), m.get("tmin"), m.get("hum")
    if tmax is not None:
        tmean = tmax if tmin is None else (tmax + tmin) / 2.0
        if tmax >= 35.0:
            types.append("HOT")
        elif tmax >= 33.0 and hum is not None and hum >= 75.0:
            types.append("HUMID_HOT")
        elif hum is not None and hum >= 80.0:
            types.append("HUMID")
        elif tmean is not None and tmean <= 14.0:
            types.append("COLD")
        elif tmean is not None and tmean <= 19.0:
            types.append("DRY_COOL" if (hum is not None and hum < 60.0) else "COOL")
    amount = m.get("rain")
    prob = m.get("prob")
    if prob is None and observed:
        prob = 100.0
    if prob is not None and amount is not None:
        if prob >= 70.0 and amount >= 40.0:
            types.append("HEAVY_RAIN")
        elif prob >= 60.0 and amount >= 5.0:
            types.append("RAINY")
    if m.get("wind") is not None and m["wind"] >= 50.0:
        types.append("STRONG_WIND")
    return types


def weather_boost(product_tags, today, weather, tag_weights, region_weights,
                  hist_w=D("60.00"), fc_w=D("40.00"), cap=D("5.00"),
                  history_days=30, forecast_days=14):
    """weather: {region: {date: metrics dict}}（其中 today 以後視為預報）。"""
    history_from = today - timedelta(days=history_days)
    forecast_to = today + timedelta(days=forecast_days - 1)
    tags = set(product_tags)
    matched = set()
    data_updated_at = None

    def window(frm, to):
        weighted_sum = D(0)
        weight_total = D(0)
        days_with = set()
        for region, days in weather.items():
            day_sum = D(0)
            count = 0
            for d, m in days.items():
                if d < frm or d > to:
                    continue
                if d < history_from or d > forecast_to:
                    continue
                observed = d < today
                best = D(0)
                for t in classify_day(m, observed):
                    for tag, w in tag_weights.get(t, {}).items():
                        if tag in tags:
                            matched.add(tag)
                            best = max(best, w)
                day_sum += best
                count += 1
                days_with.add(d)
            if count == 0:
                continue
            region_score = q(day_sum * HUNDRED / D(count), 4)
            w = region_weights.get(region, D(0))
            weighted_sum += region_score * w
            weight_total += w
        if not days_with:
            return None, 0
        if weight_total == 0:
            return D("0.00"), len(days_with)
        return q(weighted_sum / weight_total, 2), len(days_with)

    hist, hist_days = window(history_from, today - timedelta(days=1))
    fc, fc_days = window(today, forecast_to)
    if hist is None and fc is None:
        combined = None
    elif hist is None:
        combined = fc
    elif fc is None:
        combined = hist
    else:
        combined = q((hist * hist_w + fc * fc_w) / HUNDRED, 2)
    boost = D("0.00") if combined is None else q(combined * cap / HUNDRED, 2)
    for region, days in weather.items():
        for d, m in days.items():
            if history_from <= d <= forecast_to:
                fa = m.get("fetched_at")
                if fa and (data_updated_at is None or fa > data_updated_at):
                    data_updated_at = fa
    return {
        "historyScore": hist,
        "forecastScore": fc,
        "combinedScore": combined,
        "historyWeightPercentage": hist_w,
        "forecastWeightPercentage": fc_w,
        "boostCap": cap,
        "weatherBoost": boost,
        "matchedTags": sorted(matched),
        "historyDays": hist_days,
        "forecastDays": fc_days,
        "historyFrom": history_from.isoformat(),
        "forecastTo": forecast_to.isoformat(),
        "dataUpdatedAt": data_updated_at.isoformat() if data_updated_at else None,
    }


def combine_check(hist, fc, hw, fw):
    # WeatherBoostService.combine() 的剩餘分支（兩者皆非 null）已內嵌於 weather_boost()
    return q((hist * hw + fc * fw) / HUNDRED, 2)


# ---------------------------------------------------------------------------
# 評分（ProductFactorScorer / HistoricalScoreCalculator / ScoringService）
# ---------------------------------------------------------------------------
def normalize_by_band(value, lower, upper):
    if value is None:
        return None
    score = div(D(value) - lower, upper - lower) * HUNDRED
    return q(min(max(score, D(0)), HUNDRED), 6)


def normalize_by_band_log(value, lower, upper):
    if value is None:
        return None
    rng = float(upper - lower)
    offset = float(D(value) - lower)
    if offset <= 0:
        return q(0, 6)
    score = D(repr(math.log1p(offset) / math.log1p(rng) * 100))
    return q(min(max(score, D(0)), HUNDRED), 6)


def shrink(raw_rate, n, prior, k):
    if n <= 0 or raw_rate is None:
        return prior
    w = div(D(n), D(n) + D(k))
    return q(raw_rate * w + prior * (D(1) - w), 6)


def freshness_decay(raw, days_ago, half_life, neutral):
    eff = max(0, days_ago)
    freshness = q(D(repr(math.pow(0.5, eff / half_life))), 6)
    return q(raw * freshness + neutral * (D(1) - freshness), 6)


def weighted_average(pairs):
    num = D(0)
    den = D(0)
    for score, weight in pairs:
        if score is None or weight is None or weight <= 0:
            continue
        num += score * weight
        den += weight
    if den == 0:
        return None
    return div(num, den)


def data_completeness(p):
    def filled(v):
        return v is not None and str(v).strip() != ""
    n = 0
    n += 1 if filled(p["name"]) else 0
    n += 1 if p["product_type_id"] is not None else 0
    n += 1 if filled(p["supplier_name"]) else 0
    n += 1 if filled(p["campaign_tags"]) else 0
    n += 1 if p["moq"] is not None else 0
    n += 1 if p["supply_stability"] is not None else 0
    n += 1 if p["price_competitiveness"] is not None else 0
    if p["pricing_type"] == "NEW":
        total = 8
        n += 1 if filled(p["target_customer_description"]) else 0
    else:
        total = 10
        n += 1 if p["cost_price"] is not None else 0
        n += 1 if p["sale_price"] is not None else 0
        n += 1 if p["market_price"] is not None else 0
    return q(div(D(n), D(total), 4) * HUNDRED, 2)


FACTORS = ["MARGIN_RATE", "DISCOUNT_DEPTH", "SUPPLY_STABILITY", "AUDIENCE_MATCH",
           "HISTORY_FULFILLMENT", "PURCHASE_RATE", "TREND_HEAT"]
BUSINESS_GROUP = ["MARGIN_RATE", "DISCOUNT_DEPTH", "SUPPLY_STABILITY"]
FORECAST_GROUP = ["PURCHASE_RATE", "TREND_HEAT"]


@dataclass
class ScoringContext:
    bands: dict               # factor -> (lower, upper) 全域區間（本種子不建立品類區間）
    audience_keywords: list   # 生效中第一筆客群的關鍵字（已小寫、去重）
    group_buys: list          # [{product_type_id, product_id, result, imported_at}]
    trends: dict              # product_id -> [signal dict sorted by collected_at]
    shrink_k_category: int = 10
    shrink_k_product: int = 5
    half_life: int = 14
    neutral: D = D(50)
    freight: D = D(0)         # freight_cost_* 系統預設皆為 0


def history_score(p, ctx: ScoringContext, as_of: datetime):
    recs = [r for r in ctx.group_buys if r["imported_at"] <= as_of]
    eff = [r for r in recs if r["result"] in ("FULFILLED", "FAILED")]
    global_rate = div(sum(1 for r in eff if r["result"] == "FULFILLED"), len(eff)) if eff else D("0.5")
    cat = [r for r in eff if r["product_type_id"] == p["product_type_id"]]
    cat_raw = div(sum(1 for r in cat if r["result"] == "FULFILLED"), len(cat)) if cat else None
    cat_adj = shrink(cat_raw, len(cat), global_rate, ctx.shrink_k_category)
    hist_pid = p.get("resale_reference_product_id") or p["id"]
    # 商品層：以「認領時間」判斷當時是否已連到這個商品（認領前 product_id 仍為 NULL）
    prod = [r for r in eff if r["product_id"] == hist_pid and r.get("claimed_at") and r["claimed_at"] <= as_of]
    prod_raw = div(sum(1 for r in prod if r["result"] == "FULFILLED"), len(prod)) if prod else None
    final = shrink(prod_raw, len(prod), cat_adj, ctx.shrink_k_product)
    includes_sim = any(r["product_type_id"] == p["product_type_id"] and r["is_simulated"] for r in recs)
    return q(final * HUNDRED, 2), len(cat), len(prod), includes_sim


def latest_trend(pid, ctx, as_of):
    latest = None
    for s in ctx.trends.get(pid, []):
        if s["collected_at"] <= as_of:
            latest = s
        else:
            break
    return latest


def score_all(p, ctx: ScoringContext, as_of: datetime):
    s = {}
    cost, sale, market = p["cost_price"], p["sale_price"], p["market_price"]
    if cost is not None and sale is not None and sale > 0:
        rate = div(sale - cost - ctx.freight, sale)
        lo, hi = ctx.bands["MARGIN_RATE"]
        s["MARGIN_RATE"] = normalize_by_band(rate, lo, hi)
    else:
        s["MARGIN_RATE"] = None
    if market is not None and sale is not None and market > 0:
        rate = div(market - sale, market)
        lo, hi = ctx.bands["DISCOUNT_DEPTH"]
        s["DISCOUNT_DEPTH"] = normalize_by_band(rate, lo, hi)
    else:
        s["DISCOUNT_DEPTH"] = None
    ss = p["supply_stability"]
    s["SUPPLY_STABILITY"] = None if ss is None else min(max(D(ss) * 20, D(0)), HUNDRED)
    if ctx.audience_keywords:
        text = ((p["target_customer_description"] or "") + " " + (p["name"] or "")).lower()
        matched = sum(1 for k in ctx.audience_keywords if k in text)
        s["AUDIENCE_MATCH"] = div(matched, len(ctx.audience_keywords)) * HUNDRED
    else:
        s["AUDIENCE_MATCH"] = None
    s["HISTORY_FULFILLMENT"] = history_score(p, ctx, as_of)[0]
    r = p["estimated_purchase_rate"]
    s["PURCHASE_RATE"] = None if r is None else min(max(D(r) * HUNDRED, D(0)), HUNDRED)
    sig = latest_trend(p["id"], ctx, as_of)
    if sig is None:
        s["TREND_HEAT"] = None
    else:
        present = [v for v in (sig["trend_score"], sig["popularity_score"]) if v is not None]
        raw = div(sum(present, D(0)), len(present)) if present else None
        if raw is None:
            s["TREND_HEAT"] = None
        else:
            days_ago = (as_of - sig["collected_at"]).days  # ChronoUnit.DAYS.between：捨去不足一天
            s["TREND_HEAT"] = freshness_decay(raw, days_ago, ctx.half_life, ctx.neutral)
    return s


def evaluate(p, weights, ctx: ScoringContext, as_of: datetime):
    """ScoringService.calculateEvaluation() 的純函數版本（不含加成）。"""
    dc = data_completeness(p)
    result = {"data_completeness": dc, "total": None, "business": None, "audience": None,
              "historical": None, "purchase": None, "trend": None, "forecast": None, "factors": None}
    if dc < 60:
        return result
    scores = score_all(p, ctx, as_of)
    total = weighted_average([(scores.get(f), weights.get(f)) for f in FACTORS])
    if total is None:
        return result

    def group(codes):
        v = weighted_average([(scores.get(c), weights.get(c)) for c in codes])
        return None if v is None else q(v, 2)

    result.update({
        "total": q(total, 2),
        "business": group(BUSINESS_GROUP),
        "audience": q(scores["AUDIENCE_MATCH"], 2) if scores["AUDIENCE_MATCH"] is not None else None,
        "historical": scores["HISTORY_FULFILLMENT"],
        "purchase": q(scores["PURCHASE_RATE"], 2) if scores["PURCHASE_RATE"] is not None else None,
        "trend": q(scores["TREND_HEAT"], 2) if scores["TREND_HEAT"] is not None else None,
        "forecast": group(FORECAST_GROUP),
        "factors": scores,
    })
    return result


# ---------------------------------------------------------------------------
# Gate（GateEvaluationService）
# ---------------------------------------------------------------------------
SHELF_MIN = {"D7": 0, "D8_30": 8, "D31_90": 31, "D90_PLUS": 90}
SHELF_LABEL = {"D7": "7天內", "D8_30": "8-30天", "D31_90": "31-90天", "D90_PLUS": "90天以上", "NA": "不適用"}
LEAD_MAX = {"D3": 3, "D4_7": 7, "D8_14": 14, "D15_PLUS": None}
LEAD_LABEL = {"D3": "3天內", "D4_7": "4-7天", "D8_14": "8-14天", "D15_PLUS": "15天以上"}
TEMP_LABEL = {"NORMAL": "常溫", "CHILLED": "冷藏", "FROZEN": "冷凍"}


def percentile(values, pct):
    s = sorted(float(v) for v in values)
    if len(s) == 1:
        return q(D(repr(s[0])), 6)
    pos = (pct / 100.0) * (len(s) - 1)
    lo, hi = math.floor(pos), math.ceil(pos)
    frac = pos - lo
    return q(D(repr(s[lo] + frac * (s[hi] - s[lo]))), 6)


def strip_zeros(d: D):
    t = format(d.normalize(), "f")
    return t


def resolve_moq(p, types, global_default=1):
    if p["moq"] is not None:
        return p["moq"], "PRODUCT"
    leaf = types[p["product_type_id"]]
    root = types[leaf["parent_id"]] if leaf["parent_id"] else leaf
    for t in (leaf, root):
        if t.get("default_moq") is not None:
            return t["default_moq"], "PRODUCT_TYPE"
    return global_default, "GLOBAL_DEFAULT"


def resolve_attr(p, types, key):
    leaf = types[p["product_type_id"]]
    root = types[leaf["parent_id"]] if leaf["parent_id"] else leaf
    for t in (leaf, root):
        if t.get(key) is not None:
            return t[key]
    return None


def evaluate_gates(p, types, group_buys, dc, matched, today, as_of, min_sample=5, pct=75, safety=D("1.0"),
                   global_shelf=21):
    results = []
    # 1. MOQ
    moq, _ = resolve_moq(p, types)
    qty = sorted(r["actual_quantity"] for r in group_buys
                 if r["product_type_id"] == p["product_type_id"] and r["result"] == "FULFILLED"
                 and r["imported_at"] <= as_of)
    if len(qty) < min_sample:
        results.append(("GATE_MOQ_FEASIBILITY", "INSUFFICIENT_DATA",
                        "該品類成團案例僅 %d 筆（需 %d 筆以上），樣本不足無法判斷集單可行性" % (len(qty), min_sample)))
    else:
        bench = percentile(qty, pct)
        threshold = bench * safety
        if D(moq) <= threshold:
            results.append(("GATE_MOQ_FEASIBILITY", "PASSED",
                            "最低訂購量 %d，低於該品類 P%d 集單基準 %s，集單可行性合理" % (moq, pct, strip_zeros(bench))))
        else:
            results.append(("GATE_MOQ_FEASIBILITY", "FAILED",
                            "最低訂購量 %d 高於該品類 P%d 集單基準 %s（共 %d 筆成團案例），歷史上鮮少達到此量，集單失敗風險高"
                            % (moq, pct, strip_zeros(bench), len(qty))))
    # 2. Lead time
    if matched is None:
        results.append(("GATE_LEAD_TIME", "NOT_APPLICABLE", "商品未對應任何節慶檔期，無備貨期限壓力"))
    else:
        tier = p.get("supplier_lead_time_tier")
        name = matched["campaignName"]
        if not tier:
            results.append(("GATE_LEAD_TIME", "INSUFFICIENT_DATA", "未填寫供應商備貨前置期，無法判斷是否來得及趕上檔期"))
        else:
            days = (date.fromisoformat(matched["occurrenceStartDate"]) - today).days
            mx = LEAD_MAX[tier]
            if days < 0:
                results.append(("GATE_LEAD_TIME", "NOT_APPLICABLE", "「%s」已開始，備貨期限判斷不再適用" % name))
            elif mx is None:
                if days >= 15:
                    results.append(("GATE_LEAD_TIME", "PASSED",
                                    "距「%s」開始尚有 %d 天，前置期 %s 可能來得及，建議向供應商確認確切天數" % (name, days, LEAD_LABEL[tier])))
                else:
                    results.append(("GATE_LEAD_TIME", "FAILED",
                                    "距「%s」開始僅剩 %d 天，供應商前置期為 %s，備貨來不及" % (name, days, LEAD_LABEL[tier])))
            elif mx <= days:
                results.append(("GATE_LEAD_TIME", "PASSED",
                                "距「%s」開始尚有 %d 天，供應商前置期最長 %d 天，備貨來得及" % (name, days, mx)))
            else:
                results.append(("GATE_LEAD_TIME", "FAILED",
                                "距「%s」開始僅剩 %d 天，供應商前置期最長 %d 天，備貨來不及" % (name, days, mx)))
    # 3. Shelf life
    has_shelf = resolve_attr(p, types, "has_shelf_life")
    if has_shelf == 0:
        results.append(("GATE_SHELF_LIFE", "NOT_APPLICABLE", "此品類商品無效期概念，不需判斷效期充足性"))
    else:
        tier = p.get("shelf_life_tier") or resolve_attr(p, types, "default_shelf_life_tier")
        if not tier:
            results.append(("GATE_SHELF_LIFE", "INSUFFICIENT_DATA", "未填寫效期級距，且品類無預設值，無法判斷效期是否充足"))
        elif tier == "NA":
            results.append(("GATE_SHELF_LIFE", "NOT_APPLICABLE", "此商品標示為無效期概念，不需判斷效期充足性"))
        else:
            th = resolve_attr(p, types, "shelf_life_threshold_days")
            th = th if th is not None else global_shelf
            mn = SHELF_MIN[tier]
            if mn >= th:
                results.append(("GATE_SHELF_LIFE", "PASSED",
                                "效期 %s（最短 %d 天）達到品類門檻 %d 天" % (SHELF_LABEL[tier], mn, th)))
            else:
                results.append(("GATE_SHELF_LIFE", "FAILED",
                                "效期 %s（最短 %d 天）低於品類門檻 %d 天，商品送達消費者時可能所剩無幾，而團購幾乎無法退換"
                                % (SHELF_LABEL[tier], mn, th)))
    # 4. Temperature
    zone = p.get("temperature_zone") or resolve_attr(p, types, "default_temperature_zone")
    results.append(("GATE_TEMPERATURE_ZONE", "PASSED", "商品為%s品，通路支援此溫層配送" % TEMP_LABEL[zone]))
    # 5. Data completeness
    dct = strip_zeros(dc)
    if dc >= 60:
        results.append(("GATE_DATA_COMPLETENESS", "PASSED", "資料完整度 %s%%，達到 60%% 門檻" % dct))
    else:
        results.append(("GATE_DATA_COMPLETENESS", "FAILED", "資料完整度僅 %s%%，未達 60%% 門檻，評分結果參考價值有限" % dct))
    return results


def gate_summary_text(results):
    c = {"PASSED": 0, "FAILED": 0, "INSUFFICIENT_DATA": 0, "NOT_APPLICABLE": 0}
    for _, st, _ in results:
        c[st] += 1
    s = "通過 %d 項" % c["PASSED"]
    if c["FAILED"]:
        s += "，不通過 %d 項" % c["FAILED"]
    if c["INSUFFICIENT_DATA"]:
        s += "，資料不足 %d 項" % c["INSUFFICIENT_DATA"]
    if c["NOT_APPLICABLE"]:
        s += "，不適用 %d 項" % c["NOT_APPLICABLE"]
    return s
