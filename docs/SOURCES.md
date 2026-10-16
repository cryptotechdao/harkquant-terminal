# Data sources: live, needs a key, proxy

Tested 2026-10-16 from the Hark sandbox (a cloud IP that some sites rate-limit). On a home Windows PC the
rate-limited ones (Reddit, FinancialJuice, TradingView scanner) normally work better.

Status dots, everywhere in the app: **green** ok · **amber** pending / stale · **red** failing · **blue** needs a
key · **grey** switched off in Settings. The topbar shows OKX, TV (tradingview-ta), YF (Yahoo), NEWS and KEY (any
source waiting for a key).

## Market data (no keys)
| Data | Source | Status | Exact or proxy |
|---|---|---|---|
| Chart prices on Dashboard and TradingView pages | TradingView official embed widgets | live | TradingView's own feed (OANDA:XAUUSD, BITSTAMP:BTCUSD, ...) |
| Technical ratings, indicator readings | `tradingview-ta` (TradingView scanner) | live; HTTP 429 when polled too fast from one IP, so the app caches 3 min, backs off 5 min and serves stale ≤1h | exact (TradingView's ratings) |
| Crypto bars, trades, order book | OKX via ccxt | live | exact |
| Crypto taker buy/sell volume (5 min) | OKX rubik `taker-volume` (spot) and `taker-volume-contract` (perp) | live | exact: OKX reports the aggressor side |
| Funding, long/short account ratio, open interest | OKX `*-USDT-SWAP` | live | exact for crypto |
| Gold bars, trades, order book, taker volume, L/S, OI | OKX `XAU-USDT-SWAP` (gold perpetual) | live | **PROXY**: a crypto-venue gold contract, not the OTC spot market |
| Gold volume #2 | COMEX `GC=F` futures (Yahoo) | live, delayed ~10-15 min | **PROXY**: total volume only; buy/sell split is **estimated** from bar shape (close-location value) |
| Silver / FX / indices / oil volume | CME/ICE futures on Yahoo (`SI=F`, `6E=F`, `6B=F`, `6J=F`, `YM=F`, `NQ=F`, `ES=F`, `CL=F`, `DX=F`) | live, delayed | **PROXY**, estimated split, as above |
| FX / index bars on the own-data chart | Yahoo (`EURUSD=X`, `^GDAXI`, ...) | live | spot-like quotes, no volume |
| Order book for FX / indices / silver | none | n/a | Spot FX and CFDs have no public central book; the tab says so |
| NAS100, SPX500, USOIL TradingView rating | none | n/a | `tradingview-ta` has no match for these CFDs; their bias uses the other components |

## News (News Desk, 58 sources)
| Source | Status from the sandbox | Notes |
|---|---|---|
| ForexFactory calendar (faireconomy XML/JSON) | live | Red folder = `impact == High`. FF publishes one week at a time (Sun-Sat); actuals come from FinancialJuice squawks |
| ForexFactory news | fails (HTML page, no RSS) | kept for retry |
| FinancialJuice RSS | live (rate-limits cloud IPs at times) | best from a home PC |
| investingLive / ForexLive | live | |
| Truth Social, @realDonaldTrump | live via trumpstruth.org RSS | the official Truth Social API returns 403 (Cloudflare) |
| X: DeItaone, FirstSquawk, LiveSquawk, zerohedge, KobeissiLetter, Fxhedgers, financialjuice, WalterBloomberg | **needs key**. Public nitter mirrors work only now and then (some items did arrive via nitter.cz) | add an **X API v2 bearer token** (Basic tier or higher) or your own nitter/RSSHub base URL in Settings |
| Reddit r/Forex, r/Gold, r/Bitcoin, r/CryptoCurrency, r/wallstreetbets, r/economics | 4/6 live, 2 got HTTP 403 (cloud IP) | normally fine from a home PC; or add a Reddit app client id/secret (OAuth) in Settings |
| Reuters, Kitco, FXStreet | live **via Google News** `site:` searches | their direct RSS is 404/403 |
| Google News (gold, Fed, bitcoin, CPI, tariffs) | live | |
| Investing.com, CNBC, MarketWatch, WSJ, FT, Seeking Alpha, ZeroHedge, FXEmpire, BBC, Al Jazeera, Mining.com, ET | live | |
| CoinDesk, Cointelegraph, The Block, Decrypt, Bitcoinist | live | |
| Fed press + speeches, ECB, BLS | live | BIS speeches stay in the research fetchers |
| Crypto Fear & Greed (alternative.me) | live | |
| Yahoo Finance RSS, Kitco RSS, FXStreet RSS, DailyFX | fail (404/403) | retried with backoff, can be switched off in Settings |

Every item carries source, time (PKT), asset tags (XAU/BTC/USD/RISK), impact (LOW...CRITICAL), direction per asset
and a one-line reason. Optional LLM re-scoring of HIGH/CRITICAL items is off by default; turn it on in Settings with an
OpenAI-compatible URL + model (headlines only are sent).

## Keys (Settings > API keys)
Stored in `state/secrets.json` on the PC running the server. They are never sent back to the browser and never committed.
Environment variables override them: `HQT_X_BEARER`, `HQT_NITTER_BASE`, `HQT_REDDIT_CLIENT_ID`,
`HQT_REDDIT_CLIENT_SECRET`, `HQT_LLM_URL`, `HQT_LLM_MODEL`, `HQT_LLM_KEY`.
