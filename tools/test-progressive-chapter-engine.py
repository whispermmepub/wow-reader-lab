#!/usr/bin/env python3
"""Deterministic host-Chromium contract tests for progressive page pagination.

This is a finite desktop-host test.  It does not claim Android/WebView/device
coverage.  Run after the producer host fixtures have been refreshed.
"""
from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_FIXTURES = ROOT / "app" / "build" / "reader-engine-fixtures"

BRIDGE = r"""(() => {
  window.readerEvents = [];
  window.WoW = new Proxy({}, {get: (_, name) => (...args) =>
    window.readerEvents.push({name: String(name), args, at: performance.now()})});
  return true;
})()"""

SVG = "<svg width='180' height='38' viewBox='0 0 180 38' aria-label='fixture'><rect width='178' height='36' fill='#ddd' stroke='#333'/><circle cx='18' cy='18' r='11' fill='#357'/><path d='M40 28 L65 7 L90 28' fill='none' stroke='#a24' stroke-width='4'/><text x='101' y='24' font-size='12'>media</text></svg>"


def fixture() -> str:
    # Each span is a distinct text node.  The tail sentinel is deliberately in
    # a mixed-media section, so media and text traversal both have to finish.
    parts = ["<!doctype html><html><head><meta charset='utf-8'><style>html,body{font-size:16px}body{line-height:1.65;margin:0}section{break-inside:auto}p{margin:7px 0}img,svg{display:block;width:180px;height:38px;margin:5px 0}figure{margin:8px 0}</style></head><body><h1>Progressive fixture</h1>"]
    for i in range(26050):
        if i % 173 == 0:
            parts.append(f"<section data-chapter='{i // 173}'><figure>{SVG}<img alt='pixel' src='data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%22180%22 height=%2238%22%3E%3Crect width=%22180%22 height=%2238%22 fill=%22%23ccd%22/%3E%3C/svg%3E'></figure><p>")
        parts.append(f"<span>{i} deterministic chapter text </span>")
        if i % 173 == 172:
            parts.append("</p></section>")
    parts.append("<section id='tail-chapter'><figure>" + SVG + "</figure><p id='tail-sentinel'>FINAL-REACHABILITY-SENTINEL</p></section></body></html>")
    return "".join(parts)


def delayed_channel(page) -> None:
    page.evaluate(r"""(() => {
      const Native = window.MessageChannel;
      window.collectorYields = 0;
      window.MessageChannel = function() {
        const c = new Native();
        const post = c.port2.postMessage.bind(c.port2);
        c.port2.postMessage = value => {
          window.collectorYields++;
          return setTimeout(() => post(value), 12);
        };
        return c;
      };
      return true;
    })()""")


def wait_event(page, name: str, timeout: int = 20000) -> None:
    page.wait_for_function("n => readerEvents.some(e => e.name === n)", arg=name, timeout=timeout, polling=10)


def state(page) -> dict:
    return page.evaluate(r"""() => {
      const s = window.__wowPageEngine || {};
      return {count:s.count || 0, page:s.page || 0, locked:!!s.locked,
        complete:s.paginationComplete, cancelled:!!s.cancelled,
        epoch:s.measureEpoch, progress:typeof s.progress==='function'?s.progress():null,
        map:Array.isArray(s.pageMap)?s.pageMap.slice():[], yields:window.collectorYields||0};
    }""")


def new_page(context, html: str):
    page = context.new_page()
    errors = []
    page.on("pageerror", lambda e: errors.append(str(e)))
    page.set_content(html)
    page.evaluate(BRIDGE)
    delayed_channel(page)
    return page, errors


def usable(page) -> list[dict]:
    return page.evaluate("readerEvents.filter(e => e.name === 'onPageUsable')")


def run(fixtures: Path, chromium: str) -> list[dict]:
    script = (fixtures / "page.js").read_text()
    html = fixture()
    results: list[dict] = []

    def case(name: str, fn):
        fn()
        results.append({"case": name, "pass": True})

    with sync_playwright() as pw:
        browser = pw.chromium.launch(executable_path=chromium, headless=True, args=["--no-sandbox"])
        context = browser.new_context(viewport={"width": 420, "height": 760})
        context.route("**/*", lambda route: route.abort())

        page, errors = new_page(context, html)
        started = time.monotonic(); page.evaluate(script)
        page.wait_for_function("() => __wowPageEngine && __wowPageEngine.paginationComplete === false", timeout=5000)
        wait_event(page, "onPageUsable")
        early_ms = round((time.monotonic() - started) * 1000)
        early = usable(page)
        assert early and early[0]["args"][0] == -7, early[:1]
        assert page.evaluate("readerEvents.some(e => e.name === 'onPageReady')") is False
        assert state(page)["complete"] is False
        wait_event(page, "onPageReady")
        final = state(page)
        assert final["complete"] is True and final["count"] > 1 and final["progress"] == 0, final
        assert not page.evaluate("readerEvents.some(e => e.name === 'pageEngineFailed')"), errors
        case("early-usable-before-full-map", lambda: None)
        results[-1].update({"early_ms": early_ms, "usable_count": len(early), "pages": final["count"], "yields": final["yields"]})

        page.close()

        # Forward at the incomplete frontier is pending, never a chapter handoff;
        # reverse at logical page zero is an immediate chapter request.  Both
        # calls occur before the delayed collector has reached its full map.
        page, errors = new_page(context, html)
        page.evaluate(script)
        page.wait_for_function("() => __wowPageEngine && __wowPageEngine.paginationComplete === false")
        page.evaluate("__wowPageEngine.locked=false;__wowPageEngine.page=Math.max(0,__wowPageEngine.count-1);__wowPageEngine.apply(false)")
        assert page.evaluate("__wowPageEngine.turn(1)") == "pending"
        assert not page.evaluate("readerEvents.some(e => e.name === 'requestChapter')")
        page.evaluate("__wowPageEngine.locked=false;__wowPageEngine.page=0;__wowPageEngine.apply(false)")
        assert page.evaluate("__wowPageEngine.turn(-1)") == "chapter"
        assert page.evaluate("readerEvents.some(e => e.name === 'requestChapter' && e.args[1] === -1)")
        case("fast-forward-pending-frontier-and-reverse-start", lambda: None)
        page.close()

        # Real turns while the map grows must retain chapter coordinates.
        page, errors = new_page(context, html)
        page.evaluate(script); wait_event(page, "onPageUsable")
        page.wait_for_function("() => __wowPageEngine.count >= 4 && !__wowPageEngine.paginationComplete")
        turns = page.evaluate("""() => { let s=__wowPageEngine;let out=[];
          for (let d of [1,1,-1,1,-1,1]) out.push(s.turn(d));
          window.expectedPhysical=s.physical();return out; }""")
        assert all(t == "page" for t in turns), turns
        wait_event(page, "onPageReady")
        assert page.evaluate("__wowPageEngine.physical()===expectedPhysical"), state(page)
        assert not page.evaluate("readerEvents.some(e=>e.name==='requestChapter')")
        case("fast-forward-reverse-retains-physical-page-through-completion", lambda: None)
        page.close()

        # A restore at the end must wait for the completed map and retain its
        # last page, not expose a partial tail.
        page, errors = new_page(context, html)
        page.evaluate("window.restoreReady=[]")
        page.evaluate("""() => { window.__wowInitialRestore = 1; }""")
        page.evaluate(script)
        page.evaluate("__wowPageEngine.pendingRestoreRatio=1")
        wait_event(page, "onPageReady")
        s = state(page)
        assert s["page"] == s["count"] - 1 and s["progress"] == 1000 and s["complete"] is True, s
        assert not usable(page), usable(page)
        case("pending-restore-ratio-one-waits-for-final-page", lambda: None)
        page.close()

        # Adopting an already partially scanned warm chapter must not mistake
        # the current prefix for Previous's true chapter end.
        page, errors = new_page(context, html)
        page.evaluate(script); wait_event(page, "onPageUsable")
        page.evaluate("readerEvents=[]")
        assert page.evaluate((fixtures / "promote-ready-end.js").read_text()) is True
        assert not page.evaluate("readerEvents.some(e=>e.name==='onPageReady')")
        wait_event(page, "onPageReady")
        assert page.evaluate("__wowPageEngine.generation===3&&__wowPageEngine.progress()===1000")
        case("partially-ready-warm-previous-waits-for-authoritative-end", lambda: None)
        page.close()

        # A same-chapter seek/fragment chosen during scanning is resolved only
        # against the authoritative map, not a guessed prefix endpoint.
        page, errors = new_page(context, html)
        page.evaluate(script); wait_event(page, "onPageUsable")
        assert page.evaluate("__wowPageEngine.goToFragment('tail-sentinel')") is True
        wait_event(page, "onPageReady")
        assert page.evaluate("(()=>{let r=document.getElementById('tail-sentinel').getBoundingClientRect();return r.left>=-2&&r.right<=innerWidth+2&&r.top<innerHeight;})()")
        case("partial-fragment-jump-defers-until-full-map", lambda: None)
        page.close()

        page, errors = new_page(context, html)
        page.evaluate(script); wait_event(page, "onPageUsable")
        page.evaluate("__wowPageEngine.pendingRestoreRatio=0.75;__wowPageEngine.locked=true")
        wait_event(page, "onPageReady")
        assert page.evaluate("__wowPageEngine.page===Math.round((__wowPageEngine.count-1)*0.75)")
        case("partial-seek-ratio-restores-against-final-count", lambda: None)
        page.close()

        # Simulate the native first-book rejection arriving after the collector
        # finishes: the final map/readiness must not be discarded by its lock.
        page, errors = new_page(context, html)
        page.evaluate(script); wait_event(page, "onPageUsable")
        assert page.evaluate("__wowPageEngine.turn(-1)") == "chapter"
        wait_event(page, "onPageReady")
        page.evaluate("__wowPageEngine.boundaryRequested=false;__wowPageEngine.locked=false")
        assert page.evaluate("__wowPageEngine.turn(1)") == "page"
        case("rejected-boundary-race-does-not-drop-completed-map", lambda: None)
        page.close()

        # Explicit cancellation settles its promise/channel immediately and
        # invalidates the epoch; no late readiness may be emitted.
        page, errors = new_page(context, html)
        page.evaluate(script)
        page.wait_for_function("() => __wowPageEngine && __wowPageEngine.paginationComplete === false")
        page.wait_for_function("() => typeof __wowPageEngine.cancelCollect === 'function'", timeout=5000)
        page.evaluate("__wowPageEngine.cancelled=true;__wowPageEngine.measureEpoch++;__wowPageEngine.cancelCollect()")
        assert page.evaluate("__wowPageEngine.cancelCollect === null")
        page.wait_for_timeout(100)
        s = state(page)
        assert s["cancelled"] is True and not page.evaluate("readerEvents.some(e=>e.name==='onPageReady')"), s
        case("cancel-collect-closes-channel-and-no-stale-ready", lambda: None)
        page.close()

        # Superseding an epoch while a scan is delayed must suppress all stale
        # readiness, then a fresh measure is allowed to become authoritative.
        page, errors = new_page(context, html)
        page.evaluate(script)
        page.wait_for_function("() => __wowPageEngine && __wowPageEngine.paginationComplete === false")
        old_epoch = page.evaluate("__wowPageEngine.measureEpoch")
        page.evaluate("__wowPageEngine.measureEpoch++;__wowPageEngine.cancelled=false")
        page.wait_for_timeout(180)
        assert not page.evaluate("readerEvents.some(e=>e.name==='onPageReady')")
        page.evaluate("__wowPageEngine.measure(0)")
        wait_event(page, "onPageReady")
        assert page.evaluate("__wowPageEngine.measureEpoch") > old_epoch
        case("superseded-epoch-has-no-stale-ready-and-recovers", lambda: None)
        page.close()

        # Failure must unlock before reporting, and a subsequent clean measure
        # must recover rather than remain trapped.
        page, errors = new_page(context, html)
        page.evaluate("window.nativeRange=document.createRange.bind(document); document.createRange=()=>{throw new Error('progressive fixture range failure')};true")
        page.evaluate(script)
        wait_event(page, "pageEngineFailed")
        failure = page.evaluate("readerEvents.find(e=>e.name==='pageEngineFailed')")
        assert failure and page.evaluate("!__wowPageEngine.locked"), failure
        page.evaluate("document.createRange=nativeRange;__wowPageEngine.measure(0)")
        wait_event(page, "onPageReady")
        assert state(page)["complete"] is True
        case("failure-unlocks-before-error-and-clean-measure-recovers", lambda: None)
        page.close()

        # Compare a fresh full traversal with the map installed by the initial
        # progressive scan and verify the mixed-media tail remains reachable.
        page, errors = new_page(context, html)
        page.evaluate(script); wait_event(page, "onPageReady")
        comparison = page.evaluate(r"""async () => {
          const before = __wowPageEngine.pageMap.slice();
          const fresh = await __wowPageEngine.collectPageMap(__wowPageEngine.measureEpoch);
          __wowPageEngine.page=__wowPageEngine.count-1;__wowPageEngine.apply(false);
          const r=document.getElementById('tail-sentinel').getBoundingClientRect();
          return {same:JSON.stringify(before)===JSON.stringify(fresh),before:before.length,fresh:fresh.length,
            reachable:r.left>=-2&&r.right<=innerWidth+2&&r.top>=-2&&r.top<innerHeight,
            progress:__wowPageEngine.progress()};
        }""")
        assert comparison["same"] and comparison["reachable"] and comparison["progress"] == 1000, comparison
        case("mixed-media-final-map-equals-standalone-and-tail-reachable", lambda: None)
        page.close()

        # A channel can exist yet drop messages on an OEM runtime. The bounded
        # timer fallback must still complete (separate from missing-channel tests).
        page, errors = new_page(context, "<body>" + "<p>fallback text</p>" * 600 + "</body>")
        page.evaluate("window.MessageChannel=function(){this.port1={close:function(){}};this.port2={postMessage:function(){},close:function(){}}};true")
        page.evaluate(script); wait_event(page, "onPageReady", 6000)
        assert state(page)["complete"] is True and not state(page)["locked"]
        case("existing-but-dropped-message-channel-recovers-via-timer", lambda: None)
        page.close()

        browser.close()
    return results


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixtures", type=Path, default=DEFAULT_FIXTURES)
    parser.add_argument("--chromium", default="/usr/bin/chromium")
    parser.add_argument("--json", type=Path)
    args = parser.parse_args()
    results = run(args.fixtures, args.chromium)
    report = {"cases": len(results), "failures": 0, "results": results,
              "physicalDeviceTested": False,
              "limitation": "Finite host Chromium contract test only; no Android/WebView/device claim."}
    output = json.dumps(report, indent=2)
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(output + "\n")
    print(output)
