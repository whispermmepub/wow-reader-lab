package com.whisper.wowreader;

/** Publisher layout is the default. Reader preferences are global, explicit overrides. */
public final class ReaderTypographyCss {
    private ReaderTypographyCss() {}
    private static final String PROSE = "p,li,blockquote,dd,dt,figcaption,div,section,article,main,span,a,em,strong,b,i,u";
    private static final String SPECIAL = "h1,h2,h3,h4,h5,h6,sub,sup,code,pre,kbd,samp,svg,math,table";

    public static String css(double lineHeight, int weight, String alignment) {
        return css(lineHeight, weight, alignment, false);
    }
    public static String css(double lineHeight, int weight, String alignment, boolean publisherStyles) {
        if (publisherStyles) return "";
        double line = lineHeight > 0 && lineHeight < 10 ? lineHeight : 1.6;
        int safeWeight = Math.max(200, Math.min(800, Math.round(weight / 100f) * 100));
        String align = "left".equals(alignment) || "right".equals(alignment) || "justify".equals(alignment)
                ? "text-align:" + alignment + " !important;" : "";
        return "html{font-size:16px !important;}body{font-size:100% !important;line-height:" + line +
                " !important;font-weight:" + safeWeight + " !important;" + align + "}" +
                ".wow-reader-prose{font-size:inherit !important;line-height:inherit !important;}";
    }

    /** Reversible inline overrides; never erase publisher margins, widths or indents. */
    public static String normalizationJs() {
        return "var original=!!st.publisherStyles;" +
                "var override=function(n,prop,value){var key='data-wow-original-'+prop,pri=key+'-priority';" +
                "if(value!=null){if(!n.hasAttribute('style'))n.setAttribute('data-wow-style-absent','1');if(!n.hasAttribute(key)){n.setAttribute(key,n.style.getPropertyValue(prop)||'');n.setAttribute(pri,n.style.getPropertyPriority(prop)||'');}n.style.setProperty(prop,value,'important');}" +
                "else if(n.hasAttribute(key)){var old=n.getAttribute(key);if(old)n.style.setProperty(prop,old,n.getAttribute(pri)||'');else n.style.removeProperty(prop);n.removeAttribute(key);n.removeAttribute(pri);}" +
                "if(n.getAttribute('data-wow-style-absent')==='1'&&n.style.length===0){n.removeAttribute('style');n.removeAttribute('data-wow-style-absent');}};" +
                "override(document.documentElement,'font-size',original?null:'16px');" +
                "override(document.body,'font-size',original?null:'100%');" +
                "override(document.body,'line-height',original?null:String(st.lineHeight));" +
                "override(document.body,'font-weight',original?null:String(st.fontWeight));" +
                "override(document.body,'font-family',!original&&st.fontFamily?st.fontFamily:null);" +
                "var requestedAlign=align==='publisher'||original?null:align;override(document.body,'text-align',requestedAlign);" +
                // A pristine publisher chapter has no reader overrides to undo.
                // Use one native selector probe rather than JavaScript/property work
                // on every prose node; the sentinel fallback preserves restoration.
                "var normalizeProse=!original||st.readerTypographyCustomized||flow.querySelector('[data-wow-original-font-family],[data-wow-original-font-weight],[data-wow-original-font-size],[data-wow-original-line-height],[data-wow-original-text-align],[data-wow-style-absent],[data-wow-class-absent],[data-wow-lang-added],.wow-reader-prose,.wow-reader-block,.wow-align-justify,.wow-align-left,.wow-align-right,.wow-smart,.wow-mm-smart,.wow-latin-smart,.wow-rhythm,.wow-rhythm-indent');" +
                "if(normalizeProse){var prose=flow.querySelectorAll('" + PROSE + "');" +
                "for(var pi=0;pi<prose.length;pi++){var pn=prose[pi];if(pn.id==='wow-page-viewport'||pn.id==='wow-page-flow'||pn.id==='wow-count-vp'||pn.id==='wow-count-flow'||pn.closest('" + SPECIAL + "'))continue;" +
                "if(!original&&!pn.hasAttribute('class'))pn.setAttribute('data-wow-class-absent','1');" +
                "var tag=(pn.localName||pn.tagName||'').toUpperCase();pn.classList.remove('wow-align-justify','wow-align-left','wow-align-right','wow-smart','wow-mm-smart','wow-latin-smart','wow-rhythm','wow-rhythm-indent','wow-reader-block');" +
                "if(original){pn.classList.remove('wow-reader-prose');if(pn.getAttribute('data-wow-class-absent')==='1'){if(!(pn.getAttribute('class')||'').trim())pn.removeAttribute('class');pn.removeAttribute('data-wow-class-absent');}if(pn.getAttribute('data-wow-lang-added')==='1'){pn.removeAttribute('lang');pn.removeAttribute('data-wow-lang-added');}}else pn.classList.add('wow-reader-prose');" +
                "override(pn,'font-family',!original&&st.fontFamily?'inherit':null);" +
                "if(tag!=='B'&&tag!=='STRONG')override(pn,'font-weight',original?null:'inherit');" +
                "override(pn,'font-size',original?null:'inherit');override(pn,'line-height',original?null:'inherit');" +
                "if(/^(P|LI|BLOCKQUOTE|DD|DT|FIGCAPTION|DIV|SECTION|ARTICLE|MAIN)$/.test(tag))override(pn,'text-align',requestedAlign);}}st.readerTypographyCustomized=!original;";
    }

    /** Some Android WebViews suspend animation frames while the incoming view is
     * transparent. A real layout pass must not depend on receiving such a frame. */
    public static String frameSchedulerJs() {
        return "st.frame=function(fn){var fired=false;var run=function(){if(fired)return;fired=true;try{fn();}catch(e){try{WoW.pageEngineFailed(st.generation,String(e));}catch(ignore){}}};" +
                "if(window.requestAnimationFrame)window.requestAnimationFrame(run);setTimeout(run,48);};";
    }

    /** Preserve direct-body rules without increasing their CSS specificity. Very
     * old WebViews without :where use a descendant fallback, not an ID override. */
    public static String publisherSelectorsJs() {
        return "st.restorePublisherSelectors=function(){var saved=st.publisherSelectors||[];for(var i=0;i<saved.length;i++){try{saved[i].rule.selectorText=saved[i].selector;}catch(e){}}st.publisherSelectors=[];};" +
                "st.restorePublisherSelectors();if(st.mode==='page'){var neutral=false,visited=[];try{neutral=!!(window.CSS&&CSS.supports&&CSS.supports('selector(:where(*))'));}catch(e){}" +
                "var visit=function(rules){if(!rules||visited.indexOf(rules)>=0)return;visited.push(rules);for(var i=0;i<rules.length;i++){var rule=rules[i];" +
                "if(rule.selectorText){var old=rule.selectorText,newSel=old.replace(/(^|[\\s>+~,|])(body(?:[.#][\\w-]+|\\[[^\\]]*\\]|:[\\w-]+(?:\\([^)]*\\))?)*)\\s*>/gi,function(_,lead,body){return lead+body+(neutral?' :where(#wow-page-flow) >':' ');});" +
                "if(newSel!==old){try{rule.selectorText=newSel;st.publisherSelectors.push({rule:rule,selector:old});}catch(e){}}}" +
                "try{if(rule.styleSheet)visit(rule.styleSheet.cssRules);else if(rule.cssRules)visit(rule.cssRules);}catch(e){}}};for(var si=0;si<document.styleSheets.length;si++){try{var sheet=document.styleSheets[si];if(sheet.ownerNode&&sheet.ownerNode.id==='wow-reader-style')continue;visit(sheet.cssRules);}catch(e){}}}";
    }
}
