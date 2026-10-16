import pytest
from fastapi.testclient import TestClient

from hqterm import config


@pytest.fixture()
def client(monkeypatch):
    from hqterm import app as appmod
    with TestClient(appmod.app) as c:
        yield c


def test_settings_roundtrip_and_validation(client):
    r = client.post("/api/settings", json={"ticker": {"speed": 9999, "paused": True}, "bogus": 1})
    assert r.status_code == 200
    s = r.json()["settings"]
    assert s["ticker"]["speed"] == 400 and s["ticker"]["paused"] is True and "bogus" not in s
    assert client.get("/api/settings").json()["settings"]["ticker"]["speed"] == 400
    assert client.post("/api/settings/reset").json()["settings"]["ticker"]["speed"] == config.DEFAULTS["ticker"]["speed"]


def test_secrets_never_returned(client):
    client.post("/api/secrets", json={"x_bearer": "AAAAsecretvalue1234567890", "not_allowed": "x"})
    body = client.get("/api/settings").text
    assert "secretvalue" not in body
    sec = client.get("/api/secrets").json()
    assert sec["x_bearer"]["set"] is True and "not_allowed" not in sec
    client.post("/api/secrets", json={"x_bearer": ""})
    assert client.get("/api/secrets").json()["x_bearer"]["set"] is False


def test_token_protects_writes_only(client, monkeypatch):
    monkeypatch.setenv("HQT_TOKEN", "t" * 20)
    assert client.post("/api/settings", json={}).status_code == 401
    assert client.post("/api/settings", json={}, headers={"Authorization": "Bearer " + "t" * 20}).status_code == 200
    assert client.get("/api/settings").status_code == 200
    assert client.post("/api/tools/position", json={"balance": 1000, "risk_pct": 1, "entry": 10, "stop": 9}).status_code == 200


def test_static_shell_and_health(client):
    assert client.get("/health").json()["ok"]
    r = client.get("/")
    assert r.status_code == 200 and "HarkQuant" in r.text and "frame-ancestors 'none'" in r.headers["content-security-policy"]
    assert client.get("/assets/app.js").status_code == 200 and client.get("/news/news.js").status_code == 200
    assert client.get("/manifest.webmanifest").status_code == 200


def test_no_trading_endpoints(client):
    from hqterm import app as appmod
    paths = [getattr(r, "path", "") for r in appmod.app.routes]
    import re
    bad = re.compile(r"/(orders?|trades?|buy|sell|close|cancel|execute|place)(/|$)", re.I)
    assert not [p for p in paths if bad.search(p)]
    assert not any(getattr(r, "methods", None) and {"PUT", "DELETE"} & set(r.methods) for r in appmod.app.routes)


def test_symbols_and_unknown(client):
    assert len(client.get("/api/symbols").json()["symbols"]) >= 15
    assert client.get("/api/bars?symbol=NOPE").status_code == 404
    assert client.get("/api/bars?symbol=BTCUSD&tf=7m").status_code == 422


def test_ticker_and_calendar_with_seeded_db(client):
    import time
    from hqterm.newsdesk import get_desk
    d = get_desk()
    now = time.time()
    d.db.execute("INSERT OR REPLACE INTO calendar(id,ts,country,title,impact) VALUES('e1',?, 'USD','CPI m/m','High')", (now + 3600,))
    d.db.execute("INSERT OR REPLACE INTO calendar(id,ts,country,title,impact) VALUES('e2',?, 'USD','Old','High')", (now - 3600,))
    src = dict(id="t", name="Test", label="T", category="wire", cred=1.0, assets=[])
    d._ingest(src, [dict(title="Fed hikes rates by 50bp in surprise hawkish move", url="https://x/1", ts=now - 60)])
    d.db.commit()
    t = client.get("/api/ticker").json()
    assert t["events"][0]["text"] == "USD CPI m/m" and t["events"][0]["red"]
    assert all(e["ts"] > now for e in t["events"])
    assert any("Fed hikes" in h["text"] for h in t["headlines"])
    rf = client.get("/api/redfolder").json()["events"]
    assert {e["title"] for e in rf} >= {"CPI m/m"}
