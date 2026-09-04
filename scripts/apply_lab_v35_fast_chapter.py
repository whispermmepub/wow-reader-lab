from pathlib import Path

P = Path('app/src/main/java/com/whisper/wowreader/BookReaderActivity.java')
B = Path('app/build.gradle')
text = P.read_text(encoding='utf-8')

old = '''                    "st.measure=function(r){st.measureEpoch=(st.measureEpoch||0)+1;var epoch=st.measureEpoch,ratio=st.clamp(r,0,1),attempt=0,lastSig='',stableHits=0;" +
                    "var run=function(){if(epoch!==st.measureEpoch)return;st.layout();st.page=0;st.pageMap=[0];flow.style.transition='none';flow.style.transform='translate3d('+st.marginPx+'px,0,0)';st.applyTypography();st.preparePagination();" +
                    "requestAnimationFrame(function(){requestAnimationFrame(function(){if(epoch!==st.measureEpoch)return;st.layout();var map=st.collectPageMap();if(!map.length){st.count=0;st.locked=false;WoW.onEmptyChapter();return;}" +
                    "var sig=(viewport.clientWidth||0)+'x'+(viewport.clientHeight||0)+'|'+Math.round(flow.scrollWidth||0)+'|'+map.join(',');if(sig===lastSig)stableHits++;else{lastSig=sig;stableHits=0;}attempt++;" +
                    "if(stableHits<2&&attempt<9){setTimeout(run,76);return;}st.pageMap=map;st.count=map.length;st.page=st.clamp(Math.round((st.count-1)*ratio),0,st.count-1);st.apply(false);" +
                    "requestAnimationFrame(function(){if(epoch!==st.measureEpoch)return;var verify=st.collectPageMap();var sig2=(viewport.clientWidth||0)+'x'+(viewport.clientHeight||0)+'|'+Math.round(flow.scrollWidth||0)+'|'+verify.join(',');" +
                    "if(sig2!==sig&&attempt<11){lastSig=sig2;stableHits=0;setTimeout(run,64);return;}st.locked=false;st.report();WoW.onPageReady(" + styleGeneration + ",st.page+1,st.count,st.progress());" +
                    (styleToken > 0 ? "WoW.onStyleReady(" + styleToken + ");" : "") +
                    "});});});};run();};" +'''

new = '''                    "st.measure=function(r){st.measureEpoch=(st.measureEpoch||0)+1;var epoch=st.measureEpoch,ratio=st.clamp(r,0,1),attempt=0;" +
                    "var run=function(){if(epoch!==st.measureEpoch)return;st.layout();st.page=0;st.pageMap=[0];flow.style.transition='none';flow.style.transform='translate3d('+st.marginPx+'px,0,0)';st.applyTypography();st.preparePagination();" +
                    "requestAnimationFrame(function(){requestAnimationFrame(function(){if(epoch!==st.measureEpoch)return;st.layout();var geom=(viewport.clientWidth||0)+'x'+(viewport.clientHeight||0)+'|'+Math.round(flow.scrollWidth||0);" +
                    "var map=st.collectPageMap();if(!map.length){st.count=0;st.locked=false;WoW.onEmptyChapter();return;}st.pageMap=map;st.count=map.length;st.page=st.clamp(Math.round((st.count-1)*ratio),0,st.count-1);st.apply(false);" +
                    "requestAnimationFrame(function(){if(epoch!==st.measureEpoch)return;st.layout();var geom2=(viewport.clientWidth||0)+'x'+(viewport.clientHeight||0)+'|'+Math.round(flow.scrollWidth||0);" +
                    "if(geom2!==geom&&attempt<1){attempt++;setTimeout(run,42);return;}st.locked=false;st.report();WoW.onPageReady(" + styleGeneration + ",st.page+1,st.count,st.progress());" +
                    (styleToken > 0 ? "WoW.onStyleReady(" + styleToken + ");" : "") +
                    "});});});};run();};" +'''

if old not in text:
    raise SystemExit('old multi-scan measure block not found')
text = text.replace(old, new, 1)

# Keep the compositor-accurate outgoing page freeze and v33 reveal guard.
required = [
    'android.view.PixelCopy.request(getWindow(), src, shot',
    'confirmStableChapterReveal(generation, 0, -1, -1);',
    'var geom=(viewport.clientWidth||0)',
    'if(geom2!==geom&&attempt<1)',
]
for token in required:
    if token not in text:
        raise SystemExit('required v35 token missing: ' + token)

P.write_text(text, encoding='utf-8')

build = B.read_text(encoding='utf-8')
build = build.replace('versionCode 34', 'versionCode 35', 1)
build = build.replace("versionName '2.16.4-lab-v34'", "versionName '2.16.5-lab-v35'", 1)
if 'versionCode 35' not in build or "versionName '2.16.5-lab-v35'" not in build:
    raise SystemExit('version bump failed')
B.write_text(build, encoding='utf-8')
print('v35 fast stable chapter patch applied')
