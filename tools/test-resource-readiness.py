#!/usr/bin/env python3
"""Finite host-Chromium contracts for the actual Java-exported resource helper."""
import argparse
import json
from pathlib import Path
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1]
SETUP=r"""() => {
 window.events=[];window.WoW={onChapterStage:(...args)=>events.push({name:'stage',args})};
 window.__resourceTest={styleEpoch:1,mode:'page',generation:7,cancelled:false,measure:r=>events.push({name:'measure',ratio:r}),progress:()=>615};
 window.activeImageListeners=new Set();
 for(const im of document.images){const add=im.addEventListener.bind(im),remove=im.removeEventListener.bind(im);im.addEventListener=(type,fn,...rest)=>{if(type==='load'||type==='error')activeImageListeners.add({im,type,fn});return add(type,fn,...rest)};im.removeEventListener=(type,fn,...rest)=>{for(const record of activeImageListeners)if(record.im===im&&record.type===type&&record.fn===fn)activeImageListeners.delete(record);return remove(type,fn,...rest)};}
 window.testFontResolve=null;window.setTestFonts=pending=>{const ready=pending?new Promise(r=>testFontResolve=r):Promise.resolve();Object.defineProperty(document,'fonts',{configurable:true,value:{ready}})};
 window.setTestFonts(false);
}"""

def run(args):
 helper=args.fixtures.joinpath('resource-ready.js').read_text()
 script="(function(){var st=window.__resourceTest,flow=document.body,styleEpoch=st.styleEpoch;"+helper+"})()"
 results=[]
 with sync_playwright() as pw:
  browser=pw.chromium.launch(executable_path=args.chromium,headless=True,args=['--no-sandbox'])
  context=browser.new_context(viewport={'width':400,'height':800})
  def page(pending=False):
   p=context.new_page();p.set_content("<html><body><img id='auto' width='120' height='80'><img id='sync' decoding='sync' width='140' height='90'></body></html>");p.evaluate(SETUP)
   p.evaluate("pending=>{for(const im of document.images)Object.defineProperty(im,'complete',{configurable:true,get:()=>!pending})}",pending)
   return p
  def measures(p):return p.evaluate("events.filter(e=>e.name==='measure')")
  p=page();p.evaluate(script);p.wait_for_timeout(20)
  assert len(measures(p))==1 and measures(p)[0]['ratio']==0
  assert p.evaluate("document.getElementById('auto').decoding")=='async'
  assert p.evaluate("document.getElementById('sync').decoding")=='sync'
  assert p.evaluate("Array.from(document.images).map(i=>[i.width,i.height])")==[[120,80],[140,90]]
  assert p.evaluate("events.filter(e=>e.name==='stage').map(e=>e.args[1])")==['fonts_wait','images_wait']
  results.append({'case':'cached-images-one-measure-async-hint-no-dimension-change-author-sync-preserved','pass':True});p.close()
  p=page(True);p.evaluate(script);p.wait_for_timeout(30);assert not measures(p)
  p.evaluate("Array.from(document.images).forEach(im=>im.dispatchEvent(new Event('load')))");p.wait_for_timeout(20)
  assert len(measures(p))==1 and p.evaluate('activeImageListeners.size')==0
  results.append({'case':'pending-image-load-measures-once-and-removes-both-listeners','pass':True});p.close()
  p=page(True);p.evaluate('setTestFonts(true)');p.evaluate(script)
  p.evaluate("__resourceTest.cancelled=true;__resourceTest.cancelResources();testFontResolve();Array.from(document.images).forEach(im=>im.dispatchEvent(new Event('load')))")
  p.wait_for_timeout(1250);assert not measures(p) and p.evaluate('activeImageListeners.size')==0
  assert p.evaluate("events.filter(e=>e.name==='stage').length")==0
  results.append({'case':'cancel-pending-font-and-images-no-stale-stage-or-measure','pass':True});p.close()
  p=page(True);p.evaluate('setTestFonts(true)');p.evaluate(script)
  p.evaluate("window.oldFontResolve=testFontResolve;__resourceTest.styleEpoch++;setTestFonts(false);for(const im of document.images)Object.defineProperty(im,'complete',{configurable:true,value:true})")
  p.evaluate(script);p.evaluate('oldFontResolve()');p.wait_for_timeout(40)
  assert len(measures(p))==1 and p.evaluate('activeImageListeners.size')==0
  results.append({'case':'new-style-cancels-old-resources-and-old-font-cannot-remeasure','pass':True});p.close()
  p=page(True);p.evaluate(script);p.wait_for_timeout(980)
  assert len(measures(p))==1 and p.evaluate('activeImageListeners.size')==0
  p.evaluate("Array.from(document.images).forEach(im=>im.dispatchEvent(new Event('load')))");p.wait_for_timeout(30)
  assert len(measures(p))==1
  results.append({'case':'bounded-image-timeout-cleans-listeners-no-late-double-measure','pass':True});p.close()
  p=page();p.evaluate('setTestFonts(true)');p.evaluate(script);p.wait_for_timeout(1280)
  assert len(measures(p))==1
  p.evaluate('testFontResolve()');p.wait_for_timeout(30)
  assert len(measures(p))==2 and measures(p)[1]['ratio']==0.615
  results.append({'case':'late-font-remeasure-preserves-current-progress-ratio','pass':True});p.close()
  p=page(True);p.evaluate(script);p.evaluate("Array.from(document.images).forEach(im=>im.dispatchEvent(new Event('error')))");p.wait_for_timeout(30)
  assert len(measures(p))==1 and p.evaluate('activeImageListeners.size')==0
  results.append({'case':'image-error-does-not-trap-readiness-or-leak-listeners','pass':True});p.close()
  p=page(True)
  for _ in range(20):
   p.evaluate('setTestFonts(true)');p.evaluate(script);p.evaluate('__resourceTest.cancelResources();__resourceTest.styleEpoch++')
  assert p.evaluate('activeImageListeners.size')==0 and not measures(p)
  results.append({'case':'repeated-superseded-image-waits-have-no-retained-listeners','pass':True});p.close()
  browser.close()
 return {'cases':len(results),'failures':0,'results':results,'physicalDeviceTested':False,'limitation':'Host resource-callback contracts only, not Android image decode/native-memory measurements.'}

if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--fixtures',type=Path,default=ROOT/'app/build/reader-engine-fixtures');parser.add_argument('--chromium',default='/usr/bin/chromium');parser.add_argument('--json',type=Path,required=True)
 args=parser.parse_args();report=run(args);args.json.parent.mkdir(parents=True,exist_ok=True);args.json.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
