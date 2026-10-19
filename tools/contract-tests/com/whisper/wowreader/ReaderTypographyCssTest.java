package com.whisper.wowreader;
public final class ReaderTypographyCssTest {
    private static void ok(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        String css=ReaderTypographyCss.css(1.8,550,"right");
        ok(css.contains("html{font-size:16px !important;"),"consistent custom root size");
        ok(css.contains("line-height:1.8 !important"),"global custom line height");
        ok(css.contains("font-weight:600 !important"),"normalized custom weight");
        ok(css.contains("text-align:right !important"),"explicit custom alignment");
        ok(!css.contains("margin-left:0")&&!css.contains("text-indent:"),"author margins and indents preserved");
        ok(ReaderTypographyCss.css(1.8,550,"publisher",true).isEmpty(),"original mode adds no typography CSS");
        ok(!ReaderTypographyCss.css(1.8,550,"publisher").contains("text-align:"),"custom typography can retain author alignment");
        String js=ReaderTypographyCss.normalizationJs();
        ok(js.contains("override(pn,'font-size',original?null:'inherit')"),"reversible font override");
        ok(js.contains("h1,h2,h3,h4,h5,h6,sub,sup,code,pre"),"semantic content preserved");
        ok(js.contains("pn.closest("),"special subtrees preserved");
        ok(js.contains("pn.id==='wow-page-flow'"),"engine wrappers excluded");
        ok(js.contains("localName"),"HTML and XHTML element semantics");
        ok(ReaderTypographyCss.frameSchedulerJs().contains("setTimeout(run,48)"),"no invisible-view animation-frame deadlock");
        ok(ReaderTypographyCss.publisherSelectorsJs().contains("restorePublisherSelectors"),"body child selectors restored across modes");
        System.out.println("READER_TYPOGRAPHY_CSS_TEST_PASS");
    }
}
