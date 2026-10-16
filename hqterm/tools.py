"""Trader tools: pivot points, sessions, ATR stop helper, position sizing. Pure functions (unit-tested)."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone

# Session hours in UTC (standard, DST-agnostic approximations of the FX market convention)
SESSIONS = [
    ("Sydney", 21, 6), ("Tokyo", 0, 9), ("London", 7, 16), ("New York", 12, 21),
]

CONTRACT = {"XAUUSD": 100.0, "XAGUSD": 5000.0, "BTCUSD": 1.0, "ETHUSD": 1.0, "EURUSD": 100000.0, "GBPUSD": 100000.0,
            "AUDUSD": 100000.0, "USDJPY": 100000.0, "USDCAD": 100000.0, "USDCHF": 100000.0, "US30": 1.0, "NAS100": 1.0,
            "SPX500": 1.0, "GER40": 1.0, "USOIL": 1000.0, "DXY": 1.0, "SOLUSD": 1.0, "XRPUSD": 1.0, "BNBUSD": 1.0,
            "DOGEUSD": 1.0}


def pivots(high: float, low: float, close: float, open_: float | None = None) -> dict:
    p = (high + low + close) / 3
    r = high - low
    classic = dict(P=p, R1=2 * p - low, S1=2 * p - high, R2=p + r, S2=p - r, R3=high + 2 * (p - low), S3=low - 2 * (high - p))
    fib = dict(P=p, R1=p + 0.382 * r, S1=p - 0.382 * r, R2=p + 0.618 * r, S2=p - 0.618 * r, R3=p + r, S3=p - r)
    cam = dict(P=p, R1=close + r * 1.1 / 12, S1=close - r * 1.1 / 12, R2=close + r * 1.1 / 6, S2=close - r * 1.1 / 6,
               R3=close + r * 1.1 / 4, S3=close - r * 1.1 / 4, R4=close + r * 1.1 / 2, S4=close - r * 1.1 / 2)
    wood_p = (high + low + 2 * close) / 4
    wood = dict(P=wood_p, R1=2 * wood_p - low, S1=2 * wood_p - high, R2=wood_p + r, S2=wood_p - r)
    rnd = lambda d: {k: round(v, 5) for k, v in d.items()}
    return dict(classic=rnd(classic), fibonacci=rnd(fib), camarilla=rnd(cam), woodie=rnd(wood))


def daily_hlc(bars: list[dict], tz_offset_h: float = 0) -> tuple[float, float, float] | None:
    """Previous complete day's high/low/close from intraday bars (day boundary at UTC+tz_offset_h)."""
    if not bars:
        return None
    off = timedelta(hours=tz_offset_h)
    days: dict = {}
    for b in bars:
        d = (datetime.fromtimestamp(b["time"], timezone.utc) + off).date()
        x = days.setdefault(d, [b["high"], b["low"], b["close"]])
        x[0] = max(x[0], b["high"]); x[1] = min(x[1], b["low"]); x[2] = b["close"]
    keys = sorted(days)
    if len(keys) < 2:
        return None
    h, l, c = days[keys[-2]]
    return h, l, c


def sessions(now: datetime | None = None) -> list[dict]:
    now = now or datetime.now(timezone.utc)
    out = []
    for name, o, c in SESSIONS:
        h = now.hour + now.minute / 60
        active = (o <= h < c) if o < c else (h >= o or h < c)
        start = now.replace(hour=o, minute=0, second=0, microsecond=0)
        if active and o > c and h < c:
            start -= timedelta(days=1)
        elif not active and start <= now:
            start += timedelta(days=1)
        end = start + timedelta(hours=(c - o) % 24)
        wd = now.weekday()
        if wd == 5 or (wd == 6 and h < 21) or (wd == 4 and h >= 21):   # FX weekend: Fri 21:00 UTC -> Sun 21:00 UTC
            active = False
        out.append(dict(name=name, open_utc=o, close_utc=c, active=active, next_open=start.timestamp() if not active else None,
                        closes=end.timestamp() if active else None))
    return out


def position_size(balance: float, risk_pct: float | None, risk_money: float | None, entry: float, stop: float,
                  contract: float = 1.0, quote_to_account: float = 1.0) -> dict:
    """Lots so that hitting the stop loses the chosen risk. quote_to_account converts quote-currency P&L to the account."""
    if entry <= 0 or stop <= 0 or entry == stop:
        raise ValueError("entry and stop must be positive and different")
    risk = risk_money if risk_money else balance * (risk_pct or 0) / 100
    if risk <= 0:
        raise ValueError("risk must be > 0")
    loss_per_lot = abs(entry - stop) * contract * quote_to_account
    lots = risk / loss_per_lot
    return dict(risk=round(risk, 2), loss_per_lot=round(loss_per_lot, 4), lots=round(lots, 4),
                lots_rounded=round(int(lots * 100) / 100, 2), side="long" if stop < entry else "short",
                stop_distance=round(abs(entry - stop), 6))


def atr_levels(entry: float, atr: float, side: str = "long", sl_mult: float = 1.5, rr: float = 2.0) -> dict:
    sgn = 1 if side == "long" else -1
    sl = entry - sgn * sl_mult * atr
    tp = entry + sgn * sl_mult * atr * rr
    return dict(entry=entry, sl=round(sl, 5), tp=round(tp, 5), risk=round(abs(entry - sl), 5), rr=rr)
