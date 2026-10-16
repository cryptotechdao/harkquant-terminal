"""Live market data: OKX (ccxt) for crypto and the OKX gold perp, Yahoo Finance futures for OTC markets,
tradingview-ta for TradingView's technical ratings. Read-only: no keys, no order functions anywhere.

Every public function returns a plain dict with `source`, `proxy` (True when the number stands in for something that
does not exist centrally, e.g. spot gold volume) and `note`. Failures raise; the API layer turns them into a
per-source status so one dead source never blanks the page.
"""
from __future__ import annotations

import logging
import math
import threading
import time
from functools import wraps

from . import symbols

log = logging.getLogger("hqterm.market")

_cache: dict = {}
_clock = threading.Lock()
STATUS: dict[str, dict] = {}       # source -> {status, last_ok, last_error, latency_ms}


def _mark(src: str, ok: bool, t0: float, err: Exception | None = None):
    st = STATUS.setdefault(src, {})
    st["latency_ms"] = int((time.time() - t0) * 1000)
    st["last_try"] = time.time()
    if ok:
        st.update(status="ok", last_ok=time.time(), last_error=None)
    else:
        st.update(status="fail", last_error=f"{type(err).__name__}: {str(err)[:160]}")


def cached(ttl: float, src: str, stale_s: float = 600):
    """TTL cache keyed by args; records source health; serves the last good value for `stale_s` if a refresh fails."""
    def deco(fn):
        @wraps(fn)
        def wrap(*a, **kw):
            key = (fn.__name__, a, tuple(sorted(kw.items())))
            now = time.time()
            with _clock:
                hit = _cache.get(key)
            if hit and now - hit[0] < ttl:
                return hit[1]
            t0 = time.time()
            try:
                v = fn(*a, **kw)
            except Exception as e:
                _mark(src, False, t0, e)
                if hit and now - hit[0] < stale_s:
                    out = dict(hit[1]); out["stale"] = True; out["error"] = str(e)[:160]
                    return out
                raise
            _mark(src, True, t0)
            with _clock:
                _cache[key] = (time.time(), v)
            return v
        return wrap
    return deco


_ex = None
_exlock = threading.Lock()


def okx():
    global _ex
    with _exlock:
        if _ex is None:
            import ccxt
            _ex = ccxt.okx({"timeout": 15000, "enableRateLimit": True})
        return _ex


TF_SECONDS = {"1m": 60, "5m": 300, "15m": 900, "30m": 1800, "1h": 3600, "4h": 14400, "1d": 86400}
YF_INTERVAL = {"1m": ("1m", "5d"), "5m": ("5m", "30d"), "15m": ("15m", "30d"), "30m": ("30m", "30d"),
               "1h": ("60m", "180d"), "4h": ("60m", "180d"), "1d": ("1d", "5y")}


def _f(x, d=None):
    try:
        v = float(x)
        return v if math.isfinite(v) else d
    except (TypeError, ValueError):
        return d


# ------------------------------------------------------------------------------------------------ bars
@cached(15, "okx")
def _okx_bars(sym: str, tf: str, limit: int):
    rows = okx().fetch_ohlcv(sym, tf, limit=limit)
    return {"bars": [dict(time=int(r[0] / 1000), open=r[1], high=r[2], low=r[3], close=r[4], volume=r[5] or 0) for r in rows]}


@cached(60, "yfinance")
def _yf_bars(ticker: str, tf: str, limit: int):
    import yfinance as yf
    iv, period = YF_INTERVAL[tf]
    df = yf.download(ticker, interval=iv, period=period, progress=False, auto_adjust=False, threads=False)
    if df is None or df.empty:
        raise RuntimeError(f"Yahoo returned no bars for {ticker}")
    if hasattr(df.columns, "levels"):
        df.columns = [c[0] if isinstance(c, tuple) else c for c in df.columns]
    if tf == "4h":
        df = df.resample("4h", origin="epoch").agg({"Open": "first", "High": "max", "Low": "min", "Close": "last", "Volume": "sum"}).dropna()
    df = df.dropna(subset=["Close"]).tail(limit)
    out = []
    for ts, r in df.iterrows():
        out.append(dict(time=int(ts.timestamp()), open=float(r["Open"]), high=float(r["High"]), low=float(r["Low"]),
                        close=float(r["Close"]), volume=float(r.get("Volume", 0) or 0)))
    return {"bars": out}


def bars(symbol: str, tf: str = "15m", limit: int = 500, source: str = "auto") -> dict:
    p = symbols.get(symbol)
    if tf not in TF_SECONDS:
        raise ValueError(f"tf must be one of {list(TF_SECONDS)}")
    limit = max(50, min(int(limit), 1500))
    use_okx = p["okx"] and source in ("auto", "okx")
    if use_okx:
        d = _okx_bars(p["okx"], tf, limit)
        label = f"OKX {p['okx']}"
        proxy = p["kind"] != "crypto"
        note = "OKX XAU-USDT perpetual (crypto-venue gold contract, tracks spot closely but is not the OTC spot market)" if proxy else ""
    else:
        if not p["yf"]:
            raise ValueError(f"no bar source for {symbol}")
        d = _yf_bars(p["yf"], tf, limit)
        label = f"Yahoo {p['yf']}"
        proxy = p["yf"].endswith("=F") and p["kind"] != "crypto"
        note = ("Futures price (contract basis vs spot)" if proxy else "") + (" · Yahoo intraday is delayed ~10-15 min for futures" if p["yf"].endswith("=F") else "")
    return dict(symbol=p["symbol"], tf=tf, source=label, proxy=proxy, note=note.strip(" ·"), bars=d["bars"], stale=d.get("stale", False))


# ------------------------------------------------------------------------------------------------ TradingView ratings
_TV_COOLDOWN = {"until": 0.0}


@cached(180, "tradingview-ta", stale_s=3600)
def _tv(sym: str, screener: str, exchange: str, tf: str):
    """TradingView's scanner rate-limits by IP (HTTP 429): cache 3 min, back off 5 min after a 429, serve stale up to 1h."""
    from tradingview_ta import TA_Handler
    if time.time() < _TV_COOLDOWN["until"]:
        raise RuntimeError(f"TradingView rate limit cool-down ({int(_TV_COOLDOWN['until'] - time.time())}s left)")
    try:
        a = TA_Handler(symbol=sym, screener=screener, exchange=exchange, interval=tf, timeout=10).get_analysis()
    except Exception as e:
        if "429" in str(e):
            _TV_COOLDOWN["until"] = time.time() + 300
        raise
    ind = a.indicators or {}
    keep = ["close", "RSI", "MACD.macd", "MACD.signal", "ADX", "Stoch.K", "CCI20", "EMA20", "EMA50", "EMA200", "SMA20",
            "SMA50", "SMA200", "ATR", "Pivot.M.Classic.Middle", "BB.upper", "BB.lower", "Mom", "AO"]
    return dict(summary=a.summary, oscillators=a.oscillators.get("RECOMMENDATION"), ma=a.moving_averages.get("RECOMMENDATION"),
                indicators={k: _f(ind.get(k)) for k in keep if k in ind})


def rating_score(summary: dict) -> float:
    b, s, n = summary.get("BUY", 0), summary.get("SELL", 0), summary.get("NEUTRAL", 0)
    tot = b + s + n
    return round(100 * (b - s) / tot, 1) if tot else 0.0


def ratings(symbol: str, tfs=("15m", "1h", "4h")) -> dict:
    p = symbols.get(symbol)
    if not p["tv"]:
        return dict(symbol=p["symbol"], available=False, source="tradingview-ta",
                    note="tradingview-ta has no screener match for this symbol", tfs={})
    out = {}
    errs = []
    for tf in tfs:
        try:
            r = _tv(*p["tv"], tf)
            out[tf] = dict(r, score=rating_score(r["summary"]))
        except Exception as e:
            errs.append(f"{tf}: {str(e)[:80]}")
    if not out:
        raise RuntimeError("tradingview-ta failed: " + "; ".join(errs))
    score = round(sum(v["score"] for v in out.values()) / len(out), 1)
    return dict(symbol=p["symbol"], available=True, source=f"TradingView technicals ({p['tv'][2]}:{p['tv'][0]})",
                tfs=out, score=score, errors=errs)


# ------------------------------------------------------------------------------------------------ flow (taker volume)
@cached(30, "okx")
def _rubik_spot(ccy: str, period: str):
    d = okx().publicGetRubikStatTakerVolume({"ccy": ccy, "instType": "SPOT", "period": period})["data"]
    return {"rows": [(int(r[0]) // 1000, _f(r[2], 0), _f(r[1], 0)) for r in d]}   # ts, buy, sell


@cached(30, "okx")
def _rubik_swap(inst: str, period: str):
    d = okx().publicGetRubikStatTakerVolumeContract({"instId": inst, "period": period, "unit": "2", "limit": "100"})["data"]
    return {"rows": [(int(r[0]) // 1000, _f(r[2], 0), _f(r[1], 0)) for r in d]}   # USD notional


@cached(8, "okx")
def _trades(sym: str):
    tr = okx().fetch_trades(sym, limit=500)
    # ccxt `cost` already applies the contract size for swaps (XAU-USDT-SWAP contracts are fractions of an ounce)
    return {"rows": [(t["timestamp"] / 1000, t["side"], _f(t.get("cost"), None) or _f(t["amount"], 0) * _f(t["price"], 0),
                      _f(t["price"], 0)) for t in tr]}


def _series(rows):
    rows = sorted(rows)
    cvd = 0.0
    out = []
    for ts, b, s in rows:
        cvd += b - s
        out.append(dict(time=ts, buy=round(b, 4), sell=round(s, 4), delta=round(b - s, 4), cvd=round(cvd, 4)))
    return out


def _window(series, seconds):
    if not series:
        return dict(buy=0, sell=0, delta=0, buy_pct=None)
    t_end = series[-1]["time"]
    w = [r for r in series if r["time"] > t_end - seconds]
    b = sum(r["buy"] for r in w); s = sum(r["sell"] for r in w)
    return dict(buy=round(b, 2), sell=round(s, 2), delta=round(b - s, 2), buy_pct=round(100 * b / (b + s), 1) if b + s else None)


def _clv_rows(bars_):
    """Estimated buy/sell split from bar shape (close-location value). Used only where no trade-side data exists."""
    out = []
    for b in bars_:
        rng = b["high"] - b["low"]; v = b["volume"] or 0
        if v <= 0:
            continue
        clv = ((b["close"] - b["low"]) - (b["high"] - b["close"])) / rng if rng > 0 else 0.0
        buy = v * (1 + clv) / 2
        out.append((b["time"], buy, v - buy))
    return out


def flow(symbol: str) -> dict:
    """Taker buy vs sell volume ("incoming vs outgoing"), delta and CVD."""
    p = symbols.get(symbol)
    res = dict(symbol=p["symbol"], kind=p["kind"], venues=[], proxy=p["kind"] != "crypto", note=p["notes"])
    if p["ccy"]:
        s = _series(_rubik_spot(p["ccy"], "5m")["rows"])
        res["venues"].append(dict(id="okx_spot", label=f"OKX spot taker volume ({p['ccy']}, coin units)", unit=p["ccy"],
                                  series=s, w15m=_window(s, 900), w1h=_window(s, 3600), w4h=_window(s, 14400), exact=True))
    if p["okx_swap"]:
        s = _series(_rubik_swap(p["okx_swap"], "5m")["rows"])
        res["venues"].append(dict(id="okx_swap", label=f"OKX {p['okx_swap']} perp taker volume (USD)", unit="USD",
                                  series=s, w15m=_window(s, 900), w1h=_window(s, 3600), w4h=_window(s, 14400), exact=True,
                                  proxy=p["kind"] != "crypto"))
    if p["okx"]:
        try:
            rows = _trades(p["okx"])["rows"]
            b = sum(r[2] for r in rows if r[1] == "buy"); s_ = sum(r[2] for r in rows if r[1] == "sell")
            span = (rows[0][0] - rows[-1][0]) if rows else 0
            res["tape"] = dict(trades=len(rows), span_s=round(abs(span)), buy_usd=round(b), sell_usd=round(s_),
                               buy_pct=round(100 * b / (b + s_), 1) if b + s_ else None,
                               last_price=rows[0][3] if rows else None, source=f"OKX {p['okx']} last 500 trades")
        except Exception as e:
            res["tape_error"] = str(e)[:120]
    if not res["venues"] and p["yf_vol"]:
        bb = _yf_bars(p["yf_vol"], "5m", 300)["bars"]
        s = _series(_clv_rows(bb))
        res["venues"].append(dict(id="futures_clv", label=f"{p['yf_vol']} futures volume, buy/sell ESTIMATED from bar shape (CLV)",
                                  unit="contracts", series=s[-120:], w15m=_window(s, 900), w1h=_window(s, 3600),
                                  w4h=_window(s, 14400), exact=False, proxy=True))
    if p["kind"] == "metal" and p["yf_vol"]:
        try:
            bb = _yf_bars(p["yf_vol"], "5m", 300)["bars"]
            s = _series(_clv_rows(bb))
            if not any(v["id"] == "futures_clv" for v in res["venues"]):
                res["venues"].append(dict(id="futures_clv", label=f"COMEX {p['yf_vol']} futures volume, buy/sell ESTIMATED from bar shape (CLV), Yahoo delayed",
                                          unit="contracts", series=s[-120:], w15m=_window(s, 900), w1h=_window(s, 3600),
                                          w4h=_window(s, 14400), exact=False, proxy=True))
        except Exception as e:
            res["futures_error"] = str(e)[:120]
    if not res["venues"]:
        res["available"] = False
        res["note"] = (p["notes"] + " No volume source for this symbol.").strip()
    else:
        res["available"] = True
        main = res["venues"][0]
        res["score"] = _flow_score(main)
    return res


def _flow_score(v) -> float | None:
    """-100..100 from the 1h taker buy share (50% = 0)."""
    bp = v["w1h"].get("buy_pct")
    if bp is None:
        return None
    return round(max(-100, min(100, (bp - 50) * 4)), 1)     # 62.5% buys -> +50, 75% -> +100


# ------------------------------------------------------------------------------------------------ order book
@cached(5, "okx")
def _book(sym: str):
    ob = okx().fetch_order_book(sym, 400)
    return {"bids": ob["bids"][:400], "asks": ob["asks"][:400]}


def orderbook(symbol: str) -> dict:
    p = symbols.get(symbol)
    if not p["okx"]:
        return dict(symbol=p["symbol"], available=False, note=p["notes"] or "No public order book for this market.")
    ob = _book(p["okx"])
    bids, asks = ob["bids"], ob["asks"]
    if not bids or not asks:
        raise RuntimeError("empty order book")
    mid = (bids[0][0] + asks[0][0]) / 2
    bands = {}
    for pct in (0.1, 0.25, 0.5, 1.0):
        lo, hi = mid * (1 - pct / 100), mid * (1 + pct / 100)
        bv = sum(px * q for px, q, *_ in bids if px >= lo); av = sum(px * q for px, q, *_ in asks if px <= hi)
        bands[f"{pct}%"] = dict(bid_usd=round(bv), ask_usd=round(av),
                                imbalance=round(100 * (bv - av) / (bv + av), 1) if bv + av else None)
    depth = dict(bids=[[px, q] for px, q, *_ in bids[:60]], asks=[[px, q] for px, q, *_ in asks[:60]])
    imb = bands["0.5%"]["imbalance"]
    return dict(symbol=p["symbol"], available=True, source=f"OKX {p['okx']} order book (400 levels)", mid=mid,
                spread=round(asks[0][0] - bids[0][0], 6), bands=bands, depth=depth,
                score=round(max(-100, min(100, (imb or 0) * 2)), 1) if imb is not None else None,
                proxy=p["kind"] != "crypto",
                note="OKX gold perpetual book, a proxy: spot gold has no central order book." if p["kind"] == "metal" else "")


# ------------------------------------------------------------------------------------------------ positioning
@cached(60, "okx")
def _ls(inst: str):
    d = okx().publicGetRubikStatContractsLongShortAccountRatioContract({"instId": inst, "period": "5m", "limit": "100"})["data"]
    return {"rows": sorted((int(r[0]) // 1000, _f(r[1])) for r in d)}


@cached(60, "okx")
def _funding(inst: str):
    d = okx().publicGetPublicFundingRate({"instId": inst})["data"][0]
    return {"rate": _f(d.get("fundingRate")), "next": int(d.get("nextFundingTime") or d.get("fundingTime") or 0) // 1000}


@cached(60, "okx")
def _oi(inst: str):
    d = okx().publicGetRubikStatContractsOpenInterestHistory({"instId": inst, "period": "5m", "limit": "100"})["data"]
    return {"rows": sorted((int(r[0]) // 1000, _f(r[3])) for r in d)}     # oiUsd


def positioning(symbol: str) -> dict:
    p = symbols.get(symbol)
    if not p["okx_swap"]:
        return dict(symbol=p["symbol"], available=False, note="No perpetual swap for this market, so no long/short ratio or funding.")
    out = dict(symbol=p["symbol"], available=True, source=f"OKX {p['okx_swap']}", proxy=p["kind"] != "crypto")
    ls = _ls(p["okx_swap"])["rows"]
    out["long_short"] = [dict(time=t, value=v) for t, v in ls]
    out["ls_now"] = ls[-1][1] if ls else None
    try:
        f = _funding(p["okx_swap"]); out["funding"] = f["rate"]; out["funding_next"] = f["next"]
    except Exception as e:
        out["funding_error"] = str(e)[:100]
    try:
        oi = _oi(p["okx_swap"])["rows"]
        out["oi_usd"] = [dict(time=t, value=v) for t, v in oi]
        if len(oi) > 12 and oi[-13][1]:
            out["oi_change_1h_pct"] = round(100 * (oi[-1][1] / oi[-13][1] - 1), 2)
    except Exception as e:
        out["oi_error"] = str(e)[:100]
    out["score"] = positioning_score(out.get("ls_now"), out.get("funding"))
    return out


def positioning_score(ls: float | None, funding: float | None) -> float | None:
    """Crowd positioning as a trend-following reading: more long accounts and positive funding = bullish crowd.
    L/S 1.0 -> 0, 2.0 -> +50, 0.5 -> -50 (log scale); funding +-0.01%/8h adds up to +-20."""
    if ls is None or ls <= 0:
        return None
    s = 50 * math.log2(ls)
    if funding is not None:
        s += max(-20, min(20, funding / 0.0001 * 20))
    return round(max(-100, min(100, s)), 1)


@cached(15, "okx")
def ticker(symbol: str) -> dict:
    p = symbols.get(symbol)
    if p["okx"]:
        t = okx().fetch_ticker(p["okx"])
        return dict(symbol=p["symbol"], last=t["last"], change_pct=t.get("percentage"), source=f"OKX {p['okx']}")
    raise ValueError("no ticker source")
