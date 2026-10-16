/* HarkQuant Terminal - views: Dashboard, Indicators & Tools, TradingView, Market Analysis, Settings. */
"use strict";
(function (HQ) {
  const { el, clear, fmt, api, panel, compBar, dot, isNum } = HQ;
  const LWC = window.LightweightCharts;
  HQ.state = HQ.state || { summary: null, symbols: [], an: {} };

  // ------------------------------------------------------------------ shared helpers
  function tzParts(sec, o) { return new Intl.DateTimeFormat("en-GB", Object.assign({ timeZone: HQ.settings.timezone || "Asia/Karachi", hour12: false }, o)).format(new Date(sec * 1000)); }
  function lwcOpts(extra) {
    const dark = HQ.settings.theme !== "light";
    return Object.assign({
      autoSize: true,
      layout: { background: { type: "solid", color: dark ? "#12161c" : "#ffffff" }, textColor: dark ? "#9aa4b1" : "#334", fontSize: 11,
        panes: { separatorColor: dark ? "#2b3440" : "#ccd", separatorHoverColor: "rgba(240,185,11,.3)" } },
      grid: { vertLines: { color: dark ? "rgba(33,40,51,.6)" : "rgba(0,0,0,.05)" }, horzLines: { color: dark ? "rgba(33,40,51,.6)" : "rgba(0,0,0,.05)" } },
      rightPriceScale: { borderColor: dark ? "#2b3440" : "#ccd" },
      timeScale: { borderColor: dark ? "#2b3440" : "#ccd", timeVisible: true, secondsVisible: false,
        tickMarkFormatter: (t, type) => type >= 3 ? tzParts(t, { hour: "2-digit", minute: "2-digit" }) : tzParts(t, { day: "2-digit", month: "short" }) },
      localization: { timeFormatter: (t) => tzParts(t, { weekday: "short", day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" }) + " " + fmt.tz() },
      crosshair: { mode: 0 },
    }, extra || {});
  }
  function symbolSelect(value, onchange, list) {
    const s = el("select", { "aria-label": "Symbol" });
    const syms = list || HQ.state.symbols.map((x) => x.symbol);
    syms.forEach((x) => s.appendChild(el("option", { value: x, text: x, selected: x === value })));
    s.onchange = () => onchange(s.value);
    return s;
  }
  function seg(values, cur, onclick, labels) {
    const box = el("div", { class: "seg" });
    values.forEach((v, i) => box.appendChild(el("button", { class: v === cur ? "on" : "", text: labels ? labels[i] : v, onclick: () => { box.querySelectorAll("button").forEach((b) => b.classList.remove("on")); box.children[i].classList.add("on"); onclick(v); } })));
    return box;
  }
  function errBox(msg) { return el("div", { class: "empty" }, el("span", { class: "down", text: "Source unavailable: " }), msg || ""); }
  function proxyTag(isProxy, exactLabel) { return el("span", { class: "tagp " + (isProxy ? "" : "exact"), text: isProxy ? "PROXY" : (exactLabel || "EXACT") }); }

  // ------------------------------------------------------------------ bias rendering (shared)
  function biasCard(a) {
    if (!a || a.error) return el("div", { class: "bias-card" }, el("div", { class: "sym" }, el("span", { text: a ? a.symbol : "-" })), el("div", { class: "lab muted", text: "N/A" }), el("div", { class: "note", text: a && a.error ? a.error : "" }));
    const rows = a.components.filter((c) => c.used).map((c) => el("div", { class: "comp-row" }, el("span", { class: "nm", text: c.id }), compBar(c.score), el("span", { class: "v " + fmt.cls(c.score), text: fmt.signed(c.score) })));
    return el("div", { class: "bias-card " + a.label },
      el("div", { class: "sym" }, el("span", { text: a.symbol }), el("span", { text: a.agreement + " agree" })),
      el("div", { class: "lab " + a.label, text: a.label }),
      el("div", { class: "sc " + fmt.cls(a.score), text: "score " + fmt.signed(a.score, 1) + " / ±" + a.neutral_band + " neutral" }),
      a.event ? el("div", { class: "note proxy", text: `Event risk: ${a.event.country} ${a.event.title} in ${Math.round(a.event.minutes)} min, score damped` }) : null,
      el("div", { style: { display: "grid", gap: "3px", marginTop: "6px" } }, rows));
  }
  function redFolderList(events, max) {
    const box = el("div", { class: "redf" });
    const now = Date.now() / 1000;
    const evs = (events || []).slice(0, max || 12);
    if (!evs.length) box.appendChild(el("div", { class: "note", text: "No high-impact events left in this ForexFactory week. Next week's calendar is published on Sunday." }));
    evs.forEach((e) => {
      const left = e.ts - now;
      box.appendChild(el("div", { class: "ev " + (left < 0 ? "past" : left < 3600 ? "soon" : "") },
        el("span", { class: "fold", title: "High impact (red folder)" }), el("span", { class: "tm", text: fmt.dhm(e.ts) }),
        el("span", { class: "cc", text: e.country }), el("span", { class: "ti", text: e.title + (e.actual ? `  A ${e.actual}` : "") + (e.forecast ? `  F ${e.forecast}` : "") }),
        el("span", { class: "cd", dataset: { ts: e.ts }, text: left < 0 ? (e.actual ? "done" : "passed") : fmt.countdown(left) })));
    });
    return box;
  }
  function tickCountdowns(root) {
    const now = Date.now() / 1000;
    (root || document).querySelectorAll(".redf .cd").forEach((n) => { const l = Number(n.dataset.ts) - now; if (l >= 0) n.textContent = fmt.countdown(l); });
  }

  // ================================================================== 1. DASHBOARD
  const dashboard = {
    id: "dashboard", label: "Dashboard", ico: "▦",
    render(main) {
      const d = HQ.settings.dashboard || {};
      const btcBox = el("div", { class: "tvbox" }), goldBox = el("div", { class: "tvbox" });
      const newsBox = el("div", { class: "newsbox" });
      const sumBox = el("div", { class: "sum-scroll" }, el("div", { class: "empty", text: "Building market summary from news, calendar, TradingView ratings and order flow…" }));
      main.append(el("div", { class: "g-dash" },
        panel("BTC", { sub: d.btc_symbol, flush: true, cls: "p-btc" }, btcBox),
        panel("Gold", { sub: d.gold_symbol, flush: true, cls: "p-gold" }, goldBox),
        panel("News feed", { sub: "FF · FinancialJuice · Reddit · X · Truth · wires · CB", flush: true, cls: "p-news" }, newsBox),
        panel("Market analysis", { sub: "bias · red folder", flush: true, cls: "p-sum", right: el("a", { href: "#/analysis", class: "muted", text: "details →" }) }, sumBox)));
      HQ.tv.chart(btcBox, d.btc_symbol || "BITSTAMP:BTCUSD", { interval: d.chart_interval || "15", hide_side_toolbar: true });
      HQ.tv.chart(goldBox, d.gold_symbol || "OANDA:XAUUSD", { interval: d.chart_interval || "15", hide_side_toolbar: true });
      let desk = null;
      try { desk = window.NewsDesk.mount(newsBox, { api: "/api/news", embedded: true, limit: 250 }); } catch (e) { newsBox.appendChild(errBox(String(e))); }
      const draw = (s) => {
        if (!s) return;
        clear(sumBox);
        const a = s.analysis || {};
        sumBox.append(
          el("div", { class: "bias-mini" }, biasCard(a.BTCUSD), biasCard(a.XAUUSD)),
          el("div", { class: "subh" }, el("span", { text: "Summary" }), el("span", { class: "grow" }), el("span", { class: "dim", text: "as of " + fmt.hm(s.as_of) + " " + fmt.tz() })),
          el("ul", { class: "narr" }, (s.narrative || []).map((l) => el("li", { text: l }))),
          el("div", { class: "subh" }, el("span", { class: "down", text: "■" }), el("span", { text: "Red folder · high-impact (ForexFactory)" })),
          redFolderList(s.red_folder, 10),
          el("div", { class: "subh" }, el("span", { text: "News sentiment (6h)" })),
          el("div", { class: "comp-row" }, el("span", { class: "nm", text: "XAU" }), compBar(s.sentiment && s.sentiment.XAU && s.sentiment.XAU.score), el("span", { class: "v", text: fmt.signed(s.sentiment && s.sentiment.XAU && s.sentiment.XAU.score) })),
          el("div", { class: "comp-row" }, el("span", { class: "nm", text: "BTC" }), compBar(s.sentiment && s.sentiment.BTC && s.sentiment.BTC.score), el("span", { class: "v", text: fmt.signed(s.sentiment && s.sentiment.BTC && s.sentiment.BTC.score) })),
          el("div", { class: "note", text: "Analysis, not a trade signal. Gold flow uses the OKX gold perpetual and COMEX futures as proxies (spot gold has no central volume)." }));
      };
      draw(HQ.state.summary);
      const off = HQ.on("summary", draw);
      if (!HQ.state.summary) api("/api/summary").then((s) => { HQ.state.summary = s; draw(s); }).catch((e) => { clear(sumBox).appendChild(errBox(e.message)); });
      HQ.every(1000, () => tickCountdowns(sumBox), false);
      return () => { off(); if (desk) desk.destroy(); };
    },
  };

  // ================================================================== 2. INDICATORS & TOOLS
  const COLORS = ["#f0b90b", "#4c8dff", "#e040fb", "#26c6da", "#ff7043", "#9ccc65", "#ab47bc", "#ffd54f", "#8d6e63", "#78909c"];
  const indicatorsView = {
    id: "indicators", label: "Indicators & Tools", ico: "ƒ",
    render(main) {
      const st = Object.assign({ symbol: (HQ.settings.indicators || {}).default_symbol || "BTCUSD", tf: (HQ.settings.indicators || {}).default_tf || "15m" },
        JSON.parse(localStorage.getItem("hq.ind.view") || "{}"));
      let active = JSON.parse(localStorage.getItem("hq.ind.active") || "null") || [{ id: "talib:EMA", params: { timeperiod: 50 } }, { id: "talib:RSI", params: { timeperiod: 14 } }];
      const persist = () => { localStorage.setItem("hq.ind.active", JSON.stringify(active)); localStorage.setItem("hq.ind.view", JSON.stringify(st)); };
      const chartBox = el("div", { class: "lwc" });
      const srcNote = el("div", { class: "note" });
      const activeBox = el("div", { class: "active-ind" });
      const listBox = el("div", { class: "ind-list" }, el("div", { class: "empty", text: "Loading catalog…" }));
      const search = el("input", { type: "search", placeholder: "Search 170+ indicators…", style: { width: "100%" } });
      let catalog = null, chart = null, bars = [], dataCache = new Map();

      const toolbar = el("div", { class: "toolbar" },
        symbolSelect(st.symbol, (v) => { st.symbol = v; persist(); dataCache.clear(); load(); }),
        seg(["1m", "5m", "15m", "1h", "4h", "1d"], st.tf, (v) => { st.tf = v; persist(); dataCache.clear(); load(); }),
        el("span", { class: "grow" }), srcNote);
      main.append(el("div", { class: "view-head" }, el("h1", { text: "Indicators & Tools" }), el("span", { class: "sub", text: "Own-data chart (TradingView Lightweight Charts) · TA-Lib + pandas-ta-classic indicators" })),
        el("div", { class: "g-ind" },
          el("div", { class: "col" }, panel("Chart", { right: toolbar, flush: true }, chartBox)),
          el("div", { class: "col" },
            panel("On chart", { sub: "click × to remove" }, activeBox),
            panel("Add indicator", { flush: true }, el("div", { style: { padding: "8px" } }, search), listBox))),
        el("div", { class: "toolgrid", id: "tools" }));

      function build() {
        if (chart) { chart.remove(); chart = null; }
        chart = LWC.createChart(chartBox, lwcOpts());
        const cs = chart.addSeries(LWC.CandlestickSeries, { upColor: "#26a69a", downColor: "#ef5350", wickUpColor: "#26a69a", wickDownColor: "#ef5350", borderVisible: false });
        cs.setData(bars.map((b) => ({ time: b.time, open: b.open, high: b.high, low: b.low, close: b.close })));
        const vs = chart.addSeries(LWC.HistogramSeries, { priceScaleId: "vol", priceFormat: { type: "volume" }, lastValueVisible: false, priceLineVisible: false });
        chart.priceScale("vol").applyOptions({ scaleMargins: { top: 0.82, bottom: 0 } });
        vs.setData(bars.map((b) => ({ time: b.time, value: b.volume, color: b.close >= b.open ? "rgba(38,166,154,.35)" : "rgba(239,83,80,.35)" })));
        let pane = 1;
        active.forEach((a, i) => {
          const r = dataCache.get(key(a)); if (!r || r.error) return;
          const color = COLORS[i % COLORS.length];
          if (r.pane === "marker") {
            const mk = r.markers.map((m) => ({ time: m.time, position: m.value > 0 ? "belowBar" : "aboveBar", color: m.value > 0 ? "#26a69a" : "#ef5350", shape: m.value > 0 ? "arrowUp" : "arrowDown", text: r.name.split(" ")[0] }));
            if (LWC.createSeriesMarkers) LWC.createSeriesMarkers(cs, mk);
            return;
          }
          const p = r.pane === "price" ? 0 : pane++;
          r.lines.forEach((ln, j) => {
            const c = j === 0 ? color : COLORS[(i + j + 3) % COLORS.length];
            const s = ln.hist ? chart.addSeries(LWC.HistogramSeries, { color: c, priceLineVisible: false, lastValueVisible: false }, p)
              : chart.addSeries(LWC.LineSeries, { color: c, lineWidth: j === 0 ? 2 : 1, priceLineVisible: false, lastValueVisible: j === 0, title: j === 0 ? r.name.slice(0, 18) : ln.name }, p);
            s.setData(ln.hist ? ln.data.map((d) => ({ time: d.time, value: d.value, color: d.value >= 0 ? "rgba(38,166,154,.7)" : "rgba(239,83,80,.7)" })) : ln.data);
          });
        });
        try { const panes = chart.panes(); panes.forEach((pn, i) => { if (i > 0) pn.setHeight(110); }); } catch (e) { /* older LWC */ }
        chart.timeScale().setVisibleLogicalRange({ from: Math.max(0, bars.length - 220), to: bars.length + 3 });
      }
      const key = (a) => `${st.symbol}|${st.tf}|${a.id}|${JSON.stringify(a.params || {})}`;
      async function compute(a) {
        const k = key(a); if (dataCache.has(k)) return;
        try { dataCache.set(k, await api("/api/indicator", { method: "POST", body: { id: a.id, symbol: st.symbol, tf: st.tf, params: a.params || {}, limit: 600 } })); }
        catch (e) { dataCache.set(k, { error: e.message }); HQ.toast("Indicator failed", a.id + ": " + e.message, "bad"); }
      }
      function drawActive() {
        clear(activeBox);
        if (!active.length) activeBox.appendChild(el("div", { class: "note", text: "No indicators. Add one from the list below." }));
        active.forEach((a, i) => {
          const meta = catalog ? catalog.byId[a.id] : null;
          const params = Object.keys((meta && meta.params) || a.params || {}).slice(0, 2);
          activeBox.appendChild(el("div", { class: "ai" }, el("span", { class: "sw", style: { background: COLORS[i % COLORS.length] } }),
            el("span", { text: meta ? meta.name : a.id, title: a.id }),
            el("span", { style: { display: "flex", gap: "3px" } }, params.map((p) => el("input", { type: "number", step: "any", title: p, value: (a.params && a.params[p] !== undefined) ? a.params[p] : meta.params[p],
              onchange: (ev) => { a.params = Object.assign({}, a.params, { [p]: Number(ev.target.value) }); persist(); refresh(); } }))),
            el("button", { class: "btn ghost sm", text: "×", title: "Remove", onclick: () => { active.splice(i, 1); persist(); drawActive(); build(); } })));
        });
      }
      function drawList() {
        clear(listBox);
        if (!catalog) return;
        const q = search.value.trim().toLowerCase();
        catalog.groups.forEach((g) => {
          const items = g.items.filter((it) => !q || it.name.toLowerCase().includes(q) || it.short.toLowerCase().includes(q));
          if (!items.length) return;
          const det = el("details", { open: !!q || g.group.includes("Overlap") || g.group.includes("extras") }, el("summary", { text: `${g.group} (${items.length})` }));
          items.forEach((it) => det.appendChild(el("div", { class: "it" }, el("span", {}, it.name, " ", el("span", { class: "s", text: it.short + (it.pane === "price" ? " · overlay" : it.pane === "marker" ? " · markers" : " · pane") })),
            el("button", { class: "btn sm", text: "+ Add", onclick: async () => { active.push({ id: it.id, params: Object.assign({}, it.params) }); persist(); drawActive(); await refresh(); } }))));
          listBox.appendChild(det);
        });
        listBox.appendChild(el("div", { class: "note", style: { padding: "8px" }, text: `${catalog.total} indicators · ${catalog.libraries.join(" · ")}. ${catalog.note}` }));
      }
      async function refresh() { await Promise.all(active.map(compute)); drawActive(); build(); }
      async function load() {
        try {
          const b = await api(`/api/bars?symbol=${st.symbol}&tf=${st.tf}&limit=600`);
          bars = b.bars; srcNote.textContent = b.source + (b.proxy ? " (proxy)" : "") + (b.note ? " · " + b.note : "");
          srcNote.className = "note " + (b.proxy ? "proxy" : "");
          await refresh();
          tools.update(st.symbol, bars);
        } catch (e) { clear(chartBox).appendChild(errBox(e.message)); }
      }
      search.oninput = drawList;
      api("/api/indicators").then((c) => { c.byId = {}; c.groups.forEach((g) => g.items.forEach((it) => { c.byId[it.id] = it; })); catalog = c; drawList(); drawActive(); }).catch((e) => { clear(listBox).appendChild(errBox(e.message)); });
      const tools = toolsPanel(main.querySelector("#tools"), st);
      load();
      HQ.every(60000, () => { dataCache.clear(); load(); }, false);
      return () => { if (chart) chart.remove(); };
    },
  };

  function toolsPanel(box, st) {
    const out = el("div", { class: "out" }, "-");
    const f = (id, label, value, attrs) => [el("label", { for: id, text: label }), el("input", Object.assign({ id, type: "number", step: "any", value }, attrs || {}))];
    const sizeSel = el("select", { id: "t-mode" }, el("option", { value: "pct", text: "% of balance" }), el("option", { value: "money", text: "fixed money" }));
    const calc = el("div", { class: "calc" }, f("t-bal", "Balance", localStorage.getItem("hq.t.bal") || "1000"), el("label", { text: "Risk as" }), sizeSel,
      f("t-risk", "Risk", localStorage.getItem("hq.t.risk") || "1"), f("t-entry", "Entry", ""), f("t-stop", "Stop loss", ""),
      f("t-cs", "Contract size", "100", { title: "units per 1.00 lot (XAUUSD 100 oz, BTCUSD 1, FX 100000)" }), f("t-rr", "Target R:R", "2"), out);
    const pivBox = el("div", {}, el("div", { class: "note", text: "Loading pivots…" }));
    const sessBox = el("div", {});
    const atrBox = el("div", { class: "kv" });
    box.append(panel("Position size / risk calculator", { sub: "no orders are sent" }, calc),
      panel("Pivot points", { sub: "previous UTC day" }, pivBox),
      panel("Trading sessions", { sub: fmt.tz() }, sessBox),
      panel("ATR stop / target", { sub: "1.5×ATR(14), chart tf" }, atrBox));
    function recalc() {
      const g = (id) => Number(box.querySelector("#" + id).value);
      localStorage.setItem("hq.t.bal", g("t-bal")); localStorage.setItem("hq.t.risk", g("t-risk"));
      const bal = g("t-bal"), risk = g("t-risk"), e = g("t-entry"), s = g("t-stop"), cs = g("t-cs") || 1, rr = g("t-rr") || 2;
      if (!(e > 0 && s > 0 && e !== s)) { out.textContent = "Enter entry and stop"; return; }
      const money = sizeSel.value === "pct" ? bal * risk / 100 : risk;
      const lots = money / (Math.abs(e - s) * cs);
      const tp = e + (e > s ? 1 : -1) * Math.abs(e - s) * rr;
      clear(out).append(el("div", {}, el("b", { text: (Math.floor(lots * 100) / 100).toFixed(2) + " lots" }), el("span", { class: "muted", text: ` (${lots.toFixed(4)} exact) · ${e > s ? "LONG" : "SHORT"}` })),
        el("div", { class: "muted", text: `risk ${money.toFixed(2)} · stop dist ${Math.abs(e - s).toPrecision(6)} · TP @ ${fmt.price(tp)} (${rr}R = ${(money * rr).toFixed(2)})` }));
    }
    calc.addEventListener("input", recalc);
    sizeSel.onchange = recalc;
    function sessions() {
      api("/api/tools/sessions").then((r) => {
        clear(sessBox);
        const now = new Date(); const h = now.getUTCHours() + now.getUTCMinutes() / 60;
        r.sessions.forEach((s) => {
          const bar = el("div", { class: "bar" });
          const i = el("i"); const a = s.open_utc / 24 * 100, w = ((s.close_utc - s.open_utc + 24) % 24) / 24 * 100;
          i.style.left = a + "%"; i.style.width = Math.min(w, 100 - a) + "%"; bar.appendChild(i);
          if (a + w > 100) { const i2 = el("i"); i2.style.left = "0"; i2.style.width = (a + w - 100) + "%"; bar.appendChild(i2); }
          const nb = el("b"); nb.style.left = (h / 24 * 100) + "%"; bar.appendChild(nb);
          const openPk = fmt.hm(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate(), s.open_utc) / 1000);
          const closePk = fmt.hm(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate(), s.close_utc) / 1000);
          sessBox.appendChild(el("div", { class: "sess " + (s.active ? "on" : "") }, el("span", { text: s.name }), bar,
            el("span", { class: "mono " + (s.active ? "up" : "muted"), text: s.active ? "open · closes " + fmt.countdown(s.closes - Date.now() / 1000) : `${openPk}–${closePk}` })));
        });
        sessBox.appendChild(el("div", { class: "note", text: "Hours shown in " + fmt.tz() + " (standard-time convention; DST shifts London/NY by 1h). Bar = 24h UTC, gold marker = now." }));
      }).catch(() => {});
    }
    sessions();
    HQ.every(30000, sessions, false);
    return {
      update(sym, bars) {
        const csDefault = { XAUUSD: 100, XAGUSD: 5000, EURUSD: 100000, GBPUSD: 100000, USDJPY: 100000, AUDUSD: 100000, USDCAD: 100000, USDCHF: 100000, USOIL: 1000 }[sym] || 1;
        box.querySelector("#t-cs").value = csDefault;
        const last = bars[bars.length - 1];
        if (last && !box.querySelector("#t-entry").value) box.querySelector("#t-entry").value = last.close;
        recalc();
        api(`/api/tools/pivots?symbol=${sym}`).then((p) => {
          clear(pivBox);
          const rows = ["R3", "R2", "R1", "P", "S1", "S2", "S3"];
          const t = el("table", { class: "t piv" }, el("thead", {}, el("tr", {}, el("th", { text: "" }), ["classic", "fibonacci", "camarilla", "woodie"].map((k) => el("th", { text: k.slice(0, 5) })))),
            el("tbody", {}, rows.map((r) => el("tr", {}, el("td", { class: r[0] === "R" ? "down" : r[0] === "S" ? "up" : "gold", text: r }),
              ["classic", "fibonacci", "camarilla", "woodie"].map((k) => el("td", { class: "mono", text: p[k][r] !== undefined ? fmt.price(p[k][r]) : "" }))))));
          pivBox.append(t, el("div", { class: "note", text: `Prev day H ${fmt.price(p.prev_day.high)} L ${fmt.price(p.prev_day.low)} C ${fmt.price(p.prev_day.close)} · ${p.source}` }));
        }).catch((e) => { clear(pivBox).appendChild(errBox(e.message)); });
        api(`/api/tools/atr?symbol=${sym}&tf=${st.tf}`).then((a) => {
          clear(atrBox);
          const L = (k, v, c) => [el("span", { class: "k", text: k }), el("span", { class: "v " + (c || ""), text: v })];
          atrBox.append(...L("ATR(14)", fmt.price(a.atr)), ...L("Close", fmt.price(a.close)),
            ...L("Long SL / TP", `${fmt.price(a.close - 1.5 * a.atr)} / ${fmt.price(a.close + 3 * a.atr)}`, "up"),
            ...L("Short SL / TP", `${fmt.price(a.close + 1.5 * a.atr)} / ${fmt.price(a.close - 3 * a.atr)}`, "down"),
            ...L("Source", a.source));
        }).catch((e) => { clear(atrBox).appendChild(errBox(e.message)); });
      },
    };
  }

  // ================================================================== 3. TRADINGVIEW
  const tradingview = {
    id: "tradingview", label: "TradingView", ico: "TV",
    render(main) {
      const t = HQ.settings.tradingview || {};
      let sym = localStorage.getItem("hq.tv.sym") || t.default_symbol || "OANDA:XAUUSD";
      let iv = localStorage.getItem("hq.tv.iv") || t.interval || "15";
      const chartBox = el("div", { class: "tvbox tv-full" });
      const tape = el("div", { class: "tvbox", style: { height: "46px" } });
      const input = el("input", { type: "text", value: sym, placeholder: "EXCHANGE:SYMBOL e.g. OANDA:XAUUSD", style: { width: "220px" }, "aria-label": "TradingView symbol" });
      const go = () => { sym = input.value.trim().toUpperCase() || sym; localStorage.setItem("hq.tv.sym", sym); draw(); };
      input.addEventListener("keydown", (e) => { if (e.key === "Enter") go(); });
      const quick = el("div", { class: "pairbar" }, HQ.state.symbols.filter((s) => (HQ.settings.watchlist || []).includes(s.symbol)).map((s) =>
        el("button", { text: s.symbol, title: s.tvw, class: s.tvw === sym ? "on" : "", onclick: (ev) => { sym = s.tvw; input.value = sym; localStorage.setItem("hq.tv.sym", sym); ev.target.parentNode.querySelectorAll("button").forEach((b) => b.classList.toggle("on", b === ev.target)); draw(); } })));
      const ivSel = el("select", { "aria-label": "Interval" }, [["1", "1m"], ["5", "5m"], ["15", "15m"], ["60", "1h"], ["240", "4h"], ["D", "1D"], ["W", "1W"]].map(([v, l]) => el("option", { value: v, text: l, selected: v === iv })));
      ivSel.onchange = () => { iv = ivSel.value; localStorage.setItem("hq.tv.iv", iv); draw(); };
      const taBox = el("div", { class: "tvbox", style: { height: "430px" } }), infoBox = el("div", { class: "tvbox", style: { height: "430px" } }), calBox = el("div", { class: "tvbox", style: { height: "430px" } });
      main.append(el("div", { class: "view-head" }, el("h1", { text: "TradingView" }), el("span", { class: "sub", text: "Full TradingView chart with TradingView's own indicators and drawing tools" }), el("span", { class: "grow" }),
          el("div", { class: "toolbar" }, input, el("button", { class: "btn", text: "Load", onclick: go }), ivSel)),
        el("div", { style: { marginBottom: "8px" } }, quick),
        el("div", { class: "panel", style: { marginBottom: "8px", overflow: "hidden" } }, tape),
        el("div", { class: "panel", style: { overflow: "hidden" } }, chartBox, HQ.tv.attribution()),
        el("div", { class: "grid g-3", style: { marginTop: "10px" } },
          panel("Technical analysis (TradingView)", { flush: true }, taBox), panel("Symbol info (TradingView)", { flush: true }, infoBox), panel("Economic calendar (TradingView)", { flush: true }, calBox)));
      function draw() {
        HQ.tv.chart(chartBox, sym, { interval: iv, style: t.style || "1", details: false, withdateranges: true });
        HQ.tv.mount(taBox, "ta", { symbol: sym, interval: "15m", showIntervalTabs: true, displayMode: "single", width: "100%", height: "100%", isTransparent: true });
        HQ.tv.mount(infoBox, "mini", { symbol: sym, width: "100%", height: "100%", dateRange: "1D", isTransparent: true, autosize: true });
      }
      HQ.tv.mount(tape, "tape", { symbols: HQ.state.symbols.filter((s) => (HQ.settings.watchlist || []).includes(s.symbol)).map((s) => ({ proName: s.tvw, title: s.symbol })), showSymbolLogo: true, isTransparent: true, displayMode: "adaptive" });
      HQ.tv.mount(calBox, "calendar", { width: "100%", height: "100%", isTransparent: true, importanceFilter: "0,1", countryFilter: "us,eu,gb,jp,cn,au,ca,ch" });
      draw();
    },
  };

  // ================================================================== 4. MARKET ANALYSIS
  const analysisView = {
    id: "analysis", label: "Market Analysis", ico: "◐",
    render(main) {
      let sym = localStorage.getItem("hq.an.sym") || "XAUUSD";
      let tab = localStorage.getItem("hq.an.tab") || "bias";
      const charts = [];
      const body = el("div", {});
      const stamp = el("span", { class: "dim mono" });
      const bar = el("div", { class: "pairbar" }, HQ.state.symbols.map((s) => el("button", { text: s.symbol, class: s.symbol === sym ? "on" : "", title: s.name,
        onclick: (ev) => { sym = s.symbol; localStorage.setItem("hq.an.sym", sym); bar.querySelectorAll("button").forEach((b) => b.classList.toggle("on", b === ev.target)); load(true); } })));
      const tabs = [["volume", "Live Volume"], ["pressure", "Buying vs Selling"], ["bulls", "Bulls vs Bears"], ["bias", "Market Bias"]];
      const tabBar = el("div", { class: "subtabs" }, tabs.map(([id, l]) => el("button", { text: l, class: id === tab ? "on" : "", onclick: (ev) => { tab = id; localStorage.setItem("hq.an.tab", tab); tabBar.querySelectorAll("button").forEach((b) => b.classList.toggle("on", b === ev.target)); render(); } })));
      main.append(el("div", { class: "view-head" }, el("h1", { text: "Market Analysis" }), el("span", { class: "sub", text: "Order flow, pressure, positioning and the combined bias" }), el("span", { class: "grow" }), stamp), bar, tabBar, body);
      let data = null, loading = false;
      function killCharts() { while (charts.length) { try { charts.pop().remove(); } catch (e) { /* gone */ } } }
      async function load(reset) {
        if (loading) return; loading = true;
        if (reset) { data = null; killCharts(); clear(body).appendChild(el("div", { class: "empty", text: "Loading " + sym + "…" })); }
        try { data = await api("/api/analysis?symbol=" + sym); HQ.state.an[sym] = data; stamp.textContent = "updated " + fmt.clock(); render(); }
        catch (e) { clear(body).appendChild(errBox(e.message)); }
        finally { loading = false; }
      }
      function render() {
        if (!data) return;
        killCharts(); clear(body);
        ({ volume: tVolume, pressure: tPressure, bulls: tBulls, bias: tBias })[tab](body, data, charts);
      }
      load(true);
      HQ.every(Math.max(10, (HQ.settings.refresh || {}).analysis_s || 20) * 1000, () => load(false), false);
      return killCharts;
    },
  };

  function statusLine(d, k) {
    const s = (d.status || {})[k] || {};
    return el("div", { class: "subh" }, dot(s.status === "ok" ? "ok" : s.status === "fail" ? "fail" : "na"), el("span", { text: k + ": " + (s.status || "-") + (s.stale ? " (stale cache)" : "") }),
      s.error ? el("span", { class: "down", text: s.error }) : null, s.note ? el("span", { class: "dim", style: { textTransform: "none" }, text: s.note }) : null);
  }
  function tVolume(body, d, charts) {
    const f = d.details.flow;
    body.appendChild(statusLine(d, "flow"));
    if (!f || f.available === false) { body.appendChild(el("div", { class: "empty", text: (f && f.note) || "No volume source" })); return; }
    if (f.proxy && f.note) body.appendChild(el("p", { class: "note proxy", text: f.note }));
    f.venues.forEach((v) => {
      const cbox = el("div", { class: "chart md" });
      const win = el("table", { class: "t" }, el("thead", {}, el("tr", {}, ["window", v.exact ? "buy (taker)" : "buy (est.)", v.exact ? "sell (taker)" : "sell (est.)", "delta", "buy %"].map((h, i) => el("th", { class: i ? "n" : "", text: h })))),
        el("tbody", {}, [["15m", v.w15m], ["1h", v.w1h], ["4h", v.w4h]].map(([k, w]) => el("tr", {}, el("td", { text: k }), el("td", { class: "n up", text: fmt.big(w.buy) }), el("td", { class: "n down", text: fmt.big(w.sell) }),
          el("td", { class: "n " + fmt.cls(w.delta), text: fmt.big(w.delta) }), el("td", { class: "n", text: fmt.pct(w.buy_pct) })))));
      const bp = v.w1h.buy_pct || 50;
      const split = el("div", { class: "split" }, el("div", { class: "b", style: { width: bp + "%" }, text: "BUY " + fmt.pct(bp) }), el("div", { class: "s", text: "SELL " + fmt.pct(100 - bp) }));
      body.appendChild(panel(v.label, { right: proxyTag(!v.exact || v.proxy, v.exact ? "TAKER SIDE" : "") }, el("div", { class: "grid g-2" },
        el("div", { class: "col" }, el("div", { class: "subh", text: v.exact ? "Incoming (taker buys) vs outgoing (taker sells), last 1h" : "Estimated buying vs selling volume, last 1h" }), split, win,
          v.exact ? el("div", { class: "note", text: "Exact: OKX reports which side was the aggressor for every trade (5-min buckets)." }) :
            el("div", { class: "note proxy", text: "Estimated: futures bars carry total volume only; the buy/sell split is inferred from where each bar closed in its range (close-location value)." })),
        el("div", { class: "col" }, el("div", { class: "subh", text: "Delta per 5m (bars) and cumulative volume delta (line)" }), cbox))));
      requestAnimationFrame(() => {
        const ch = LWC.createChart(cbox, lwcOpts()); charts.push(ch);
        const h = ch.addSeries(LWC.HistogramSeries, { priceLineVisible: false, lastValueVisible: false, priceFormat: { type: "volume" } });
        h.setData(v.series.map((r) => ({ time: r.time, value: r.delta, color: r.delta >= 0 ? "rgba(38,166,154,.8)" : "rgba(239,83,80,.8)" })));
        const l = ch.addSeries(LWC.LineSeries, { color: "#f0b90b", lineWidth: 2, priceLineVisible: false, title: "CVD", priceFormat: { type: "volume" } }, 1);
        l.setData(v.series.map((r) => ({ time: r.time, value: r.cvd })));
        try { ch.panes()[1].setHeight(110); } catch (e) { /* */ }
        ch.timeScale().fitContent();
      });
    });
    if (f.tape) body.appendChild(panel("Tape · " + f.tape.source, {}, el("div", { class: "tiles" },
      tile("Trades", f.tape.trades), tile("Span", fmt.countdown(f.tape.span_s)), tile("Buy $", fmt.big(f.tape.buy_usd), "up"), tile("Sell $", fmt.big(f.tape.sell_usd), "down"), tile("Buy share", fmt.pct(f.tape.buy_pct)), tile("Last", fmt.price(f.tape.last_price)))));
  }
  function tile(k, v, c, s) { return el("div", { class: "tile" }, el("div", { class: "k", text: k }), el("div", { class: "v " + (c || ""), text: v === undefined || v === null ? "-" : String(v) }), s ? el("div", { class: "s", text: s }) : null); }

  function tPressure(body, d, charts) {
    const ob = d.details.orderbook, f = d.details.flow;
    body.appendChild(statusLine(d, "orderbook"));
    const grid = el("div", { class: "grid g-2" });
    if (ob && ob.available !== false) {
      const t = el("table", { class: "t" }, el("thead", {}, el("tr", {}, ["band from mid", "bids $", "asks $", "imbalance", ""].map((h, i) => el("th", { class: i && i < 4 ? "n" : "", text: h })))),
        el("tbody", {}, Object.entries(ob.bands).map(([k, b]) => el("tr", {}, el("td", { text: "±" + k }), el("td", { class: "n up", text: fmt.big(b.bid_usd) }), el("td", { class: "n down", text: fmt.big(b.ask_usd) }),
          el("td", { class: "n " + fmt.cls(b.imbalance), text: fmt.signed(b.imbalance, 1) + "%" }), el("td", { style: { width: "30%" } }, compBar(b.imbalance))))));
      const cv = el("canvas", { class: "cv", height: 260 });
      grid.append(panel("Order-book imbalance", { sub: ob.source, right: proxyTag(ob.proxy, "LIVE BOOK") }, el("div", { class: "tiles" }, tile("Mid", fmt.price(ob.mid)), tile("Spread", fmt.price(ob.spread)), tile("Imbalance ±0.5%", fmt.signed(ob.bands["0.5%"].imbalance, 1) + "%", fmt.cls(ob.bands["0.5%"].imbalance)), tile("Book score", fmt.signed(ob.score), fmt.cls(ob.score))), t,
          ob.note ? el("p", { class: "note proxy", text: ob.note }) : null,
          el("p", { class: "note", text: "Positive = more resting bids than asks near price (buyers defending). Bands beyond the 400 visible levels repeat the deepest value." })),
        panel("Depth (cumulative, top 60 levels)", {}, cv));
      requestAnimationFrame(() => depthChart(cv, ob.depth));
    } else {
      grid.appendChild(panel("Order book", {}, el("div", { class: "empty", text: (ob && ob.note) || "No public order book for this market." })));
    }
    if (f && f.available !== false) {
      const v = f.venues[0];
      grid.appendChild(panel("Aggressor delta", { sub: v.label, right: proxyTag(!v.exact || v.proxy) }, el("div", { class: "tiles" },
        tile("Δ 15m", fmt.big(v.w15m.delta), fmt.cls(v.w15m.delta)), tile("Δ 1h", fmt.big(v.w1h.delta), fmt.cls(v.w1h.delta)), tile("Δ 4h", fmt.big(v.w4h.delta), fmt.cls(v.w4h.delta)),
        tile("Buy share 1h", fmt.pct(v.w1h.buy_pct)), tile("Flow score", fmt.signed(f.score), fmt.cls(f.score))),
        el("p", { class: "note", text: "Delta = taker buy volume minus taker sell volume. Sustained positive delta with a flat price often means passive sellers absorbing." })));
    }
    body.appendChild(grid);
  }
  function depthChart(cv, depth) {
    const ctx = cv.getContext("2d"); const W = cv.width = cv.clientWidth * (window.devicePixelRatio || 1), H = cv.height = 260 * (window.devicePixelRatio || 1);
    ctx.clearRect(0, 0, W, H);
    const bids = []; let c = 0; depth.bids.forEach(([p, q]) => { c += q; bids.push([p, c]); });
    const asks = []; c = 0; depth.asks.forEach(([p, q]) => { c += q; asks.push([p, c]); });
    if (!bids.length || !asks.length) return;
    const minP = bids[bids.length - 1][0], maxP = asks[asks.length - 1][0], maxQ = Math.max(bids[bids.length - 1][1], asks[asks.length - 1][1]);
    const X = (p) => (p - minP) / (maxP - minP || 1) * W, Y = (q) => H - 20 - q / maxQ * (H - 30);
    const fill = (pts, col, line) => { ctx.beginPath(); ctx.moveTo(X(pts[0][0]), H - 20); pts.forEach(([p, q]) => ctx.lineTo(X(p), Y(q))); ctx.lineTo(X(pts[pts.length - 1][0]), H - 20); ctx.closePath(); ctx.fillStyle = col; ctx.fill(); ctx.strokeStyle = line; ctx.stroke(); };
    fill(bids, "rgba(38,166,154,.25)", "#26a69a"); fill(asks, "rgba(239,83,80,.25)", "#ef5350");
    ctx.fillStyle = "#7d8794"; ctx.font = `${11 * (window.devicePixelRatio || 1)}px monospace`;
    ctx.fillText(fmt.price(minP), 4, H - 4); const t = fmt.price(maxP); ctx.fillText(t, W - ctx.measureText(t).width - 4, H - 4);
  }

  function tBulls(body, d, charts) {
    const r = d.details.technical, p = d.details.positioning;
    body.appendChild(statusLine(d, "technical"));
    const grid = el("div", { class: "grid g-2" });
    if (r && r.available) {
      const t = el("table", { class: "t" }, el("thead", {}, el("tr", {}, ["tf", "summary", "buy", "neutral", "sell", "osc", "MAs", "score"].map((h, i) => el("th", { class: i > 1 && i < 5 || i === 7 ? "n" : "", text: h })))),
        el("tbody", {}, Object.entries(r.tfs).map(([tf, x]) => el("tr", {}, el("td", { text: tf }), el("td", { class: /BUY/.test(x.summary.RECOMMENDATION) ? "up" : /SELL/.test(x.summary.RECOMMENDATION) ? "down" : "warn", text: x.summary.RECOMMENDATION.replace("_", " ") }),
          el("td", { class: "n up", text: x.summary.BUY }), el("td", { class: "n", text: x.summary.NEUTRAL }), el("td", { class: "n down", text: x.summary.SELL }), el("td", { text: (x.oscillators || "").replace("_", " ") }), el("td", { text: (x.ma || "").replace("_", " ") }),
          el("td", { class: "n " + fmt.cls(x.score), text: fmt.signed(x.score) })))));
      const first = Object.values(r.tfs)[0];
      const ind = first ? Object.entries(first.indicators).filter(([k]) => ["RSI", "ADX", "Stoch.K", "CCI20", "MACD.macd", "EMA50", "EMA200", "ATR"].includes(k)) : [];
      grid.append(panel("TradingView technical ratings", { sub: r.source }, t, el("div", { class: "kv", style: { marginTop: "8px" } }, ind.map(([k, v]) => [el("span", { class: "k", text: k + " (" + Object.keys(r.tfs)[0] + ")" }), el("span", { class: "v", text: isNum(v) ? (Math.abs(v) > 100 ? fmt.price(v) : v.toFixed(2)) : "-" })]))));
    } else {
      grid.appendChild(panel("TradingView technical ratings", {}, el("div", { class: "empty", text: (r && r.note) || "Unavailable" })));
    }
    const sym = HQ.state.symbols.find((s) => s.symbol === d.symbol);
    const taBox = el("div", { class: "tvbox", style: { height: "400px" } });
    grid.appendChild(panel("TradingView gauge (official widget)", { flush: true }, taBox));
    if (sym) HQ.tv.mount(taBox, "ta", { symbol: sym.tvw, interval: "15m", showIntervalTabs: true, displayMode: "single", width: "100%", height: "100%", isTransparent: true });
    if (p && p.available) {
      const lsBox = el("div", { class: "chart md" });
      grid.appendChild(panel("Long / short accounts · funding · OI", { sub: p.source, right: proxyTag(p.proxy, "LIVE") }, el("div", { class: "tiles" },
        tile("Long/short accts", isNum(p.ls_now) ? p.ls_now.toFixed(2) : "-", p.ls_now > 1 ? "up" : "down", isNum(p.ls_now) ? `${(100 * p.ls_now / (1 + p.ls_now)).toFixed(0)}% long accounts` : ""),
        tile("Funding / 8h", isNum(p.funding) ? (p.funding * 100).toFixed(4) + "%" : "-", fmt.cls(p.funding)),
        tile("OI change 1h", isNum(p.oi_change_1h_pct) ? fmt.signed(p.oi_change_1h_pct, 2) + "%" : "-", fmt.cls(p.oi_change_1h_pct)),
        tile("Crowd score", fmt.signed(p.score), fmt.cls(p.score))), lsBox,
        el("p", { class: "note", text: "Read as crowd positioning (trend-following). Extreme readings are often contrarian; that is why its default weight is low." })));
      requestAnimationFrame(() => {
        const ch = LWC.createChart(lsBox, lwcOpts()); charts.push(ch);
        const s = ch.addSeries(LWC.LineSeries, { color: "#4c8dff", lineWidth: 2, title: "L/S", priceLineVisible: false }); s.setData(p.long_short);
        if (p.oi_usd) { const o = ch.addSeries(LWC.AreaSeries, { lineColor: "#f0b90b", topColor: "rgba(240,185,11,.25)", bottomColor: "rgba(240,185,11,0)", title: "OI $", priceFormat: { type: "volume" }, priceLineVisible: false }, 1); o.setData(p.oi_usd); try { ch.panes()[1].setHeight(100); } catch (e) { /* */ } }
        ch.timeScale().fitContent();
      });
    } else grid.appendChild(panel("Long / short positioning", {}, el("div", { class: "empty", text: (p && p.note) || "Unavailable" })));
    const ns = d.status.news || {}, fg = d.status.fear_greed || {};
    const sent = (HQ.state.summary && HQ.state.summary.sentiment) || {};
    const tag = ns.tag && sent[ns.tag];
    grid.appendChild(panel("News & sentiment", { sub: "News Desk · 6h impact-weighted" }, el("div", { class: "tiles" },
      tile("News score", ns.status === "ok" ? fmt.signed(d.components.find((c) => c.id === "news").score) : "n/a", fmt.cls(d.components.find((c) => c.id === "news").score), ns.tag ? "tag " + ns.tag + (ns.proxy ? " (crypto-wide)" : "") : ns.note),
      tile("Bullish items", tag ? tag.bull : "-", "up"), tile("Bearish items", tag ? tag.bear : "-", "down"),
      tile("Fear & Greed", fg.status === "ok" ? fg.value : "n/a", "", fg.label || "crypto only"))));
    body.appendChild(grid);
  }

  function tBias(body, d) {
    const pos = (v) => ((Math.max(-100, Math.min(100, v)) + 100) / 2) + "%";
    const band = el("div", { class: "band" }); band.style.left = pos(-d.neutral_band); band.style.width = (d.neutral_band) + "%";
    const needle = el("div", { class: "needle" }); needle.style.left = pos(d.score);
    body.appendChild(el("div", { class: "bigbias " + d.label },
      el("div", { class: "muted mono", text: d.symbol + " · " + d.name }),
      el("div", { class: "word " + d.label, text: d.label }),
      el("div", { class: "num " + fmt.cls(d.score), text: `${fmt.signed(d.score, 1)} / 100` + (d.dampened ? `  (raw ${fmt.signed(d.raw, 1)}, damped for event risk)` : "") }),
      el("div", { class: "gauge" }, band, needle),
      el("div", { class: "gauge-l" }, el("span", { text: "-100 BEARISH" }), el("span", { text: `neutral ±${d.neutral_band}` }), el("span", { text: "BULLISH +100" })),
      el("div", { class: "muted", style: { marginTop: "10px" }, text: `${d.agreement} active components agree · confidence ${(d.confidence * 100).toFixed(0)}%` })));
    if (d.event) body.appendChild(el("div", { class: "banner red", style: { marginTop: "10px" }, text: `Red-folder event in ${Math.round(d.event.minutes)} min: ${d.event.country} ${d.event.title} (${d.event.time_pkt}). Score shrunk toward neutral.` }));
    const t = el("table", { class: "t bias-tbl" }, el("thead", {}, el("tr", {}, ["component", "what it measures", "score", "", "weight", "eff. weight", "contribution", "status"].map((h, i) => el("th", { class: [2, 4, 5, 6].includes(i) ? "n" : "", text: h })))),
      el("tbody", {}, d.components.map((c) => {
        const s = d.status[c.id] || {};
        return el("tr", { class: c.used ? "" : "dim" }, el("td", { text: c.id }), el("td", { class: "wrap", text: c.desc }), el("td", { class: "n " + fmt.cls(c.score), text: c.score === null ? "n/a" : fmt.signed(c.score) }),
          el("td", { style: { width: "110px" } }, c.score === null ? "" : compBar(c.score)), el("td", { class: "n", text: c.weight.toFixed(2) }), el("td", { class: "n", text: c.used ? (c.eff_weight * 100).toFixed(0) + "%" : "-" }),
          el("td", { class: "n " + fmt.cls(c.contribution), text: c.contribution === null ? "-" : fmt.signed(c.contribution, 1) }),
          el("td", {}, dot(s.status === "ok" ? "ok" : s.status === "fail" ? "fail" : "na"), " ", s.status || "-", s.proxy ? el("span", { class: "tagp", style: { marginLeft: "6px" }, text: "PROXY" }) : null));
      })));
    body.appendChild(panel("Score breakdown", { sub: "weights editable in Settings", flush: true }, el("div", { style: { overflowX: "auto" } }, t)));
    if (d.notes) body.appendChild(el("p", { class: "note proxy", text: d.notes }));
    body.appendChild(el("p", { class: "note", text: "Score = weighted average of the available components (missing ones are skipped and the weights renormalised). |score| below the neutral band = NEUTRAL. This is market analysis, not a trade signal; nothing here places orders." }));
  }

  // ================================================================== 5. SETTINGS
  const settingsView = {
    id: "settings", label: "Settings", ico: "⚙",
    render(main) {
      const box = el("div", {}, el("div", { class: "empty", text: "Loading settings…" }));
      main.append(el("div", { class: "view-head" }, el("h1", { text: "Settings" }), el("span", { class: "sub", text: "Saved on the server (state/settings.json); keys in state/secrets.json, never shown back" })), box);
      Promise.all([api("/api/settings"), api("/api/news/sources").catch(() => null)]).then(([r, src]) => draw(r, src)).catch((e) => { clear(box).appendChild(errBox(e.message)); });
      function draw(r, src) {
        clear(box);
        const s = JSON.parse(JSON.stringify(r.settings));
        const health = {}; ((src && src.sources) || []).forEach((x) => { health[x.id] = x; });
        const row = (label, input, help) => [el("label", { text: label }), input, el("span", { class: "help", text: help || "" })];
        const txt = (path, attrs) => { const [a, b] = path.split("."); const i = el("input", Object.assign({ type: "text", value: b ? s[a][b] : s[a] }, attrs || {})); i.oninput = () => { const v = i.type === "number" ? Number(i.value) : i.value; if (b) s[a][b] = v; else s[a] = v; }; return i; };
        const num = (path, attrs) => txt(path, Object.assign({ type: "number", step: "any" }, attrs || {}));
        const sel = (path, opts) => { const [a, b] = path.split("."); const cur = b ? s[a][b] : s[a]; const x = el("select", {}, opts.map((o) => el("option", { value: o, text: o, selected: String(o) === String(cur) }))); x.onchange = () => { if (b) s[a][b] = x.value; else s[a] = x.value; }; return x; };
        const chk = (path) => { const [a, b] = path.split("."); const c = el("input", { type: "checkbox", checked: !!s[a][b] }); c.onchange = () => { s[a][b] = c.checked; }; return c; };
        const wl = el("input", { type: "text", value: (s.watchlist || []).join(", ") }); wl.oninput = () => { s.watchlist = wl.value.split(/[ ,]+/).map((x) => x.trim().toUpperCase()).filter(Boolean); };
        const tfs = el("input", { type: "text", value: (s.bias.rating_tfs || []).join(", ") }); tfs.oninput = () => { s.bias.rating_tfs = tfs.value.split(/[ ,]+/).filter(Boolean); };
        const tzs = ["Asia/Karachi", "UTC", "Asia/Dubai", "Europe/London", "America/New_York", "Asia/Tokyo", "Asia/Singapore", "Australia/Sydney"];
        const general = el("div", { class: "form" },
          row("Timezone", sel("timezone", tzs.includes(s.timezone) ? tzs : [s.timezone].concat(tzs)), "all times in the app (News Desk stays PKT)"),
          row("Theme", sel("theme", ["dark", "light"])),
          row("Dashboard BTC chart", txt("dashboard.btc_symbol"), "TradingView symbol, e.g. BITSTAMP:BTCUSD or BINANCE:BTCUSDT"),
          row("Dashboard gold chart", txt("dashboard.gold_symbol"), "e.g. OANDA:XAUUSD"),
          row("Dashboard chart interval", sel("dashboard.chart_interval", ["1", "5", "15", "60", "240", "D"])),
          row("TradingView default symbol", txt("tradingview.default_symbol")),
          row("TradingView interval", sel("tradingview.interval", ["1", "5", "15", "60", "240", "D", "W"])),
          row("Watchlist", wl, "symbols for the TradingView page and ticker tape"),
          row("Indicators default symbol", txt("indicators.default_symbol")),
          row("Indicators default timeframe", sel("indicators.default_tf", ["1m", "5m", "15m", "1h", "4h", "1d"])));
        const refresh = el("div", { class: "form" },
          row("Analysis / summary", num("refresh.analysis_s", { min: 5 }), "seconds"), row("Order flow", num("refresh.flow_s", { min: 5 }), "seconds"),
          row("Ticker data", num("refresh.ticker_s", { min: 5 }), "seconds"), row("Status dots", num("refresh.status_s", { min: 5 }), "seconds"));
        const ticker = el("div", { class: "form" },
          row("Speed", num("ticker.speed", { min: 10, max: 400 }), "pixels per second (also − / + on the ticker)"), row("Paused", chk("ticker.paused")),
          row("Headlines shown", num("ticker.headlines", { min: 1, max: 10 })), row("Events shown", num("ticker.events", { min: 1, max: 10 })),
          row("Headline max age", num("ticker.headline_max_age_min", { min: 5 }), "minutes; older headlines leave the ticker"),
          row("Headline min impact", sel("ticker.headline_min_impact", ["LOW", "MEDIUM", "HIGH", "CRITICAL"])));
        const w = s.bias.weights;
        const bias = el("div", { class: "form" }, Object.keys(w).map((k) => { const i = el("input", { type: "number", step: "0.05", min: 0, max: 1, value: w[k] }); i.oninput = () => { w[k] = Number(i.value); }; return row("Weight · " + k, i); }),
          row("Neutral band", num("bias.neutral_band", { min: 0, max: 60 }), "|score| below this = NEUTRAL"),
          row("Event dampening", num("bias.event_dampen", { min: 0, max: 1, step: 0.05 }), "0..1, how much a near red-folder event shrinks the score"),
          row("Event window", num("bias.event_window_min", { min: 0 }), "minutes before a high-impact event"),
          row("Rating timeframes", tfs, "tradingview-ta intervals, e.g. 15m, 1h, 4h"));
        const disabled = new Set(s.news.disabled || []);
        const srcGrid = el("div", { class: "src-grid" }, r.sources.map((x) => {
          const h = health[x.id] || {};
          const c = el("input", { type: "checkbox", checked: !disabled.has(x.id) });
          c.onchange = () => { if (c.checked) disabled.delete(x.id); else disabled.add(x.id); s.news.disabled = Array.from(disabled); };
          return el("label", { title: h.error || h.status || "" }, c, dot(h.status || "pending"), el("span", {}, x.name, " ", el("small", { text: h.status === "needs_key" ? "needs key" : (h.status || "") })));
        }));
        const news = el("div", {}, el("div", { class: "form" }, row("LLM refinement", chk("news.llm"), "re-score HIGH/CRITICAL headlines with your LLM (needs LLM URL + model below)")), el("h3", { class: "sec", text: "Sources (dot = live status)" }), srcGrid,
          el("p", { class: "note", text: "Green ok · amber pending/stale · red failing · blue needs a key · grey off. Reddit and FinancialJuice rate-limit cloud IPs; from a home PC they normally work. X needs an API bearer token or your own nitter/RSSHub." }));
        const secIn = {};
        const sec = el("div", { class: "form" }, [["x_bearer", "X API bearer token", "X API v2 (Basic tier+) for the X accounts"], ["nitter_base", "Nitter / RSSHub base URL", "self-hosted mirror, e.g. http://127.0.0.1:8080"],
          ["reddit_client_id", "Reddit app client id", "reddit.com/prefs/apps → script/web app"], ["reddit_client_secret", "Reddit app secret", ""],
          ["llm_url", "LLM base URL", "OpenAI-compatible, e.g. http://127.0.0.1:20128/v1"], ["llm_model", "LLM model", ""], ["llm_key", "LLM API key", ""]].map(([k, l, h]) => {
          const info = r.secrets[k] || {};
          const i = el("input", { type: k === "nitter_base" || k === "llm_url" || k === "llm_model" ? "text" : "password", placeholder: info.set ? "saved" + (info.from_env ? " (from env)" : "") + " — type to replace" : "not set", autocomplete: "off" });
          secIn[k] = i;
          return row(l, i, (info.set ? "✓ set. " : "") + h);
        }));
        let tokIn = null;
        if (r.token_required) {
          tokIn = el("input", { type: "password", placeholder: "HQT_TOKEN", value: sessionStorage.getItem("hq.token") || "" });
        }
        const save = async () => {
          try {
            if (tokIn) sessionStorage.setItem("hq.token", tokIn.value.trim());
            const out = await api("/api/settings", { method: "POST", body: s });
            HQ.applySettings(out.settings);
            const patch = {}; Object.entries(secIn).forEach(([k, i]) => { if (i.value.trim()) patch[k] = i.value.trim(); });
            if (Object.keys(patch).length) await api("/api/secrets", { method: "POST", body: patch });
            HQ.toast("Settings saved", Object.keys(patch).length ? "keys stored on the server" : "", "ok"); HQ.ticker.rebuild();
            settingsView.render(clear(main));
          } catch (e) { HQ.toast("Save failed", e.status === 401 ? "this server needs its HQT_TOKEN (field at the bottom)" : e.message, "bad"); }
        };
        const clearKey = el("select", {}, el("option", { value: "", text: "Remove a saved key…" }), Object.entries(r.secrets).filter(([, v]) => v.set && !v.from_env).map(([k]) => el("option", { value: k, text: k })));
        clearKey.onchange = async () => { if (!clearKey.value) return; try { await api("/api/secrets", { method: "POST", body: { [clearKey.value]: "" } }); HQ.toast("Key removed", clearKey.value, "ok"); settingsView.render(clear(main)); } catch (e) { HQ.toast("Failed", e.message, "bad"); } };
        box.append(
          el("div", { class: "grid g-2" }, panel("General", {}, general), el("div", { class: "col" }, panel("Refresh rates", {}, refresh), panel("Ticker", {}, ticker))),
          el("div", { class: "grid g-2", style: { marginTop: "10px" } }, panel("Market bias weights", {}, bias), panel("API keys", { sub: "stored server-side, never displayed" }, sec, el("div", { class: "toolbar", style: { marginTop: "8px" } }, clearKey))),
          el("div", { style: { marginTop: "10px" } }, panel("News sources", {}, news)),
          el("div", { class: "toolbar", style: { margin: "12px 0 30px" } }, el("button", { class: "btn primary", text: "Save settings", onclick: save }),
            el("button", { class: "btn", text: "Reset to defaults", onclick: async () => { try { const o = await api("/api/settings/reset", { method: "POST" }); HQ.applySettings(o.settings); settingsView.render(clear(main)); HQ.toast("Defaults restored", "", "ok"); } catch (e) { HQ.toast("Reset failed", e.message, "bad"); } } }),
            tokIn ? el("span", { class: "muted", text: "Server token:" }) : null, tokIn));
      }
    },
  };

  HQ.views = [dashboard, indicatorsView, tradingview, analysisView, settingsView];
})(window.HQ);
