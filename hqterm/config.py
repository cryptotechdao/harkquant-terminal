"""Settings and secrets for HarkQuant Terminal.

state/settings.json  user settings (symbols, refresh rates, ticker speed, news toggles, theme, timezone, bias weights)
state/secrets.json   API keys (X bearer, Reddit OAuth app, LLM key). Never returned by the API, never committed.
Both live under HQT_STATE (default <repo>/state). Environment variables override secrets (HQT_X_BEARER, ...).
"""
from __future__ import annotations

import copy
import json
import os
import threading
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STATE = Path(os.getenv("HQT_STATE", ROOT / "state"))

DEFAULTS: dict = {
    "timezone": "Asia/Karachi",
    "theme": "dark",                       # dark | light
    "dashboard": {"btc_symbol": "BITSTAMP:BTCUSD", "gold_symbol": "OANDA:XAUUSD", "chart_interval": "15"},
    "tradingview": {"default_symbol": "OANDA:XAUUSD", "interval": "15", "style": "1"},
    "watchlist": ["BTCUSD", "XAUUSD", "ETHUSD", "SOLUSD", "XRPUSD", "EURUSD", "GBPUSD", "USDJPY", "XAGUSD", "US30", "DXY"],
    "refresh": {"analysis_s": 20, "flow_s": 10, "ticker_s": 30, "status_s": 30},
    "ticker": {"speed": 60, "paused": False, "headline_max_age_min": 90, "headline_min_impact": "MEDIUM",
               "headlines": 3, "events": 3},
    "news": {"disabled": [], "max_age_h": 72, "llm": False},
    "bias": {"weights": {"technical": 0.30, "flow": 0.25, "orderbook": 0.15, "positioning": 0.10, "news": 0.15,
                         "fear_greed": 0.05},
             "neutral_band": 15, "event_dampen": 0.5, "event_window_min": 60, "rating_tfs": ["15m", "1h", "4h"]},
    "indicators": {"default_symbol": "BTCUSD", "default_tf": "15m"},
}

SECRET_KEYS = {
    "x_bearer": "HQT_X_BEARER",            # X API v2 bearer token (Basic tier or higher can read user timelines)
    "nitter_base": "HQT_NITTER_BASE",      # optional self-hosted nitter / RSSHub base URL (not a secret, but per-machine)
    "reddit_client_id": "HQT_REDDIT_CLIENT_ID",
    "reddit_client_secret": "HQT_REDDIT_CLIENT_SECRET",
    "llm_url": "HQT_LLM_URL", "llm_model": "HQT_LLM_MODEL", "llm_key": "HQT_LLM_KEY",
}

_lock = threading.Lock()


def _merge(base: dict, over: dict) -> dict:
    out = copy.deepcopy(base)
    for k, v in (over or {}).items():
        if isinstance(v, dict) and isinstance(out.get(k), dict):
            out[k] = _merge(out[k], v)
        else:
            out[k] = v
    return out


def _read(p: Path) -> dict:
    try:
        return json.loads(p.read_text())
    except Exception:
        return {}


def _write(p: Path, data: dict) -> None:
    p.parent.mkdir(parents=True, exist_ok=True)
    tmp = p.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2))
    os.replace(tmp, p)
    try:
        os.chmod(p, 0o600)
    except OSError:
        pass


def load_settings() -> dict:
    with _lock:
        return _merge(DEFAULTS, _read(STATE / "settings.json"))


def validate(s: dict) -> dict:
    """Clamp numbers into safe ranges, drop unknown top-level keys."""
    s = {k: v for k, v in s.items() if k in DEFAULTS}
    s = _merge(DEFAULTS, s)
    s["ticker"]["speed"] = max(10, min(400, int(s["ticker"]["speed"])))
    s["ticker"]["headline_max_age_min"] = max(5, min(24 * 60, int(s["ticker"]["headline_max_age_min"])))
    for k in ("headlines", "events"):
        s["ticker"][k] = max(1, min(10, int(s["ticker"][k])))
    for k, v in list(s["refresh"].items()):
        s["refresh"][k] = max(5, min(3600, int(v)))
    w = s["bias"]["weights"]
    for k in list(w):
        w[k] = max(0.0, min(1.0, float(w[k])))
    s["bias"]["neutral_band"] = max(0, min(60, float(s["bias"]["neutral_band"])))
    s["bias"]["event_dampen"] = max(0.0, min(1.0, float(s["bias"]["event_dampen"])))
    s["theme"] = s["theme"] if s["theme"] in ("dark", "light") else "dark"
    if not isinstance(s["news"]["disabled"], list):
        s["news"]["disabled"] = []
    return s


def save_settings(patch: dict) -> dict:
    with _lock:
        cur = _merge(DEFAULTS, _read(STATE / "settings.json"))
        new = validate(_merge(cur, patch or {}))
        _write(STATE / "settings.json", new)
        return new


def reset_settings() -> dict:
    with _lock:
        _write(STATE / "settings.json", copy.deepcopy(DEFAULTS))
        return copy.deepcopy(DEFAULTS)


def secret(name: str) -> str | None:
    env = SECRET_KEYS.get(name)
    if env and os.getenv(env):
        return os.getenv(env)
    v = _read(STATE / "secrets.json").get(name)
    return v or None


def secrets_public() -> dict:
    """Which secrets are set (never the values)."""
    out = {}
    for k, env in SECRET_KEYS.items():
        v = secret(k)
        out[k] = {"set": bool(v), "from_env": bool(os.getenv(env)), "hint": (v[:3] + "…" + v[-2:]) if v and len(v) > 8 else None}
    return out


def save_secrets(patch: dict) -> dict:
    with _lock:
        cur = _read(STATE / "secrets.json")
        for k, v in (patch or {}).items():
            if k not in SECRET_KEYS:
                continue
            if v in (None, ""):
                cur.pop(k, None)
            else:
                cur[k] = str(v).strip()
        _write(STATE / "secrets.json", cur)
    return secrets_public()
