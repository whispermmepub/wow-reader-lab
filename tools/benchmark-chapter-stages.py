#!/usr/bin/env python3
"""Compare exported chapter engines with real raster images on host Chromium.

Not Android/WebView, not a physical phone, not a complete APK transition. Timings
start at reader-style injection after HTML/resource load, matching onPageFinished.
"""
import argparse
import base64
import hashlib
import importlib.util
import io
import json
import statistics
import time
from pathlib import Path

from PIL import Image
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
BRIDGE = """(() => {window.events=[];window.WoW=new Proxy({}, {get:(_,name)=>(...args)=>events.push({name:String(name),args,at:performance.now()})});})()"""


def jpeg(w, h, color):
    image = Image.new('RGB', (w, h), color)
    # A visible border and diagonals make each fixture nonempty and recognizable.
    from PIL import ImageDraw
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 1, w-2, h-2), outline='black', width=12)
    draw.line((0, 0, w, h), fill='white', width=24)
    draw.line((0, h, w, 0), fill='black', width=24)
    buf = io.BytesIO(); image.save(buf, format='JPEG', quality=82)
    return 'data:image/jpeg;base64,' + base64.b64encode(buf.getvalue()).decode()


def fixtures():
    text = 'Burmese မင်္ဂလာပါ စာဖတ်ခြင်း and Latin mixed-language reader geometry. '
    big = jpeg(6000, 4000, '#819bae')
    medium = [jpeg(1200, 1800, c) for c in ('#aabbaa', '#ccbbaa', '#aabbcc', '#bb99aa')]
    start = "<!doctype html><html><head><meta charset='utf-8'><style>body{font-size:16px;line-height:1.8}figure{margin:8px 0}img{max-width:100%;height:auto}p{margin:0 0 12px}.nest{padding-left:2px;border-left:1px solid #ddd}h2{break-before:page}.empty{page-break-before:always}</style></head><body>"
    end = "<p id='tail'>AUTHORITATIVE TAIL မင်္ဂလာပါ</p></body></html>"
    single = start + "<figure><img id='large-raster' src='"+big+"' width='6000' height='4000'/><figcaption>Uncropped 24 MP image</figcaption></figure>" + ''.join('<p>'+str(i)+'. '+text*8+'</p>' for i in range(180)) + end
    many = start + ''.join("<figure><img id='raster-"+str(i)+"' src='"+medium[i % 4]+"' width='1200' height='1800'/><figcaption>Complete image "+str(i)+"</figcaption></figure><p>"+text*3+'</p>' for i in range(24)) + end
    deep = start + ''.join('<section>'+'<div class="nest">'*6+'<p>'+str(i)+'. '+text*6+'</p>'+'</div>'*6+'</section>' for i in range(800)) + end
    return {'single-24MP-raster': single, '24-medium-rasters': many, 'nested-myanmar-text': deep}


def exercise(context, script, html):
    page=context.new_page(); errors=[]
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.set_content(html, wait_until='load'); page.evaluate(BRIDGE)
    page.evaluate('window.benchmarkStarted=performance.now()')
    t=time.monotonic(); page.evaluate(script); injection_ms=(time.monotonic()-t)*1000
    page.wait_for_function("events.some(e=>e.name==='onPageUsable'||e.name==='onPageReady')", timeout=60000)
    page.wait_for_function("events.some(e=>e.name==='onPageReady')&&__wowPageEngine.paginationComplete===true", timeout=60000)
    state=page.evaluate("""() => {
        const start=benchmarkStarted, first=events.find(e=>e.name==='onPageUsable'||e.name==='onPageReady'), final=events.find(e=>e.name==='onPageReady');
        const im=Array.from(document.images).map(i=>({id:i.id,natural:[i.naturalWidth,i.naturalHeight],width:Math.round(i.getBoundingClientRect().width*100)/100,height:Math.round(i.getBoundingClientRect().height*100)/100,complete:i.complete}));
        const st=__wowPageEngine; st.page=st.count-1;st.apply(false);
        return {first_usable_ms:first.at-start,full_map_ms:final.at-start,map:st.pageMap,images:im,stages:events.filter(e=>e.name==='onChapterStage').map(e=>({stage:e.args[1],duration_ms:e.args[2]})),tailReachable:st.page===st.count-1&&!st.locked,failures:events.filter(e=>e.name==='pageEngineFailed')};
    }""")
    assert not errors, errors
    assert not state.pop('failures'), state
    assert state['tailReachable'] and all(im['complete'] and im['natural'][0]>0 for im in state['images']), state
    state['style_injection_block_ms']=injection_ms
    state['map_sha256']=hashlib.sha256(json.dumps(state.pop('map')).encode()).hexdigest()
    page.close(); return state


def run(args):
    scripts={label:{mode:(path/(mode+'.js')).read_text() for mode in ('page','page-publisher')} for label,path in [('v79',args.baseline),('v80',args.candidate)]}
    cases=[]
    with sync_playwright() as pw:
        browser=pw.chromium.launch(executable_path=args.chromium,headless=True,args=['--no-sandbox'])
        context=browser.new_context(viewport={'width':400,'height':800})
        context.route('**/*', lambda route: route.abort())
        for name,html in fixtures().items():
            for mode in scripts['v79']:
                runs={label:[] for label in scripts}
                # Alternate candidates to reduce simple order/temperature bias.
                for sample in range(args.samples):
                    for label in (('v79','v80') if sample%2==0 else ('v80','v79')):
                        runs[label].append(exercise(context,scripts[label][mode],html))
                first=runs['v79'][0]
                for rr in runs.values():
                    for r in rr:
                        assert r['map_sha256']==first['map_sha256'], (name,mode,'logical page map changed')
                        assert r['images']==first['images'], (name,mode,'image dimensions/geometry changed')
                medians={label:{key:round(statistics.median(r[key] for r in rows),2) for key in ('first_usable_ms','full_map_ms','style_injection_block_ms')} for label,rows in runs.items()}
                cases.append({'case':name+'-'+mode,'html_sha256':hashlib.sha256(html.encode()).hexdigest(),'html_bytes':len(html.encode()),'pass':True,'identical_page_map_and_image_geometry':True,'median_ms':medians,'runs':runs})
                print('PASS',name,mode,json.dumps(medians),flush=True)
        browser.close()
    return {'samples_per_candidate':args.samples,'cases':cases,'failures':0,'physicalDeviceTested':False,'androidWebViewTested':False,'scope':'Host reader-style injection to first usable and authoritative map, after document/resources load; no APK navigation/ZIP extraction timing.','limitations':['Synthetic rasters, not owner failing EPUB.','Host Chromium does not measure Android image native heap or Vivo Y30 latency.','Wall-clock values depend on CPU load; no device or universal speed claim.']}


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline',type=Path,default=ROOT/'build/v80-baseline-fixtures')
    parser.add_argument('--candidate',type=Path,default=ROOT/'app/build/reader-engine-fixtures')
    parser.add_argument('--samples',type=int,default=3)
    parser.add_argument('--chromium',default='/usr/bin/chromium')
    parser.add_argument('--json',type=Path,required=True)
    args=parser.parse_args(); assert args.samples>=1
    report=run(args);args.json.parent.mkdir(parents=True,exist_ok=True);args.json.write_text(json.dumps(report,indent=2)+'\n')
    print('Saved',args.json,flush=True)
