/* HarkQuant News Desk — embeddable module.
 *   <link rel="stylesheet" href="/static/news/news.css"><script src="/static/news/news.js"></script>
 *   const desk = NewsDesk.mount(document.getElementById('news'), { api: '/api/news' });
 *   desk.destroy();   // when the SPA section is unmounted
 * Options: api (default '/api/news'), embedded (default true: no own page chrome, host gives height),
 *          tab ('feed'|'cal'|'src' initial tab on narrow), limit (default 300), onCritical(item) callback.
 * Emits DOM CustomEvent 'newsdesk:item' on the mount element for every new item (detail = item).
 */
(function (global) {
  'use strict';
  const LV = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  const ASSETS = ['XAU', 'BTC', 'USD', 'RISK'];
  const PKT_OFF = 5 * 3600 * 1000;
  const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const pad = n => String(n).padStart(2, '0');
  const pktDate = ts => new Date(ts * 1000 + PKT_OFF);           // read with getUTC* => PKT wall clock
  const hm = ts => { const d = pktDate(ts); return pad(d.getUTCHours()) + ':' + pad(d.getUTCMinutes()); };
  const hms = ts => { const d = pktDate(ts); return hm(ts) + ':' + pad(d.getUTCSeconds()); };
  const dayKey = ts => { const d = pktDate(ts); return d.getUTCFullYear() + '-' + pad(d.getUTCMonth() + 1) + '-' + pad(d.getUTCDate()); };
  const dayLabel = ts => { const d = pktDate(ts); return ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][d.getUTCDay()] + ' ' + pad(d.getUTCDate()) + ' ' + ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][d.getUTCMonth()]; };
  function ago(ts, now) {
    const s = Math.max(0, Math.round(now - ts));
    if (s < 60) return s + 's ago';
    if (s < 3600) return Math.floor(s / 60) + 'm ' + pad(s % 60) + 's ago';
    if (s < 86400) return Math.floor(s / 3600) + 'h ' + pad(Math.floor(s % 3600 / 60)) + 'm ago';
    return Math.floor(s / 86400) + 'd ago';
  }
  function countdown(sec) {
    if (sec <= 0) return 'NOW';
    const d = Math.floor(sec / 86400), h = Math.floor(sec % 86400 / 3600), m = Math.floor(sec % 3600 / 60), s = Math.floor(sec % 60);
    return (d ? d + 'd ' : '') + (d || h ? pad(h) + ':' : '') + pad(m) + ':' + pad(s);
  }
  const store = {
    get(k, d) { try { const v = localStorage.getItem('nd.' + k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } },
    set(k, v) { try { localStorage.setItem('nd.' + k, JSON.stringify(v)); } catch (e) { } }
  };

  function mount(el, opts) {
    if (typeof el === 'string') el = document.querySelector(el);
    if (!el) throw new Error('NewsDesk.mount: container not found');
    opts = Object.assign({ api: '/api/news', embedded: true, limit: 300, tab: 'feed' }, opts || {});
    const API = opts.api.replace(/\/$/, '');
    const S = {
      items: new Map(), order: [], skew: 0, lastIns: 0, es: null, mode: 'off', pollT: null, timers: [],
      filt: Object.assign({ impact: [], asset: [], source: '', q: '' }, store.get('filt', {})),
      sound: store.get('sound', false), cal: [], next: null, calRange: store.get('calRange', 'today'), calHigh: store.get('calHigh', false),
      sources: null, sent: null, open: new Set(), unseen: 0, dead: false, audio: null
    };
    const now = () => Date.now() / 1000 + S.skew;

    const root = document.createElement('div');
    root.className = 'nd-root' + (opts.embedded ? ' nd-embedded' : '');
    root.dataset.tab = opts.tab;
    root.innerHTML = `
      <div class="nd-top">
        <span class="nd-brand">NEWS DESK<small>XAU · BTC · MACRO</small></span>
        <span class="nd-live off" data-r="live"><span class="nd-dot"></span><span data-r="livetxt">CONNECTING</span></span>
        <span class="nd-clock" data-r="clock">--:--:-- PKT</span>
        <span class="nd-next" data-r="next" title="Next high-impact event"><b>NEXT HIGH</b><span class="nd-nev" data-r="nextev">—</span><span class="nd-cd" data-r="nextcd"></span></span>
        <span class="nd-sp"></span>
        <button class="nd-btn" data-a="sound" title="Sound alert on CRITICAL"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/></svg><span class="nd-bl">CRIT ALERT</span></button>
        <button class="nd-btn" data-a="refresh" title="Force refresh all sources"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12a9 9 0 1 1-2.6-6.4L21 8"/><path d="M21 3v5h-5"/></svg><span class="nd-bl">REFRESH</span></button>
      </div>
      <div class="nd-gauges" data-r="gauges"></div>
      <div class="nd-filters">
        <div class="nd-chips" data-r="fimp">${LV.slice().reverse().map(l => `<button class="nd-chip" data-k="impact" data-v="${l}">${l === 'CRITICAL' ? 'CRIT' : l === 'MEDIUM' ? 'MED' : l}</button>`).join('')}</div>
        <span class="nd-sep"></span>
        <div class="nd-chips" data-r="fasset">${ASSETS.map(a => `<button class="nd-chip a" data-k="asset" data-v="${a}">${a}</button>`).join('')}</div>
        <span class="nd-sep"></span>
        <select class="nd-sel" data-r="fsrc"><option value="">All sources</option></select>
        <input class="nd-in" data-r="fq" type="search" placeholder="Search headlines…  e.g. CPI, Warsh, ETF" autocomplete="off">
      </div>
      <div class="nd-tabs">
        <button class="nd-tab" data-tab="feed">Squawk<span class="nd-badge CRITICAL" data-r="unseen" style="display:none"></span></button>
        <button class="nd-tab" data-tab="cal">Calendar</button>
        <button class="nd-tab" data-tab="src">Sources</button>
      </div>
      <div class="nd-body">
        <section class="nd-col feed">
          <div class="nd-ph">Live Squawk<span class="nd-sub" data-r="feedsub"></span><span class="nd-sp"></span><span class="nd-sub" data-r="feedcount"></span></div>
          <div class="nd-scroll" data-r="feedscroll"><button class="nd-newbar" data-r="newbar"></button><div data-r="feed"><div class="nd-empty">Loading feed…</div></div></div>
        </section>
        <div class="nd-side">
          <section class="nd-pane cal">
            <div class="nd-ph">Economic Calendar<span class="nd-sp"></span>
              <button class="nd-chip" data-cal="today">TODAY</button><button class="nd-chip" data-cal="week">WEEK</button><button class="nd-chip" data-cal="high">HIGH</button></div>
            <div class="nd-scroll" data-r="cal"><div class="nd-empty">Loading calendar…</div></div>
          </section>
          <section class="nd-pane src">
            <div class="nd-ph">Source Health<span class="nd-sub" data-r="srcsum"></span></div>
            <div class="nd-scroll" data-r="src"><div class="nd-empty">Loading…</div></div>
          </section>
        </div>
      </div>`;
    el.innerHTML = ''; el.appendChild(root);
    const $ = r => root.querySelector(`[data-r="${r}"]`);

    // ---------- responsive (container width, so it works inside any SPA panel)
    const ro = new ResizeObserver(es => { for (const e of es) root.classList.toggle('nd-narrow', e.contentRect.width < 820); });
    ro.observe(root);
    function setTab(t) { root.dataset.tab = t; root.querySelectorAll('.nd-tab').forEach(b => b.classList.toggle('on', b.dataset.tab === t)); if (t === 'feed') { S.unseen = 0; renderUnseen(); } }
    setTab(opts.tab);

    // ---------- fetch helpers
    async function j(path, init) {
      const r = await fetch(API + path, Object.assign({ cache: 'no-store' }, init || {}));
      if (!r.ok) throw new Error(r.status + ' ' + path);
      return r.json();
    }
    const syncClock = st => { if (st) S.skew = st - Date.now() / 1000; };

    // ---------- filters
    function syncFilterUI() {
      root.querySelectorAll('.nd-chip[data-k]').forEach(b => b.classList.toggle('on', S.filt[b.dataset.k].includes(b.dataset.v)));
      $('fq').value = S.filt.q || ''; $('fsrc').value = S.filt.source || '';
    }
    function matches(it) {
      const f = S.filt;
      if (f.impact.length && !f.impact.includes(it.impact)) return false;
      if (f.asset.length && !f.asset.some(a => (it.assets || []).includes(a))) return false;
      if (f.source && it.source !== f.source) return false;
      if (f.q) { const t = (it.title + ' ' + (it.summary || '')).toLowerCase(); if (!f.q.toLowerCase().split(/\s+/).every(w => t.includes(w))) return false; }
      return true;
    }
    root.addEventListener('click', e => {
      const chip = e.target.closest('.nd-chip[data-k]');
      if (chip) {
        const k = chip.dataset.k, v = chip.dataset.v, a = S.filt[k];
        S.filt[k] = a.includes(v) ? a.filter(x => x !== v) : a.concat(v);
        store.set('filt', S.filt); syncFilterUI(); renderFeed(); return;
      }
      const cal = e.target.closest('[data-cal]');
      if (cal) {
        if (cal.dataset.cal === 'high') { S.calHigh = !S.calHigh; store.set('calHigh', S.calHigh); }
        else { S.calRange = cal.dataset.cal; store.set('calRange', S.calRange); loadCal(); }
        renderCal(); return;
      }
      const tab = e.target.closest('.nd-tab'); if (tab) { setTab(tab.dataset.tab); return; }
      const act = e.target.closest('[data-a]');
      if (act) {
        if (act.dataset.a === 'sound') { S.sound = !S.sound; store.set('sound', S.sound); act.classList.toggle('on', S.sound); if (S.sound) beep(true); }
        if (act.dataset.a === 'refresh') { act.disabled = true; j('/refresh', { method: 'POST' }).catch(() => { }).finally(() => setTimeout(() => { act.disabled = false; loadSources(); }, 4000)); }
        return;
      }
      if (e.target.closest('[data-r="newbar"]')) { $('feedscroll').scrollTop = 0; S.unseen = 0; renderUnseen(); return; }
      if (e.target.closest('a')) return;
      const row = e.target.closest('.nd-row');
      if (row) { const id = row.dataset.id; S.open.has(id) ? S.open.delete(id) : S.open.add(id); row.classList.toggle('open'); }
    });
    let qT; $('fq').addEventListener('input', e => { clearTimeout(qT); qT = setTimeout(() => { S.filt.q = e.target.value.trim(); store.set('filt', S.filt); renderFeed(); }, 180); });
    $('fsrc').addEventListener('change', e => { S.filt.source = e.target.value; store.set('filt', S.filt); renderFeed(); });
    root.querySelector('[data-a="sound"]').classList.toggle('on', S.sound);

    // ---------- feed
    function upsert(it) {
      const had = S.items.has(it.id);
      S.items.set(it.id, it);
      if (!had) S.order.push(it.id);
      if (it.ins > S.lastIns) S.lastIns = it.ins;
      return !had;
    }
    function sortTrim() {
      S.order.sort((a, b) => S.items.get(b).ts - S.items.get(a).ts);
      while (S.order.length > Math.max(opts.limit, 100) * 2) S.items.delete(S.order.pop());
    }
    function dirHtml(d) {
      if (!d) return '';
      return Object.entries(d).map(([a, v]) => `<span class="nd-dir ${v === 'bullish' ? 'bull' : 'bear'}">${v === 'bullish' ? '▲' : '▼'} ${a === 'XAU' ? 'GOLD' : a}</span>`).join('');
    }
    function rowHtml(it, fresh) {
      const n = now(), open = S.open.has(it.id);
      const also = (it.also || []).length ? `<span class="nd-also">+${it.also.length} src</span>` : '';
      const ai = it.llm ? `<span class="nd-ai" title="LLM refined${it.llm.model ? ' · ' + esc(it.llm.model) : ''}">AI</span>` : '';
      const src = it.author && it.category !== 'aggregator' ? it.author : (it.author || it.source_name);
      return `<div class="nd-row ${it.impact}${fresh ? ' fresh' : ''}${open ? ' open' : ''}" data-id="${it.id}">
        <div class="nd-t" title="${esc(it.time_pkt)} · ${esc(it.time_utc)}">${hms(it.ts)}<span class="nd-ago" data-ts="${it.ts}">${ago(it.ts, n)}</span></div>
        <div class="nd-meta"><span class="nd-badge ${it.impact}">${it.impact === 'CRITICAL' ? 'CRIT' : it.impact === 'MEDIUM' ? 'MED' : it.impact}</span>
          <span class="nd-src" title="${esc(it.source_name)}"><i>${esc(it.label)}</i>${esc(src && src !== it.source_name ? src : '')}</span></div>
        <div class="nd-main"><div class="nd-title">${esc(it.title.replace(/^FinancialJuice:\s*/, ''))}</div>
          <div class="nd-tags">${(it.assets || []).map(a => `<span class="nd-tag ${a}">${a}</span>`).join('')}${dirHtml(it.direction)}${ai}${also}</div></div>
        <div class="nd-x"><div class="nd-why">▸ ${esc(it.why || '')}</div>${it.summary ? `<div>${esc(it.summary.slice(0, 600))}</div>` : ''}
          <div class="nd-xm"><span>${esc(it.source_name)}</span><span>${esc(it.time_pkt)}</span><span>score ${it.score}</span>
          ${(it.also || []).length ? `<span>also: ${esc(it.also.join(', '))}</span>` : ''}
          ${it.url ? `<a href="${esc(it.url)}" target="_blank" rel="noopener noreferrer">Open source ↗</a>` : ''}</div></div>
      </div>`;
    }
    function renderFeed() {
      const list = S.order.map(id => S.items.get(id)).filter(matches).slice(0, opts.limit);
      $('feed').innerHTML = list.length ? list.map(it => rowHtml(it, false)).join('') : `<div class="nd-empty">No items match these filters.</div>`;
      const f = S.filt, parts = [];
      if (f.impact.length) parts.push(f.impact.join('/')); if (f.asset.length) parts.push(f.asset.join('/'));
      if (f.source) parts.push(f.source); if (f.q) parts.push('“' + f.q + '”');
      $('feedsub').textContent = parts.length ? '· ' + parts.join(' · ') : '· all';
      $('feedcount').textContent = list.length + ' items';
    }
    function prepend(it) {
      if (!matches(it)) return;
      const box = $('feed'); const empty = box.querySelector('.nd-empty'); if (empty) empty.remove();
      // insert at the right time position (feeds can arrive slightly out of order)
      const rows = box.children; let ref = null;
      for (const r of rows) { const o = S.items.get(r.dataset.id); if (o && o.ts < it.ts) { ref = r; break; } }
      const tmp = document.createElement('div'); tmp.innerHTML = rowHtml(it, true);
      box.insertBefore(tmp.firstElementChild, ref);
      while (box.children.length > opts.limit) box.lastElementChild.remove();
      $('feedcount').textContent = box.children.length + ' items';
      const sc = $('feedscroll');
      if (sc.scrollTop > 60 || (root.classList.contains('nd-narrow') && root.dataset.tab !== 'feed')) { S.unseen++; renderUnseen(); }
    }
    function renderUnseen() {
      const b = $('newbar'), u = $('unseen');
      b.classList.toggle('show', S.unseen > 0); b.textContent = `▲ ${S.unseen} new item${S.unseen === 1 ? '' : 's'}`;
      u.style.display = S.unseen ? '' : 'none'; u.textContent = S.unseen;
    }
    function onNew(it, live) {
      const isNew = upsert(it);
      if (!isNew) { const row = $('feed').querySelector(`[data-id="${it.id}"]`); if (row) { const t = document.createElement('div'); t.innerHTML = rowHtml(it, false); row.replaceWith(t.firstElementChild); } return; }
      if (!live) return;
      prepend(it);
      el.dispatchEvent(new CustomEvent('newsdesk:item', { detail: it }));
      if (it.impact === 'CRITICAL' && now() - it.ts < 900) {
        if (S.sound) beep(); if (typeof opts.onCritical === 'function') try { opts.onCritical(it); } catch (e) { }
      }
    }
    async function loadFeed() {
      const d = await j('/feed?limit=' + opts.limit); syncClock(d.server_time);
      d.items.forEach(it => upsert(it)); sortTrim(); renderFeed();
    }
    async function poll() {
      try {
        const d = await j('/feed?limit=200&since=' + (S.lastIns || 0)); syncClock(d.server_time);
        d.items.sort((a, b) => a.ts - b.ts).forEach(it => onNew(it, true)); sortTrim();
        if (S.mode !== 'sse') setMode('poll');
      } catch (e) { if (S.mode !== 'sse') setMode('off'); }
    }

    // ---------- SSE with polling fallback
    function setMode(m) {
      S.mode = m; const l = $('live'); l.className = 'nd-live ' + m;
      $('livetxt').textContent = m === 'sse' ? 'LIVE' : m === 'poll' ? 'POLLING' : 'OFFLINE';
    }
    function startPolling() { if (!S.pollT) S.pollT = setInterval(poll, 10000); }
    function stopPolling() { clearInterval(S.pollT); S.pollT = null; }
    function connect() {
      if (S.dead || !global.EventSource) { startPolling(); return; }
      try { if (S.es) S.es.close(); } catch (e) { }
      const es = new EventSource(API + '/stream'); S.es = es;
      let opened = false;
      es.addEventListener('hello', e => { opened = true; setMode('sse'); stopPolling(); try { syncClock(JSON.parse(e.data).server_time); } catch (x) { } poll(); });
      es.addEventListener('item', e => { try { const it = JSON.parse(e.data); onNew(it, true); } catch (x) { } });
      es.addEventListener('update', e => { try { onNew(JSON.parse(e.data), true); } catch (x) { } });
      es.addEventListener('calendar', () => loadCal());
      es.addEventListener('sentiment', e => { try { S.sent = JSON.parse(e.data); renderGauges(); } catch (x) { } });
      es.onerror = () => {
        if (S.dead) return;
        if (es.readyState === 2 || !opened) {          // closed / never opened: fall back to polling, retry SSE later
          try { es.close(); } catch (x) { }
          setMode(S.mode === 'off' ? 'off' : 'poll'); startPolling(); poll();
          S.timers.push(setTimeout(connect, 30000));
        } else { setMode('poll'); startPolling(); }    // browser auto-reconnects; poll meanwhile
      };
    }

    // ---------- sentiment gauges
    function gauge(name, score, label, sub, right, short) {
      const pos = Math.max(0, Math.min(100, (score + 100) / 2));
      const cls = score > 20 ? 'bull' : score < -20 ? 'bear' : 'neu';
      return `<div class="nd-g"><div class="nd-g-h"><span><span class="nd-gl">${name}</span><span class="nd-gs">${short || name}</span></span><b class="${cls}">${label}</b></div>
        <div class="nd-g-bar"><span class="nd-g-n" style="left:${pos}%"></span></div>
        <div class="nd-g-s"><span>${sub}</span><span>${right || ''}</span></div></div>`;
    }
    function renderGauges() {
      const s = S.sent; if (!s) return;
      const g = (k, n, sh) => { const v = s[k] || { score: 0, n: 0, bull: 0, bear: 0 }; return gauge(n, v.score, (v.score > 0 ? '+' : '') + v.score + ' ' + (v.label || '').toUpperCase(), `▲${v.bull} ▼${v.bear} · ${s.window_h}h`, v.n + ' signals', sh); };
      const f = s.crypto_fear_greed;
      const fg = f ? gauge('Crypto Fear & Greed', (f.value - 50) * 2, f.value + ' ' + f.label.toUpperCase(), 'prev ' + (f.prev ?? '–'), 'alternative.me', 'F&G') : gauge('Crypto Fear & Greed', 0, '—', 'unavailable', '');
      $('gauges').innerHTML = g('XAU', 'Gold news bias', 'GOLD') + g('BTC', 'BTC news bias', 'BTC') + fg;
    }
    async function loadSent() { try { S.sent = await j('/sentiment'); renderGauges(); } catch (e) { } }

    // ---------- calendar
    async function loadCal() {
      try { const d = await j('/calendar?range=' + (S.calRange === 'today' ? 'today' : 'week')); syncClock(d.server_time); S.cal = d.events; S.next = d.next_high; renderCal(); renderNext(); } catch (e) { $('cal').innerHTML = `<div class="nd-empty">Calendar unavailable (${esc(e.message)})</div>`; }
    }
    function renderCal() {
      root.querySelectorAll('[data-cal]').forEach(b => b.classList.toggle('on', b.dataset.cal === 'high' ? S.calHigh : b.dataset.cal === S.calRange));
      let ev = S.cal.filter(e => !S.calHigh || e.impact === 'High');
      if (!ev.length) { $('cal').innerHTML = `<div class="nd-empty">No ${S.calHigh ? 'high-impact ' : ''}events ${S.calRange === 'today' ? 'today' : 'this week'}.</div>`; return; }
      const n = now(), today = dayKey(n); let html = '', lastDay = '';
      const nextId = S.next && S.next.id;
      for (const e of ev) {
        const dk = dayKey(e.ts);
        if (dk !== lastDay) { html += `<div class="nd-cal-day${dk === today ? ' today' : ''}">${dayLabel(e.ts)}${dk === today ? ' · today' : ''} <span style="float:right">PKT</span></div>`; lastDay = dk; }
        const until = e.ts - n;
        const act = e.actual ? `<span>A <b class="${e.result || ''}">${esc(e.actual)}</b></span>` : (until < 0 && until > -7200 && e.impact !== 'Holiday' ? '<span>A <b>…</b></span>' : '');
        html += `<div class="nd-ev${e.past ? ' past' : ''}${e.id === nextId ? ' next' : ''}" title="${esc(e.time_pkt)}${e.actual_src ? ' · actual via ' + esc(e.actual_src) : ''}">
          <span class="nd-et">${hm(e.ts)}</span><span class="nd-cc">${esc(e.country)}</span><span class="nd-imp ${esc(e.impact)}" title="${esc(e.impact)}"></span>
          <span class="nd-en">${e.url ? `<a href="${esc(e.url)}" target="_blank" rel="noopener" style="color:inherit">${esc(e.title)}</a>` : esc(e.title)}${until > 0 && until < 3 * 3600 ? ` <span class="nd-in-t" data-cd="${e.ts}">in ${countdown(until)}</span>` : ''}</span>
          ${(e.forecast || e.previous || act) ? `<span class="nd-nums">${act}<span>F <b>${esc(e.forecast || '–')}</b></span><span>P <b>${esc(e.previous || '–')}</b></span></span>` : ''}
        </div>`;
      }
      const sc = $('cal'), keep = sc.scrollTop; sc.innerHTML = html;
      if (!S._calScrolled) { const nx = sc.querySelector('.nd-ev.next') || sc.querySelector('.nd-ev:not(.past)'); if (nx) sc.scrollTop = Math.max(0, nx.offsetTop - 60); S._calScrolled = true; } else sc.scrollTop = keep;
    }
    function renderNext() {
      const x = S.next;
      if (!x) { $('nextev').textContent = 'none left this week'; $('nextcd').textContent = ''; return; }
      $('nextev').textContent = `${x.country} ${x.title} · ${hm(x.ts)}`;
      $('nextcd').textContent = countdown(x.ts - now());
    }

    // ---------- sources
    async function loadSources() {
      try {
        const d = await j('/sources'); S.sources = d; syncClock(d.server_time);
        const sel = $('fsrc'), cur = S.filt.source;
        sel.innerHTML = '<option value="">All sources</option>' + d.sources.filter(s => s.items_total > 0 && s.kind !== 'ff_cal' && s.kind !== 'fng')
          .sort((a, b) => a.name.localeCompare(b.name)).map(s => `<option value="${esc(s.id)}">${esc(s.name)} (${s.items_24h || 0})</option>`).join('');
        sel.value = cur;
        renderSources();
      } catch (e) { $('src').innerHTML = `<div class="nd-empty">Source health unavailable (${esc(e.message)})</div>`; }
    }
    function renderSources() {
      const d = S.sources; if (!d) return; const n = now();
      const s = d.summary; $('srcsum').textContent = `· ${s.ok}/${s.total} ok · ${s.fail} failed${s.stale ? ' · ' + s.stale + ' stale' : ''}${d.llm && d.llm.enabled ? ' · AI ' + d.llm.ok + '/' + d.llm.calls : ''}`;
      const rank = { ok: 0, stale: 1, pending: 2, fail: 3 };
      const rows = d.sources.slice().sort((a, b) => (rank[a.status] - rank[b.status]) || ((b.items_24h || 0) - (a.items_24h || 0)));
      $('src').innerHTML = `<div class="nd-srow nd-shead"><span></span><span>source</span><span class="nd-r">latency</span><span class="nd-r">fetched</span><span class="nd-r">24h</span></div>` +
        rows.map(x => `<div class="nd-srow ${x.status}" title="${esc(x.name)} · every ${x.interval_s}s · ${esc(x.status)}${x.via ? ' · via ' + esc(x.via) : ''}${x.error ? '\n' + esc(x.error) : ''}">
          <span class="nd-sdot ${x.status}"></span><span class="nd-sn">${esc(x.name)}</span>
          <span class="nd-r">${x.latency_ms != null ? x.latency_ms + 'ms' : '–'}</span>
          <span class="nd-r">${x.last_fetch ? ago(x.last_fetch, n).replace(' ago', '') : '–'}</span>
          <span class="nd-r">${x.kind === 'ff_cal' ? x.items_total + 'ev' : (x.items_24h || 0)}</span>
          ${x.status === 'fail' && x.error ? `<span></span><span class="nd-se">${esc(x.error)}</span>` : ''}</div>`).join('');
    }

    // ---------- sound (WebAudio, no asset files)
    function beep(test) {
      try {
        S.audio = S.audio || new (global.AudioContext || global.webkitAudioContext)();
        const ctx = S.audio, t0 = ctx.currentTime;
        [[880, 0], [660, 0.16], [880, 0.32]].slice(0, test ? 1 : 3).forEach(([f, dt]) => {
          const o = ctx.createOscillator(), g = ctx.createGain(); o.type = 'square'; o.frequency.value = f;
          g.gain.setValueAtTime(0.0001, t0 + dt); g.gain.exponentialRampToValueAtTime(0.12, t0 + dt + 0.01); g.gain.exponentialRampToValueAtTime(0.0001, t0 + dt + 0.14);
          o.connect(g).connect(ctx.destination); o.start(t0 + dt); o.stop(t0 + dt + 0.15);
        });
      } catch (e) { }
    }

    // ---------- tickers
    function tick() {
      const n = now();
      $('clock').textContent = hms(n) + ' PKT';
      root.querySelectorAll('.nd-ago[data-ts]').forEach(x => { x.textContent = ago(+x.dataset.ts, n); });
      root.querySelectorAll('[data-cd]').forEach(x => { x.textContent = 'in ' + countdown(+x.dataset.cd - n); });
      if (S.next) { const left = S.next.ts - n; $('nextcd').textContent = countdown(left); if (left < -60) { S.next = null; loadCal(); } }
    }

    // ---------- boot
    syncFilterUI();
    S.timers.push(setInterval(tick, 1000));
    S.timers.push(setInterval(loadSources, 20000));
    S.timers.push(setInterval(loadCal, 120000));
    S.timers.push(setInterval(loadSent, 60000));
    Promise.allSettled([loadFeed(), loadCal(), loadSources(), loadSent()]).then(r => {
      if (r[0].status === 'rejected') { $('feed').innerHTML = `<div class="nd-empty">News API unreachable at ${esc(API)} — retrying…</div>`; setMode('off'); }
      connect();
    });
    tick();

    return {
      root,
      refresh: () => j('/refresh', { method: 'POST' }),
      setFilter(f) { Object.assign(S.filt, f); store.set('filt', S.filt); syncFilterUI(); renderFeed(); },
      setTab,
      destroy() {
        S.dead = true; S.timers.forEach(t => { clearInterval(t); clearTimeout(t); }); stopPolling(); ro.disconnect();
        try { S.es && S.es.close(); } catch (e) { } try { S.audio && S.audio.close(); } catch (e) { }
        root.remove();
      }
    };
  }

  global.NewsDesk = { mount, version: '1.0.0' };
})(window);
