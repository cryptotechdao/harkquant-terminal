# HarkQuant Terminal

A market-news and analysis terminal for **XAUUSD, BTCUSD, other crypto and major FX/CFDs**. It is read-only:
there are **no order, trade or signal-copy functions** anywhere in the code.

It is built from parts that already existed:

| Part | Reused from |
|---|---|
| Dashboard shell, dark look and feel, PWA, Windows launchers | Hark Titans Edge dashboard (`cryptotechdao/hark-titans`) |
| News engine (58 sources, dedupe, impact classifier, FF calendar, SSE) and its UI | HarkQuant News Desk (`newsdesk.py`, `newsdesk_api.py`, `static/news/`) |
| Charts with prices, indicators and drawing tools | TradingView's free official embed widgets |
| Technical ratings | `tradingview-ta` (TradingView's own scanner) |
| Own-data chart | TradingView Lightweight Charts v5.2.1 (Apache-2.0, vendored) |
| Order flow, order book, funding, long/short, OI | OKX public API via `ccxt` (no keys) |
| Futures volume for OTC markets | Yahoo Finance (`yfinance`) |
| Indicators | TA-Lib (161 functions) and pandas-ta-classic extras |

![Dashboard](docs/screens/dashboard_desktop.png)

## Install and run on Windows (same as Titans)

1. Install Python 3.11+ (tick "Add python.exe to PATH") and Git for Windows.
2. `git clone https://github.com/cryptotechdao/harkquant-terminal.git`
3. Double-click **Install-HarkQuant-Terminal.bat**. It creates `.venv`, installs `requirements.txt`, puts a
   **HarkQuant Terminal** shortcut on the desktop and probes the live sources once.
4. Double-click **HarkQuant Terminal** (or `Start-HarkQuant-Terminal.bat`). It starts
   `python -m hqterm serve --port 8702` in a minimised console, waits for `/health` and opens
   `msedge --app=http://127.0.0.1:8702/`. In Edge: **... > Apps > Install this site as an app**.
5. Update later with **Update-HarkQuant-Terminal.bat** (`git pull` + `pip install`). `state\` is never touched.

Port 8702 is used so it can run next to Hark Titans (8701).

**iPhone on the same Wi-Fi:** run `Start-HarkQuant-Terminal.bat lan`, then open `http://<PC-IP>:8702/` in Safari
and use *Share > Add to Home Screen*. LAN mode binds `0.0.0.0` and requires the token in `state\hqt_token.txt`
for any settings or key change (reads stay open).

Linux/macOS: `python -m venv .venv && .venv/bin/pip install -r requirements.txt && ./start.sh`.

## Views

1. **Dashboard**: 2x2 grid of the BTC chart, the gold chart (both TradingView widgets), the News Desk feed and
   the market-analysis summary (BTC and XAU bias with component bars, plain-language summary, the **red folder**
   list of ForexFactory high-impact events with live countdowns, news sentiment).
   A **TV-news-style ticker** runs across every page. It shows 3 current headlines and 3 upcoming high-impact
   events, interleaved. Red-folder events are highlighted red. When a headline ages out or an event passes, the next
   one takes its slot. Use **−** / **II** / **+** to slow down, pause and speed up; the choice is saved.
2. **Indicators & Tools**: a full-size Lightweight Charts candle chart on our own data (OKX for crypto and the OKX
   gold perpetual, Yahoo for FX/indices) with a searchable catalog of **185 non-TradingView indicators** (all TA-Lib
   groups except math operators, plus pandas-ta-classic SuperTrend, VWAP, Ichimoku, Donchian, Keltner, HMA, VWMA,
   ZLMA, Squeeze, Choppiness and Vortex). Add or remove them and edit their parameters. Candlestick patterns are shown as markers.
   Tools: position size / risk calculator, pivot points (classic, Fibonacci, Camarilla, Woodie), trading sessions
   clock, ATR stop/target.
3. **TradingView**: the full TradingView Advanced Chart with TradingView's own indicators and drawing tools. You can switch the
   symbol (watchlist buttons or any `EXCHANGE:SYMBOL`). It also has a ticker tape, TradingView's technical-analysis
   gauge, a mini overview and TradingView's economic calendar.
4. **Market Analysis**: a pair selector (20 markets) and four sub-tabs:
   - **Live Volume**: taker buy ("incoming") vs taker sell ("outgoing") volume, delta per 5 min and CVD, plus the
     last-500-trades tape.
   - **Buying vs Selling**: order-book imbalance in ±0.1/0.25/0.5/1% bands, depth chart, aggressor delta.
   - **Bulls vs Bears**: TradingView ratings per timeframe, the official TradingView gauge, long/short account
     ratio, funding, open interest, News Desk sentiment and Fear & Greed.
   - **Market Bias**: one big **GREEN BULLISH / RED BEARISH** (or amber NEUTRAL inside the band). It shows the
     gauge, the full score breakdown (score, weight, effective weight, contribution, status, proxy flags) and
     event-risk damping.
5. **Settings**: timezone (default Asia/Karachi), theme, chart symbols, watchlist, refresh rates, ticker speed and
   content, bias weights, neutral band and event damping, per-source news toggles with live status dots, and API
   keys (X bearer, nitter/RSSHub base, Reddit app, OpenAI-compatible LLM).

Screens of every view at desktop and iPhone width are in [`docs/screens/`](docs/screens). Details are in
[`docs/DASHBOARD.md`](docs/DASHBOARD.md). Every source is listed with what is live, what needs a key and what is a proxy in
[`docs/SOURCES.md`](docs/SOURCES.md).

## Honesty rules built in
- No mock or demo data in the default path. If a source fails, its status dot turns red and its panel says so.
  The rest of the page keeps working, and a last good value is served (flagged *stale*) for a while.
- Wherever a number stands in for something that does not exist centrally, it is tagged **PROXY** and explained.
  Spot gold and spot FX have no central volume or order book.
- The bias is analysis, not a signal. Missing components are skipped, never invented.

## Tests
`python -m pytest -q` runs 34 offline tests (bias maths, tools, flow maths, indicators, settings/secrets API, the
token guard, the absence of any trading endpoint, and ticker/red-folder logic on a seeded DB).
`HQT_LIVE_TESTS=1 python -m pytest -q tests/test_live_sources.py` also hits OKX and Yahoo for real.

## Layout
```
hqterm/          FastAPI app (app.py), market data (market.py), bias (bias.py), analysis/ticker (analysis.py),
                 indicators (indicators.py), tools (tools.py), settings/secrets (config.py), symbols (symbols.py),
                 News Desk engine + router (newsdesk.py, news_api.py)
static/          index.html, assets/ (core, tv, ticker, views, app, css), news/ (News Desk UI), vendor/ (LWC), icons/
tests/           pytest suite
scripts/shoot.py screenshots of every view (Playwright)
docs/            DASHBOARD.md, SOURCES.md, screens/
state/           runtime only, git-ignored: settings.json, secrets.json, news.db
```
