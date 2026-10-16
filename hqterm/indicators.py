"""Non-TradingView indicators for the Indicators & Tools chart.

Library 1: TA-Lib (every function in the overlap, momentum, volatility, volume, cycle, price-transform, statistic and
pattern groups; math operators are skipped as they are not chart indicators).
Library 2: pandas-ta-classic extras that TA-Lib lacks (SuperTrend, VWAP, Ichimoku, Donchian, Keltner, Squeeze, HMA,
VWMA, ZLMA, Choppiness, Vortex).
Each indicator returns line series [{time, value}] plus a `pane` hint: "price" (overlay) or "own" (separate pane).
Candlestick patterns return markers instead of lines.
"""
from __future__ import annotations

import math
from functools import lru_cache

import numpy as np
import pandas as pd

SKIP_GROUPS = {"Math Operators", "Math Transform"}
PRICE_GROUPS = {"Overlap Studies", "Price Transform"}

PTA = {
    "SUPERTREND": dict(name="SuperTrend", params={"length": 10, "multiplier": 3.0}, pane="price"),
    "VWAP": dict(name="VWAP (session, daily anchor)", params={}, pane="price"),
    "ICHIMOKU": dict(name="Ichimoku Cloud", params={"tenkan": 9, "kijun": 26, "senkou": 52}, pane="price"),
    "DONCHIAN": dict(name="Donchian Channels", params={"lower_length": 20, "upper_length": 20}, pane="price"),
    "KC": dict(name="Keltner Channels", params={"length": 20, "scalar": 2.0}, pane="price"),
    "HMA": dict(name="Hull Moving Average", params={"length": 21}, pane="price"),
    "VWMA": dict(name="Volume Weighted MA", params={"length": 20}, pane="price"),
    "ZLMA": dict(name="Zero-Lag MA", params={"length": 21}, pane="price"),
    "SQUEEZE": dict(name="TTM-style Squeeze (LazyBear)", params={"bb_length": 20, "kc_length": 20}, pane="own"),
    "CHOP": dict(name="Choppiness Index", params={"length": 14}, pane="own"),
    "VORTEX": dict(name="Vortex Indicator", params={"length": 14}, pane="own"),
}


@lru_cache(maxsize=1)
def catalog() -> dict:
    import talib
    from talib import abstract
    groups = []
    for g, names in talib.get_function_groups().items():
        if g in SKIP_GROUPS:
            continue
        items = []
        for n in names:
            info = abstract.Function(n).info
            params = {k: (float(v) if isinstance(v, float) else int(v)) for k, v in info["parameters"].items()}
            items.append(dict(id=f"talib:{n}", name=info["display_name"], short=n, params=params,
                              outputs=list(info["output_names"]), pane="price" if g in PRICE_GROUPS else
                              ("marker" if g == "Pattern Recognition" else "own")))
        groups.append(dict(group=f"TA-Lib · {g}", items=items))
    groups.append(dict(group="pandas-ta-classic · extras",
                       items=[dict(id=f"pta:{k}", name=v["name"], short=k, params=v["params"], outputs=[], pane=v["pane"])
                              for k, v in PTA.items()]))
    total = sum(len(g["items"]) for g in groups)
    return dict(groups=groups, total=total,
                libraries=["TA-Lib (BSD)", "pandas-ta-classic (MIT)"],
                note="Computed on the server from the chart's own bars (OKX / Yahoo). Not TradingView indicators.")


def frame(bars: list[dict]) -> pd.DataFrame:
    df = pd.DataFrame(bars)
    if df.empty:
        raise ValueError("no bars")
    df = df.sort_values("time").drop_duplicates("time")
    df.index = pd.to_datetime(df["time"], unit="s", utc=True)
    for c in ("open", "high", "low", "close", "volume"):
        df[c] = df[c].astype(float)
    return df


def _line(df, values) -> list[dict]:
    out = []
    for t, v in zip(df["time"].tolist(), np.asarray(values, dtype=float).tolist()):
        if v is not None and math.isfinite(v):
            out.append(dict(time=int(t), value=round(v, 6)))
    return out


def _coerce(params: dict, defaults: dict) -> dict:
    out = {}
    for k, d in defaults.items():
        v = params.get(k, d)
        try:
            out[k] = float(v) if isinstance(d, float) else int(float(v))
        except (TypeError, ValueError):
            out[k] = d
        if isinstance(d, int) and k.endswith(("period", "length")) and not 1 <= out[k] <= 1000:
            raise ValueError(f"{k} out of range")
    return out


def compute(ind_id: str, bars: list[dict], params: dict | None = None) -> dict:
    params = params or {}
    df = frame(bars)
    lib, _, name = ind_id.partition(":")
    if lib == "talib":
        from talib import abstract
        f = abstract.Function(name.upper())
        info = f.info
        p = _coerce(params, dict(info["parameters"]))
        res = f({"open": df["open"].values, "high": df["high"].values, "low": df["low"].values,
                 "close": df["close"].values, "volume": df["volume"].values}, **p)
        outs = res if isinstance(res, list) else [res]
        names = list(info["output_names"])
        if info["group"] == "Pattern Recognition":
            v = np.asarray(outs[0])
            markers = [dict(time=int(t), value=int(x)) for t, x in zip(df["time"], v) if x != 0]
            return dict(id=ind_id, name=info["display_name"], pane="marker", params=p, markers=markers, lines=[])
        pane = "price" if info["group"] in PRICE_GROUPS else "own"
        lines = [dict(name=n, data=_line(df, o), hist=n.endswith("hist")) for n, o in zip(names, outs)]
        return dict(id=ind_id, name=info["display_name"], pane=pane, params=p, lines=lines)
    if lib == "pta":
        import pandas_ta_classic as ta
        key = name.upper()
        if key not in PTA:
            raise KeyError(ind_id)
        spec = PTA[key]
        p = _coerce(params, spec["params"])
        h, l, c, v = df["high"], df["low"], df["close"], df["volume"]
        if key == "SUPERTREND":
            r = ta.supertrend(h, l, c, length=p["length"], multiplier=p["multiplier"])
            cols = [x for x in r.columns if x.startswith("SUPERT_")]
            lines = [dict(name="supertrend", data=_line(df, r[cols[0]]))]
        elif key == "VWAP":
            r = ta.vwap(h, l, c, v.replace(0, np.nan).fillna(1.0), anchor="D")
            lines = [dict(name="vwap", data=_line(df, r))]
        elif key == "ICHIMOKU":
            r, _ = ta.ichimoku(h, l, c, tenkan=p["tenkan"], kijun=p["kijun"], senkou=p["senkou"])
            lines = [dict(name=col.split("_")[0].lower(), data=_line(df, r[col])) for col in r.columns]
        elif key == "DONCHIAN":
            r = ta.donchian(h, l, lower_length=p["lower_length"], upper_length=p["upper_length"])
            lines = [dict(name=col, data=_line(df, r[col])) for col in r.columns]
        elif key == "KC":
            r = ta.kc(h, l, c, length=p["length"], scalar=p["scalar"])
            lines = [dict(name=col, data=_line(df, r[col])) for col in r.columns]
        elif key in ("HMA", "ZLMA"):
            r = getattr(ta, key.lower())(c, length=p["length"])
            lines = [dict(name=key.lower(), data=_line(df, r))]
        elif key == "VWMA":
            r = ta.vwma(c, v.replace(0, np.nan).fillna(1.0), length=p["length"])
            lines = [dict(name="vwma", data=_line(df, r))]
        elif key == "SQUEEZE":
            r = ta.squeeze(h, l, c, bb_length=p["bb_length"], kc_length=p["kc_length"], lazybear=True)
            col = [x for x in r.columns if x.startswith("SQZ_")][0]
            lines = [dict(name="squeeze", data=_line(df, r[col]), hist=True)]
        elif key == "CHOP":
            lines = [dict(name="chop", data=_line(df, ta.chop(h, l, c, length=p["length"])))]
        elif key == "VORTEX":
            r = ta.vortex(h, l, c, length=p["length"])
            lines = [dict(name=col, data=_line(df, r[col])) for col in r.columns]
        else:  # pragma: no cover
            raise KeyError(ind_id)
        return dict(id=ind_id, name=spec["name"], pane=spec["pane"], params=p, lines=lines)
    raise KeyError(f"unknown indicator {ind_id}")
