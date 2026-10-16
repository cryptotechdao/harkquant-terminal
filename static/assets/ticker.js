/* TV-news style ticker: N current headlines + M upcoming high-impact events, interleaved, scrolling continuously.
   Items expire (headline older than the max age, event time passed) and the next one in line takes the slot.
   Speed -/+ and pause persist to Settings (server) and localStorage. */
"use strict";
(function (HQ) {
  const { el, fmt } = HQ;
  const T = { data: null, ids: "", x: 0, last: 0, w: 0, raf: 0, saveT: 0 };
  const cfg = () => Object.assign({ speed: 60, paused: false, headlines: 3, events: 3 }, HQ.settings.ticker || {});

  function pick() {
    const now = Date.now() / 1000, c = cfg();
    if (!T.data) return [];
    const heads = (T.data.headlines || []).filter((h) => h.expires > now).slice(0, c.headlines);
    const evs = (T.data.events || []).filter((e) => e.expires > now).slice(0, c.events);
    const out = [];
    for (let i = 0; i < Math.max(heads.length, evs.length); i++) { if (heads[i]) out.push(heads[i]); if (evs[i]) out.push(evs[i]); }
    if (!evs.length) out.push({ kind: "info", id: "noev", text: "No more red-folder events in this ForexFactory week (next week's list appears Sunday)" });
    if (!heads.length) out.push({ kind: "info", id: "nohead", text: "No fresh medium/high-impact headlines right now" });
    return out;
  }
  function itemNode(it) {
    if (it.kind === "event") {
      const cd = el("span", { class: "cd", dataset: { ts: it.ts } }, fmt.countdown(it.ts - Date.now() / 1000));
      return el("span", { class: "tk-item ev " + (it.red ? "red" : "") },
        el("span", { class: "lbl", text: it.red ? "RED FOLDER" : "MED" }), el("span", { class: "txt", text: it.text }),
        el("span", { class: "t", text: fmt.dhm(it.ts) + " " + fmt.tz() }),
        it.forecast ? el("span", { class: "t", text: "f " + it.forecast }) : null, el("span", { class: "t", text: "in" }), cd);
    }
    if (it.kind === "info") return el("span", { class: "tk-item" }, el("span", { class: "t", text: it.text }));
    const d = it.direction || {};
    const dir = Object.entries(d).map(([a, v]) => a + (v === "bullish" ? "▲" : "▼")).join(" ");
    return el("span", { class: "tk-item " + it.impact, title: it.time_pkt },
      el("span", { class: "src", text: it.source || "" }), el("span", { class: "t", text: fmt.hm(it.ts) }),
      el("span", { class: "txt", text: it.text }), dir ? el("span", { class: "t " + (dir.includes("▲") ? "up" : "down"), text: dir }) : null);
  }
  function build(force) {
    const items = pick();
    const ids = items.map((i) => i.id).join("|");
    if (!force && ids === T.ids) return;
    T.ids = ids;
    const track = document.getElementById("tk-track"); if (!track) return;
    HQ.clear(track);
    const one = () => items.map(itemNode);
    track.append(...one(), ...one());            // two copies -> seamless loop
    T.w = track.scrollWidth / 2;
    const view = document.getElementById("tk-view");
    if (T.w < view.clientWidth) { track.append(...one(), ...one()); T.w = track.scrollWidth / 2; }
    if (-T.x > T.w) T.x = 0;
  }
  function frame(t) {
    const dt = T.last ? Math.min(0.1, (t - T.last) / 1000) : 0; T.last = t;
    const c = cfg();
    if (!c.paused && T.w > 0) {
      T.x -= c.speed * dt;
      if (-T.x >= T.w) T.x += T.w;
      const track = document.getElementById("tk-track"); if (track) track.style.transform = `translate3d(${T.x.toFixed(1)}px,0,0)`;
    }
    T.raf = requestAnimationFrame(frame);
  }
  function tickSecond() {
    const now = Date.now() / 1000;
    document.querySelectorAll("#tk-track .cd").forEach((n) => { n.textContent = fmt.countdown(Number(n.dataset.ts) - now); });
    build(false);      // expiry: if the selection changed, the next item takes the slot
  }
  function save() {
    clearTimeout(T.saveT);
    const c = cfg();
    document.getElementById("tk-spd").textContent = c.speed + "px/s";
    document.getElementById("tk-pause").textContent = c.paused ? "▶" : "II";
    T.saveT = setTimeout(() => {
      HQ.api("/api/settings", { method: "POST", body: { ticker: { speed: c.speed, paused: c.paused } } })
        .then((r) => HQ.applySettings(r.settings)).catch(() => { /* token-protected server: kept locally */ });
    }, 800);
  }
  function setSpeed(f) {
    const s = HQ.settings; s.ticker = cfg();
    s.ticker.speed = Math.round(Math.max(10, Math.min(400, s.ticker.speed * f)));
    HQ.applySettings(s); save();
  }
  function init() {
    document.getElementById("tk-slow").onclick = () => setSpeed(0.8);
    document.getElementById("tk-fast").onclick = () => setSpeed(1.25);
    document.getElementById("tk-pause").onclick = () => { const s = HQ.settings; s.ticker = cfg(); s.ticker.paused = !s.ticker.paused; HQ.applySettings(s); save(); };
    const view = document.getElementById("tk-view");
    view.addEventListener("mouseenter", () => { T.hover = true; });
    const c = cfg();
    document.getElementById("tk-spd").textContent = c.speed + "px/s";
    document.getElementById("tk-pause").textContent = c.paused ? "▶" : "II";
    setInterval(tickSecond, 1000);
    window.addEventListener("resize", () => build(true));
    T.raf = requestAnimationFrame(frame);
  }
  function update(data) { T.data = data; build(false); }
  HQ.ticker = { init, update, rebuild: () => build(true) };
})(window.HQ);
