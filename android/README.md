# HarkQuant Terminal — Android

Native Android client (Kotlin + Jetpack Compose, Material 3) for the HarkQuant Terminal market/news dashboard.
Read-only analysis: **the app never places, modifies or cancels orders.**

- Package `com.harkquant.terminal` · versionName 1.0.0 · minSdk 26 · targetSdk/compileSdk 35
- Default server: `https://your-server.example.com` (editable in Settings)
- All times are shown in **Asia/Karachi (PKT, UTC+05:00)**, independent of the phone's time zone.

## Screens
| Tab | What it shows |
|---|---|
| Dashboard | live ticker strip, BTCUSD / XAUUSD price cards (24h change + sparkline), native candlestick chart (5m–1d), bias pills + bias meter + component bars, narrative summary, red-folder countdowns, news sentiment + Fear & Greed, latest headlines → full **News** screen (search/impact/asset filters, live items, detail sheet) and **economic calendar** (today / week / upcoming, high-impact filter) |
| Indicators & Tools | symbol + timeframe, candlestick chart with server-computed TA-Lib overlays (EMA etc.) and own-pane indicators (RSI, MACD…), catalog of 180+ indicators with search and editable params, position-size calculator (offline maths + “verify on server” via `POST /api/tools/position`), ATR stop/target, pivots (classic/fib/camarilla/woodie), trading sessions in PKT |
| TradingView | native chart from our `/api/bars` data (EMA 21 overlay) **or** the official TradingView Advanced Chart widget (WebView only on this tab, timezone Asia/Karachi) |
| Market Analysis | per-symbol: combined bias + score breakdown, volume/taker flow (delta + CVD), order-book pressure + depth, technical ratings + long/short/funding/OI, news sentiment |
| Settings | 20 themes (live switch, DataStore), server URL + connection test + source status, refresh interval, server settings token (EncryptedSharedPreferences, Android Keystore), editable server settings (POST with token), headline alerts, about |

Charts are custom Compose Canvas: pinch to zoom, horizontal drag to scroll (vertical drags still scroll the page), long-press for a PKT crosshair with OHLC, double-tap to reset.

## Live data
`/api/stream` (SSE: `ticker`, `summary`, `item`) via OkHttp-SSE; on disconnect the app falls back to polling at the refresh
interval and retries the stream with backoff (2 s → 60 s). States: LIVE / POLLING / OFFLINE, skeleton loaders, stale-data banners and retry cards.
The stream stops when the app is in the background unless headline alerts are on.

## Themes
5 layout forms × 4 palettes, specs taken from the web theme pack (`work/themes`): A Pro Terminal (dense, hairline borders, uppercase mono labels, horizontal bias meter), B Glassmorphism (gradient + blurred blobs, translucent cards, floating pill nav, gauge), C Minimal Editorial (serif masthead, rules instead of cards, linear scale), D Cyber HUD (grid background, cut-corner glowing frames, Orbitron, ring gauge), E Mobile App (big rounded cards, greeting header, gauge, tab bar). Default: **17 Mobile App · Onyx Lime**.

## Architecture
MVVM: `data/` (kotlinx.serialization models, `HqApi` on OkHttp, `Repository` with StateFlows + SSE/polling, `Prefs` DataStore + encrypted token) → `ui/ViewModels.kt` → stateless `*Content` composables in `ui/screens/` (bound in `Routes.kt`). No DI framework (a tiny service locator in `HqApp`).

## Build
```bash
export JAVA_HOME=/path/to/jdk17 ANDROID_HOME=/path/to/sdk
./gradlew :app:testDebugUnitTest :app:assembleRelease :app:bundleRelease
```
Release signing is read from a properties file **outside the repo** (`../.secrets/hqt-signing.properties`, or `HQT_SIGNING=/path`):
```
storeFile=/abs/path/hqt-release.jks
storePassword=…
keyAlias=harkquant
keyPassword=…
```
Without it the release build is produced unsigned. On linux-aarch64 hosts add `android.aapt2FromMavenOverride=<arm64 aapt2>` to `gradle.properties`.

## Tests & screenshots
- `app/src/test`: format/time-zone tests (PKT independent of device zone, countdowns, prices), parsing tests against JSON captured from the live server (also cross-checking every `time_pkt` the server writes against our formatter), API tests with MockWebServer (query building, news router prefix, token only on POST, 401/502 messages, SSE parsing), theme tests, position-sizing vs server example.
- `ScreenshotTest` (Robolectric + Roborazzi) renders the real screens on x86_64/macOS hosts; it skips itself on linux-aarch64 where Robolectric has no native graphics runtime.
- `desktop-shots/` renders the same stateless composables with Compose Desktop (Skia) on any host: `cd desktop-shots && ./gradlew shots` → `screenshots/*.png`.
