# HarkQuant Terminal: dashboard reference

FastAPI serves the PWA at `/` on `127.0.0.1:8702`. Plain HTML/CSS/JS, no build step. The look and feel, the PWA
manifest/service worker and the launchers come from the Hark Titans dashboard. The News Desk UI (`static/news/`) is
the HarkQuant News Desk module, mounted with `NewsDesk.mount(el)`.

## Start
| How | Command |
|---|---|
| Windows, first time | `Install-HarkQuant-Terminal.bat` (venv, requirements, desktop shortcut, source check) |
| Windows, daily | **HarkQuant Terminal** shortcut / `Start-HarkQuant-Terminal.bat` → Edge app window |
| Windows, phone on the same Wi-Fi | `Start-HarkQuant-Terminal.bat lan` (binds 0.0.0.0, writes need `state\hqt_token.txt`) |
| Update | `Update-HarkQuant-Terminal.bat` |
| Any OS | `python -m hqterm serve [--host 127.0.0.1] [--port 8702]`; `python -m hqterm check` probes the sources |

Environment: `HQT_PORT`, `HQT_HOST`, `HQT_STATE` (state folder), `HQT_TOKEN` (protects writes), `HQT_NO_NEWS=1`
(no news pollers), `HQ_NEWS_DISABLE=id,id`, plus the key variables in SOURCES.md.

## Security
- Read endpoints are open (the app holds no account data and places no orders).
- If `HQT_TOKEN` is set, `POST /api/settings*`, `/api/secrets` and `/api/news/refresh` need
  `Authorization: Bearer <token>`. The Settings page asks for it. Without it the ticker speed and pause are kept
  in the browser only.
- Secrets are write-only from the browser. `GET` returns only whether each key is set.
- The shell is served with a CSP that allows only TradingView's widget hosts besides `'self'`, `frame-ancestors 'none'` and
  `nosniff`. Server text reaches the DOM through `textContent`.

## Views (keys 1-5)
1. **Dashboard**: TradingView BTC and gold charts, the News Desk (Squawk / Calendar / Sources tabs with per-source
   health), and the analysis summary: BTC and XAU bias cards, a narrative built only from the numbers, the red-folder
   list with countdowns (released events stay dimmed for 12 h), and 6 h news sentiment. The topbar shows both
   biases and a countdown to the next red-folder event.
2. **Indicators & Tools**: candles + volume from `GET /api/bars`, indicators from `POST /api/indicator`, saved in
   localStorage. Overlays sit on the price pane, oscillators get their own pane, and patterns appear as markers.
   Tools: position size (lots = risk ÷ (|entry−stop| × contract size); XAUUSD 100 oz, FX 100,000, BTC 1), pivots
   from the previous UTC day, the sessions bar (UTC 24 h, now marker, hours shown in PKT) and ATR(14) 1.5× stop / 3× target.
3. **TradingView**: Advanced Chart (all of TradingView's indicators and drawing tools, symbol search on), ticker
   tape of the watchlist, TradingView TA gauge, mini overview, TradingView economic calendar.
4. **Market Analysis**: `GET /api/analysis?symbol=` refreshed every `refresh.analysis_s`.
5. **Settings**: everything in `state/settings.json` plus the keys.

## Ticker
`GET /api/ticker` (also pushed over `GET /api/stream` as `event: ticker`). Headlines are MEDIUM+ (configurable) and
younger than `headline_max_age_min`. Events are the upcoming FF High events; if fewer remain in the FF week, Medium
ones fill the slots in amber. The browser picks the first N live headlines and M live events, interleaves them,
scrolls with requestAnimationFrame at `speed` px/s and re-picks every second, so an expired item is replaced by the
next one.

## Market bias
Each component is scored −100..+100:

| Component | Score | Default weight |
|---|---|---|
| technical | average over `rating_tfs` (15m, 1h, 4h) of 100 × (buy − sell) / total TradingView indicators | 0.30 |
| flow | (1 h taker buy share − 50%) × 4 | 0.25 |
| orderbook | 2 × imbalance % within ±0.5% of mid | 0.15 |
| positioning | 50 × log2(long/short accounts) ± up to 20 for funding (crowd, trend-following reading) | 0.10 |
| news | News Desk impact-weighted, time-decayed sentiment (XAU or BTC tag) | 0.15 |
| fear_greed | (index − 50) × 2, crypto only | 0.05 |

Score = Σ score × weight ÷ Σ weight over the available components. If a High-impact event for the symbol's
currencies is within `event_window_min` (60), the score is multiplied by (1 − `event_dampen`) (0.5). Above
+`neutral_band` (15) = **BULLISH**, below − = **BEARISH**, otherwise **NEUTRAL**. Confidence = share of weight available ×
share of components that agree with the sign. This is analysis, not a trade signal.

## API (all GET unless noted)
`/health` · `/api/symbols` · `/api/settings` (POST to save, POST `/api/settings/reset`) · `/api/secrets` (POST) ·
`/api/status` · `/api/bars?symbol&tf&limit&source` · `/api/ratings?symbol` · `/api/flow?symbol` ·
`/api/orderbook?symbol` · `/api/positioning?symbol` · `/api/analysis?symbol` · `/api/summary` · `/api/ticker` ·
`/api/redfolder` · `/api/indicators` · POST `/api/indicator {id, symbol, tf, params}` · `/api/tools/pivots` ·
`/api/tools/sessions` · `/api/tools/atr` · POST `/api/tools/position` · `/api/stream` (SSE: ticker, summary, item) ·
`/api/news/*` (News Desk: feed, item, sources, calendar, sentiment, stream, POST refresh).

## Screens
`python scripts/shoot.py --url http://127.0.0.1:8702 --tabs` writes desktop (1600×1000) and phone (390×844) PNGs of
every view and each Market Analysis sub-tab into `docs/screens/`, and prints any horizontal overflow and console errors.
