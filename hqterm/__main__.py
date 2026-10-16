"""python -m hqterm serve [--host 127.0.0.1] [--port 8702]"""
from __future__ import annotations

import argparse
import logging
import os


def main(argv=None):
    ap = argparse.ArgumentParser(prog="hqterm", description="HarkQuant Terminal (market news & analysis, no trading)")
    sub = ap.add_subparsers(dest="cmd")
    s = sub.add_parser("serve", help="run the dashboard server")
    s.add_argument("--host", default=os.getenv("HQT_HOST", "127.0.0.1"))
    s.add_argument("--port", type=int, default=int(os.getenv("HQT_PORT", "8702")))
    s.add_argument("--log", default=os.getenv("HQT_LOG", "INFO"))
    sub.add_parser("check", help="probe every live source once and print a status table")
    a = ap.parse_args(argv)
    if a.cmd in (None, "serve"):
        import uvicorn
        logging.basicConfig(level=getattr(a, "log", "INFO"), format="%(asctime)s %(levelname)s %(name)s: %(message)s")
        logging.getLogger("httpx").setLevel(logging.WARNING)
        uvicorn.run("hqterm.app:app", host=getattr(a, "host", "127.0.0.1"), port=getattr(a, "port", 8702), log_level="warning")
    elif a.cmd == "check":
        from . import market
        for name, fn in [("OKX BTC flow", lambda: market.flow("BTCUSD")["venues"][0]["w1h"]),
                         ("OKX gold perp book", lambda: market.orderbook("XAUUSD")["bands"]["0.5%"]),
                         ("TradingView ratings XAUUSD", lambda: market.ratings("XAUUSD")["score"]),
                         ("Yahoo GC=F bars", lambda: len(market.bars("XAGUSD", "15m", 100)["bars"]))]:
            try:
                print(f"OK   {name}: {fn()}")
            except Exception as e:
                print(f"FAIL {name}: {e}")


if __name__ == "__main__":
    main()
