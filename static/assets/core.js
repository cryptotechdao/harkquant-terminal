/* HarkQuant Terminal - core: DOM helpers (from Hark Titans), formatting in the user's timezone (default Asia/Karachi),
   API client, settings, toasts. Server text reaches the DOM through textContent only. */
"use strict";
window.HQ = window.HQ || {};
(function (HQ) {
  function el(tag, attrs, ...kids) {
    const n = document.createElement(tag);
    if (attrs) {
      for (const [k, v] of Object.entries(attrs)) {
        if (v === undefined || v === null || v === false) continue;
        if (k === "class") n.className = v;
        else if (k === "text") n.textContent = v;
        else if (k === "style" && typeof v === "object") Object.assign(n.style, v);
        else if (k.startsWith("on") && typeof v === "function") n.addEventListener(k.slice(2), v);
        else if (k === "dataset") Object.assign(n.dataset, v);
        else if (v === true) n.setAttribute(k, "");
        else n.setAttribute(k, String(v));
      }
    }
    for (const c of kids.flat(Infinity)) {
      if (c === undefined || c === null || c === false) continue;
      n.appendChild(c instanceof Node ? c : document.createTextNode(String(c)));
    }
    return n;
  }
  const $ = (s, r) => (r || document).querySelector(s);
  const clear = (n) => { while (n && n.firstChild) n.removeChild(n.firstChild); return n; };
  const isNum = (v) => typeof v === "number" && isFinite(v);

  // ---------------- settings (server is the source of truth; localStorage mirrors for instant start)
  HQ.settings = (() => { try { return JSON.parse(localStorage.getItem("hq.settings") || "null"); } catch (e) { return null; } })() ||
    { timezone: "Asia/Karachi", theme: "dark", ticker: { speed: 60, paused: false }, refresh: { analysis_s: 20, flow_s: 10, ticker_s: 30, status_s: 30 },
      dashboard: { btc_symbol: "BITSTAMP:BTCUSD", gold_symbol: "OANDA:XAUUSD", chart_interval: "15" }, tradingview: { default_symbol: "OANDA:XAUUSD", interval: "15", style: "1" },
      watchlist: ["BTCUSD", "XAUUSD"], bias: { weights: {}, neutral_band: 15 }, news: { disabled: [] }, indicators: { default_symbol: "BTCUSD", default_tf: "15m" } };
  HQ.applySettings = function (s) {
    HQ.settings = s;
    try { localStorage.setItem("hq.settings", JSON.stringify(s)); } catch (e) { /* private mode */ }
    document.documentElement.classList.toggle("light", s.theme === "light");
    tzCache = null;
  };

  // ---------------- time formatting in the configured zone
  let tzCache = null;
  function tzf() {
    if (tzCache) return tzCache;
    const tz = HQ.settings.timezone || "Asia/Karachi";
    const mk = (o) => { try { return new Intl.DateTimeFormat("en-GB", Object.assign({ timeZone: tz, hour12: false }, o)); } catch (e) { return new Intl.DateTimeFormat("en-GB", Object.assign({ timeZone: "Asia/Karachi", hour12: false }, o)); } };
    const abbr = (() => { if (tz === "Asia/Karachi") return "PKT"; try { return new Intl.DateTimeFormat("en-US", { timeZone: tz, timeZoneName: "short" }).formatToParts(new Date()).find((p) => p.type === "timeZoneName").value; } catch (e) { return tz; } })();
    tzCache = { hms: mk({ hour: "2-digit", minute: "2-digit", second: "2-digit" }), hm: mk({ hour: "2-digit", minute: "2-digit" }),
      dhm: mk({ weekday: "short", day: "2-digit", hour: "2-digit", minute: "2-digit" }), abbr };
    return tzCache;
  }
  const fmt = {
    tz: () => tzf().abbr,
    clock: (ms) => tzf().hms.format(ms ? new Date(ms) : new Date()),
    hm: (sec) => isNum(sec) ? tzf().hm.format(new Date(sec * 1000)) : "-",
    dhm: (sec) => isNum(sec) ? tzf().dhm.format(new Date(sec * 1000)) : "-",
    price(v) { if (!isNum(v)) return "-"; const a = Math.abs(v); return a >= 1000 ? v.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : a >= 10 ? v.toFixed(2) : a >= 1 ? v.toFixed(4) : v.toPrecision(4); },
    big(v) { if (!isNum(v)) return "-"; const a = Math.abs(v); const s = v < 0 ? "-" : ""; if (a >= 1e9) return s + (a / 1e9).toFixed(2) + "B"; if (a >= 1e6) return s + (a / 1e6).toFixed(2) + "M"; if (a >= 1e3) return s + (a / 1e3).toFixed(1) + "K"; return s + a.toFixed(a < 10 ? 2 : 0); },
    signed(v, d) { return isNum(v) ? (v > 0 ? "+" : "") + v.toFixed(d === undefined ? 0 : d) : "-"; },
    pct(v, d) { return isNum(v) ? v.toFixed(d === undefined ? 1 : d) + "%" : "-"; },
    countdown(sec) {
      if (!isNum(sec)) return "-"; if (sec <= 0) return "NOW";
      const d = Math.floor(sec / 86400), h = Math.floor((sec % 86400) / 3600), m = Math.floor((sec % 3600) / 60), s = Math.floor(sec % 60);
      const p = (x) => String(x).padStart(2, "0");
      return (d ? d + "d " : "") + (d || h ? p(h) + ":" : "") + p(m) + ":" + p(s);
    },
    ago(sec) { const s = Math.max(0, Math.round(Date.now() / 1000 - sec)); if (s < 60) return s + "s"; if (s < 3600) return Math.floor(s / 60) + "m"; if (s < 86400) return Math.floor(s / 3600) + "h"; return Math.floor(s / 86400) + "d"; },
    cls: (v) => !isNum(v) || v === 0 ? "" : v > 0 ? "up" : "down",
  };

  // ---------------- API
  async function api(path, opts) {
    opts = opts || {};
    const init = { method: opts.method || "GET", headers: { Accept: "application/json" }, cache: "no-store" };
    if (opts.body !== undefined) { init.headers["Content-Type"] = "application/json"; init.body = JSON.stringify(opts.body); }
    const tok = sessionStorage.getItem("hq.token") || localStorage.getItem("hq.token");
    if (tok && init.method !== "GET") init.headers.Authorization = "Bearer " + tok;
    const ctl = new AbortController(); const t = setTimeout(() => ctl.abort(), opts.timeout || 60000); init.signal = ctl.signal;
    let r;
    try { r = await fetch(path, init); } finally { clearTimeout(t); }
    let data = null; try { data = await r.json(); } catch (e) { data = null; }
    if (!r.ok) { const err = new Error((data && (data.detail || data.error)) || ("HTTP " + r.status)); err.status = r.status; err.data = data; throw err; }
    return data;
  }

  function toast(title, msg, kind) {
    const box = $("#toasts"); if (!box) return;
    const t = el("div", { class: "toast " + (kind || "") }, el("b", { text: title }), msg ? el("span", { text: msg }) : null);
    box.appendChild(t); setTimeout(() => t.remove(), 5000);
  }

  // ---------------- small widgets
  function panel(title, opts, ...body) {
    opts = opts || {};
    const ph = el("div", { class: "ph" }, el("b", { text: title }), opts.sub ? el("span", { class: "muted", text: opts.sub }) : null, el("span", { class: "grow" }), opts.right || null);
    return el("section", { class: "panel " + (opts.cls || "") }, ph, el("div", { class: "pb " + (opts.flush ? "flush" : "") }, ...body));
  }
  function compBar(score) {
    const v = isNum(score) ? Math.max(-100, Math.min(100, score)) : 0;
    const i = el("i", { class: v >= 0 ? "pos" : "neg" }); i.style.width = Math.abs(v) / 2 + "%";
    return el("div", { class: "cbar" }, i);
  }
  function dot(status, title) { return el("span", { class: "dot " + (status || "na"), title: title || status || "" }); }

  // ---------------- timers that die with the view
  const timers = new Set();
  HQ.every = (ms, fn, now) => { if (now !== false) fn(); const id = setInterval(fn, ms); timers.add(id); return id; };
  HQ.killTimers = () => { timers.forEach(clearInterval); timers.clear(); };

  Object.assign(HQ, { el, $, clear, fmt, api, toast, panel, compBar, dot, isNum });
})(window.HQ);
