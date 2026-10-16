"""Screenshots of every view at desktop and phone width (Playwright + Chromium).
Usage: python scripts/shoot.py [--url http://127.0.0.1:8702] [--out docs/screens] [view ...]
Env CHROME=<path to chrome> to use a specific Chromium build."""
import argparse, asyncio, os

VIEWS = ["dashboard", "indicators", "tradingview", "analysis", "settings"]
WAIT = {"dashboard": 14000, "tradingview": 12000, "indicators": 9000, "analysis": 9000, "settings": 4000}


async def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="http://127.0.0.1:8702")
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "..", "docs", "screens"))
    ap.add_argument("--tabs", action="store_true", help="also shoot each Market Analysis sub-tab")
    ap.add_argument("views", nargs="*")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    from playwright.async_api import async_playwright
    async with async_playwright() as p:
        kw = dict(args=["--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage"])
        if os.getenv("CHROME"): kw["executable_path"] = os.getenv("CHROME")
        if os.getenv("SHOT_PROXY"):            # e.g. a sandbox whose egress needs an HTTP proxy (then shoot a public URL)
            from urllib.parse import urlsplit
            u = urlsplit(os.getenv("SHOT_PROXY"))
            kw["proxy"] = dict(server=f"http://{u.hostname}:{u.port}", username=u.username or "", password=u.password or "")
        b = await p.chromium.launch(**kw)
        for name, vp, mob in (("desktop", dict(width=1600, height=1000), False), ("phone", dict(width=390, height=844), True)):
            ctx = await b.new_context(viewport=vp, device_scale_factor=1 if not mob else 2, is_mobile=mob, has_touch=mob)
            pg = await ctx.new_page(); errs = []
            pg.on("pageerror", lambda e: errs.append(str(e)))
            pg.on("console", lambda m: errs.append("console:" + m.text) if m.type == "error" else None)
            await pg.goto(a.url + "/#/dashboard", wait_until="domcontentloaded")
            jobs = [(v, None) for v in (a.views or VIEWS)]
            if a.tabs:
                jobs += [("analysis", t) for t in ("volume", "pressure", "bulls", "bias")]
            for v, tab in jobs:
                if tab:
                    await pg.evaluate(f"localStorage.setItem('hq.an.tab','{tab}')")
                    await pg.evaluate("location.hash='#/dashboard'"); await pg.wait_for_timeout(300)
                await pg.evaluate(f"location.hash='#/{v}'")
                await pg.wait_for_timeout(WAIT.get(v, 5000))
                h = await pg.evaluate("document.querySelector('#main').scrollHeight")
                full = v in ("settings", "analysis") or (mob and v == "dashboard")
                if full:
                    await pg.set_viewport_size(dict(width=vp["width"], height=min(max(h + 140, vp["height"]), 6000)))
                    await pg.wait_for_timeout(2500)
                ov = await pg.evaluate("""() => { const W = document.documentElement.clientWidth; const bad = [];
                    document.querySelectorAll('#main *').forEach(n => { const r = n.getBoundingClientRect(); if (r.width && r.right > W + 2 && !n.closest('.tk-track,.tvbox,iframe,.tbl-wrap,table,[style*="overflow"]')) bad.push(n.tagName + '.' + n.className); });
                    return {docW: document.documentElement.scrollWidth, W, bad: bad.slice(0, 8)}; }""")
                fn = f"{v}{'-' + tab if tab else ''}_{name}.png"
                await pg.screenshot(path=os.path.join(a.out, fn))
                await pg.set_viewport_size(vp)
                print(fn, "overflow:", ov, "errors:", errs[-4:] if errs else "none", flush=True); errs.clear()
            await ctx.close()
        await b.close()

asyncio.run(main())
