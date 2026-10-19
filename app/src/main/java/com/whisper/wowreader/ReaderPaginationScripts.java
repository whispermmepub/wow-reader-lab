package com.whisper.wowreader;

/** JavaScript snippets used by the reader pagination engine. */
public final class ReaderPaginationScripts {
    private ReaderPaginationScripts() {
    }

    /**
     * Installs an asynchronous page-map collector on the existing page-engine state.
     * The returned script intentionally uses only ES5 syntax.
     */
    public static String pageMapJs() {
        return "st.collectPageMap=function(epoch){"
                + "return new Promise(function(resolve,reject){"
                + "var used={},done=false,walker,range,media,mediaIndex=0,channel,schedule;"
                + "try{channel=new MessageChannel();channel.port1.onmessage=function(){var fn=channel._fn;channel._fn=null;if(fn)fn();};schedule=function(fn){channel._fn=fn;channel.port2.postMessage(0);};}catch(e){schedule=function(fn){setTimeout(fn,0);};}"
                + "var cancelled=function(){return epoch!==st.measureEpoch||st.mode!=='page';};"
                + "var finish=function(value){if(done)return;done=true;if(channel){try{channel.port1.close();channel.port2.close();}catch(e){}}resolve(value);};"
                + "var fail=function(error){if(done)return;done=true;if(channel){try{channel.port1.close();channel.port2.close();}catch(e){}}reject(error);};"
                + "var mark=function(r){if(!r||r.width<0.35||r.height<0.35)return;"
                + "var a=Math.max(0,Math.floor((r.left-st.marginPx+1)/st.step));"
                + "var b=Math.max(a,Math.floor((r.right-st.marginPx-1)/st.step));"
                + "for(var k=a;k<=b;k++)used[k]=1;};"
                + "var result=function(){var keys=Object.keys(used).map(function(x){return parseInt(x,10);});"
                + "keys=keys.filter(function(x){return isFinite(x)&&x>=0;}).sort(function(a,b){return a-b;});"
                + "var out=[],last=-1;for(var i=0;i<keys.length;i++){if(keys[i]!==last){out.push(keys[i]);last=keys[i];}}"
                + "return out;};"
                + "var scanMedia=function(){if(cancelled()){finish([]);return;}"
                + "var started=Date.now(),count=0,r;"
                + "while(mediaIndex<media.length&&count<1024){if(count>0&&Date.now()-started>=12)break;"
                + "try{r=media[mediaIndex].getBoundingClientRect();mark(r);}catch(e){}"
                + "mediaIndex++;count++;}"
                + "if(mediaIndex<media.length){schedule(scanMedia);return;}"
                + "if(cancelled()){finish([]);return;}finish(result());};"
                + "var scanText=function(){if(cancelled()){finish([]);return;}"
                + "var started=Date.now(),count=0,n,rr,j;"
                + "while(count<1024){if(count>0&&Date.now()-started>=12)break;"
                + "try{n=walker.nextNode();}catch(e){fail(e);return;}"
                + "if(!n){try{media=flow.querySelectorAll('img,svg,video,audio,object,embed,table,math,canvas,hr');}"
                + "catch(e){fail(e);return;}schedule(scanMedia);return;}"
                + "count++;try{if((n.nodeValue||'').replace(/\\s+/g,'').length){range.selectNodeContents(n);rr=range.getClientRects();"
                + "for(j=0;j<rr.length;j++)mark(rr[j]);}}catch(e){}}"
                + "schedule(scanText);};"
                + "try{walker=document.createTreeWalker(flow,NodeFilter.SHOW_TEXT,null,false);"
                + "range=document.createRange();}catch(e){fail(e);return;}"
                + "schedule(scanText);"
                + "});};";
    }
}
