package com.whisper.wowreader;

/** Keeps first-page usability separate from authoritative pagination completion. */
final class ReaderProgressivePaginationScripts {
    private ReaderProgressivePaginationScripts() {}

    static String measureAndTurnJs(int styleToken) {
        return "st.measure=function(r){if(st.cancelCollect)st.cancelCollect();st.measureEpoch=(st.measureEpoch||0)+1;"
                + "var epoch=st.measureEpoch,ratio=st.clamp(r,0,1),attempt=0;st.paginationComplete=false;st.partialRevealed=false;st.boundaryRequested=false;st.locked=true;"
                + "var clock=function(){return window.performance&&performance.now?performance.now():Date.now();};var stage=function(name,started){try{var d=clock()-started;if(d>=0&&d<=120000&&typeof WoW.onChapterStage==='function')WoW.onChapterStage(st.generation,name,d);}catch(ignore){}};var measureStarted=clock();"
                + "var valid=function(){return !st.cancelled&&epoch===st.measureEpoch&&st.mode==='page';};"
                + "var run=function(){if(!valid())return;st.layout();st.page=0;st.pageMap=[0];st.apply(false);"
                + "if(!st.typographyPrepared){st.applyTypography();st.preparePagination();st.typographyPrepared=true;}"
                + "st.frame(function(){st.frame(function(){if(!valid())return;var layoutStarted=measureStarted;st.layout();var geom=(viewport.clientWidth||0)+'x'+(viewport.clientHeight||0)+'|'+Math.round(flow.scrollWidth||0);stage('layout_prepare',layoutStarted);var scanStarted=clock();"
                + "var partial=function(map){if(!valid()||st.boundaryRequested||!map.length||map[0]!==0||ratio!==0||(st.pendingRestoreRatio!=null&&st.pendingRestoreRatio!==0))return;"
                + "var first=!st.partialRevealed,oldPage=st.page,restoreStart=st.pendingRestoreRatio===0;if(restoreStart)st.pendingRestoreRatio=null;var physical=st.partialRevealed&&!restoreStart?st.physical():map[0];st.pageMap=map;st.count=map.length;st.page=st.nearestLogical(physical);st.partialRevealed=true;st.locked=false;st.apply(false);if(first||oldPage!==st.page)st.report();};"
                + "st.collectPageMap(epoch,partial).then(function(map){if(!valid())return;stage('page_scan',scanStarted);"
                + "if(!map.length){st.count=0;st.locked=false;st.paginationComplete=true;WoW.onEmptyChapter(st.generation);return;}"
                + "var physical=st.physical(),preserve=st.partialRevealed&&st.pendingRestoreRatio==null;st.pageMap=map;st.count=map.length;"
                + "if(st.pendingRestoreRatio!=null){ratio=st.clamp(st.pendingRestoreRatio,0,1);st.pendingRestoreRatio=null;preserve=false;}"
                + "st.page=preserve?st.nearestLogical(physical):st.clamp(Math.round((st.count-1)*ratio),0,st.count-1);st.apply(false);"
                + "st.frame(function(){if(!valid())return;st.layout();var geom2=(viewport.clientWidth||0)+'x'+(viewport.clientHeight||0)+'|'+Math.round(flow.scrollWidth||0);"
                // Reflow cannot restore the entry ratio after the reader has moved.
                + "if(geom2!==geom&&attempt<1&&!st.partialRevealed){attempt++;setTimeout(run,42);return;}"
                + "if(st.pendingRestoreRatio!=null){st.page=st.clamp(Math.round((st.count-1)*st.clamp(st.pendingRestoreRatio,0,1)),0,st.count-1);st.pendingRestoreRatio=null;st.apply(false);}st.paginationComplete=true;st.locked=false;if(st.pendingFragment){var fragment=st.pendingFragment;st.pendingFragment=null;st.goToFragment(fragment);}st.report();WoW.onPageReady(st.generation,st.page+1,st.count,st.progress());"
                + (styleToken > 0 ? "WoW.onStyleReady(" + styleToken + ");" : "")
                + "});}).catch(function(e){if(valid()){st.locked=false;WoW.pageEngineFailed(st.generation,String(e));}});});});};run();};"
                + "st.turn=function(d){if(st.cancelled||st.mode!=='page'||st.locked)return 'locked';"
                + "if(d<0&&(st.page||0)<=0){st.boundaryRequested=true;st.locked=true;WoW.requestChapter(st.generation,-1);return 'chapter';}"
                // A partial frontier is NOT the chapter end. Never enqueue stale
                // forward intent that could fire after the user reverses direction.
                + "if(d>0&&(st.page||0)>=(st.count||1)-1){if(!st.paginationComplete){WoW.onPageTurnComplete(st.generation,(st.page||0)+1,st.count||1,st.progress());return 'pending';}st.boundaryRequested=true;st.locked=true;WoW.requestChapter(st.generation,1);return 'chapter';}"
                + "st.locked=true;st.page=st.clamp((st.page||0)+d,0,(st.count||1)-1);st.paperTurn(d,function(){if(st.cancelled)return;st.report();st.locked=false;WoW.onPageTurnComplete(st.generation,st.page+1,st.count,st.progress());});return 'page';};";
    }
}
