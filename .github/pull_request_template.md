## Summary

<!-- What does this change and why? Link the issue it closes, e.g. "Closes #12". -->

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] Data source added / changed
- [ ] Documentation
- [ ] Refactor / tooling / CI

## Checklist

- [ ] `python -m pytest -q` passes locally (and `./gradlew :app:testDebugUnitTest` for Android changes)
- [ ] New backend behaviour is covered by offline tests; network tests are behind `HQT_LIVE_TESTS=1`
- [ ] The change stays read-only: no order, trade or signal-copy functionality
- [ ] No mock data in the default path; approximations are tagged PROXY
- [ ] No secrets, tokens, keystores, server addresses or `state/` files are committed
- [ ] Docs and `CHANGELOG.md` (Unreleased) are updated where relevant

## Screenshots

<!-- For UI changes, before/after screenshots (desktop and phone width if possible). -->
