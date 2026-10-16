from hqterm import market, symbols


def test_rating_score():
    assert market.rating_score({"BUY": 13, "SELL": 3, "NEUTRAL": 10}) == round(100 * 10 / 26, 1)
    assert market.rating_score({}) == 0


def test_positioning_score():
    assert market.positioning_score(1.0, 0) == 0
    assert market.positioning_score(2.0, None) == 50
    assert market.positioning_score(0.5, None) == -50
    assert market.positioning_score(None, 0.0001) is None
    assert market.positioning_score(16, 0.01) == 100


def test_series_and_window():
    rows = [(0, 10, 5), (300, 5, 10), (600, 20, 0)]
    s = market._series(rows)
    assert [r["cvd"] for r in s] == [5, 0, 20]
    w = market._window(s, 600)
    assert w["buy"] == 25 and w["sell"] == 10 and w["buy_pct"] == round(100 * 25 / 35, 1)


def test_clv_split():
    rows = market._clv_rows([dict(time=0, open=1, high=2, low=1, close=2, volume=100),
                             dict(time=1, open=2, high=2, low=1, close=1, volume=100),
                             dict(time=2, open=1, high=1, low=1, close=1, volume=0)])
    assert rows == [(0, 100, 0), (1, 0, 100)]


def test_flow_score():
    assert market._flow_score({"w1h": {"buy_pct": 62.5}}) == 50
    assert market._flow_score({"w1h": {"buy_pct": None}}) is None


def test_symbols_registry_honest_about_proxies():
    for s, p in symbols.PAIRS.items():
        assert p["tvw"] and ":" in p["tvw"]
        if p["kind"] in ("metal", "fx"):
            assert p["notes"], f"{s} must explain its volume proxy"
    assert symbols.get("xauusd")["okx_swap"] == "XAU-USDT-SWAP"


def test_unknown_symbol():
    import pytest
    with pytest.raises(KeyError):
        symbols.get("NOPE")
