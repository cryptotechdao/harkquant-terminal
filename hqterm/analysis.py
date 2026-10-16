"""Market analysis: per-symbol bias from all live components, the dashboard summary, the red-folder list and the ticker."""
from __future__ import annotations

import logging
import threading
import time
from concurrent.futures import ThreadPoolExecutor

from . import bias, config, market, symbols
from .newsdesk import get_desk, LEVELS

log = logging.getLogger("hqterm.analysis")
POOL = ThreadPoolExecutor(max_workers=12, thread_name_prefix="hq-an")
OUTER = ThreadPoolExecutor(max_workers=4, thread_name_prefix="hq-sum")   # separate pool: analyze() submits to POOL

CCY = {"crypto": ("USD",), "metal": ("USD",), "index": ("USD",), "energy": ("USD",)}


def symbol_ccys(sym: str) -> tuple:
    p = symbols.get(sym)
    if p["kind"] == "fx":
        return (sym[:3], sym[3:6])
    if sym == "GER40":
        return ("EUR", "USD")
    return CCY.get(p["kind"], ("USD",))


def _safe(fn, *a):
    t0 = time.time()
    try:
        return dict(ok=True, data=fn(*a), ms=int((time.time() - t0) * 1000))
    except Exception as e:
        return dict(ok=False, error=f"{type(e).__name__}: {str(e)[:160]}", ms=int((time.time() - t0) * 1000))


def next_event(ccys, window_min: float) -> dict | None:
    cal = get_desk().calendar(range_="upcoming", impact="High", currency=",".join(ccys))
    now = time.time()
    for e in cal["events"]:
        if e["ts"] >= now - 300:
            mins = (e["ts"] - now) / 60
            if mins <= window_min:
                return dict(title=e["title"], country=e["country"], time_pkt=e["time_pkt"], minutes=round(mins, 1), ts=e["ts"])
            return None
    return None


def analyze(sym: str, light: bool = False) -> dict:
    s = config.load_settings()["bias"]
    p = symbols.get(sym)
    tfs = tuple(s.get("rating_tfs") or ("15m", "1h", "4h"))
    jobs = {"technical": POOL.submit(_safe, market.ratings, sym, tfs), "flow": POOL.submit(_safe, market.flow, sym)}
    jobs["orderbook"] = POOL.submit(_safe, market.orderbook, sym)
    jobs["positioning"] = POOL.submit(_safe, market.positioning, sym)
    res = {k: f.result(timeout=40) for k, f in jobs.items()}
    scores, status = {}, {}
    for k, r in res.items():
        if r["ok"]:
            d = r["data"]
            if isinstance(d, dict) and d.get("available") is False:
                status[k] = dict(status="n/a", note=d.get("note"))
                continue
            sc = d.get("score") if isinstance(d, dict) else None
            scores[k] = sc
            status[k] = dict(status="ok" if sc is not None else "n/a", proxy=bool(d.get("proxy")), stale=bool(d.get("stale")))
        else:
            status[k] = dict(status="fail", error=r["error"])
    desk = get_desk()
    sent = desk.sentiment(6)
    if p["news"] and p["news"] in sent and sent[p["news"]]["n"] > 0:
        scores["news"] = sent[p["news"]]["score"]
        status["news"] = dict(status="ok", n=sent[p["news"]]["n"], tag=p["news"],
                              proxy=p["kind"] == "crypto" and sym != "BTCUSD")
    else:
        status["news"] = dict(status="n/a", note="News Desk tags direction for XAU and BTC only" if not p["news"] else "no tagged headlines in 6h")
    fng = sent.get("crypto_fear_greed")
    if p["kind"] == "crypto" and fng:
        scores["fear_greed"] = (fng["value"] - 50) * 2
        status["fear_greed"] = dict(status="ok", value=fng["value"], label=fng["label"])
    else:
        status["fear_greed"] = dict(status="n/a")
    ev = None
    try:
        ev = next_event(symbol_ccys(sym), s["event_window_min"])
    except Exception as e:
        log.debug("calendar: %s", e)
    out = bias.combine(scores, s["weights"], s["neutral_band"], ev, s["event_dampen"])
    out.update(symbol=p["symbol"], name=p["name"], kind=p["kind"], notes=p["notes"], status=status, as_of=time.time())
    out["details"] = {k: (r["data"] if r["ok"] else None) for k, r in res.items()}
    if light:
        for k in ("flow", "orderbook", "positioning"):
            d = out["details"].get(k)
            if isinstance(d, dict):
                d.pop("depth", None); d.pop("long_short", None); d.pop("oi_usd", None)
                for v in d.get("venues", []):
                    v.pop("series", None)
    return out


_sum_cache: dict = {}
_sum_lock = threading.Lock()


def red_folder(limit: int = 12) -> list[dict]:
    cal = get_desk().calendar(range_="week", impact="High")
    now = time.time()
    ev = [e for e in cal["events"] if e["ts"] >= now - 12 * 3600]   # keep today's released ones (dimmed) for context
    out = []
    for e in ev[:limit]:
        out.append(dict(e, seconds_to=int(e["ts"] - now)))
    return out


def summary() -> dict:
    with _sum_lock:
        hit = _sum_cache.get("s")
        if hit and time.time() - hit[0] < 15:
            return hit[1]
    syms = ["BTCUSD", "XAUUSD"]
    futs = {s_: OUTER.submit(_safe, analyze, s_, True) for s_ in syms}
    an = {}
    for k, f in futs.items():
        r = f.result(timeout=60)
        an[k] = r["data"] if r["ok"] else dict(symbol=k, error=r["error"], label="N/A", score=0)
    desk = get_desk()
    sent = desk.sentiment(6)
    top = desk.feed(impact="HIGH+", limit=8)["items"]
    calsum = desk.calendar(range_="week")
    out = dict(analysis=an, sentiment=sent, red_folder=red_folder(), top_news=top,
               next_high=calsum.get("next_high"), as_of=time.time())
    out["narrative"] = narrative(out)
    with _sum_lock:
        _sum_cache["s"] = (time.time(), out)
    return out


def narrative(s: dict) -> list[str]:
    """Plain-language lines built only from the numbers in the summary (no LLM)."""
    lines = []
    for sym, a in s["analysis"].items():
        if a.get("error"):
            lines.append(f"{sym}: analysis unavailable ({a['error'][:60]}).")
            continue
        parts = []
        for c in a["components"]:
            if c["used"] and abs(c["score"]) >= 20:
                parts.append(f"{c['id']} {'+' if c['score'] > 0 else ''}{c['score']:.0f}")
        ev = a.get("event")
        evs = f" Event risk: {ev['country']} {ev['title']} in {ev['minutes']:.0f} min (score damped)." if ev else ""
        lines.append(f"{sym} {a['label']} {a['score']:+.0f} ({a['agreement']} components agree)"
                     + (f": {', '.join(parts)}." if parts else ".") + evs)
    nh = s.get("next_high")
    if nh:
        lines.append(f"Next red-folder event: {nh['country']} {nh['title']} at {nh['time_pkt']}.")
    fng = (s.get("sentiment") or {}).get("crypto_fear_greed")
    if fng:
        lines.append(f"Crypto Fear & Greed {fng['value']} ({fng['label']}).")
    return lines


def ticker() -> dict:
    t = config.load_settings()["ticker"]
    desk = get_desk()
    now = time.time()
    lvl = t["headline_min_impact"] if t["headline_min_impact"] in LEVELS else "MEDIUM"
    items = desk.feed(impact=lvl + "+", limit=60)["items"]
    max_age = t["headline_max_age_min"] * 60
    heads = [dict(kind="news", id=i["id"], text=i["title"], source=i["label"] or i["source_name"], impact=i["impact"],
                  ts=i["ts"], time_pkt=i["time_pkt"], expires=i["ts"] + max_age, url=i["url"], assets=i["assets"],
                  direction=i["direction"])
             for i in items if now - i["ts"] < max_age]
    heads.sort(key=lambda x: (-LEVELS.index(x["impact"]), -x["ts"]))
    heads = sorted(heads[:20], key=lambda x: -x["ts"])
    cal = desk.calendar(range_="upcoming", impact="High,Medium")["events"]
    up = [e for e in cal if e["ts"] > now]
    high = [e for e in up if e["impact"] == "High"]
    # High-impact first; pad with Medium (shown amber, not red) when fewer red-folder events remain in the FF week
    pick = high[:20] + [e for e in up if e["impact"] == "Medium"][:max(0, t["events"] - len(high))]
    pick.sort(key=lambda e: e["ts"])
    evs = [dict(kind="event", id=e["id"], text=f"{e['country']} {e['title']}", impact=e["impact"].upper(), ts=e["ts"],
                time_pkt=e["time_pkt"], expires=e["ts"], forecast=e["forecast"], previous=e["previous"],
                red=e["impact"] == "High") for e in pick]
    return dict(headlines=heads, events=evs, high_remaining=len(high), settings=t, server_time=now,
                calendar_note="ForexFactory publishes one week at a time (Sun-Sat); next week's events appear on Sunday.")
