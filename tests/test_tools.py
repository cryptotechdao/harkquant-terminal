from datetime import datetime, timezone

import pytest
from hqterm import tools


def test_classic_pivots():
    p = tools.pivots(110, 90, 100)["classic"]
    assert p["P"] == 100 and p["R1"] == 110 and p["S1"] == 90 and p["R2"] == 120 and p["S2"] == 80


def test_position_size_gold():
    r = tools.position_size(1000, 1, None, 4000, 3995, contract=100)
    assert r["risk"] == 10 and r["lots"] == pytest.approx(0.02) and r["side"] == "long"


def test_position_size_money_short():
    r = tools.position_size(0, None, 50, 100, 101, contract=1)
    assert r["lots"] == 50 and r["side"] == "short"


def test_position_size_rejects_bad_input():
    with pytest.raises(ValueError):
        tools.position_size(1000, 1, None, 100, 100)


def test_sessions_london_open_on_weekday():
    s = {x["name"]: x for x in tools.sessions(datetime(2026, 10, 14, 9, 0, tzinfo=timezone.utc))}  # Wednesday
    assert s["London"]["active"] and not s["New York"]["active"] and not s["Sydney"]["active"]
    assert s["Tokyo"]["active"] is False and s["Tokyo"]["next_open"] > 0


def test_sessions_closed_on_saturday():
    s = tools.sessions(datetime(2026, 10, 17, 12, 0, tzinfo=timezone.utc))
    assert not any(x["active"] for x in s)


def test_daily_hlc():
    day = 86400
    bars = [dict(time=t, high=h, low=l, close=c) for t, h, l, c in
            [(0, 10, 5, 7), (3600, 12, 6, 11), (day, 20, 15, 18), (day + 60, 21, 14, 19)]]
    assert tools.daily_hlc(bars) == (12, 5, 11)


def test_atr_levels():
    r = tools.atr_levels(100, 2, "short", 1.5, 2)
    assert r["sl"] == 103 and r["tp"] == 94
