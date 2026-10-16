"""Pair registry: one row per tradable the terminal knows, with where each kind of data comes from.

kind      crypto | metal | fx | index | energy
tvw       TradingView widget symbol (prices on the charts are TradingView's own feed)
tv        (symbol, screener, exchange) for tradingview-ta ratings, None when tradingview-ta has no match
okx       ccxt symbol on OKX for bars / trades / order book (crypto spot, or the OKX gold perp as a gold proxy)
okx_swap  OKX perpetual instId for funding, long/short ratio, contract taker volume
ccy       OKX rubik currency for aggregated spot taker volume
yf        Yahoo Finance ticker for bars when OKX has nothing; futures tickers carry real exchange volume
news      News Desk asset tag whose sentiment applies (XAU, BTC) or None
"""
from __future__ import annotations

def P(kind, name, tvw, tv=None, okx=None, okx_swap=None, ccy=None, yf=None, news=None, yf_vol=None, notes=""):
    return dict(kind=kind, name=name, tvw=tvw, tv=tv, okx=okx, okx_swap=okx_swap, ccy=ccy, yf=yf, yf_vol=yf_vol,
                news=news, notes=notes)

PAIRS: dict[str, dict] = {
    "BTCUSD": P("crypto", "Bitcoin", "BITSTAMP:BTCUSD", ("BTCUSDT", "crypto", "BINANCE"), "BTC/USDT", "BTC-USDT-SWAP", "BTC", "BTC-USD", "BTC"),
    "ETHUSD": P("crypto", "Ethereum", "BITSTAMP:ETHUSD", ("ETHUSDT", "crypto", "BINANCE"), "ETH/USDT", "ETH-USDT-SWAP", "ETH", "ETH-USD", "BTC"),
    "SOLUSD": P("crypto", "Solana", "COINBASE:SOLUSD", ("SOLUSDT", "crypto", "BINANCE"), "SOL/USDT", "SOL-USDT-SWAP", "SOL", "SOL-USD", "BTC"),
    "XRPUSD": P("crypto", "XRP", "BITSTAMP:XRPUSD", ("XRPUSDT", "crypto", "BINANCE"), "XRP/USDT", "XRP-USDT-SWAP", "XRP", "XRP-USD", "BTC"),
    "BNBUSD": P("crypto", "BNB", "BINANCE:BNBUSDT", ("BNBUSDT", "crypto", "BINANCE"), "BNB/USDT", "BNB-USDT-SWAP", "BNB", "BNB-USD", "BTC"),
    "DOGEUSD": P("crypto", "Dogecoin", "BINANCE:DOGEUSDT", ("DOGEUSDT", "crypto", "BINANCE"), "DOGE/USDT", "DOGE-USDT-SWAP", "DOGE", "DOGE-USD", "BTC"),
    "XAUUSD": P("metal", "Gold (spot)", "OANDA:XAUUSD", ("XAUUSD", "cfd", "OANDA"), "XAU/USDT:USDT", "XAU-USDT-SWAP", None, "GC=F", "XAU",
                yf_vol="GC=F",
                notes="Spot gold is OTC: there is no central volume or order book. Flow uses the OKX XAU-USDT perpetual "
                      "(a crypto-venue gold contract) and COMEX gold futures GC=F volume (Yahoo, delayed). Both are proxies."),
    "XAGUSD": P("metal", "Silver (spot)", "OANDA:XAGUSD", ("SILVER", "cfd", "TVC"), None, None, None, "SI=F", None, yf_vol="SI=F",
                notes="Spot silver is OTC. Volume is COMEX silver futures SI=F (proxy). No order book."),
    "EURUSD": P("fx", "Euro / US Dollar", "FX:EURUSD", ("EURUSD", "forex", "OANDA"), yf="EURUSD=X", yf_vol="6E=F",
                notes="Spot FX is OTC: volume is CME euro futures 6E=F (proxy). No order book."),
    "GBPUSD": P("fx", "British Pound / US Dollar", "FX:GBPUSD", ("GBPUSD", "forex", "OANDA"), yf="GBPUSD=X", yf_vol="6B=F",
                notes="Spot FX is OTC: volume is CME pound futures 6B=F (proxy). No order book."),
    "USDJPY": P("fx", "US Dollar / Yen", "FX:USDJPY", ("USDJPY", "forex", "OANDA"), yf="JPY=X", yf_vol="6J=F",
                notes="Spot FX is OTC: volume is CME yen futures 6J=F (inverted quote, proxy). No order book."),
    "AUDUSD": P("fx", "Australian Dollar / US Dollar", "FX:AUDUSD", ("AUDUSD", "forex", "OANDA"), yf="AUDUSD=X", yf_vol="6A=F",
                notes="Spot FX is OTC: volume is CME futures 6A=F (proxy)."),
    "USDCAD": P("fx", "US Dollar / Canadian Dollar", "FX:USDCAD", ("USDCAD", "forex", "OANDA"), yf="CAD=X", yf_vol="6C=F",
                notes="Spot FX is OTC: volume is CME futures 6C=F (inverted quote, proxy)."),
    "USDCHF": P("fx", "US Dollar / Swiss Franc", "FX:USDCHF", ("USDCHF", "forex", "OANDA"), yf="CHF=X", yf_vol="6S=F",
                notes="Spot FX is OTC: volume is CME futures 6S=F (inverted quote, proxy)."),
    "US30": P("index", "Dow Jones (US30 CFD)", "OANDA:US30USD", ("US30USD", "cfd", "OANDA"), yf="YM=F", yf_vol="YM=F",
              notes="CFD has no central volume: uses CBOT E-mini Dow futures YM=F (proxy)."),
    "NAS100": P("index", "Nasdaq 100 (CFD)", "OANDA:NAS100USD", None, yf="NQ=F", yf_vol="NQ=F",
                notes="tradingview-ta has no match for this CFD, so no TV rating. Volume is CME NQ=F futures (proxy)."),
    "SPX500": P("index", "S&P 500 (CFD)", "OANDA:SPX500USD", None, yf="ES=F", yf_vol="ES=F",
                notes="tradingview-ta has no match for this CFD, so no TV rating. Volume is CME ES=F futures (proxy)."),
    "GER40": P("index", "DAX (GER40 CFD)", "OANDA:DE30EUR", ("DE30EUR", "cfd", "OANDA"), yf="^GDAXI",
               notes="Index cash volume is not available; no flow data."),
    "USOIL": P("energy", "WTI crude oil", "TVC:USOIL", None, yf="CL=F", yf_vol="CL=F",
               notes="tradingview-ta has no match, so no TV rating. Volume is NYMEX CL=F futures."),
    "DXY": P("index", "US Dollar Index", "TVC:DXY", ("DXY", "cfd", "TVC"), yf="DX-Y.NYB", yf_vol="DX=F",
             notes="DXY is an index: volume is ICE DX=F futures (proxy)."),
}


def get(sym: str) -> dict:
    s = (sym or "").upper().replace("/", "")
    if s not in PAIRS:
        raise KeyError(f"unknown symbol {sym!r}; known: {', '.join(PAIRS)}")
    return dict(PAIRS[s], symbol=s)


def public() -> list[dict]:
    return [dict(symbol=k, kind=v["kind"], name=v["name"], tvw=v["tvw"], rating=bool(v["tv"]),
                 orderbook=bool(v["okx"]), flow="okx" if (v["ccy"] or v["okx_swap"]) else ("futures" if v["yf_vol"] else None),
                 notes=v["notes"]) for k, v in PAIRS.items()]
