/* TradingView's free official embed widgets (https://www.tradingview.com/widget-docs/). Prices, charts, indicators and
   drawing tools inside them are TradingView's own. Each widget is a <script src> with a JSON config as its text, which
   TradingView's loader replaces with an iframe. */
"use strict";
(function (HQ) {
  const BASE = "https://s3.tradingview.com/external-embedding/embed-widget-";
  const TYPES = {
    chart: "advanced-chart", tape: "ticker-tape", ta: "technical-analysis", info: "symbol-info",
    calendar: "events", mini: "mini-symbol-overview", quotes: "market-quotes", single: "single-quote",
  };
  function theme() { return HQ.settings.theme === "light" ? "light" : "dark"; }
  function mount(box, type, cfg) {
    HQ.clear(box);
    const wrap = HQ.el("div", { class: "tradingview-widget-container" });
    wrap.style.width = "100%"; wrap.style.height = "100%";
    const inner = HQ.el("div", { class: "tradingview-widget-container__widget" });
    inner.style.width = "100%"; inner.style.height = "100%";
    wrap.appendChild(inner);
    const s = document.createElement("script");
    s.type = "text/javascript"; s.async = true; s.src = BASE + TYPES[type] + ".js";
    const base = { locale: "en", colorTheme: theme(), theme: theme(), isTransparent: false };
    s.textContent = JSON.stringify(Object.assign(base, cfg || {}));
    wrap.appendChild(s);
    box.appendChild(wrap);
    return () => HQ.clear(box);
  }
  function chart(box, symbol, opts) {
    opts = opts || {};
    return mount(box, "chart", Object.assign({
      autosize: true, symbol, interval: opts.interval || "15", timezone: HQ.settings.timezone || "Asia/Karachi",
      style: opts.style || "1", allow_symbol_change: opts.allow_symbol_change !== false, hide_side_toolbar: !!opts.hide_side_toolbar,
      hide_top_toolbar: !!opts.hide_top_toolbar, hide_legend: false, save_image: true, calendar: false, details: !!opts.details,
      hide_volume: !!opts.hide_volume, withdateranges: !!opts.withdateranges, studies: opts.studies || [],
      support_host: "https://www.tradingview.com", backgroundColor: theme() === "dark" ? "#12161c" : "#ffffff",
      gridColor: theme() === "dark" ? "rgba(33,40,51,0.6)" : "rgba(0,0,0,0.06)",
    }, opts.extra || {}));
  }
  function attribution() {
    const a = HQ.el("a", { href: "https://www.tradingview.com/", target: "_blank", rel: "noopener nofollow", text: "TradingView" });
    return HQ.el("div", { class: "tv-attr" }, "Charts and prices by ", a, " (official embed widget)");
  }
  HQ.tv = { mount, chart, attribution };
})(window.HQ);
