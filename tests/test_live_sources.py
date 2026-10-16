"""Live smoke tests against the real public sources. Skipped unless HQT_LIVE_TESTS=1 (network + rate limits)."""
import os

import pytest

pytestmark = pytest.mark.skipif(os.getenv("HQT_LIVE_TESTS") != "1", reason="set HQT_LIVE_TESTS=1 to hit live sources")


def test_okx_flow_btc():
    from hqterm import market
    f = market.flow("BTCUSD")
    assert f["available"] and f["venues"][0]["exact"] and f["venues"][0]["series"]


def test_okx_gold_perp_book():
    from hqterm import market
    ob = market.orderbook("XAUUSD")
    assert ob["available"] and ob["proxy"] and ob["mid"] > 1000


def test_yahoo_futures_bars():
    from hqterm import market
    b = market.bars("XAGUSD", "15m", 100)
    assert b["bars"] and b["source"].startswith("Yahoo")
