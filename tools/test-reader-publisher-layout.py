#!/usr/bin/env python3
"""Actual exported reader engine: authored EPUB layout and missing-frame regressions.
Desktop Chromium is not an Android/OEM or end-to-end APK validation.
"""
import argparse
import json
import time
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
BRIDGE = "window.events=[];window.WoW=new Proxy({}, {get:(_,name)=>(...args)=>events.push({name,args})});true"
WORDS = "The publisher chooses this alignment and indentation. " * 8


def authored_fixture(xml=False):
    ns = ' xmlns="http://www.w3.org/1999/xhtml"' if xml else ''
    return f'''<html{ns}><head><title>Publisher layout regression</title><style>
body{{font-family:serif;font-size:19px;line-height:1.4;}}
body > p{{text-align:left;}}
p.author-center{{text-align:center;}}
body > p.author-center{{width:60%;margin-left:auto;margin-right:auto;color:rgb(0,128,0);}}
body > p.author-left{{text-align:left;margin-left:33px;padding-right:13px;text-indent:1.5em;}}
body[data-book="story"]:first-of-type > p.author-right{{text-align:right;}}
body > .verse{{width:70%;margin-left:auto;margin-right:auto;}}
.verse p{{text-align:center;margin-top:0;margin-bottom:0;text-indent:0;}}
</style></head><body data-book="story"><h1 style="text-align:center">A centered heading</h1>
<p id="center" class="author-center" style="font-size:19px!important">Centered author line</p>
<p id="left" class="author-left" style="text-align:left!important">{WORDS}</p>
<p id="right" class="author-right">A right-aligned signature</p>
<div id="verse" class="verse"><p id="verse1">Poetry is centered</p><p id="verse2">Without an invented indent</p></div>
<p id="attribute-probe">Attribute absence must return.</p><p id="rtl" dir="rtl" style="text-align:start">مرحبا بالعالم</p>
<p>{WORDS}</p><p>{WORDS}</p></body></html>'''


def inspect(page):
    return page.evaluate("""()=>{let get=id=>{let n=document.getElementById(id),c=getComputedStyle(n);return {align:c.textAlign,font:c.fontSize,line:c.lineHeight,left:c.marginLeft,pad:c.paddingRight,indent:c.textIndent,width:c.width,color:c.color,direction:c.direction};};return {center:get('center'),left:get('left'),right:get('right'),verse:get('verse'),verse2:get('verse2'),rtl:get('rtl'),heading:getComputedStyle(document.querySelector('h1')).textAlign};}""")


def ready(page, mode, timeout=5000):
    name='onPageReady' if mode=='page' else 'onScrollReady'
    page.wait_for_function('name=>events.some(e=>e.name===name)',arg=name,timeout=timeout,polling=25)
    errors=page.evaluate("events.filter(e=>e.name==='pageEngineFailed')")
    assert not errors,errors


def preserved(m):
    assert m['center']['align']=='center',m
    assert m['center']['color']=='rgb(0, 128, 0)',m
    assert m['center']['font']=='19px',m
    assert m['left']['align']=='left' and m['left']['left']=='33px' and m['left']['pad']=='13px',m
    assert m['left']['indent']=='28.5px',m
    assert m['right']['align']=='right',m
    assert m['verse2']['align']=='center' and m['verse2']['indent']=='0px',m
    assert m['rtl']['align']=='start' and m['rtl']['direction']=='rtl',m
    assert m['heading']=='center',m


def run(directory,chromium):
    scripts={name:(directory/(name+'.js')).read_text() for name in ['page','scroll','page-publisher','scroll-publisher','page-right','page-custom-original-align']}
    results=[]
    with sync_playwright() as p:
        browser=p.chromium.launch(executable_path=chromium,headless=True,args=['--no-sandbox'])
        context=browser.new_context(viewport={'width':400,'height':800})
        context.route('**/*',lambda r:r.abort())
        for xml in (False,True):
            for mode in ('page','scroll'):
                page=context.new_page()
                if xml:
                    page.route('http://reader.test/chapter.xhtml',lambda r:r.fulfill(content_type='application/xhtml+xml',body=authored_fixture(True)))
                    page.goto('http://reader.test/chapter.xhtml')
                else: page.set_content(authored_fixture())
                preserved(inspect(page))
                page.evaluate(BRIDGE);page.evaluate(scripts[mode+'-publisher']);ready(page,mode)
                preserved(inspect(page))
                results.append({'case':f'{mode}-original-{"xhtml" if xml else "html"}-author-layout','pass':True})
                page.close()
        # Explicit reader overrides preserve author margins/indents; selecting
        # Original reverses inline-important font/alignment changes completely.
        page=context.new_page();page.set_content(authored_fixture());page.evaluate(BRIDGE)
        page.evaluate(scripts['page-publisher']);ready(page,'page')
        page.evaluate('events=[]');page.evaluate(scripts['page-right']);ready(page,'page')
        m=inspect(page);assert m['left']['align']=='right' and m['left']['font']=='16px',m
        assert m['left']['left']=='33px' and m['left']['pad']=='13px',m
        page.evaluate('events=[]');page.evaluate(scripts['page-publisher']);ready(page,'page');preserved(inspect(page))
        assert page.evaluate("(()=>{let p=document.getElementById('attribute-probe');return !p.hasAttribute('class')&&!p.hasAttribute('style')&&!p.hasAttribute('lang');})()")
        page.evaluate('events=[]');page.evaluate(scripts['scroll-publisher']);ready(page,'scroll');preserved(inspect(page))
        assert page.evaluate("document.getElementById('left').parentNode===document.body")
        results.append({'case':'override-to-original-and-page-to-scroll-restores-css','pass':True});page.close()
        page=context.new_page();page.set_content(authored_fixture());page.evaluate(BRIDGE)
        page.evaluate(scripts['page-custom-original-align']);ready(page,'page');m=inspect(page)
        assert m['center']['align']=='center' and m['left']['align']=='left' and m['right']['align']=='right',m
        assert m['left']['font']=='16px' and m['left']['left']=='33px' and m['left']['pad']=='13px',m
        assert m['verse2']['indent']=='0px',m
        results.append({'case':'global-custom-font-with-authored-alignment','pass':True});page.close()
        # Simulates an Android compositor withholding rAF from an incoming view.
        for mode in ('page','scroll'):
            for font_blocked in (False,True):
                page=context.new_page();page.set_content(authored_fixture());page.evaluate(BRIDGE)
                page.evaluate('window.requestAnimationFrame=function(){}')
                if font_blocked: page.evaluate("Object.defineProperty(document,'fonts',{value:{ready:new Promise(()=>{})}})")
                start=time.monotonic();page.evaluate(scripts[mode+'-publisher']);ready(page,mode,4000)
                elapsed=round((time.monotonic()-start)*1000)
                assert elapsed<3500,elapsed
                preserved(inspect(page))
                results.append({'case':f'{mode}-missing-frame-font-blocked-{font_blocked}','ready_ms':elapsed,'pass':True});page.close()
        # Older/OEM runtimes without MessageChannel still yield and complete.
        for setup,label in [("window.MessageChannel=undefined",'missing'),("window.MessageChannel=function(){throw new Error('disabled')}",'disabled')]:
            page=context.new_page();page.set_content(authored_fixture());page.evaluate(BRIDGE);page.evaluate(setup+";true")
            page.evaluate(scripts['page-publisher']);ready(page,'page');preserved(inspect(page))
            results.append({'case':'pagination-channel-'+label+'-timer-fallback','pass':True});page.close()
        # Old collectors stopped after 24,000 text nodes and silently lost the tail.
        large='<html><head><style>body{font-size:16px}</style></head><body>'+''.join('<p>'+('<span>word </span>'*20)+'</p>' for _ in range(1300))+'<p id="tail">The final chapter sentinel.</p></body></html>'
        page=context.new_page();page.set_content(large);page.evaluate(BRIDGE)
        page.evaluate("""(()=>{window.timerTicks=0;window.collectorYields=0;
            var NativeChannel=window.MessageChannel;
            window.MessageChannel=function(){var ch=new NativeChannel(),post=ch.port2.postMessage.bind(ch.port2);ch.port2.postMessage=function(value){collectorYields++;return post(value);};return ch;};
            window.tick=setInterval(()=>timerTicks++,5);return true;})()""")
        start=time.monotonic();page.evaluate(scripts['page-publisher']);ready(page,'page',20000)
        assert page.evaluate('timerTicks')>0
        assert page.evaluate('collectorYields')>=27  # actual cooperative task yields, not an artificial minimum run time
        assert page.evaluate("__wowPageEngine.goToFragment('tail')") is True
        assert page.evaluate("(()=>{let r=document.getElementById('tail').getBoundingClientRect();return r.left>=-2&&r.right<=innerWidth+2&&r.top>=-2&&r.top<innerHeight;})()")
        results.append({'case':'uncapped-26000-text-nodes-last-page-reachable-timer-yields','ready_ms':round((time.monotonic()-start)*1000),'pass':True});page.close()
        page=context.new_page();page.set_content(large);page.evaluate(BRIDGE);page.evaluate(scripts['page-publisher'])
        page.wait_for_function('window.__wowPageEngine&&__wowPageEngine.measureEpoch>0',polling=25)
        page.evaluate("var e=__wowPageEngine;window.cancelledMap=null;e.collectPageMap(e.measureEpoch).then(m=>window.cancelledMap=m);e.measureEpoch++;e.mode='scroll';events=[]")
        page.wait_for_function('Array.isArray(window.cancelledMap)',polling=25)
        assert page.evaluate('cancelledMap.length')==0
        assert not page.evaluate("events.some(e=>e.name==='onPageReady'||e.name==='onEmptyChapter')")
        results.append({'case':'obsolete-pagination-cancelled-after-mode-change','pass':True});page.close()
        page=context.new_page();page.set_content(authored_fixture());page.evaluate(BRIDGE)
        page.evaluate("document.createRange=function(){throw new Error('fixture range failure')};true")
        page.evaluate(scripts['page-publisher'])
        page.wait_for_function("events.some(e=>e.name==='pageEngineFailed')",polling=25)
        assert page.evaluate("events.some(e=>e.name==='pageEngineFailed'&&e.args[0]===-10&&e.args[1].includes('fixture range failure'))")
        assert not page.evaluate("events.some(e=>e.name==='onEmptyChapter')")
        results.append({'case':'async-pagination-failure-reported-not-fake-empty-or-ready','pass':True});page.close()
        browser.close()
    return results


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fixtures',type=Path,default=ROOT/'app/build/reader-engine-fixtures')
    parser.add_argument('--chromium',default='/usr/bin/chromium')
    parser.add_argument('--json',type=Path)
    args=parser.parse_args();results=run(args.fixtures,args.chromium)
    output=json.dumps({'cases':len(results),'failures':0,'results':results,'limitation':'Desktop Chromium only; actual Android WebView/device QA remains.'},indent=2)
    if args.json:args.json.parent.mkdir(parents=True,exist_ok=True);args.json.write_text(output+'\n')
    print(output)
