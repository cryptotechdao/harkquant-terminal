/* HarkQuant Terminal - shell: navigation (hash router), live stream (SSE), source status dots, clock, PWA. */
"use strict";
(function (HQ) {
  const { el, $, clear, fmt, api } = HQ;
  const bus = {};
  HQ.on = (ev, fn) => { (bus[ev] = bus[ev] || new Set()).add(fn); return () => bus[ev].delete(fn); };
  const emit = (ev, d) => (bus[ev] || []).forEach((fn) => { try { fn(d); } catch (e) { console.error(e); } });

  let cleanup = null, current = null;
  function nav() {
    const n = clear($("#nav"));
    HQ.views.forEach((v, i) => n.appendChild(el("a", { href: "#/" + v.id, "data-view": v.id, title: `${v.label} (${i + 1})` },
      el("span", { class: "ico", text: v.ico }), el("span", { class: "lbl", text: v.label }))));
    n.append(el("div", { class: "sep" }), el("div", { class: "foot" }, "Read-only analysis", el("br"), "No order placement", el("br"), "Keys 1-5 switch views"));
  }
  function route() {
    const id = (location.hash.replace(/^#\/?/, "") || "dashboard").split("?")[0];
    const v = HQ.views.find((x) => x.id === id) || HQ.views[0];
    if (cleanup) { try { cleanup(); } catch (e) { /* */ } cleanup = null; }
    HQ.killTimers();
    const main = clear($("#main"));
    main.scrollTop = 0;
    current = v.id;
    document.querySelectorAll("#nav a").forEach((a) => a.classList.toggle("active", a.dataset.view === v.id));
    document.title = v.label + " · HarkQuant Terminal";
    try { cleanup = v.render(main) || null; } catch (e) { console.error(e); main.appendChild(el("div", { class: "empty down", text: "View failed: " + e.message })); }
  }
  document.addEventListener("keydown", (e) => {
    if (e.target.closest("input,select,textarea") || e.ctrlKey || e.metaKey || e.altKey) return;
    const i = Number(e.key) - 1; if (i >= 0 && i < HQ.views.length) location.hash = "#/" + HQ.views[i].id;
  });

  // ------------------------------------------------------------------ live stream
  let es = null, pollT = null;
  function connect() {
    if (es) es.close();
    es = new EventSource("/api/stream");
    const pill = $("#pill-conn");
    es.onopen = () => { pill.className = "pill ok hide-sm"; pill.textContent = "live"; if (pollT) { clearInterval(pollT); pollT = null; } };
    es.onerror = () => {
      pill.className = "pill warn hide-sm"; pill.textContent = "reconnecting";
      if (!pollT) pollT = setInterval(() => { api("/api/ticker").then(HQ.ticker.update).catch(() => {}); }, 30000);
    };
    es.addEventListener("ticker", (e) => { try { HQ.ticker.update(JSON.parse(e.data)); } catch (x) { /* */ } });
    es.addEventListener("summary", (e) => { try { const s = JSON.parse(e.data); HQ.state.summary = s; kpis(s); emit("summary", s); } catch (x) { /* */ } });
    es.addEventListener("item", (e) => { try { const it = JSON.parse(e.data); if (it.impact === "CRITICAL") HQ.toast("CRITICAL · " + (it.label || it.source_name), it.title, "bad"); } catch (x) { /* */ } });
  }
  function kpis(s) {
    const box = clear($("#kpis"));
    Object.values(s.analysis || {}).forEach((a) => {
      if (!a || a.error) return;
      box.appendChild(el("a", { class: "kpi", href: "#/analysis", style: { textDecoration: "none", color: "inherit" }, onclick: () => localStorage.setItem("hq.an.sym", a.symbol) },
        el("span", { class: "k", text: a.symbol + " bias" }), el("span", { class: "v " + a.label, text: a.label + " " + fmt.signed(a.score) })));
    });
    if (s.next_high) {
      const k = el("span", { class: "kpi" }, el("span", { class: "k", text: "next red folder" }), el("span", { class: "v down", dataset: { ts: s.next_high.ts }, text: s.next_high.country + " " + fmt.countdown(s.next_high.ts - Date.now() / 1000) }));
      k.title = s.next_high.title; box.appendChild(k);
    }
  }

  // ------------------------------------------------------------------ status dots (topbar)
  async function status() {
    try {
      const s = await api("/api/status");
      const box = clear($("#topdots"));
      const m = s.market || {};
      const add = (name, st, title) => box.appendChild(el("span", { title: name + ": " + title, style: { display: "inline-flex", alignItems: "center", gap: "3px", fontSize: "10px", color: "var(--muted)" } }, HQ.dot(st), el("span", { class: "hide-sm", text: name })));
      ["okx", "tradingview-ta", "yfinance"].forEach((k) => add(k === "tradingview-ta" ? "TV" : k === "yfinance" ? "YF" : "OKX", m[k] ? m[k].status : "pending", m[k] ? (m[k].last_error || "ok") : "not used yet"));
      const n = s.news || {};
      add("NEWS", n.fail > n.ok ? "fail" : n.ok ? "ok" : "pending", `${n.ok}/${n.total} sources ok, ${n.fail} failing, ${n.needs_key || 0} need a key`);
      if ((s.needs_key || []).length) add("KEY", "needs_key", "needs key: " + s.needs_key.join(", "));
    } catch (e) { /* server down: pill shows it */ }
  }

  async function boot() {
    try { const r = await api("/api/settings"); HQ.applySettings(r.settings); } catch (e) { HQ.applySettings(HQ.settings); }
    try { HQ.state.symbols = (await api("/api/symbols")).symbols; } catch (e) { HQ.state.symbols = []; }
    nav();
    HQ.ticker.init();
    api("/api/ticker").then(HQ.ticker.update).catch(() => {});
    connect();
    setInterval(() => {
      $("#clock").firstChild.textContent = fmt.clock();
      $("#clock small").textContent = fmt.tz();
      document.querySelectorAll("#kpis [data-ts]").forEach((n) => { n.textContent = n.textContent.split(" ")[0] + " " + fmt.countdown(Number(n.dataset.ts) - Date.now() / 1000); });
    }, 1000);
    status(); setInterval(status, ((HQ.settings.refresh || {}).status_s || 30) * 1000);
    window.addEventListener("hashchange", route);
    route();
    if ("serviceWorker" in navigator) navigator.serviceWorker.register("/sw.js", { scope: "/" }).catch(() => { /* optional */ });
  }
  boot();
})(window.HQ);
