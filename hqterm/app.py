"""HarkQuant Terminal: FastAPI app serving the PWA at "/" and the read-only market/news API.

No trading or order endpoints exist. Optional HQT_TOKEN: when set, writes (settings, secrets, news refresh) need
`Authorization: Bearer <token>`; reads stay open so the dashboard works without a login.
"""
from __future__ import annotations

import asyncio
import hmac
import json
import logging
import os
import time
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import Body, FastAPI, HTTPException, Query, Request
from fastapi.responses import FileResponse, JSONResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles

from . import __version__, analysis, config, indicators, market, symbols, tools
from . import news_api
from .newsdesk import get_desk

log = logging.getLogger("hqterm")
STATIC = Path(__file__).resolve().parent.parent / "static"

CSP = ("default-src 'self'; "
       "script-src 'self' https://s3.tradingview.com https://*.tradingview.com https://*.tradingview-widget.com; "
       "frame-src https://*.tradingview.com https://*.tradingview-widget.com https://s.tradingview.com; "
       "img-src 'self' data: https:; style-src 'self' 'unsafe-inline'; font-src 'self' data: https:; "
       "connect-src 'self' https://*.tradingview.com https://*.tradingview-widget.com; "
       "frame-ancestors 'none'; base-uri 'self'")


PROTECTED = ("/api/settings", "/api/secrets", "/api/news/refresh")   # writes that need HQT_TOKEN when it is set


@asynccontextmanager
async def lifespan(app):
    if os.getenv("HQT_NO_NEWS") != "1":
        await news_api.start()
    try:
        yield
    finally:
        if os.getenv("HQT_NO_NEWS") != "1":
            await news_api.stop()


app = FastAPI(title="HarkQuant Terminal", version=__version__, lifespan=lifespan)
app.include_router(news_api.router)


@app.middleware("http")
async def headers(request: Request, call_next):
    if request.method in ("POST", "PUT", "DELETE") and request.url.path.startswith(PROTECTED):
        tok = os.getenv("HQT_TOKEN")
        if tok:
            got = request.headers.get("authorization", "").removeprefix("Bearer ").strip()
            if not hmac.compare_digest(got, tok):
                return JSONResponse({"error": "token required (HQT_TOKEN is set on this server)"}, status_code=401)
    resp = await call_next(request)
    resp.headers.setdefault("X-Content-Type-Options", "nosniff")
    resp.headers.setdefault("Referrer-Policy", "no-referrer")
    if not request.url.path.startswith("/api/"):
        resp.headers.setdefault("Content-Security-Policy", CSP)
    return resp


def _run(fn, *a, **kw):
    return asyncio.to_thread(fn, *a, **kw)


async def _guard(fn, *a, **kw):
    try:
        return await _run(fn, *a, **kw)
    except KeyError as e:
        raise HTTPException(404, str(e).strip("'\""))
    except ValueError as e:
        raise HTTPException(422, str(e))
    except Exception as e:
        log.warning("%s failed: %s", getattr(fn, "__name__", fn), e)
        return JSONResponse({"error": f"{type(e).__name__}: {str(e)[:200]}", "source_failed": True}, status_code=502)


# ------------------------------------------------------------------------------------------------ basics
@app.get("/health")
async def health():
    return {"ok": True, "app": "harkquant-terminal", "version": __version__, "time": time.time()}


@app.get("/api/symbols")
async def api_symbols():
    return {"symbols": symbols.public()}


@app.get("/api/settings")
async def get_settings():
    return {"settings": config.load_settings(), "secrets": config.secrets_public(), "defaults": config.DEFAULTS,
            "token_required": bool(os.getenv("HQT_TOKEN")), "sources": [
                dict(id=s["id"], name=s["name"], category=s["category"], kind=s["kind"]) for s in get_desk().sources.values()]}


@app.post("/api/settings")
async def post_settings(patch: dict = Body(...)):
    s = config.save_settings(patch)
    get_desk().set_disabled(s["news"]["disabled"])
    get_desk().llm_on = bool(s["news"].get("llm")) and bool(config.secret("llm_url"))
    return {"settings": s}


@app.post("/api/settings/reset")
async def reset_settings():
    s = config.reset_settings()
    get_desk().set_disabled([])
    return {"settings": s}


@app.get("/api/secrets")
async def get_secrets():
    return config.secrets_public()


@app.post("/api/secrets")
async def post_secrets(patch: dict = Body(...)):
    out = config.save_secrets(patch)
    d = get_desk()
    for sid, src in d.sources.items():           # retry the sources a new key may unlock
        if src["kind"] in ("x", "reddit"):
            d.state[sid].pop("x_uid", None); d.state[sid]["fails"] = 0
    d.refresh(None)
    return out


@app.get("/api/status")
async def status():
    ns = get_desk().sources_health()
    sec = config.secrets_public()
    return {"market": market.STATUS, "news": ns["summary"], "news_role": ns["role"], "secrets": {k: v["set"] for k, v in sec.items()},
            "needs_key": [s["id"] for s in ns["sources"] if s["status"] == "needs_key"], "server_time": time.time()}


# ------------------------------------------------------------------------------------------------ market data
@app.get("/api/bars")
async def api_bars(symbol: str = "BTCUSD", tf: str = "15m", limit: int = Query(500, ge=50, le=1500), source: str = "auto"):
    return await _guard(market.bars, symbol, tf, limit, source)


@app.get("/api/ratings")
async def api_ratings(symbol: str = "BTCUSD"):
    tfs = tuple(config.load_settings()["bias"]["rating_tfs"])
    return await _guard(market.ratings, symbol, tfs)


@app.get("/api/flow")
async def api_flow(symbol: str = "BTCUSD"):
    return await _guard(market.flow, symbol)


@app.get("/api/orderbook")
async def api_orderbook(symbol: str = "BTCUSD"):
    return await _guard(market.orderbook, symbol)


@app.get("/api/positioning")
async def api_positioning(symbol: str = "BTCUSD"):
    return await _guard(market.positioning, symbol)


@app.get("/api/analysis")
async def api_analysis(symbol: str = "BTCUSD"):
    return await _guard(analysis.analyze, symbol)


@app.get("/api/summary")
async def api_summary():
    return await _guard(analysis.summary)


@app.get("/api/ticker")
async def api_ticker():
    return await _guard(analysis.ticker)


@app.get("/api/redfolder")
async def api_redfolder():
    return {"events": await _run(analysis.red_folder, 20), "server_time": time.time()}


# ------------------------------------------------------------------------------------------------ indicators & tools
@app.get("/api/indicators")
async def api_indicators():
    return await _run(indicators.catalog)


@app.post("/api/indicator")
async def api_indicator(body: dict = Body(...)):
    sym, tf = body.get("symbol", "BTCUSD"), body.get("tf", "15m")

    def go():
        b = market.bars(sym, tf, int(body.get("limit", 500)), body.get("source", "auto"))
        return indicators.compute(body["id"], b["bars"], body.get("params") or {})
    if "id" not in body:
        raise HTTPException(422, "id required")
    return await _guard(go)


@app.get("/api/tools/pivots")
async def api_pivots(symbol: str = "XAUUSD"):
    def go():
        b = market.bars(symbol, "1h", 120)
        hlc = tools.daily_hlc(b["bars"], 0)
        if not hlc:
            raise ValueError("not enough bars for a previous day")
        h, l, c = hlc
        return dict(symbol=symbol, source=b["source"], prev_day=dict(high=h, low=l, close=c), **tools.pivots(h, l, c))
    return await _guard(go)


@app.get("/api/tools/sessions")
async def api_sessions():
    return {"sessions": tools.sessions(), "server_time": time.time()}


@app.get("/api/tools/atr")
async def api_atr(symbol: str = "XAUUSD", tf: str = "15m", length: int = 14):
    def go():
        b = market.bars(symbol, tf, 300)
        r = indicators.compute("talib:ATR", b["bars"], {"timeperiod": length})
        last = r["lines"][0]["data"][-1]["value"]
        return dict(symbol=symbol, tf=tf, atr=last, close=b["bars"][-1]["close"], source=b["source"],
                    contract=tools.CONTRACT.get(symbol.upper(), 1.0))
    return await _guard(go)


@app.post("/api/tools/position")
async def api_position(body: dict = Body(...)):
    try:
        return tools.position_size(float(body.get("balance", 0)), body.get("risk_pct") and float(body["risk_pct"]),
                                   body.get("risk_money") and float(body["risk_money"]), float(body["entry"]),
                                   float(body["stop"]), float(body.get("contract", 1)), float(body.get("fx", 1)))
    except (KeyError, ValueError, TypeError) as e:
        raise HTTPException(422, str(e))


# ------------------------------------------------------------------------------------------------ SSE
@app.get("/api/stream")
async def stream(request: Request):
    """Server-Sent Events: `ticker` and `summary` snapshots on the Settings refresh cadence, `item` for new headlines."""
    desk = get_desk()
    q = desk.subscribe()

    async def gen():
        last_t = last_s = 0.0
        try:
            yield "retry: 3000\n\n"
            while True:
                if await request.is_disconnected():
                    break
                r = config.load_settings()["refresh"]
                now = time.time()
                if now - last_t >= r["ticker_s"]:
                    last_t = now
                    try:
                        yield f"event: ticker\ndata: {json.dumps(await _run(analysis.ticker), default=str)}\n\n"
                    except Exception as e:
                        yield f"event: error\ndata: {json.dumps({'where': 'ticker', 'error': str(e)[:120]})}\n\n"
                if now - last_s >= r["analysis_s"]:
                    last_s = now
                    try:
                        yield f"event: summary\ndata: {json.dumps(await _run(analysis.summary), default=str)}\n\n"
                    except Exception as e:
                        yield f"event: error\ndata: {json.dumps({'where': 'summary', 'error': str(e)[:120]})}\n\n"
                try:
                    ev, data = await asyncio.wait_for(q.get(), timeout=5)
                    if ev == "item":
                        yield f"event: item\ndata: {json.dumps(data, default=str)}\n\n"
                except asyncio.TimeoutError:
                    yield f": ping {int(time.time())}\n\n"
        finally:
            desk.unsubscribe(q)

    return StreamingResponse(gen(), media_type="text/event-stream",
                             headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})


# ------------------------------------------------------------------------------------------------ static shell
@app.get("/")
async def index():
    return FileResponse(STATIC / "index.html", headers={"Cache-Control": "no-cache"})


@app.get("/manifest.webmanifest")
async def manifest():
    return FileResponse(STATIC / "manifest.webmanifest", media_type="application/manifest+json")


@app.get("/sw.js")
async def sw():
    return FileResponse(STATIC / "sw.js", media_type="text/javascript", headers={"Cache-Control": "no-cache"})


for sub in ("assets", "vendor", "icons", "news"):
    app.mount(f"/{sub}", StaticFiles(directory=STATIC / sub), name=sub)
app.mount("/static/news", StaticFiles(directory=STATIC / "news"), name="static-news")
