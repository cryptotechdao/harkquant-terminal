import math

from hqterm import indicators


def bars(n=300):
    out = []
    for i in range(n):
        c = 100 + 10 * math.sin(i / 15) + i * 0.05
        out.append(dict(time=1_700_000_000 + i * 900, open=c - 0.3, high=c + 1, low=c - 1, close=c, volume=100 + i % 7))
    return out


def test_catalog_has_talib_and_extras():
    c = indicators.catalog()
    ids = {it["id"] for g in c["groups"] for it in g["items"]}
    assert c["total"] > 150 and "talib:RSI" in ids and "pta:SUPERTREND" in ids and "talib:CDLENGULFING" in ids


def test_talib_multi_output():
    r = indicators.compute("talib:MACD", bars(), {"fastperiod": 12})
    assert r["pane"] == "own" and [l["name"] for l in r["lines"]] == ["macd", "macdsignal", "macdhist"]
    assert r["lines"][2]["hist"] and len(r["lines"][0]["data"]) > 200


def test_talib_overlay():
    r = indicators.compute("talib:BBANDS", bars(), {"timeperiod": 20})
    assert r["pane"] == "price" and len(r["lines"]) == 3


def test_pattern_markers():
    r = indicators.compute("talib:CDLENGULFING", bars(), {})
    assert r["pane"] == "marker" and isinstance(r["markers"], list)


def test_pandas_ta_extras():
    for k in indicators.PTA:
        r = indicators.compute(f"pta:{k}", bars(), {})
        assert r["lines"] and any(l["data"] for l in r["lines"]), k


def test_bad_params_and_ids():
    import pytest
    with pytest.raises(ValueError):
        indicators.compute("talib:RSI", bars(), {"timeperiod": 0})
    with pytest.raises(KeyError):
        indicators.compute("nope:X", bars(), {})
