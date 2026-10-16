"""Market bias: combine component scores (-100..100 each) into one reading.

Pure functions, no I/O, so they are unit-tested. Missing components are skipped and the remaining weights renormalised.
A high-impact event inside `event_window_min` shrinks the score toward neutral by `event_dampen` (event risk, not direction).
"""
from __future__ import annotations

COMPONENTS = {
    "technical": "TradingView technical rating (avg of timeframes)",
    "flow": "Taker buy vs sell volume, last 1h",
    "orderbook": "Order-book imbalance within 0.5% of mid",
    "positioning": "Long/short account ratio + funding (crowd, trend-following reading)",
    "news": "News Desk headline sentiment (impact-weighted, decayed)",
    "fear_greed": "Crypto Fear & Greed index (crypto only)",
}


def label(score: float, band: float) -> str:
    if score >= band:
        return "BULLISH"
    if score <= -band:
        return "BEARISH"
    return "NEUTRAL"


def combine(scores: dict, weights: dict, neutral_band: float = 15, event: dict | None = None,
            event_dampen: float = 0.5) -> dict:
    used, wsum, acc = [], 0.0, 0.0
    rows = []
    for k, desc in COMPONENTS.items():
        s = scores.get(k)
        w = float(weights.get(k, 0) or 0)
        ok = s is not None and w > 0
        rows.append(dict(id=k, desc=desc, score=None if s is None else round(float(s), 1), weight=w, used=ok))
        if ok:
            s = max(-100.0, min(100.0, float(s)))
            acc += s * w; wsum += w; used.append(k)
    raw = acc / wsum if wsum else 0.0
    for r in rows:
        r["contribution"] = round(r["score"] * r["weight"] / wsum, 1) if r["used"] and wsum else None
        r["eff_weight"] = round(r["weight"] / wsum, 3) if r["used"] and wsum else 0
    final = raw
    damp = None
    if event:
        damp = max(0.0, min(1.0, event_dampen))
        final = raw * (1 - damp)
    agree = sum(1 for k in used if (scores[k] > 0) == (final > 0) and abs(scores[k]) >= 5)
    return dict(score=round(final, 1), raw=round(raw, 1), label=label(final, neutral_band), neutral_band=neutral_band,
                components=rows, used=len(used), agreement=f"{agree}/{len(used)}" if used else "0/0",
                event=event, dampened=damp is not None and damp > 0,
                confidence=round(min(1.0, wsum) * (agree / len(used) if used else 0), 2))
