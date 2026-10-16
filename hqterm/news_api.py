"""HarkQuant News Desk API (reused from HarkQuant; mounted by hqterm.app) (FastAPI APIRouter, prefix /api/news).

Integrate into the terminal app:
    import newsdesk_api
    app.include_router(newsdesk_api.router)
    app.mount("/static/news", StaticFiles(directory="static/news"), name="news")   # if static/ isn't already mounted
    @app.on_event("startup")  async def _nd(): await newsdesk_api.start()     # or call newsdesk_api.start_poller() from a sync hook
    @app.on_event("shutdown") async def _nd2(): await newsdesk_api.stop()
(or use newsdesk_api.lifespan in a combined lifespan)

Standalone test server:  ../.venv/bin/python newsdesk_api.py   ->  http://127.0.0.1:8791/  (serves static/news/news.html)
"""
from __future__ import annotations

import asyncio, json, os, sys, time
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import APIRouter, Query, Request
from fastapi.responses import JSONResponse, StreamingResponse

from .newsdesk import get_desk, LEVELS  # noqa: E402

router = APIRouter(prefix="/api/news", tags=["news"])
STATIC_DIR = Path(__file__).resolve().parent.parent / "static" / "news"


async def start():
    await get_desk().start()


async def stop():
    await get_desk().stop()


def start_poller():
    """Sync hook for app.py startup handlers: schedules the poller on the running event loop
    (or, if called outside one, runs it in a private background loop thread)."""
    try:
        loop = asyncio.get_running_loop()
        loop.create_task(start())
    except RuntimeError:
        import threading
        def run():
            lp = asyncio.new_event_loop(); asyncio.set_event_loop(lp)
            lp.run_until_complete(start()); lp.run_forever()
        threading.Thread(target=run, name="newsdesk-poller", daemon=True).start()


@asynccontextmanager
async def lifespan(app):
    await start()
    try:
        yield
    finally:
        await stop()


@router.get("/feed")
async def feed(impact: str | None = Query(None, description="LOW,MEDIUM,HIGH,CRITICAL comma list, or e.g. HIGH+"),
         source: str | None = Query(None, description="source id(s) or label(s), comma list"),
         asset: str | None = Query(None, description="XAU,BTC,USD,RISK"),
         q: str | None = Query(None, description="search words (all must match)"),
         since: str | None = Query(None, description="unix seconds or ISO; returns items inserted after it"),
         category: str | None = Query(None, description="squawk,social,wire,official,crypto,aggregator,geo,calendar"),
         limit: int = Query(200, ge=1, le=1000)):
    return get_desk().feed(impact=impact, source=source, asset=asset, q=q, since=since, limit=limit, category=category)


@router.get("/item/{item_id}")
async def item(item_id: str):
    it = get_desk().item(item_id)
    return it if it else JSONResponse({"error": "not found"}, status_code=404)


@router.get("/sources")
async def sources():
    return get_desk().sources_health()


@router.get("/calendar")
async def calendar(range: str = Query("week", pattern="^(today|week|upcoming)$"),
             impact: str | None = Query(None, description="High,Medium,Low,Holiday"),
             currency: str | None = Query(None, description="USD,EUR,...")):
    return get_desk().calendar(range_=range, impact=impact, currency=currency)


@router.get("/sentiment")
async def sentiment(hours: float = Query(6, ge=0.5, le=72)):
    return get_desk().sentiment(hours)


@router.post("/refresh")
async def refresh(source: str | None = None):
    ids = get_desk().refresh(source)
    return {"refreshing": ids, "count": len(ids), "server_time": time.time()}


@router.get("/stream")
async def stream(request: Request, impact: str | None = None, asset: str | None = None):
    """Server-Sent Events: `item` (new), `update` (LLM-refined), `calendar`, `sentiment`, `hello`; heartbeat every 15 s."""
    desk = get_desk()
    q = desk.subscribe()
    levels = set(x.strip().upper() for x in impact.split(",")) if impact else None
    assets = set(x.strip().upper() for x in asset.split(",")) if asset else None

    def ok(ev, data):
        if ev not in ("item", "update"): return True
        if levels and data.get("impact") not in levels: return False
        if assets and not assets & set(data.get("assets", [])): return False
        return True

    async def gen():
        try:
            yield "retry: 3000\n\n"
            yield f"event: hello\ndata: {json.dumps({'server_time': time.time(), 'levels': LEVELS})}\n\n"
            while True:
                if await request.is_disconnected(): break
                try:
                    ev, data = await asyncio.wait_for(q.get(), timeout=15)
                except asyncio.TimeoutError:
                    yield f": ping {int(time.time())}\n\n"; continue
                if ok(ev, data):
                    yield f"event: {ev}\nid: {data.get('id', '') if isinstance(data, dict) else ''}\ndata: {json.dumps(data, default=str)}\n\n"
        finally:
            desk.unsubscribe(q)

    return StreamingResponse(gen(), media_type="text/event-stream",
                             headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no", "Connection": "keep-alive"})


def create_standalone_app():
    from fastapi import FastAPI
    from fastapi.responses import RedirectResponse
    from fastapi.staticfiles import StaticFiles
    app = FastAPI(title="HarkQuant News Desk", lifespan=lifespan)
    app.include_router(router)
    app.mount("/static/news", StaticFiles(directory=STATIC_DIR), name="news")

    @app.get("/")
    def root():
        return RedirectResponse("/static/news/news.html")
    return app


if __name__ == "__main__":
    import logging, uvicorn
    logging.basicConfig(level=os.getenv("HQ_LOG", "INFO")); logging.getLogger("httpx").setLevel(logging.WARNING)
    uvicorn.run(create_standalone_app(), host=os.getenv("HQ_NEWS_HOST", "127.0.0.1"), port=int(os.getenv("HQ_NEWS_PORT", "8791")),
                log_level="warning")
