#!/usr/bin/env python3
"""Exercise scripts exported by ChapterTransitionIntegrationTest in local Chromium.

Run the host test first, then this file. This is a DOM/layout regression, not an
Android WebView/OEM performance benchmark. No book files or network are required.
Requires Python playwright and a local Chromium executable.
"""
import argparse
import json
import time
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
BRIDGE = """(()=>{window.readerEvents=[];window.WoW=new Proxy({}, {get:function(_,name){return function(){readerEvents.push({name:String(name),args:Array.from(arguments),at:performance.now()});};}});return true;})()"""
TEXT = "A consistent reader keeps every chapter at the chosen size and spacing. " * 8
PARAGRAPHS = "".join(f'<p style="font-size:27px!important;margin-left:39px!important;padding-right:21px!important">{i}. {TEXT}<span style="font-size:220%!important">{TEXT}</span> <strong>bold</strong><em>italic</em></p>' for i in range(18))


def fixture(variant):
    size = "9px" if variant == "small" else "31px"
    return f'''<!doctype html><html><head><meta charset="utf-8"><style>
html{{font-size:{size};}}body{{font-size:{size};}}p{{font-size:{size};line-height:3.1;}}
h1{{font-size:32px;}}pre{{font-size:12px;}}svg{{display:block;}}
</style></head><body><h1>Heading <span>preserved</span></h1>{PARAGRAPHS}<p>Subscript H<sub>2</sub>O, x<sup>2</sup>.</p><pre><code>code stays code</code></pre><svg width="100" height="24"><rect width="90" height="20" /></svg></body></html>'''


def wait_ready(page, mode, timeout=5000):
    name = "onPageReady" if mode == "page" else "onScrollReady"
    page.wait_for_function("name=>readerEvents.some(e=>e.name===name)", arg=name, timeout=timeout)
    # Since v79, onPageReady can announce a usable prefix before the final map.
    # This legacy suite checks final counts/end promotion, not first-page latency.
    # Progressive-first-page behavior is covered by its dedicated harness.
    if mode == "page":
        page.wait_for_function("window.__wowPageEngine && __wowPageEngine.paginationComplete === true", timeout=timeout)
    errors = page.evaluate("readerEvents.filter(e=>e.name==='pageEngineFailed')")
    assert not errors, errors


def metrics(page):
    return page.evaluate("""() => {var p=document.querySelector('p'),s=p.querySelector('span'),h=document.querySelector('h1'),flow=document.getElementById('wow-page-flow'),st=window.__wowPageEngine;var cs=getComputedStyle(p);return {font:cs.fontSize,spanFont:getComputedStyle(s).fontSize,line:cs.lineHeight,left:cs.marginLeft,rightPadding:cs.paddingRight,align:cs.textAlign,heading:getComputedStyle(h).fontSize,code:getComputedStyle(document.querySelector('code')).fontSize,bold:getComputedStyle(document.querySelector('strong')).fontWeight,italic:getComputedStyle(document.querySelector('em')).fontStyle,count:st.count||0,mode:st.mode,width:flow?flow.getBoundingClientRect().width:document.body.clientWidth,generation:st.generation};} """)


def run(fixtures_dir, chromium):
    scripts = {mode: (fixtures_dir / f"{mode}.js").read_text() for mode in ("page", "scroll")}
    results = []
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=chromium, headless=True, args=["--no-sandbox"])
        context = browser.new_context(viewport={"width": 400, "height": 800})
        context.route("**/*", lambda route: route.abort())
        for mode in ("page", "scroll"):
            chapter_metrics = []
            for variant in ("small", "large"):
                page = context.new_page()
                errors = []
                page.on("pageerror", lambda error: errors.append(str(error)))
                page.set_content(fixture(variant))
                page.evaluate(BRIDGE)
                start = time.monotonic()
                page.evaluate(scripts[mode])
                wait_ready(page, mode)
                ready_ms = round((time.monotonic()-start)*1000)
                m = metrics(page)
                assert m["font"] == m["spanFont"] == "16px", m
                assert m["line"] == "28.8px", m
                assert m["left"] == "39px" and m["rightPadding"] == "21px", m
                assert m["align"] == "left", m
                assert m["heading"] == "32px" and m["code"] == "12px", m
                assert int(m["bold"]) >= 600 and m["italic"] == "italic", m
                assert m["generation"] == -7, m
                assert not errors, errors
                if mode == "page":
                    assert m["count"] > 2, m
                    # Promotion restores previous chapter's last logical page without measuring again.
                    epochs = page.evaluate("__wowPageEngine.measureEpoch")
                    assert page.evaluate((fixtures_dir/"promote-ready-end.js").read_text()) is True
                    assert page.evaluate("__wowPageEngine.progress()") == 1000
                    assert page.evaluate("__wowPageEngine.measureEpoch") == epochs
                    assert page.evaluate("__wowPageEngine.turn(-1)") == "page"
                    assert not page.evaluate("__wowPageEngine.locked")
                    page.evaluate("var st=__wowPageEngine;st.page=0;st.apply(false);")
                    assert page.evaluate("__wowPageEngine.turn(-1)") == "chapter"
                    assert page.evaluate("readerEvents.some(e=>e.name==='requestChapter'&&e.args[0]===3&&e.args[1]===-1)")
                    page.evaluate("__wowPageEngine.locked=false")
                    page.set_viewport_size({"width": 440, "height": 800})
                    page.wait_for_timeout(400)
                    assert not page.evaluate("__wowPageEngine.locked")
                chapter_metrics.append(m)
                results.append({"case": f"{mode}-{variant}-publisher-css", "ready_ms": ready_ms, "pages": m["count"], "pass": True})
                page.close()
            assert chapter_metrics[0]["count"] == chapter_metrics[1]["count"], chapter_metrics

        # A never-resolving EPUB font cannot trap either reading mode forever.
        for mode in ("page", "scroll"):
            page = context.new_page()
            page.set_content(fixture("small"))
            page.evaluate(BRIDGE)
            page.evaluate("Object.defineProperty(document,'fonts',{value:{ready:new Promise(function(){})}})")
            start=time.monotonic()
            page.evaluate(scripts[mode])
            wait_ready(page,mode,4000)
            elapsed=round((time.monotonic()-start)*1000)
            assert elapsed<3500,elapsed
            results.append({"case":f"{mode}-unresolved-font", "ready_ms":elapsed,"pass":True})
            page.close()

        # Adopt an in-flight warm document, preserving Previous's end locator.
        for mode in ("page","scroll"):
            page=context.new_page();page.set_content(fixture("large"));page.evaluate(BRIDGE)
            page.evaluate("Object.defineProperty(document,'fonts',{value:{ready:new Promise(done=>window.finishFonts=done)},configurable:true})")
            page.evaluate(scripts[mode])
            assert page.evaluate((fixtures_dir/"promote-pending-end.js").read_text()) is True
            page.evaluate("finishFonts()")
            wait_ready(page,mode)
            assert page.evaluate("__wowPageEngine.generation") == 3
            if mode=="page": assert page.evaluate("__wowPageEngine.progress()") == 1000
            else: assert page.evaluate("Math.abs(window.scrollY-(document.documentElement.scrollHeight-innerHeight))<2")
            results.append({"case":f"{mode}-adopt-pending-previous-end","pass":True});page.close()

        # An older font/layout promise cannot overwrite a newer settings pass.
        page=context.new_page();page.set_content(fixture("small"));page.evaluate(BRIDGE)
        page.evaluate("Object.defineProperty(document,'fonts',{value:{ready:new Promise(done=>window.finishOldFonts=done)},configurable:true})")
        page.evaluate(scripts["page"])
        page.evaluate("Object.defineProperty(document,'fonts',{value:{ready:Promise.resolve()},configurable:true})")
        page.evaluate((fixtures_dir/"page-right.js").read_text());wait_ready(page,"page")
        assert metrics(page)["align"]=="right"
        assert metrics(page)["line"]=="32px"
        before=page.evaluate("__wowPageEngine.measureEpoch")
        page.evaluate("finishOldFonts()");page.wait_for_timeout(150)
        assert page.evaluate("__wowPageEngine.measureEpoch")==before
        assert page.evaluate("__wowPageEngine.generation")==-8
        results.append({"case":"superseded-font-callback-ignored","pass":True});page.close()

        # Switching back to Publisher restores original inline font-family values.
        page=context.new_page();page.set_content(fixture("small"));page.evaluate(BRIDGE)
        page.evaluate("document.body.style.fontFamily='serif';document.querySelector('p').style.setProperty('font-family','monospace','important')")
        page.evaluate((fixtures_dir/"page-selected-font.js").read_text());wait_ready(page,"page")
        assert "WoWPyidaungsu" in page.evaluate("getComputedStyle(document.querySelector('p')).fontFamily")
        assert page.evaluate("getComputedStyle(document.querySelector('p')).fontWeight")=="600"
        page.evaluate("readerEvents=[]");page.evaluate(scripts["page"]);wait_ready(page,"page")
        assert page.evaluate("getComputedStyle(document.querySelector('p')).fontFamily")=="monospace"
        results.append({"case":"selected-font-weight-and-publisher-restore","pass":True});page.close()

        # None stays instantaneous even if publisher CSS tries to animate the
        # reader-owned column wrapper with an !important global rule.
        page=context.new_page();page.set_content(fixture("small"));page.evaluate(BRIDGE)
        page.evaluate(scripts["page"]);wait_ready(page,"page")
        page.add_style_tag(content="#wow-page-flow{transition:transform 800ms!important;animation:wobble 2s infinite!important}@keyframes wobble{from{opacity:.4}to{opacity:1}}")
        page.evaluate("readerEvents=[]")
        assert page.evaluate("__wowPageEngine.turn(1)")=="page"
        state=page.evaluate("(()=>{let st=__wowPageEngine,c=getComputedStyle(st.flow),m=new DOMMatrixReadOnly(c.transform);return {page:st.page,x:m.m41,expected:st.marginPx-st.physical()*st.step,duration:c.transitionDuration,animation:c.animationName,active:st.flow.getAnimations().length};})()")
        assert state['page']==1 and abs(state['x']-state['expected'])<1,state
        assert state['duration']=='0s' and state['animation']=='none' and state['active']==0,state
        assert page.evaluate("readerEvents.some(e=>e.name==='onPageTurnComplete')")
        results.append({"case":"none-page-turn-resists-important-publisher-animation","pass":True});page.close()
        page=context.new_page();page.set_content(fixture("small"));page.evaluate(BRIDGE)
        page.evaluate(scripts["page"]);wait_ready(page,"page")
        page.evaluate("readerEvents=[];__wowPageEngine.generation=999")
        assert page.evaluate((fixtures_dir/"promote-ready-end.js").read_text())=='pending'
        assert page.evaluate("__wowPageEngine.generation")==999
        assert not page.evaluate("readerEvents.some(e=>e.name==='onPageReady')")
        results.append({"case":"old-spare-document-cannot-promote-as-new-chapter","pass":True});page.close()
        page=context.new_page();page.set_content('<body>Unstyled incoming document</body>');page.evaluate(BRIDGE)
        assert page.evaluate((fixtures_dir/"promote-pending-end.js").read_text())=='pending'
        assert not page.evaluate("readerEvents.length")
        results.append({"case":"cold-spare-waits-for-document-engine","pass":True});page.close()
        # Empty documents and image/SVG-only chapters must not be conflated.
        for content, expected in [("<body></body>","onEmptyChapter"),("<body><svg width='120' height='120'><rect width='100' height='100'/></svg></body>","onPageReady")]:
            page=context.new_page();page.set_content(content);page.evaluate(BRIDGE);page.evaluate(scripts["page"])
            page.wait_for_function("name=>readerEvents.some(e=>e.name===name)",arg=expected)
            results.append({"case":f"page-{expected}","pass":True});page.close()
        browser.close()
    return results


if __name__ == "__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixtures",type=Path,default=ROOT/"app/build/reader-engine-fixtures")
    parser.add_argument("--chromium",default="/usr/bin/chromium")
    parser.add_argument("--json",type=Path)
    args=parser.parse_args()
    results=run(args.fixtures,args.chromium)
    output=json.dumps({"cases":len(results),"failures":0,"results":results,"limitation":"Local desktop Chromium layout tests, not phone latency measurements."},indent=2)
    if args.json:
        args.json.parent.mkdir(parents=True,exist_ok=True);args.json.write_text(output+"\n")
    print(output)
