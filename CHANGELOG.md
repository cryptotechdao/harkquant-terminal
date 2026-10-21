# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- MIT license.
- Contributor docs: `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md` (Contributor Covenant 2.1), `SECURITY.md`, this changelog,
  issue and pull request templates.
- GitHub Actions CI running the offline pytest suite on Python 3.11 and 3.12 for every push and pull request.
- `HQT_CONTACT` environment variable for the contact sent in the User-Agent to official feeds that ask for one (BLS).

### Changed
- README rewritten: features, quick start for Windows, Linux/macOS and Android, configuration, architecture, data
  sources and licenses, roadmap and disclaimer.
- The TA-Lib indicator tests skip cleanly when TA-Lib is not installed.

## [1.0.1] - 2026-10-20

### Added
- Native Android app (`android/`): Kotlin + Jetpack Compose, Material 3, 5 screens, 20 themes, custom Canvas
  charts with pinch-zoom and crosshair, SSE live data with polling fallback, encrypted token storage, and a
  user-set server URL (default placeholder `https://your-server.example.com`).

### Changed
- Android: release builds are signed with APK signature schemes v1, v2 and v3.
- Android: native libraries are packaged compressed (legacy JNI packaging) for a smaller APK.
- Android: `versionCode` 2, `versionName` 1.0.1.

### Fixed
- Web: the service worker caches only the root navigation as the app shell instead of intercepting other paths.

## [1.0.0] - 2026-10-16

### Added
- HarkQuant Terminal backend (`hqterm`, FastAPI) and PWA web UI with five views: Dashboard, Indicators & Tools,
  TradingView, Market Analysis and Settings.
- News Desk engine: 58 sources, de-duplication, impact classifier, ForexFactory calendar, SSE streaming, and a
  TV-news-style ticker with red-folder events.
- Market data from OKX (via ccxt), Yahoo Finance (via yfinance) and TradingView ratings (via tradingview-ta), with
  PROXY tagging and stale-value fallbacks.
- 185 indicators (TA-Lib and pandas-ta-classic) on a Lightweight Charts chart, plus position-size, pivot, sessions
  and ATR tools.
- Weighted market-bias score with full breakdown and event-risk damping.
- Windows launchers (install, start, update), `start.sh` for Linux/macOS, docs and screenshots.
- Offline pytest suite (34 tests) and opt-in live-source tests.

[Unreleased]: https://github.com/cryptotechdao/harkquant-terminal/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/cryptotechdao/harkquant-terminal/releases/tag/v1.0.1
[1.0.0]: https://github.com/cryptotechdao/harkquant-terminal/commits/main
