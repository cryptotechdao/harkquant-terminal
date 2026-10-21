"""HarkQuant News Desk engine (copied from HarkQuant, adapted for HarkQuant Terminal: settings-driven source toggles,
X API v2 bearer, Reddit OAuth app, custom nitter base, OpenAI-compatible LLM, `needs_key` / `off` source states).

Async multi-source news poller for an XAUUSD / BTCUSD trader:
  * every source runs on its own interval (squawks 15-30 s, wires 60-180 s, slow feeds minutes)
  * conditional GET (ETag / Last-Modified), exponential backoff on failure, live health per source
  * dedupe across sources by URL and normalised-title token similarity
  * SQLite storage (data/news.db), all timestamps stored as UTC epoch seconds
  * fast rule-based impact classifier (CRITICAL/HIGH/MEDIUM/LOW, asset tags, direction, 1-line why)
  * optional LLM refinement of HIGH/CRITICAL items through harkquant.agents provider chain
    (cached, rate limited, never blocks the feed, fails silently back to the rules)
  * ForexFactory economic calendar, with actuals filled in from FinancialJuice "Actual X (Forecast Y..." squawks
  * in-process pub/sub so the API can push new items over Server-Sent Events

Public API used by newsdesk_api.py:  get_desk() -> NewsDesk ; desk.start() / desk.stop() ;
desk.feed(...), desk.sources(), desk.calendar(...), desk.sentiment(), desk.subscribe(), desk.refresh().
Env: HQ_NEWS_DB, HQ_NEWS_LLM (1/0, default 1), HQ_NEWS_LLM_INTERVAL (s, default 8), HQ_NEWS_LLM_DAILY (default 300),
     HQ_NEWS_DISABLE (comma list of source ids), HQ_NEWS_MAX_AGE_H (default 72). LLM endpoint: HQ_LLM_URL/MODEL/KEY via agents.py.
"""
from __future__ import annotations

import asyncio, calendar as _cal, hashlib, json, logging, math, os, random, re, sqlite3, time
import xml.etree.ElementTree as ET
from collections import deque
from datetime import datetime, timezone, timedelta
from email.utils import parsedate_to_datetime
from pathlib import Path
from urllib.parse import quote, urlsplit, urlunsplit

import httpx

try:
    import feedparser
except ImportError:  # pragma: no cover
    feedparser = None

log = logging.getLogger("newsdesk")
PKT = timezone(timedelta(hours=5), "PKT")
from . import config as _cfg
DB_PATH = Path(os.getenv("HQ_NEWS_DB", _cfg.STATE / "news.db"))
MAX_AGE_H = float(os.getenv("HQ_NEWS_MAX_AGE_H", "72"))
BROWSER_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
BOT_UA = "linux:harkquant.newsdesk:v1.0 (by /u/harkquant)"
# Some official sources (e.g. BLS) ask for a contact in the User-Agent. Set HQT_CONTACT to your own email/URL.
CONTACT_UA = "HarkQuant research desk " + os.environ.get(
    "HQT_CONTACT", "(+https://github.com/cryptotechdao/harkquant-terminal)")
LEVELS = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]
RULES_VERSION = "11"   # bump when classify() changes: stored rule-only items are re-scored on start

# ----------------------------------------------------------------------------------------------- sources
def _gn(q: str) -> str:
    return f"https://news.google.com/rss/search?q={quote(q)}%20when:1d&hl=en-US&gl=US&ceid=US:en"

NITTER_MIRRORS = ["https://nitter.cz", "https://nitter.net", "https://xcancel.com", "https://nitter.poast.org",
                  "https://nitter.privacydev.net", "https://nitter.space"]
RSSHUB_MIRRORS = ["https://rsshub.app", "https://rsshub.rssforever.com", "https://rsshub.ktachibana.party"]
X_ACCOUNTS = ["DeItaone", "FirstSquawk", "LiveSquawk", "zerohedge", "KobeissiLetter", "Fxhedgers", "financialjuice", "WalterBloomberg"]
SUBREDDITS = ["Forex", "Gold", "Bitcoin", "CryptoCurrency", "wallstreetbets", "economics"]

def S(id, name, url, interval, cred, cat, kind="rss", label=None, assets=(), **kw):
    return dict(id=id, name=name, url=url, interval=interval, cred=cred, category=cat, kind=kind,
                label=label or name[:4].upper(), assets=list(assets), **kw)

SOURCES: list[dict] = [
    # --- real-time squawks / social
    S("financialjuice", "FinancialJuice", "https://www.financialjuice.com/feed.ashx?xy=rss", 60, 1.0, "squawk", label="FJ"),
    S("forexlive", "investingLive (ForexLive)", "https://www.forexlive.com/feed/news", 30, 0.9, "squawk", label="FXL"),
    S("truth_trump", "Trump · Truth Social", "https://trumpstruth.org/feed", 30, 0.95, "social", label="TRUTH", trump=True),
    S("truth_api", "Truth Social API", "https://truthsocial.com/api/v1/accounts/107780257626128497/statuses?limit=20",
      600, 0.95, "social", kind="truth_api", label="TRUTH", trump=True),
] + [S(f"x_{a.lower()}", f"X @{a}", a, 150, 0.85 if a in ("DeItaone", "FirstSquawk", "LiveSquawk", "WalterBloomberg", "financialjuice") else 0.7,
       "social", kind="x", label="X") for a in X_ACCOUNTS] + [
    # --- calendar & indices
    S("ff_calendar", "ForexFactory Calendar", "https://nfs.faireconomy.media/ff_calendar_thisweek.xml", 900, 1.0, "calendar",
      kind="ff_cal", label="FF", json_url="https://nfs.faireconomy.media/ff_calendar_thisweek.json"),
    S("ff_news", "ForexFactory News", "https://www.forexfactory.com/rss/news", 900, 0.8, "wire", label="FF"),
    S("fng_crypto", "Crypto Fear & Greed", "https://api.alternative.me/fng/?limit=2", 900, 1.0, "index", kind="fng", label="F&G", assets=["BTC"]),
    # --- central banks / statistics
    S("fed_press", "Federal Reserve press", "https://www.federalreserve.gov/feeds/press_all.xml", 60, 1.0, "official", label="FED", assets=["USD"]),
    S("fed_speeches", "Federal Reserve speeches", "https://www.federalreserve.gov/feeds/speeches.xml", 300, 0.9, "official", label="FED", assets=["USD"]),
    S("ecb", "ECB press", "https://www.ecb.europa.eu/rss/press.html", 300, 0.9, "official", label="ECB"),
    S("bls", "BLS latest numbers", "https://www.bls.gov/feed/bls_latest.rss", 120, 1.0, "official", label="BLS", assets=["USD"],
      ua=CONTACT_UA),
    S("bls_empsit", "BLS Employment Situation", "https://www.bls.gov/feed/empsit.rss", 300, 1.0, "official", label="BLS", assets=["USD"],
      ua=CONTACT_UA),
    # --- wires / markets
    S("cnbc_top", "CNBC Top News", "https://search.cnbc.com/rs/search/combinedcms/view.xml?partnerId=wrss01&id=100003114", 60, 0.85, "wire", label="CNBC"),
    S("cnbc_econ", "CNBC Economy", "https://search.cnbc.com/rs/search/combinedcms/view.xml?partnerId=wrss01&id=20910258", 120, 0.85, "wire", label="CNBC"),
    S("mw_top", "MarketWatch Top", "https://feeds.content.dowjones.io/public/rss/mw_topstories", 90, 0.8, "wire", label="MW"),
    S("mw_rt", "MarketWatch Real-time", "https://feeds.content.dowjones.io/public/rss/mw_realtimeheadlines", 60, 0.8, "wire", label="MW"),
    S("wsj_markets", "WSJ Markets", "https://feeds.a.dj.com/rss/RSSMarketsMain.xml", 300, 0.9, "wire", label="WSJ"),
    S("ft_markets", "Financial Times Markets", "https://www.ft.com/markets?format=rss", 180, 0.9, "wire", label="FT"),
    S("investing_cmd", "Investing.com Commodities", "https://www.investing.com/rss/news_11.rss", 90, 0.8, "wire", label="INV", assets=["XAU"]),
    S("investing_news", "Investing.com Latest", "https://www.investing.com/rss/news_25.rss", 90, 0.75, "wire", label="INV"),
    S("seekingalpha", "Seeking Alpha Currents", "https://seekingalpha.com/market_currents.xml", 120, 0.7, "wire", label="SA"),
    S("zerohedge", "ZeroHedge", "https://feeds.feedburner.com/zerohedge/feed", 120, 0.6, "wire", label="ZH"),
    S("fxempire", "FXEmpire", "https://www.fxempire.com/api/v1/en/articles/rss/news", 180, 0.65, "wire", label="FXE"),
    S("et_commod", "Economic Times Commodities", "https://economictimes.indiatimes.com/markets/commodities/rssfeeds/1808152121.cms", 300, 0.6, "wire", label="ET"),
    S("mining", "Mining.com", "https://www.mining.com/feed/", 600, 0.6, "wire", label="MIN", assets=["XAU"]),
    S("aljazeera", "Al Jazeera (geopolitics)", "https://www.aljazeera.com/xml/rss/all.xml", 120, 0.75, "geo", label="AJ"),
    S("bbc_business", "BBC Business", "https://feeds.bbci.co.uk/news/business/rss.xml", 180, 0.8, "wire", label="BBC"),
    S("yahoo", "Yahoo Finance", "https://finance.yahoo.com/news/rssindex", 300, 0.7, "wire", label="YF"),
    S("fxstreet", "FXStreet", "https://www.fxstreet.com/rss/news", 300, 0.75, "wire", label="FXS"),
    S("kitco", "Kitco", "https://www.kitco.com/rss/KitcoNews.xml", 300, 0.8, "wire", label="KIT", assets=["XAU"]),
    S("dailyfx", "DailyFX", "https://www.dailyfx.com/feeds/market-news", 600, 0.75, "wire", label="DFX"),
    # --- Google News (also proxies Reuters / Kitco / FXStreet which block direct RSS)
    S("gn_gold", "Google News · gold price", _gn("gold price"), 180, 0.7, "aggregator", label="GN", assets=["XAU"]),
    S("gn_fed", "Google News · Federal Reserve", _gn("Federal Reserve"), 180, 0.7, "aggregator", label="GN", assets=["USD"]),
    S("gn_btc", "Google News · bitcoin", _gn("bitcoin"), 180, 0.65, "aggregator", label="GN", assets=["BTC"]),
    S("gn_cpi", "Google News · CPI inflation", _gn("CPI inflation"), 300, 0.7, "aggregator", label="GN", assets=["USD"]),
    S("gn_tariffs", "Google News · Trump tariffs", _gn("Trump tariffs"), 300, 0.7, "aggregator", label="GN"),
    S("gn_reuters", "Reuters (via Google News)", _gn("site:reuters.com markets"), 180, 0.9, "wire", label="RTRS"),
    S("gn_kitco", "Kitco (via Google News)", _gn("site:kitco.com"), 300, 0.8, "wire", label="KIT", assets=["XAU"]),
    S("gn_fxstreet", "FXStreet (via Google News)", _gn("site:fxstreet.com gold OR dollar OR bitcoin"), 300, 0.75, "wire", label="FXS"),
    # --- crypto
    S("coindesk", "CoinDesk", "https://www.coindesk.com/arc/outboundfeeds/rss/", 90, 0.8, "crypto", label="CD", assets=["BTC"]),
    S("cointelegraph", "Cointelegraph", "https://cointelegraph.com/rss", 90, 0.7, "crypto", label="CT", assets=["BTC"]),
    S("theblock", "The Block", "https://www.theblock.co/rss.xml", 90, 0.8, "crypto", label="TB", assets=["BTC"]),
    S("decrypt", "Decrypt", "https://decrypt.co/feed", 180, 0.7, "crypto", label="DEC", assets=["BTC"]),
    S("bitcoinist", "Bitcoinist", "https://bitcoinist.com/feed/", 300, 0.55, "crypto", label="BTN", assets=["BTC"]),
] + [S(f"reddit_{s.lower()}", f"Reddit r/{s}", s, 300, 0.35, "social", kind="reddit", label="RDT",
       assets=["BTC"] if s in ("Bitcoin", "CryptoCurrency") else ["XAU"] if s == "Gold" else []) for s in SUBREDDITS]

# ----------------------------------------------------------------------------------------------- classifier
# (regex, weight, asset tags, gold dir, btc dir, label)
_R = lambda p: re.compile(p, re.I)
RULES = [
    (_R(r"\b(fomc|fed(eral reserve)? (statement|decision|holds|raises|cuts|leaves))\b"), 6, ["USD", "XAU", "BTC"], 0, 0, "FOMC decision"),
    (_R(r"\b(rate decision|interest rate decision|policy rate|rates? unchanged|holds rates|keeps rates)\b"), 5, ["USD", "XAU"], 0, 0, "rate decision"),
    (_R(r"\b(rate hike|hikes rates|raises rates|raise rates|tighten|hawkish)\b"), 4, ["USD", "XAU", "BTC"], -1, -1, "hawkish"),
    (_R(r"\b(rate cut|cuts rates|cut rates|lower rates|monetary easing|policy easing|easing cycle|dovish|qe\b|quantitative easing)\b"), 4, ["USD", "XAU", "BTC"], 1, 1, "dovish"),
    (_R(r"\b(powell|warsh|fed chair|waller|bowman|jefferson|hammack|goolsbee|logan|williams|daly|kashkari|musalem|fed'?s)\b"), 3, ["USD", "XAU"], 0, 0, "Fed speaker"),
    (_R(r"\b(fed|federal reserve|fomc)\b"), 2, ["USD", "XAU"], 0, 0, "Fed"),
    (_R(r"\b(nonfarm|non-farm|nfp|payrolls)\b"), 6, ["USD", "XAU", "BTC"], 0, 0, "NFP"),
    (_R(r"\b(cpi|consumer price|core inflation|inflation rate)\b"), 5, ["USD", "XAU", "BTC"], 0, 0, "CPI"),
    (_R(r"\b(pce|ppi|producer price|core pce)\b"), 4, ["USD", "XAU"], 0, 0, "inflation print"),
    (_R(r"\b(gdp|jobless claims|unemployment rate|retail sales|ism|pmi|jolts)\b"), 3, ["USD", "XAU"], 0, 0, "macro data"),
    (_R(r"\b(war|wars|missiles?|airstrikes?|air strikes?|strikes? on|attack(s|ed)?|invasion|invades?|invaded|troops|drone strikes?|bombing|explosions?|nuclear|retaliat\w*|escalat\w*|sunk|tankers? (hit|seized|attacked)|blockade)\b"), 4, ["XAU", "RISK"], 1, 0, "geopolitical risk"),
    (_R(r"\b(launch(es|ed)? (missile |air |drone )?strikes?|strikes? (on|against)|declares? war|invad(es|ed)|nuclear (strike|attack|test))\b"), 3, ["XAU", "RISK", "BTC"], 1, -1, "military action"),
    (_R(r"\b(ceasefire|truce|peace (deal|talks|agreement))\b"), 3, ["XAU", "RISK"], -1, 1, "de-escalation"),
    (_R(r"\b(iran|israel|russia|ukraine|taiwan|north korea|hezbollah|hamas|houthi|strait of hormuz|red sea)\b"), 1.5, ["XAU", "RISK"], 0, 0, "geopolitics"),
    (_R(r"\b(tariff|tariffs|trade war|duties|export (curbs?|controls?|ban)|curbs? .{0,25}exports|import ban)\b"), 3.5, ["USD", "XAU", "RISK"], 1, -1, "trade/tariffs"),
    (_R(r"\$\s?\d{3,}\s?(m|mn|million)\b|\$\s?\d+(\.\d+)?\s?(b|bn|billion|trillion)\b"), 1.5, [], 0, 0, "large $ figure"),
    (_R(r"\b\d{2,3}%\s+(tariffs?|duties|levy)\b"), 2, ["USD", "RISK"], 1, -1, "tariff size"),
    (_R(r"\b(sanction|sanctions|embargo)\b"), 3, ["XAU", "RISK"], 1, 0, "sanctions"),
    (_R(r"\btrump\b"), 2, ["USD", "RISK"], 0, 0, "Trump"),
    (_R(r"\b(treasury|bessent|yellen|debt ceiling|shutdown|default|downgrade)\b"), 2.5, ["USD", "XAU"], 0, 0, "fiscal/treasury"),
    (_R(r"\b(yields?|10-year|treasuries|bond rout|bond rally)\b"), 1.5, ["USD", "XAU"], 0, 0, "yields"),
    (_R(r"\b(dollar|dxy|greenback)\b"), 1.5, ["USD", "XAU"], 0, 0, "dollar"),
    (_R(r"\b(gold|bullion|xau|precious metals?|spot gold|comex)\b"), 2.5, ["XAU"], 0, 0, "gold"),
    (_R(r"\b(central bank (gold )?(buying|purchases)|pboc .*gold|gold reserves|gold etf)\b"), 2, ["XAU"], 1, 0, "gold demand"),
    (_R(r"\b(bitcoin|btc|crypto|ethereum|stablecoins?|coinbase|binance|kraken|bybit|okx|tether|usdt)\b"), 2.5, ["BTC"], 0, 0, "crypto"),
    (_R(r"\betfs?\b.{0,40}\b(approv\w*|inflows?)\b|\brecord inflows\b"), 4, ["BTC"], 0, 1, "ETF flows/approval"),
    (_R(r"\betfs?\b.{0,40}\boutflows?\b|\brecord outflows\b"), 3, ["BTC"], 0, -1, "ETF outflows"),
    (_R(r"\b(hack|hacked|exploit(ed)?|drained|stolen|breach)\b"), 4.5, ["BTC"], 0, -1, "hack/exploit"),
    (_R(r"\b(sec|cftc|gensler|atkins|lawsuit|crackdown|ban on crypto|crypto ban)\b"), 2.5, ["BTC"], 0, 0, "regulation"),
    (_R(r"\b(strategic (bitcoin )?reserve|bitcoin reserve|saylor|microstrategy|strategy buys)\b"), 3, ["BTC"], 0, 1, "BTC accumulation"),
    (_R(r"\b(liquidat\w*|short squeeze|flash crash)\b"), 2.5, ["BTC", "RISK"], 0, 0, "liquidations"),
    (_R(r"\b(stocks? (plunge|tumble|crash|sink|rout)|sell-?off|risk-off|vix)\b"), 2.5, ["RISK", "XAU", "BTC"], 1, -1, "risk-off"),
    (_R(r"\b(oil|crude|opec|brent|wti)\b"), 1.5, ["RISK"], 0, 0, "oil"),
    (_R(r"\b(emergency|unscheduled|surprise)\b"), 3, [], 0, 0, "surprise"),
    (_R(r"\b(bank (failure|collapse|run)|bailout|contagion|insolven)"), 4, ["XAU", "BTC", "RISK"], 1, 0, "financial stress"),
]
# directional phrases on the asset itself
_GAP = r"(?:(?!\b(?:yields?|dollar|usd|stocks?|equities|oil|crude|treasur\w*|rates?|silver|bonds?)\b)[^.;:!?]){0,30}?"
_UPW = r"\b(record|all-time high|surge[sd]?|soar\w*|jump\w*|rall(y|ies|ied)|climb\w*|rise[sn]?|rising|gain\w*|higher|rebound\w*|recover\w*|attracts? buyers|up \d|breaks? above|tops?)\b"
_DNW = r"\b(slump\w*|plunge\w*|tumble\w*|crash\w*|drop\w*|fall(s|ing)?|fell|slide[sd]?|sliding|lower|down \d|sinks?|sank|retreat\w*|dips?|dipped|breaks? below|dump\w*|selloff|sell-off)\b"
GOLD_UP = _R(r"\b(gold|bullion|xau)\b" + _GAP + _UPW)
GOLD_DN = _R(r"\b(gold|bullion|xau)\b" + _GAP + _DNW)
BTC_UP = _R(r"\b(bitcoin|btc|crypto)\b" + _GAP + _UPW)
BTC_DN = _R(r"\b(bitcoin|btc|crypto)\b" + _GAP + _DNW)
NONUS = _R(r"\b(swed\w*|norw\w*|danish|denmark|uk|u\.k\.|british|britain|japan\w*|china|chinese|euro ?zone|eu\b|german\w*|france|french|ital\w*|spain|spanish|canad\w*|australia\w*|new zealand|nz|swiss|switzerland|korea\w*|india\w*|brazil\w*|mexic\w*|turk\w*|taiwan\w*|singapore\w*|hong kong|south africa\w*|indonesia\w*|poland|polish|czech|hungar\w*)\b")
USMENT = _R(r"\b(us|u\.s\.|united states|american|fed|fomc|treasury|dollar|wall street)\b")
MACRO_LABELS = {"NFP", "CPI", "inflation print", "macro data"}
NEG = _R(r"\b(no plans?|won'?t|wouldn'?t|will not|not|denies|denied|rules? out|ruled out|unlikely|disarmament|talks)\b")
USD_UP = _R(r"\b(stronger|firmer) (us )?(dollar|greenback)|\b(dollar|greenback|dxy)\b.{0,30}\b(strengthen|rise|gain|jump|surge|rall)")
USD_DN = _R(r"\b(weaker|softer) (us )?(dollar|greenback)|\b(dollar|greenback|dxy)\b.{0,30}\b(weaken|fall|drop|slide|slump|retreat)")
SQUAWK_FLAG = _R(r"^(\*|breaking|flash|urgent|just in|alert)\b|^\W*\*|🚨|⚠")
ACTUAL_RE = _R(r"^(?P<name>.+?)\s+actual\s+(?P<act>[-+]?[\d.,]+\s*[%KMBT]?)\s*\(forecast\s+(?P<fc>[^,]*),\s*previous\s+(?P<prev>[^,)]+)")
INFL_EMP = _R(r"\b(cpi|ppi|pce|inflation|price|payroll|nonfarm|employment change|wages|earnings|gdp|retail sales|pmi|ism)\b")
UNEMP = _R(r"\b(unemployment|jobless|claims)\b")
COUNTRY = {"us": "USD", "u.s.": "USD", "united states": "USD", "eurozone": "EUR", "euro zone": "EUR", "german": "EUR", "germany": "EUR",
           "uk": "GBP", "u.k.": "GBP", "british": "GBP", "japan": "JPY", "japanese": "JPY", "canada": "CAD", "canadian": "CAD",
           "australia": "AUD", "australian": "AUD", "new zealand": "NZD", "nz": "NZD", "swiss": "CHF", "china": "CNY", "chinese": "CNY",
           "french": "EUR", "italian": "EUR", "spanish": "EUR"}

def _num(s):
    if s is None: return None
    s = str(s).strip().replace(",", "")
    m = re.match(r"^([-+]?\d*\.?\d+)\s*([%KMBT]?)$", s, re.I)
    if not m: return None
    v = float(m.group(1)); mult = {"K": 1e3, "M": 1e6, "B": 1e9, "T": 1e12}.get(m.group(2).upper(), 1)
    return v * mult

def classify(title: str, summary: str = "", src: dict | None = None, ff_impact: str | None = None) -> dict:
    """Fast deterministic classifier. Returns impact, score, assets, dir {XAU,BTC}, why."""
    src = src or {}
    text = f"{title} {summary[:300]}"
    t = title
    score, assets, gold, btc, why = 0.0, set(src.get("assets", [])), 0.0, 0.0, []
    surprise_hit = False
    neg = bool(NEG.search(t))
    foreign = bool(NONUS.search(t)) and not USMENT.search(t)       # e.g. "Swedish unemployment rate" is not a USD print
    for rx, w, tags, gd, bd, lab in RULES:
        if rx.search(text):
            ww = w if rx.search(t) else w * 0.5          # matches only in summary count half
            if neg and lab == "geopolitical risk": ww *= 0.6; gd = bd = 0   # "no plans to deploy troops", "nuclear talks"
            if foreign and lab in MACRO_LABELS: ww *= 0.4; tags = []
            score += ww; assets.update(tags); gold += gd * ww; btc += bd * ww
            if lab not in why: why.append(lab)
    for up, dn, a in ((GOLD_UP, GOLD_DN, "XAU"), (BTC_UP, BTC_DN, "BTC")):
        mu, md = up.search(t), dn.search(t)
        if mu and md:                                  # both: the verb closest to the asset wins
            mu, md = (mu, None) if mu.end() <= md.end() else (None, md)
        if mu or md:
            v = 3 if mu else -3; assets.add(a); why.append(f"{'gold' if a == 'XAU' else 'BTC'} {'up' if mu else 'down'}")
            if a == "XAU": gold += v
            else: btc += v
    if USD_UP.search(t): gold -= 2; assets.update(["USD", "XAU"]); why.append("USD up")
    if USD_DN.search(t): gold += 2; assets.update(["USD", "XAU"]); why.append("USD down")
    flag = bool(SQUAWK_FLAG.search(t.strip()))
    caps = not flag and len(t) > 25 and sum(c.isupper() for c in t) > 0.7 * sum(c.isalpha() for c in t)   # squawk-style caps
    if flag: score += 3.5; why.insert(0, "breaking")
    elif caps and score >= 2: score += 1.5; why.insert(0, "squawk")
    if src.get("trump"):
        score += 2; assets.update(["USD", "RISK"]); why.insert(0, "Trump post")
    # economic data surprise: "X Actual a (Forecast f, Previous p)"
    m = ACTUAL_RE.search(t.replace("FinancialJuice:", "").strip())
    if m:
        a, f = _num(m["act"]), _num(m["fc"])
        name = m["name"].lower()
        usd = any(name.startswith(k + " ") for k in ("us", "u.s.")) or " usd" in name or "fed" in name
        if usd: assets.update(["USD", "XAU"]); score += 2
        if a is not None and f is not None and f != 0 or (a is not None and f == 0):
            surprise = (a - f) / (abs(f) if f else 1)
            hot = surprise > 0.02 if not UNEMP.search(name) else surprise < -0.02   # hot = USD-positive
            cold = surprise < -0.02 if not UNEMP.search(name) else surprise > 0.02
            if usd and (INFL_EMP.search(name) or UNEMP.search(name)) and (hot or cold):
                d = -1 if hot else 1
                gold += 3 * d; btc += 1.5 * d; score += 3 + min(4, abs(surprise) * 30); surprise_hit = True
                why.insert(0, f"{'hotter' if hot else 'softer'} than forecast ({m['act'].strip()} vs {m['fc'].strip()})")
        elif usd:
            why.insert(0, "data release")
    if ff_impact:
        score += {"High": 5, "Medium": 2.5, "Low": 0.5}.get(ff_impact, 0)
    score *= 0.55 + 0.45 * float(src.get("cred", 0.7))
    if src.get("category") == "official" and score >= 2: score += 1.5
    impact = "CRITICAL" if score >= 10.5 else "HIGH" if score >= 6.5 else "MEDIUM" if score >= 3.5 else "LOW"
    # CRITICAL only for genuinely market-moving combos: a breaking flag, a data surprise, an official/squawk/Trump
    # source or an actual policy decision, and only from credible sources
    hard = (flag or surprise_hit or src.get("trump") or "FOMC decision" in why
            or (src.get("category") in ("squawk", "official") and score >= 13))
    if impact == "CRITICAL" and (float(src.get("cred", 0.7)) < 0.75 or not hard): impact = "HIGH"
    d = {}   # direction only for assets the headline is actually tagged with
    if abs(gold) >= 1.5 and "XAU" in assets: d["XAU"] = "bullish" if gold > 0 else "bearish"
    if abs(btc) >= 1.5 and "BTC" in assets: d["BTC"] = "bullish" if btc > 0 else "bearish"
    tags = [x for x in ("XAU", "BTC", "USD", "RISK") if x in assets]
    why_s = ", ".join(dict.fromkeys(why)) or "general market news"
    if d: why_s += " → " + ", ".join(f"{k} {v}" for k, v in d.items())
    return dict(impact=impact, score=round(score, 2), assets=tags, direction=d, why=why_s[:180])

# ----------------------------------------------------------------------------------------------- helpers
_STOP = set("the a an of to in on for and or at by with from as is are be was were it its this that will after over into amid says said "
            "new vs per up down than more less has have had not no but about us u.s".split())
_PREFIX = re.compile(r"^(financialjuice:|breaking( news)?:|rt by @\w+:|r to @\w+:|just in:|flash:|urgent:|\*+|🚨+)\s*", re.I)

def clean_title(t: str) -> str:
    t = re.sub(r"\s+", " ", (t or "")).strip()
    t = re.sub(r"<[^>]+>", "", t)
    return t

def norm_title(t: str) -> str:
    t = clean_title(t).lower()
    for _ in range(3): t = _PREFIX.sub("", t)
    t = re.sub(r"\s+[-–|]\s+[^-–|]{2,40}$", "", t)          # " - Reuters" style suffix
    t = re.sub(r"[^a-z0-9%.$ ]+", " ", t)
    return re.sub(r"\s+", " ", t).strip()

def tokens(n: str) -> frozenset:
    return frozenset(w for w in n.split() if len(w) > 2 and w not in _STOP)

def norm_url(u: str) -> str:
    if not u: return ""
    try:
        p = urlsplit(u)
        if "news.google.com" in p.netloc: return u
        return urlunsplit((p.scheme, p.netloc.lower().replace("www.", ""), p.path.rstrip("/"), "", ""))
    except Exception:
        return u

def strip_html(s: str) -> str:
    s = re.sub(r"<(script|style)[^>]*>.*?</\1>", " ", s or "", flags=re.S | re.I)
    s = re.sub(r"<br\s*/?>|</p>", " ", s, flags=re.I)
    s = re.sub(r"<[^>]+>", "", s)
    import html
    return re.sub(r"\s+", " ", html.unescape(s)).strip()

def entry_ts(e) -> float | None:
    for k in ("published_parsed", "updated_parsed", "created_parsed"):
        v = e.get(k)
        if v:
            try: return float(_cal.timegm(v))
            except Exception: pass
    for k in ("published", "updated"):
        v = e.get(k)
        if v:
            try: return parsedate_to_datetime(v).timestamp()
            except Exception: pass
            try: return datetime.fromisoformat(v.replace("Z", "+00:00")).timestamp()
            except Exception: pass
    return None

def iso_utc(ts: float) -> str:
    return datetime.fromtimestamp(ts, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

def pkt(ts: float) -> str:
    return datetime.fromtimestamp(ts, PKT).strftime("%Y-%m-%d %H:%M:%S PKT")

def _hid(*parts) -> str:
    return hashlib.sha1("|".join(str(p) for p in parts).encode()).hexdigest()[:16]

# ----------------------------------------------------------------------------------------------- engine
SCHEMA = """
CREATE TABLE IF NOT EXISTS items(
  id TEXT PRIMARY KEY, ts REAL, ins REAL, source TEXT, source_name TEXT, label TEXT, category TEXT,
  title TEXT, summary TEXT, url TEXT, norm TEXT, impact TEXT, score REAL, assets TEXT, direction TEXT,
  why TEXT, also TEXT DEFAULT '[]', llm TEXT, author TEXT);
CREATE INDEX IF NOT EXISTS ix_items_ts ON items(ts);
CREATE INDEX IF NOT EXISTS ix_items_ins ON items(ins);
CREATE INDEX IF NOT EXISTS ix_items_src ON items(source);
CREATE TABLE IF NOT EXISTS calendar(
  id TEXT PRIMARY KEY, ts REAL, country TEXT, title TEXT, impact TEXT, forecast TEXT, previous TEXT,
  actual TEXT, actual_src TEXT, url TEXT, updated REAL);
CREATE INDEX IF NOT EXISTS ix_cal_ts ON calendar(ts);
CREATE TABLE IF NOT EXISTS source_state(id TEXT PRIMARY KEY, state TEXT);
CREATE TABLE IF NOT EXISTS llm_cache(k TEXT PRIMARY KEY, v TEXT, ts REAL);
CREATE TABLE IF NOT EXISTS kv(k TEXT PRIMARY KEY, v TEXT);
"""

class NewsDesk:
    def __init__(self, db_path: Path | str = DB_PATH):
        self.db_path = Path(db_path); self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self.db = sqlite3.connect(self.db_path, check_same_thread=False)
        self.db.row_factory = sqlite3.Row
        self.db.executescript(SCHEMA); self.db.execute("PRAGMA journal_mode=WAL"); self.db.commit()
        self.disabled = {x.strip() for x in os.getenv("HQ_NEWS_DISABLE", "").split(",") if x.strip()}
        try:
            st0 = _cfg.load_settings()["news"]
            self.disabled |= set(st0.get("disabled") or [])
        except Exception:
            st0 = {}
        self.sources = {s["id"]: dict(s) for s in SOURCES}
        self.state: dict[str, dict] = {}
        for sid in self.sources:
            row = self.db.execute("SELECT state FROM source_state WHERE id=?", (sid,)).fetchone()
            st = json.loads(row["state"]) if row else {}
            st.update(status=st.get("status", "pending"), fails=st.get("fails", 0), running=False)
            self.state[sid] = st
        self.recent: deque = deque(maxlen=4000)   # (ts, id, norm, tokens, nurl)
        for r in self.db.execute("SELECT id, ts, norm, url FROM items WHERE ts > ? ORDER BY ts", (time.time() - 48 * 3600,)):
            self.recent.append((r["ts"], r["id"], r["norm"], tokens(r["norm"]), norm_url(r["url"])))
        self.subs: set[asyncio.Queue] = set()
        self.tasks: list[asyncio.Task] = []
        self.client: httpx.AsyncClient | None = None
        self.wake: dict[str, asyncio.Event] = {}
        self.llm_q: asyncio.Queue | None = None
        self.llm_on = os.getenv("HQ_NEWS_LLM", "1" if st0.get("llm") else "0") not in ("0", "false", "no")
        self.llm_interval = float(os.getenv("HQ_NEWS_LLM_INTERVAL", "8"))
        self.llm_daily = int(os.getenv("HQ_NEWS_LLM_DAILY", "300"))
        self.llm_stats = dict(calls=0, ok=0, fail=0, cached=0, last_error=None, last_source=None, day=None, today=0)
        self.fng: dict | None = None
        row = self.db.execute("SELECT v FROM kv WHERE k='fng'").fetchone()
        if row: self.fng = json.loads(row["v"])
        self.started = None

    # ------------------------------------------------------------------ lifecycle
    async def start(self):
        """Start polling. Only one process per database polls (file lock); any other process (e.g. the terminal app and
        the standalone test server running together) becomes a follower that tails the DB for SSE and takes over if the
        leader exits."""
        if self.tasks: return
        self.started = time.time()
        self.leader = self._try_lock()
        if not self.leader:
            self.tasks.append(asyncio.create_task(self._follow(), name="nd:follow"))
            log.info("newsdesk: another process owns the poller for %s; following", self.db_path)
            return
        await self._start_pollers()

    def _try_lock(self) -> bool:
        try:
            self._lockf = open(str(self.db_path) + ".lock", "w")
            if os.name == "nt":                       # Windows: msvcrt byte-range lock instead of fcntl
                import msvcrt
                msvcrt.locking(self._lockf.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(self._lockf, fcntl.LOCK_EX | fcntl.LOCK_NB)
            self._lockf.write(str(os.getpid())); self._lockf.flush()
            return True
        except OSError:
            try: self._lockf.close()
            except Exception: pass
            self._lockf = None
            return False

    async def _follow(self):
        last = time.time(); last_upd = time.time()
        while True:
            await asyncio.sleep(2)
            try:
                for r in self.db.execute("SELECT * FROM items WHERE ins > ? ORDER BY ins", (last,)).fetchall():
                    last = max(last, r["ins"]); self._publish("item", self._row_out(r))
                ups = self.db.execute("SELECT v FROM kv WHERE k='last_llm_update'").fetchone()
                if ups and float(json.loads(ups["v"])["t"]) > last_upd:
                    u = json.loads(ups["v"]); last_upd = float(u["t"])
                    r = self.db.execute("SELECT * FROM items WHERE id=?", (u["id"],)).fetchone()
                    if r: self._publish("update", self._row_out(r))
                if self._try_lock():
                    log.info("newsdesk: poller leader gone, taking over"); self.leader = True
                    asyncio.create_task(self._start_pollers()); return
            except Exception as e:
                log.debug("follow: %s", e)

    async def _start_pollers(self):
        self.reclassify()
        self.client = httpx.AsyncClient(timeout=httpx.Timeout(20, connect=10), follow_redirects=True,
                                        headers={"User-Agent": BROWSER_UA, "Accept": "*/*"},
                                        limits=httpx.Limits(max_connections=40, max_keepalive_connections=20))
        self.llm_q = asyncio.Queue(maxsize=200)
        for i, sid in enumerate(self.sources):
            self.wake[sid] = asyncio.Event()
            k = self.sources[sid]["kind"]
            if k in ("x", "reddit"):     # stagger mirror/IP-sensitive sources so they never burst together
                grp = [x for x in self.sources if self.sources[x]["kind"] == k]
                delay = (5 if k == "x" else 20) + grp.index(sid) * (18 if k == "x" else 40)
            else:
                delay = min(i * 0.3, 15)
            self.tasks.append(asyncio.create_task(self._loop(sid, delay=delay), name=f"nd:{sid}"))
        self.tasks.append(asyncio.create_task(self._llm_worker(), name="nd:llm"))
        self.tasks.append(asyncio.create_task(self._calendar_alerts(), name="nd:cal"))
        self.tasks.append(asyncio.create_task(self._sentiment_ticker(), name="nd:sent"))
        log.info("newsdesk started with %d sources", len(self.sources))

    async def stop(self):
        for t in self.tasks: t.cancel()
        await asyncio.gather(*self.tasks, return_exceptions=True)
        self.tasks = []
        if self.client: await self.client.aclose(); self.client = None
        lf = getattr(self, "_lockf", None)
        if lf:
            try: lf.close()
            except Exception: pass
            self._lockf = None

    def reclassify(self, force=False):
        row = self.db.execute("SELECT v FROM kv WHERE k='rules_version'").fetchone()
        if row and row["v"] == RULES_VERSION and not force: return 0
        n = 0
        for r in self.db.execute("SELECT * FROM items WHERE llm IS NULL").fetchall():
            src = self.sources.get(r["source"]) or dict(cred=1.0, category=r["category"], assets=[])
            c = classify(r["title"], r["summary"] or "", src)
            self.db.execute("UPDATE items SET impact=?, score=?, assets=?, direction=?, why=? WHERE id=?",
                            (c["impact"], c["score"], json.dumps(c["assets"]), json.dumps(c["direction"]), c["why"], r["id"])); n += 1
        self.db.execute("INSERT OR REPLACE INTO kv VALUES('rules_version',?)", (RULES_VERSION,)); self.db.commit()
        log.info("re-classified %d items with rules v%s", n, RULES_VERSION)
        return n

    def set_disabled(self, ids) -> None:
        env = {x.strip() for x in os.getenv("HQ_NEWS_DISABLE", "").split(",") if x.strip()}
        new = env | {i for i in (ids or []) if i in self.sources}
        woke = self.disabled - new
        self.disabled = new
        for sid in woke:
            self.state[sid]["status"] = "pending"
            if sid in self.wake: self.wake[sid].set()

    def refresh(self, source: str | None = None) -> list[str]:
        ids = [source] if source and source in self.sources else list(self.sources)
        for sid in ids:
            self.state[sid]["forced"] = True
            ev = self.wake.get(sid)
            if ev: ev.set()
        return ids

    # ------------------------------------------------------------------ pub/sub
    def subscribe(self) -> asyncio.Queue:
        q = asyncio.Queue(maxsize=500); self.subs.add(q); return q

    def unsubscribe(self, q):
        self.subs.discard(q)

    def _publish(self, event: str, data):
        for q in list(self.subs):
            try: q.put_nowait((event, data))
            except asyncio.QueueFull: pass

    # ------------------------------------------------------------------ per-source loop
    async def _loop(self, sid: str, delay: float = 0):
        await asyncio.sleep(delay)
        src = self.sources[sid]
        while True:
            st = self.state[sid]
            if sid in self.disabled:
                st.update(status="off", running=False)
                ev = self.wake[sid]
                try: await asyncio.wait_for(ev.wait(), timeout=30)
                except asyncio.TimeoutError: pass
                ev.clear(); continue
            t0 = time.time(); st["running"] = True
            try:
                n_parsed, new = await self._fetch(src, st)
                st.update(status="ok", last_ok=time.time(), fails=0, last_error=None, items_last_fetch=n_parsed,
                          new_last_fetch=new)
                if new and src["kind"] != "ff_cal": st["last_new"] = time.time()
            except asyncio.CancelledError:
                raise
            except Exception as e:
                st["fails"] = st.get("fails", 0) + 1
                st.update(status="needs_key" if isinstance(e, NeedsKey) else "fail", last_error=_reason(e)[:220])
                log.debug("source %s failed: %s", sid, e)
            finally:
                st["running"] = False
                st["last_fetch"] = time.time(); st["latency_ms"] = int((time.time() - t0) * 1000)
                self._save_state(sid)
            interval = src["interval"] * (min(2 ** st["fails"], 12) if st["fails"] else 1)
            if st.get("retry_after"): interval = max(interval, st.pop("retry_after"))
            interval = min(interval, 1800) * random.uniform(0.9, 1.1)
            st["next_fetch"] = time.time() + interval
            ev = self.wake[sid]
            try:
                await asyncio.wait_for(ev.wait(), timeout=interval)
            except asyncio.TimeoutError:
                pass
            ev.clear(); st.pop("forced", None)

    def _save_state(self, sid):
        st = {k: v for k, v in self.state[sid].items() if k not in ("running", "forced")}
        self.db.execute("INSERT OR REPLACE INTO source_state VALUES(?,?)", (sid, json.dumps(st))); self.db.commit()

    async def _get(self, url, st, headers=None, conditional=True):
        h = dict(headers or {})
        if conditional and not st.get("forced"):
            if st.get("etag"): h["If-None-Match"] = st["etag"]
            if st.get("lastmod"): h["If-Modified-Since"] = st["lastmod"]
        r = await self.client.get(url, headers=h)
        st["http"] = r.status_code
        if r.status_code == 304: return None
        if r.status_code >= 400:
            ra = r.headers.get("retry-after")
            if r.status_code == 429 and ra and ra.isdigit(): st["retry_after"] = min(int(ra), 1800)
            raise FetchError(_http_reason(r))
        if conditional:
            st["etag"] = r.headers.get("etag"); st["lastmod"] = r.headers.get("last-modified")
        return r

    async def _fetch(self, src, st) -> tuple[int, int]:
        k = src["kind"]
        if k == "rss": return await self._fetch_rss(src, st, src["url"], {"User-Agent": src["ua"]} if src.get("ua") else None)
        if k == "x": return await self._fetch_x(src, st)
        if k == "reddit": return await self._fetch_reddit(src, st)
        if k == "truth_api": return await self._fetch_truth_api(src, st)
        if k == "ff_cal": return await self._fetch_ff(src, st)
        if k == "fng": return await self._fetch_fng(src, st)
        raise FetchError(f"unknown kind {k}")

    async def _fetch_rss(self, src, st, url, headers=None, conditional=True, author=None):
        r = await self._get(url, st, headers, conditional)
        if r is None: return st.get("items_last_fetch", 0), 0
        ct = r.headers.get("content-type", "")
        f = feedparser.parse(r.content)
        if not f.entries:
            if "html" in ct and b"<rss" not in r.content[:2000] and b"<feed" not in r.content[:2000]:
                raise FetchError(f"HTTP {r.status_code} but HTML, not a feed ({_html_hint(r.text)})")
            raise FetchError("feed parsed with 0 entries")
        items = []
        for e in f.entries[:60]:
            title = clean_title(e.get("title", ""))
            summ = strip_html(e.get("summary", "") or (e.get("content") or [{}])[0].get("value", ""))
            if src.get("trump") and (not title or title.lower().startswith("[no title]")):
                title = (summ[:220] + "…") if len(summ) > 220 else summ
            if src["kind"] == "x":
                title = re.sub(r"^(RT by @\w+:\s*|R to @\w+:\s*)", "", title)
                title = re.sub(r"\s*(https?://\S+|pic\.twitter\.com/\S+)", "", title).strip()
                if len(title) > 260: title = title[:257] + "…"
            if not title: continue
            srcname = None
            if src["category"] == "aggregator" or src["id"].startswith("gn_"):
                srcname = (e.get("source") or {}).get("title")
                if srcname and title.endswith(" - " + srcname): title = title[: -len(srcname) - 3]
            if summ and (summ.startswith(title[:50]) or src["id"].startswith("gn_")) and len(summ) < len(title) + 80:
                summ = ""                                        # Google News & co repeat the title as the summary
            items.append(dict(title=title, summary=summ[:600] if summ != title else "", url=e.get("link", ""),
                              ts=entry_ts(e), author=author or srcname))
        return len(f.entries), self._ingest(src, items)

    async def _fetch_x_api(self, src, st, bearer):
        acct = src["url"]; h = {"Authorization": f"Bearer {bearer}"}
        uid = st.get("x_uid")
        if not uid:
            r = await self.client.get(f"https://api.twitter.com/2/users/by/username/{acct}", headers=h)
            st["http"] = r.status_code
            if r.status_code >= 400: raise FetchError(f"X API {r.status_code}: {r.text[:120]}")
            uid = st["x_uid"] = r.json()["data"]["id"]
        r = await self.client.get(f"https://api.twitter.com/2/users/{uid}/tweets",
                                  params={"max_results": 10, "tweet.fields": "created_at", "exclude": "replies"}, headers=h)
        st["http"] = r.status_code
        if r.status_code == 429:
            st["retry_after"] = 900; raise FetchError("X API rate limited (429)")
        if r.status_code >= 400: raise FetchError(f"X API {r.status_code}: {r.text[:120]}")
        items = []
        for t in r.json().get("data", []) or []:
            txt = re.sub(r"\s*https?://\S+", "", t.get("text", "")).strip()
            if not txt: continue
            ts = datetime.fromisoformat(t["created_at"].replace("Z", "+00:00")).timestamp() if t.get("created_at") else None
            items.append(dict(title=txt[:260], url=f"https://x.com/{acct}/status/{t['id']}", ts=ts, author="@" + acct))
        st["via"] = "X API v2"
        return len(items), self._ingest(src, items)

    async def _fetch_x(self, src, st):
        acct = src["url"]; errors = []
        bearer = _cfg.secret("x_bearer")
        if bearer:
            try:
                return await self._fetch_x_api(src, st, bearer)
            except Exception as e:
                errors.append(f"X API: {_reason(e)[:80]}")
        mirrors = list(NITTER_MIRRORS)
        custom = _cfg.secret("nitter_base")
        if custom: mirrors.insert(0, custom.rstrip("/"))
        if st.get("mirror") in mirrors: mirrors.remove(st["mirror"]); mirrors.insert(0, st["mirror"])
        for base in mirrors + [m + "/twitter/user" for m in RSSHUB_MIRRORS]:
            url = f"{base}/{acct}/rss" if "rsshub" not in base else f"{base}/{acct}"
            try:
                out = await self._fetch_rss(src, st, url, conditional=False, author="@" + acct)
                st["mirror"] = base; st["via"] = urlsplit(base).netloc
                return out
            except Exception as e:
                errors.append(f"{urlsplit(base).netloc}: {_reason(e)[:60]}")
        msg = "; ".join(errors[:4])
        if not bearer and not custom:
            raise NeedsKey("needs key: public nitter/RSSHub mirrors failed. Add an X API bearer token or a self-hosted "
                           "nitter/RSSHub base URL in Settings. " + msg)
        raise FetchError("X failed: " + msg)

    async def _reddit_token(self):
        cid, sec = _cfg.secret("reddit_client_id"), _cfg.secret("reddit_client_secret")
        if not (cid and sec): return None
        tok = getattr(self, "_rtok", None)
        if tok and tok[1] > time.time() + 60: return tok[0]
        r = await self.client.post("https://www.reddit.com/api/v1/access_token", data={"grant_type": "client_credentials"},
                                   auth=(cid, sec), headers={"User-Agent": BOT_UA})
        if r.status_code >= 400: raise FetchError(f"Reddit OAuth token HTTP {r.status_code}")
        j = r.json(); self._rtok = (j["access_token"], time.time() + float(j.get("expires_in", 3600)))
        return self._rtok[0]

    async def _fetch_reddit(self, src, st):
        sub = src["url"]; errs = []
        try:
            tok = await self._reddit_token()
        except Exception as e:
            tok = None; errs.append(f"oauth: {_reason(e)[:60]}")
        if tok:
            r = await self.client.get(f"https://oauth.reddit.com/r/{sub}/new", params={"limit": 25, "raw_json": 1},
                                      headers={"User-Agent": BOT_UA, "Authorization": f"bearer {tok}"})
            st["http"] = r.status_code
            if r.status_code < 400:
                kids = r.json()["data"]["children"]
                items = [dict(title=clean_title(c["data"].get("title")), summary=(c["data"].get("selftext") or "")[:400],
                              url="https://www.reddit.com" + c["data"].get("permalink", ""),
                              ts=float(c["data"].get("created_utc") or 0) or None, author="u/" + str(c["data"].get("author")))
                         for c in kids if not c["data"].get("stickied")]
                st["via"] = "Reddit OAuth"
                return len(kids), self._ingest(src, items)
            errs.append(f"oauth.reddit.com HTTP {r.status_code}")
        for url, kind in ((f"https://www.reddit.com/r/{sub}/new.json?limit=25&raw_json=1", "json"),
                          (f"https://old.reddit.com/r/{sub}/new.json?limit=25&raw_json=1", "json"),
                          (f"https://www.reddit.com/r/{sub}/new/.rss", "rss")):
            try:
                if kind == "rss":
                    return await self._fetch_rss(src, st, url, {"User-Agent": BOT_UA}, conditional=False)
                r = await self._get(url, st, {"User-Agent": BOT_UA, "Accept": "application/json"}, conditional=False)
                kids = r.json()["data"]["children"]
                items = []
                for c in kids:
                    d = c["data"]
                    if d.get("stickied"): continue
                    items.append(dict(title=clean_title(d.get("title")), summary=(d.get("selftext") or "")[:400],
                                      url="https://www.reddit.com" + d.get("permalink", ""), ts=float(d.get("created_utc") or 0) or None,
                                      author="u/" + str(d.get("author"))))
                return len(kids), self._ingest(src, items)
            except Exception as e:
                errs.append(f"{urlsplit(url).netloc}{'/.rss' if kind == 'rss' else ''}: {_reason(e)[:50]}")
        raise FetchError("Reddit blocked from this IP (" + "; ".join(errs) + "). Usually works from a home PC IP; "
                         "or add a Reddit app client id/secret in Settings.")

    async def _fetch_truth_api(self, src, st):
        r = await self._get(src["url"], st, {"Accept": "application/json"}, conditional=False)
        try: data = r.json()
        except Exception: raise FetchError(f"non-JSON response ({_html_hint(r.text)})")
        items = [dict(title=(strip_html(p.get("content", ""))[:260] or "[media post]"), url=p.get("url") or p.get("uri"),
                      ts=datetime.fromisoformat(p["created_at"].replace("Z", "+00:00")).timestamp(), author="@realDonaldTrump")
                 for p in data if isinstance(p, dict)]
        return len(items), self._ingest(src, items)

    async def _fetch_fng(self, src, st):
        r = await self._get(src["url"], st, conditional=False)
        d = r.json()["data"]
        cur = d[0]; prev = d[1] if len(d) > 1 else None
        self.fng = dict(value=int(cur["value"]), label=cur["value_classification"], ts=float(cur["timestamp"]),
                        prev=int(prev["value"]) if prev else None, source="alternative.me")
        self.db.execute("INSERT OR REPLACE INTO kv VALUES('fng',?)", (json.dumps(self.fng),)); self.db.commit()
        return len(d), 0

    async def _fetch_ff(self, src, st):
        events = []
        try:
            r = await self._get(src["url"], st, conditional=False)
            root = ET.fromstring(r.content)
            for e in root.iter("event"):
                g = {c.tag: (c.text or "").strip() for c in e}
                try:
                    tm = g.get("time", "")
                    if re.match(r"^\d{1,2}:\d{2}[ap]m$", tm, re.I):
                        dt = datetime.strptime(f"{g['date']} {tm.lower()}", "%m-%d-%Y %I:%M%p")   # feed is UTC
                        allday = False
                    else:
                        dt = datetime.strptime(g["date"], "%m-%d-%Y"); allday = True
                    ts = dt.replace(tzinfo=timezone.utc).timestamp()
                except Exception:
                    continue
                events.append(dict(ts=ts, country=g.get("country"), title=g.get("title"), impact=g.get("impact"),
                                   forecast=g.get("forecast") or None, previous=g.get("previous") or None,
                                   actual=g.get("actual") or None, url=g.get("url"), time_label=None if not allday else (tm or "All Day")))
            st["via"] = "xml"
        except Exception as ex:
            err1 = _reason(ex)
            r = await self._get(src["json_url"], st, conditional=False)
            for g in r.json():
                ts = datetime.fromisoformat(g["date"]).timestamp()
                events.append(dict(ts=ts, country=g.get("country"), title=g.get("title"), impact=g.get("impact"),
                                   forecast=g.get("forecast") or None, previous=g.get("previous") or None,
                                   actual=g.get("actual") or None, url=g.get("url"), time_label=None))
            st["via"] = f"json (xml failed: {err1[:60]})"
        now = time.time()
        for ev in events:
            cid = _hid(ev["country"], ev["title"], int(ev["ts"]))
            row = self.db.execute("SELECT actual FROM calendar WHERE id=?", (cid,)).fetchone()
            actual = ev["actual"] or (row["actual"] if row else None)
            self.db.execute("INSERT OR REPLACE INTO calendar(id,ts,country,title,impact,forecast,previous,actual,actual_src,url,updated)"
                            " VALUES(?,?,?,?,?,?,?,?,COALESCE((SELECT actual_src FROM calendar WHERE id=?),?),?,?)",
                            (cid, ev["ts"], ev["country"], ev["title"], ev["impact"], ev["forecast"], ev["previous"], actual,
                             cid, "ForexFactory" if ev["actual"] else None, ev["url"], now))
        self.db.commit()
        self._publish("calendar", {"updated": now, "count": len(events)})
        return len(events), 0

    # ------------------------------------------------------------------ ingest / dedupe
    def _dupe(self, norm: str, toks: frozenset, nurl: str, ts: float):
        for (rts, rid, rnorm, rtoks, rurl) in reversed(self.recent):
            if nurl and nurl == rurl: return rid
            if norm == rnorm: return rid
            if len(toks) >= 4 and len(rtoks) >= 4 and abs(ts - rts) < 36 * 3600:
                inter = len(toks & rtoks)
                if inter >= 4 and inter / len(toks | rtoks) >= 0.72: return rid
        return None

    def _ingest(self, src, items) -> int:
        now = time.time(); new = 0; cutoff = now - MAX_AGE_H * 3600
        for it in items:
            ts = it.get("ts") or now
            if ts > now + 120: ts = now                      # feeds with future/bad clocks
            if ts < cutoff: continue
            norm = norm_title(it["title"])
            if len(norm) < 8: continue
            toks = tokens(norm); nurl = norm_url(it.get("url", ""))
            d = self._dupe(norm, toks, nurl, ts)
            if d:
                row = self.db.execute("SELECT also, source FROM items WHERE id=?", (d,)).fetchone()
                if row and row["source"] != src["id"]:
                    also = json.loads(row["also"] or "[]")
                    if src["id"] not in also and len(also) < 12:
                        also.append(src["id"]); self.db.execute("UPDATE items SET also=? WHERE id=?", (json.dumps(also), d))
                continue
            c = classify(it["title"], it.get("summary", ""), src)
            iid = _hid(src["id"], nurl or norm)
            rec = dict(id=iid, ts=ts, ins=now, source=src["id"], source_name=src["name"], label=src["label"], category=src["category"],
                       title=it["title"], summary=it.get("summary", ""), url=it.get("url", ""), norm=norm, impact=c["impact"],
                       score=c["score"], assets=json.dumps(c["assets"]), direction=json.dumps(c["direction"]), why=c["why"],
                       also="[]", llm=None, author=it.get("author"))
            try:
                self.db.execute(f"INSERT INTO items({','.join(rec)}) VALUES({','.join('?' * len(rec))})", tuple(rec.values()))
            except sqlite3.IntegrityError:      # another writer (second process) stored it first: still new to *our* clients
                self.recent.append((ts, iid, norm, toks, nurl))
                r0 = self.db.execute("SELECT * FROM items WHERE id=?", (iid,)).fetchone()
                if r0 and r0["ins"] > now - 300: self._publish("item", self._row_out(r0))
                continue
            self.recent.append((ts, iid, norm, toks, nurl)); new += 1
            if src["id"] == "financialjuice": self._match_actual(it["title"], ts)
            out = self._row_out(rec)
            if now - ts < 6 * 3600 or self.started and now - self.started > 120:
                self._publish("item", out)
            if self.llm_on and c["impact"] in ("HIGH", "CRITICAL") and now - ts < 2 * 3600 and self.llm_q:
                try: self.llm_q.put_nowait(iid)
                except asyncio.QueueFull: pass
        self.db.commit()
        return new

    def _match_actual(self, title: str, ts: float):
        m = ACTUAL_RE.search(title.replace("FinancialJuice:", "").strip())
        if not m: return
        name = m["name"].strip().lower(); cur = None
        for k, v in sorted(COUNTRY.items(), key=lambda kv: -len(kv[0])):
            if name.startswith(k + " "): cur = v; name = name[len(k) + 1:]; break
        if not cur: return
        def nt(s):
            s = s.lower().replace("m/m", "mom").replace("q/q", "qoq").replace("y/y", "yoy")
            return set(re.findall(r"[a-z0-9]+", s)) - {"us", "the", "rate"}
        want = nt(name)
        best, bs = None, 0.0
        for r in self.db.execute("SELECT id,title,ts FROM calendar WHERE country=? AND ts BETWEEN ? AND ?", (cur, ts - 3 * 3600, ts + 1800)):
            have = nt(r["title"]); s = len(want & have) / max(len(want | have), 1)
            if s > bs: best, bs = r["id"], s
        if best and bs >= 0.5:
            self.db.execute("UPDATE calendar SET actual=?, actual_src='FinancialJuice', updated=? WHERE id=?", (m["act"].strip(), time.time(), best))
            self._publish("calendar", {"updated": time.time(), "actual": best})

    # ------------------------------------------------------------------ LLM refinement
    async def _llm_worker(self):
        last = 0.0
        while True:
            iid = await self.llm_q.get()
            row = self.db.execute("SELECT * FROM items WHERE id=?", (iid,)).fetchone()
            if not row or row["llm"]: continue
            key = hashlib.sha1(row["norm"].encode()).hexdigest()
            c = self.db.execute("SELECT v FROM llm_cache WHERE k=?", (key,)).fetchone()
            if c:
                self.llm_stats["cached"] += 1; self._apply_llm(row, json.loads(c["v"])); continue
            day = datetime.now(timezone.utc).strftime("%Y-%m-%d")
            if self.llm_stats["day"] != day: self.llm_stats.update(day=day, today=0)
            if self.llm_stats["today"] >= self.llm_daily: continue
            wait = self.llm_interval - (time.time() - last)
            if wait > 0: await asyncio.sleep(wait)
            last = time.time(); self.llm_stats["calls"] += 1; self.llm_stats["today"] += 1
            try:
                res = await asyncio.wait_for(asyncio.to_thread(_llm_judge, row["title"], row["summary"] or "", row["source_name"]), timeout=60)
                self.db.execute("INSERT OR REPLACE INTO llm_cache VALUES(?,?,?)", (key, json.dumps(res), time.time()))
                self.llm_stats["ok"] += 1; self.llm_stats["last_source"] = res.get("model")
                self._apply_llm(row, res)
            except Exception as e:
                self.llm_stats["fail"] += 1; self.llm_stats["last_error"] = _reason(e)[:160]

    def _apply_llm(self, row, res):
        imp = res.get("impact", "").upper()
        d = json.loads(row["direction"] or "{}")
        for a, k in (("XAU", "gold"), ("BTC", "btc")):
            v = res.get(k)
            if isinstance(v, (int, float)):
                if v >= 0.25: d[a] = "bullish"
                elif v <= -0.25: d[a] = "bearish"
                else: d.pop(a, None)
        newimp = imp if imp in LEVELS else row["impact"]
        llm = json.dumps(dict(impact=imp, why=res.get("why"), gold=res.get("gold"), btc=res.get("btc"), model=res.get("model")))
        why = row["why"]
        if res.get("why"): why = f"{res['why']}"[:200]
        self.db.execute("UPDATE items SET impact=?, direction=?, why=?, llm=? WHERE id=?", (newimp, json.dumps(d), why, llm, row["id"]))
        self.db.execute("INSERT OR REPLACE INTO kv VALUES('last_llm_update',?)", (json.dumps(dict(t=time.time(), id=row["id"])),))
        self.db.commit()
        r2 = self.db.execute("SELECT * FROM items WHERE id=?", (row["id"],)).fetchone()
        self._publish("update", self._row_out(dict(r2)))

    # ------------------------------------------------------------------ calendar alerts & sentiment
    async def _calendar_alerts(self):
        fired = set()
        while True:
            try:
                now = time.time()
                for r in self.db.execute("SELECT * FROM calendar WHERE impact='High' AND ts BETWEEN ? AND ?", (now, now + 15 * 60)):
                    if r["id"] in fired: continue
                    fired.add(r["id"]); mins = max(1, round((r["ts"] - now) / 60))
                    src = dict(id="calendar", name="Economic Calendar", label="CAL", category="calendar", cred=1.0,
                               assets=["USD", "XAU"] if r["country"] == "USD" else [])
                    title = f"UPCOMING in {mins} min: {r['country']} {r['title']} (fcst {r['forecast'] or '-'}, prev {r['previous'] or '-'})"
                    self._ingest(src, [dict(title=title, url=r["url"], ts=now)])
            except Exception as e:
                log.debug("calendar alert: %s", e)
            await asyncio.sleep(30)

    async def _sentiment_ticker(self):
        while True:
            await asyncio.sleep(30)
            try: self._publish("sentiment", self.sentiment())
            except Exception: pass

    def sentiment(self, hours: float = 6) -> dict:
        now = time.time(); w = {"LOW": 0.3, "MEDIUM": 1, "HIGH": 2.5, "CRITICAL": 4}
        acc = {"XAU": [0.0, 0.0, 0, 0, 0], "BTC": [0.0, 0.0, 0, 0, 0]}   # sum, weight, bull, bear, n
        for r in self.db.execute("SELECT ts, impact, direction FROM items WHERE ts > ?", (now - hours * 3600,)):
            d = json.loads(r["direction"] or "{}")
            decay = math.exp(-(now - r["ts"]) / (hours * 3600 / 2))
            for a in acc:
                if a in d:
                    s = 1 if d[a] == "bullish" else -1; ww = w.get(r["impact"], 1) * decay
                    acc[a][0] += s * ww; acc[a][1] += ww; acc[a][4] += 1
                    acc[a][2 if s > 0 else 3] += 1
        out = {}
        for a, (s, ww, bu, be, n) in acc.items():
            v = round(100 * s / ww) if ww else 0
            out[a] = dict(score=v, label="bullish" if v > 20 else "bearish" if v < -20 else "neutral", bull=bu, bear=be, n=n)
        out["window_h"] = hours; out["crypto_fear_greed"] = self.fng; out["as_of"] = iso_utc(now)
        return out

    # ------------------------------------------------------------------ queries
    def _row_out(self, r) -> dict:
        r = dict(r)
        llm = json.loads(r["llm"]) if r.get("llm") else None
        return dict(id=r["id"], ts=r["ts"], ins=r["ins"], time_utc=iso_utc(r["ts"]), time_pkt=pkt(r["ts"]), source=r["source"],
                    source_name=r["source_name"], label=r["label"], category=r["category"], title=r["title"],
                    summary=r.get("summary") or "", url=r.get("url"), impact=r["impact"], score=r["score"],
                    assets=json.loads(r["assets"] or "[]"), direction=json.loads(r["direction"] or "{}"), why=r["why"],
                    also=json.loads(r.get("also") or "[]"), llm=llm, author=r.get("author"))

    def feed(self, impact=None, source=None, asset=None, q=None, since=None, limit=200, category=None) -> dict:
        sql, args = "SELECT * FROM items WHERE 1=1", []
        if self.disabled:
            dd = sorted(self.disabled)
            sql += f" AND source NOT IN ({','.join('?' * len(dd))})"; args += dd
        if impact:
            lv = [x.strip().upper() for x in impact.split(",") if x.strip()]
            if len(lv) == 1 and lv[0].endswith("+") and lv[0][:-1] in LEVELS:
                lv = LEVELS[LEVELS.index(lv[0][:-1]):]
            sql += f" AND impact IN ({','.join('?' * len(lv))})"; args += lv
        if source:
            ss = [x.strip() for x in source.split(",") if x.strip()]
            sql += f" AND (source IN ({','.join('?' * len(ss))}) OR label IN ({','.join('?' * len(ss))}))"; args += ss + ss
        if category:
            cc = [x.strip() for x in category.split(",")]
            sql += f" AND category IN ({','.join('?' * len(cc))})"; args += cc
        if asset:
            aa = [x.strip().upper() for x in asset.split(",") if x.strip()]
            sql += " AND (" + " OR ".join("assets LIKE ?" for _ in aa) + ")"; args += [f'%"{a}"%' for a in aa]
        if q:
            for w in q.split():
                sql += " AND (title LIKE ? OR summary LIKE ?)"; args += [f"%{w}%", f"%{w}%"]
        if since:
            s = _parse_since(since)
            if s: sql += " AND ins > ?"; args.append(s)
        sql += " ORDER BY ts DESC LIMIT ?"; args.append(max(1, min(int(limit), 1000)))
        rows = [self._row_out(r) for r in self.db.execute(sql, args)]
        return dict(items=rows, count=len(rows), server_time=time.time(), server_time_pkt=pkt(time.time()))

    def item(self, iid):
        r = self.db.execute("SELECT * FROM items WHERE id=?", (iid,)).fetchone()
        return self._row_out(r) if r else None

    def sources_health(self) -> dict:
        now = time.time(); out = []
        if not getattr(self, "leader", True):          # follower: state lives in the leader process, read it from the DB
            for r in self.db.execute("SELECT id, state FROM source_state"):
                if r["id"] in self.state: self.state[r["id"]].update(json.loads(r["state"]))
        counts = {r["source"]: (r["n"], r["n24"]) for r in self.db.execute(
            "SELECT source, COUNT(*) n, SUM(CASE WHEN ts > ? THEN 1 ELSE 0 END) n24 FROM items GROUP BY source", (now - 86400,))}
        for sid, s in self.sources.items():
            st = self.state[sid]; n, n24 = counts.get(sid, (0, 0))
            status = st.get("status", "pending")
            if status == "ok" and st.get("last_ok") and now - st["last_ok"] > max(3 * s["interval"], 600): status = "stale"
            if s["kind"] == "ff_cal":
                n = self.db.execute("SELECT COUNT(*) FROM calendar").fetchone()[0]
            out.append(dict(id=sid, name=s["name"], label=s["label"], kind=s["kind"], category=s["category"], interval_s=s["interval"],
                            credibility=s["cred"], status=status, http=st.get("http"), latency_ms=st.get("latency_ms"),
                            last_fetch=st.get("last_fetch"), last_fetch_pkt=pkt(st["last_fetch"]) if st.get("last_fetch") else None,
                            last_ok=st.get("last_ok"), last_new=st.get("last_new"), next_fetch=st.get("next_fetch"),
                            consecutive_fails=st.get("fails", 0), error=st.get("last_error"), via=st.get("via"),
                            items_last_fetch=st.get("items_last_fetch"), new_last_fetch=st.get("new_last_fetch"),
                            items_total=n, items_24h=n24, running=st.get("running", False), enabled=sid not in self.disabled))
        summ = dict(total=len(out), ok=sum(o["status"] == "ok" for o in out), fail=sum(o["status"] == "fail" for o in out),
                    stale=sum(o["status"] == "stale" for o in out), pending=sum(o["status"] == "pending" for o in out),
                    needs_key=sum(o["status"] == "needs_key" for o in out), off=sum(o["status"] == "off" for o in out))
        llm = dict(self.llm_stats, enabled=self.llm_on, interval_s=self.llm_interval, daily_cap=self.llm_daily)
        return dict(summary=summ, sources=out, llm=llm, server_time=now, started=self.started,
                    role="poller" if getattr(self, "leader", True) else "follower")

    def calendar(self, range_="week", impact=None, currency=None) -> dict:
        now = time.time()
        if range_ == "today":
            d0 = datetime.now(PKT).replace(hour=0, minute=0, second=0, microsecond=0)
            lo, hi = d0.timestamp(), (d0 + timedelta(days=1)).timestamp()
        elif range_ == "upcoming":
            lo, hi = now - 3600, now + 7 * 86400
        else:
            lo, hi = now - 8 * 86400, now + 8 * 86400
        sql, args = "SELECT * FROM calendar WHERE ts BETWEEN ? AND ?", [lo, hi]
        if impact:
            ii = [x.strip().capitalize() for x in impact.split(",")]
            sql += f" AND impact IN ({','.join('?' * len(ii))})"; args += ii
        if currency:
            cc = [x.strip().upper() for x in currency.split(",")]
            sql += f" AND country IN ({','.join('?' * len(cc))})"; args += cc
        ev = []
        for r in self.db.execute(sql + " ORDER BY ts", args):
            r = dict(r); a, f = _num(r["actual"]), _num(r["forecast"])
            beat = None
            if a is not None and f is not None and a != f:
                better_low = bool(UNEMP.search(r["title"])) or "inventor" in r["title"].lower()
                beat = "better" if (a > f) != better_low else "worse"
            ev.append(dict(id=r["id"], ts=r["ts"], time_utc=iso_utc(r["ts"]), time_pkt=pkt(r["ts"]), country=r["country"],
                           title=r["title"], impact=r["impact"], forecast=r["forecast"], previous=r["previous"], actual=r["actual"],
                           actual_src=r["actual_src"], result=beat, url=r["url"], past=r["ts"] < now))
        nxt = self.db.execute("SELECT * FROM calendar WHERE impact='High' AND ts > ? ORDER BY ts LIMIT 1", (now,)).fetchone()
        nh = None
        if nxt:
            nh = dict(id=nxt["id"], ts=nxt["ts"], time_pkt=pkt(nxt["ts"]), country=nxt["country"], title=nxt["title"],
                      forecast=nxt["forecast"], previous=nxt["previous"], seconds_to=int(nxt["ts"] - now))
        return dict(events=ev, count=len(ev), next_high=nh, range=range_, server_time=now,
                    note="ForexFactory calendar times are UTC in source; actuals filled from FinancialJuice squawks when matched")


class FetchError(Exception):
    pass

class NeedsKey(FetchError):
    pass

def _http_reason(r: httpx.Response) -> str:
    hint = ""
    if r.status_code in (403, 503) and ("cf-ray" in r.headers or "Just a moment" in r.text[:3000]): hint = " Cloudflare bot challenge"
    elif r.status_code == 429: hint = " rate limited"
    elif r.status_code == 451: hint = " unavailable for legal reasons (mirror blocked)"
    elif r.status_code == 410: hint = " gone (mirror shut down)"
    elif r.status_code == 404: hint = " feed URL not found (feed retired?)"
    return f"HTTP {r.status_code}{hint}"

def _html_hint(t: str) -> str:
    m = re.search(r"<title>(.*?)</title>", t or "", re.S | re.I)
    return (m.group(1).strip()[:50] if m else "no title")

def _reason(e: Exception) -> str:
    if isinstance(e, FetchError): return str(e)
    if isinstance(e, httpx.ProxyError): return f"blocked at network egress proxy: {e}"
    if isinstance(e, httpx.TimeoutException): return "timeout"
    if isinstance(e, httpx.ConnectError): return f"connect error: {str(e)[:100]}"
    return f"{type(e).__name__}: {str(e)[:150]}"

def _parse_since(s):
    try: return float(s)
    except (TypeError, ValueError): pass
    try: return datetime.fromisoformat(str(s).replace("Z", "+00:00")).timestamp()
    except Exception: return None

LLM_SYS = ("You are a senior macro news desk editor for an intraday trader of XAUUSD (spot gold) and BTCUSD. "
           "Judge a single headline. Answer ONLY compact JSON: "
           '{"impact":"CRITICAL|HIGH|MEDIUM|LOW","gold":-1..1,"btc":-1..1,"why":"<=18 words"}. '
           "CRITICAL = likely to move gold or BTC >0.7% within an hour (Fed decision surprise, NFP/CPI surprise, war escalation, major hack/ETF ruling). "
           "gold/btc = expected short-term direction, 0 if unclear.")

def _llm_judge(title: str, summary: str, source: str) -> dict:
    """OpenAI-compatible chat call (llm_url / llm_model / llm_key from Settings). Off unless configured."""
    url, model, key = _cfg.secret("llm_url"), _cfg.secret("llm_model"), _cfg.secret("llm_key")
    if not (url and model):
        raise FetchError("LLM not configured")
    h = {"Content-Type": "application/json"}
    if key: h["Authorization"] = f"Bearer {key}"
    r = httpx.post(url.rstrip("/") + ("" if url.rstrip("/").endswith("/chat/completions") else "/chat/completions"), headers=h, timeout=25,
                   json={"model": model, "temperature": 0, "messages": [{"role": "system", "content": LLM_SYS},
                         {"role": "user", "content": f"Source: {source}\nHeadline: {title}\nDetail: {summary[:400]}"}]})
    r.raise_for_status()
    txt = r.json()["choices"][0]["message"]["content"]
    j = json.loads(txt[txt.find("{"): txt.rfind("}") + 1])
    j["model"] = model
    for k in ("gold", "btc"):
        try: j[k] = max(-1.0, min(1.0, float(j.get(k, 0))))
        except Exception: j[k] = 0.0
    return j

_DESK: NewsDesk | None = None

def get_desk() -> NewsDesk:
    global _DESK
    if _DESK is None: _DESK = NewsDesk()
    return _DESK
