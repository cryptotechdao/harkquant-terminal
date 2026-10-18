/* HarkQuant Terminal service worker (same pattern as Hark Titans): caches the app shell only (never API data). Network-first so updates load,
   cache fallback so the window still opens when the server is down (it then shows "offline"). */
"use strict";
const CACHE = "hqterm-shell-v2";
const SHELL = [
  "/", "/manifest.webmanifest",
  "/assets/app.css", "/assets/core.js", "/assets/tv.js", "/assets/ticker.js", "/assets/views.js", "/assets/app.js", "/news/news.js", "/news/news.css",
  "/vendor/lightweight-charts.standalone.production.js",
  "/icons/icon-192.png", "/icons/icon-512.png", "/icons/icon-maskable-512.png", "/icons/favicon-32.png"
];
self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});
self.addEventListener("fetch", (e) => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;
  const isShell = (req.mode === "navigate" && url.pathname === "/") || SHELL.includes(url.pathname);
  if (!isShell) return;                       // API calls, streams: straight to the network, never cached
  const key = req.mode === "navigate" ? "/" : url.pathname;
  e.respondWith(
    fetch(req).then((res) => {
      if (res.ok) { const copy = res.clone(); caches.open(CACHE).then((c) => c.put(key, copy)); }
      return res;
    }).catch(() => caches.match(key))
  );
});
