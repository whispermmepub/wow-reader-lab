package com.whisper.wowreader;

/** Resource readiness only; never changes image source, dimensions, quality, or loading policy. */
final class ReaderResourceReadinessScripts {
    private ReaderResourceReadinessScripts() {}

    static String pageReadyJs(double ratio) {
        return "if(st.cancelResources)st.cancelResources();"
                + "var resourceActive=true,fontStarted=false,measured=false,remaining=0,imageTimer=0,fontTimer=0,imageWaitStarted=0,watched=[];"
                + "var resourceClock=function(){return window.performance&&performance.now?performance.now():Date.now();},fontWaitStarted=resourceClock();"
                + "var resourceValid=function(){return resourceActive&&!st.cancelled&&styleEpoch===st.styleEpoch&&st.mode==='page';};"
                + "var resourceStage=function(name,started){if(!resourceValid())return;try{var d=resourceClock()-started;if(d>=0&&d<=120000&&typeof WoW.onChapterStage==='function')WoW.onChapterStage(st.generation,name,d);}catch(ignore){}};"
                + "var clearImageWait=function(){clearTimeout(imageTimer);imageTimer=0;for(var wi=0;wi<watched.length;wi++){var w=watched[wi];w.im.removeEventListener('load',w.done);w.im.removeEventListener('error',w.done);}watched=[];};"
                + "var cancelResources=function(){resourceActive=false;clearImageWait();clearTimeout(fontTimer);if(st.cancelResources===cancelResources)st.cancelResources=null;};st.cancelResources=cancelResources;"
                + "var measureOnce=function(){if(!resourceValid()||measured||!fontStarted)return;measured=true;clearImageWait();resourceStage('images_wait',imageWaitStarted);st.measure(" + ratio + ");};"
                + "var images=flow.querySelectorAll('img');for(var ii=0;ii<images.length;ii++){var im=images[ii];"
                // Respect explicit author sync/async choices. The browser still owns
                // full-resolution decoding; never eagerly decode every offscreen image.
                + "try{if(!im.getAttribute('decoding')||im.decoding==='auto')im.decoding='async';}catch(ignore){}"
                + "if(im.complete)continue;remaining++;(function(image){var settled=false;var done=function(){if(settled)return;settled=true;image.removeEventListener('load',done);image.removeEventListener('error',done);remaining--;if(remaining===0)measureOnce();};watched.push({im:image,done:done});image.addEventListener('load',done);image.addEventListener('error',done);if(image.complete)done();})(im);}"
                + "var startResources=function(){if(!resourceValid()||fontStarted)return;fontStarted=true;resourceStage('fonts_wait',fontWaitStarted);imageWaitStarted=resourceClock();if(remaining===0)measureOnce();else imageTimer=setTimeout(measureOnce,900);};"
                + "if(document.fonts&&document.fonts.ready){document.fonts.ready.then(function(){if(!resourceValid())return;clearTimeout(fontTimer);if(!fontStarted)startResources();else if(measured)st.measure(st.progress()/1000);});fontTimer=setTimeout(startResources,1200);}else startResources();";
    }
}
