# Contributing to HarkQuant Terminal

Thanks for your interest in improving HarkQuant Terminal. Bug reports, documentation fixes, new data sources and
code contributions are all welcome.

By participating you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Ground rules

- **Read-only, always.** HarkQuant Terminal never places, modifies or cancels orders. Pull requests that add order
  execution, broker/exchange trading keys or signal-copy features will not be accepted. The test suite checks that
  no trading endpoint exists.
- **No fake data.** Do not add mock or demo data to the default code path. If a source fails, show that it failed.
  Anything that approximates a value that does not exist centrally must be tagged **PROXY** and explained.
- **No secrets in the repo.** Never commit API keys, tokens, keystores, `signing.properties`, server addresses or
  anything from `state/`. Use environment variables or the Settings page.

## Getting started

```bash
git clone https://github.com/<you>/harkquant-terminal.git
cd harkquant-terminal
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python -m pytest -q
.venv/bin/python -m hqterm serve --port 8702   # http://127.0.0.1:8702/
```

On Windows use `Install-HarkQuant-Terminal.bat`, or the same commands with `.venv\Scripts\python`.

The Android app lives in [`android/`](android) and builds with JDK 17 and the Android SDK; see
[`android/README.md`](android/README.md).

## Making a change

1. Look for an existing issue, or open one first for anything larger than a small fix, so we can agree on the approach.
   Issues labelled [`good first issue`](https://github.com/cryptotechdao/harkquant-terminal/labels/good%20first%20issue)
   are a good place to start.
2. Create a branch from `main` (`fix/news-source-timeout`, `feat/docker-image`, ...).
3. Keep the change focused. Match the existing style: small modules, no build step for the web UI, plain
   HTML/CSS/JS in `static/`.
4. Add or update tests in `tests/` for backend changes. Tests must run **offline**; anything that needs the network
   belongs in `tests/test_live_sources.py` behind `HQT_LIVE_TESTS=1`.
5. Run `python -m pytest -q` (and `./gradlew :app:testDebugUnitTest` for Android changes).
6. Update the docs (`README.md`, `docs/DASHBOARD.md`, `docs/SOURCES.md`) and add a line under **Unreleased** in
   [`CHANGELOG.md`](CHANGELOG.md).
7. Open a pull request and fill in the template.

## Adding a news source

Sources are declared in `hqterm/newsdesk.py` (`SOURCES`). Give each one an id, a display name, a poll interval
that respects the provider, a reliability weight and asset tags. Then add it to the table in `docs/SOURCES.md` with
its status and any key it needs. Only use feeds the publisher offers publicly; store headlines and links, not full
articles.

## Commit messages

Use a short imperative subject line (`Add Docker compose file`, `Fix ticker pause on Safari`), with details in the
body when needed.

## Reporting bugs and security issues

- Bugs and feature ideas: [open an issue](https://github.com/cryptotechdao/harkquant-terminal/issues/new/choose).
- Security vulnerabilities: please **do not** open a public issue; follow [SECURITY.md](SECURITY.md).

## License

By contributing you agree that your contributions are licensed under the [MIT License](LICENSE).
