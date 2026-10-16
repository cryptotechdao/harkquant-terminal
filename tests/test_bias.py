from hqterm import bias

W = {"technical": 0.3, "flow": 0.25, "orderbook": 0.15, "positioning": 0.1, "news": 0.15, "fear_greed": 0.05}


def test_all_bullish_is_bullish():
    r = bias.combine({k: 60 for k in W}, W, 15)
    assert r["label"] == "BULLISH" and r["score"] == 60 and r["used"] == 6 and r["agreement"] == "6/6"


def test_missing_components_renormalise():
    r = bias.combine({"technical": -40, "flow": None}, W, 15)
    assert r["used"] == 1 and r["score"] == -40 and r["label"] == "BEARISH"
    tech = next(c for c in r["components"] if c["id"] == "technical")
    assert tech["eff_weight"] == 1.0 and tech["contribution"] == -40


def test_neutral_band():
    assert bias.combine({"technical": 10}, W, 15)["label"] == "NEUTRAL"
    assert bias.combine({"technical": 15}, W, 15)["label"] == "BULLISH"
    assert bias.combine({"technical": -15}, W, 15)["label"] == "BEARISH"


def test_event_dampens_toward_neutral():
    r = bias.combine({"technical": 40}, W, 15, event={"title": "NFP", "minutes": 10}, event_dampen=0.75)
    assert r["raw"] == 40 and r["score"] == 10 and r["label"] == "NEUTRAL" and r["dampened"]


def test_clamps_and_zero_weights():
    r = bias.combine({"technical": 500, "flow": -50}, {"technical": 1, "flow": 0}, 15)
    assert r["score"] == 100 and r["used"] == 1


def test_no_components():
    r = bias.combine({}, W, 15)
    assert r["score"] == 0 and r["label"] == "NEUTRAL" and r["agreement"] == "0/0"
