package com.whisper.wowreader;

/** JavaScript snippets used by the reader pagination engine. */
public final class ReaderPaginationScripts {
    private ReaderPaginationScripts() {}

    /** ES5, cooperatively cancellable DOM-order scan; partial maps are never final counts. */
    public static String pageMapJs() {
        return "st.collectPageMap=function(epoch,onPartial){"
                + "if(st.cancelCollect)st.cancelCollect();return new Promise(function(resolve,reject){"
                + "var used={},done=false,walker,range,channel,schedule,timer,dirty=true,cached=[],partialSerial=0;"
                + "try{channel=new MessageChannel();channel.port1.onmessage=function(){var fn=channel._fn;channel._fn=null;if(fn&&!done)fn();};schedule=function(fn){var fired=false;var run=function(){if(fired||done)return;fired=true;clearTimeout(timer);fn();};channel._fn=run;timer=setTimeout(run,48);channel.port2.postMessage(0);};}catch(e){schedule=function(fn){timer=setTimeout(fn,0);};}"
                + "var cancelled=function(){return st.cancelled||epoch!==st.measureEpoch||st.mode!=='page';};"
                + "var cleanup=function(){clearTimeout(timer);if(channel){channel._fn=null;try{channel.port1.close();channel.port2.close();}catch(e){}}if(st.cancelCollect===cancel)st.cancelCollect=null;};"
                + "var finish=function(value){if(done)return;done=true;cleanup();resolve(value);};"
                + "var fail=function(error){if(done)return;done=true;cleanup();reject(error);};"
                + "var cancel=function(){finish([]);};st.cancelCollect=cancel;"
                + "var mark=function(r){if(!r||r.width<0.35||r.height<0.35)return;"
                // Reading can move the flow during collection. All marks must stay in
                // chapter coordinates, not the currently translated viewport coordinates.
                + "var offset=(st.physical?st.physical():0)*st.step,margin=st.marginPx,step=st.step;"
                + "var a=Math.max(0,Math.floor((r.left-margin+offset+1)/step));"
                + "var b=Math.max(a,Math.floor((r.right-margin+offset-1)/step));"
                + "for(var k=a;k<=b;k++)if(!used[k]){used[k]=1;dirty=true;}};"
                + "var result=function(){if(!dirty)return cached;var keys=Object.keys(used).map(function(x){return parseInt(x,10);});"
                + "cached=keys.filter(function(x){return isFinite(x)&&x>=0;}).sort(function(a,b){return a-b;});dirty=false;return cached;};"
                + "var scan=function(){if(cancelled()){finish([]);return;}"
                + "var started=Date.now(),count=0,n,rr,j;"
                + "try{while(count<768){if(count>0&&Date.now()-started>=10)break;n=walker.nextNode();"
                + "if(!n){if(cancelled()){finish([]);return;}finish(result());return;}count++;"
                + "if(n.nodeType===1){if(/^(IMG|svg|SVG|VIDEO|AUDIO|OBJECT|EMBED|TABLE|math|MATH|CANVAS|HR)$/.test(n.tagName||''))mark(n.getBoundingClientRect());}"
                + "else if((n.nodeValue||'').replace(/\\s+/g,'').length){range.selectNodeContents(n);rr=range.getClientRects();for(j=0;j<rr.length;j++)mark(rr[j]);}}"
                + "if(cancelled()){finish([]);return;}partialSerial++;if(onPartial&&(partialSerial===1||partialSerial%3===0))onPartial(dirty?result():cached);schedule(scan);"
                + "}catch(e){fail(e);}};"
                + "try{walker=document.createTreeWalker(flow,NodeFilter.SHOW_TEXT|NodeFilter.SHOW_ELEMENT,null,false);range=document.createRange();schedule(scan);}catch(e){fail(e);}"
                + "});};";
    }
}
