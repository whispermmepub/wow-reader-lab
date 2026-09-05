package com.whisper.wowreader;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Device regression using real WebViews, a paginated EPUB and the public link/card flow. */
public class RegressionInstrumentation extends Instrumentation {
    private Activity reader;
    private final StringBuilder report = new StringBuilder();
    interface Query { Object get() throws Exception; }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            testCalendarAndShelves();
            File book = fixture();
            for (String mode : new String[]{"page", "scroll"}) {
                for (String animation : new String[]{"none", "slide"}) {
                    runCase(book, mode, animation);
                    report.append(mode).append('/').append(animation).append(": PASS\n");
                }
            }
            result.putString("stream", "\nREGRESSION_PASS\n" + report);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable e) {
            result.putString("stream", "\nREGRESSION_FAILED\n" + report + android.util.Log.getStackTraceString(e));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private Object ui(Query q) throws Exception {
        AtomicReference<Object> value = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        runOnMainSync(() -> { try { value.set(q.get()); } catch(Exception e) { error.set(e); } });
        if (error.get() != null) throw error.get();
        return value.get();
    }
    private Object field(String name) throws Exception {
        Field f = reader.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(reader);
    }
    private Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = reader.getClass().getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(reader,args);
    }
    private void await(Query q, String label) throws Exception {
        long until = SystemClock.uptimeMillis() + 15000;
        while (SystemClock.uptimeMillis() < until) {
            if (Boolean.TRUE.equals(ui(q))) return;
            SystemClock.sleep(60);
        }
        throw new AssertionError("Timeout: " + label);
    }
    private String js(String source) throws Exception {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<String> value = new AtomicReference<>();
        ui(() -> { ((WebView)field("webView")).evaluateJavascript(source, s -> {value.set(s);done.countDown();});return null; });
        if (!done.await(8, TimeUnit.SECONDS)) throw new AssertionError("JS timeout");
        return value.get();
    }
    private void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private void ready(String file) throws Exception {
        await(() -> !((Boolean)field("chapterLoading")) && !((Boolean)field("footnoteReturnPending")) &&
                ((WebView)field("webView")).getUrl() != null && ((WebView)field("webView")).getUrl().contains(file), "ready " + file);
        SystemClock.sleep(250);
    }
    private View find(View v, String text) {
        if (v instanceof TextView && text.contentEquals(((TextView)v).getText())) return v;
        if (v instanceof ViewGroup) { ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View f=find(g.getChildAt(i),text);if(f!=null)return f;} }
        return null;
    }
    private void runCase(File book, String mode, String animation) throws Exception {
        SharedPreferences prefs=getTargetContext().getSharedPreferences("wow_reader",0);
        prefs.edit().putString("epub_reading_mode",mode).putString("epub_page_animation",animation)
          .putBoolean("reader_v19_defaults_applied",true).putBoolean("reader_v20_defaults_applied",true)
          .putBoolean("reader_v210_animation_default_applied",true).putInt("epub_chapter_"+book.getName(),0)
          .putInt("epub_scroll_"+book.getName(),0).putBoolean("google_sync_enabled",false).commit();
        Intent intent=new Intent(getTargetContext(),BookReaderActivity.class).putExtra("path",book.getAbsolutePath()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        reader=startActivitySync(intent);ready("intro.xhtml");
        await(() -> (Boolean)field("preloadReady"), "preload ready");
        ui(() -> call("navigateChapter",new Class[]{int.class,boolean.class},1,false));ready("chapter.xhtml");
        check("true".equals(js("!!window.__wowReaderLinkNavInstalled")), "preloaded document has link handler");
        for(int i=0;i<5;i++) {
            SystemClock.sleep(1900);
            // Different reference destinations in one long chapter expose chapter-start fallback bugs.
            String id=i%2==0?"ref2":"ref1";
            js("window.__wowPageEngine.goToFragment('"+id+"')");SystemClock.sleep(300);
            int page=(Integer)ui(() -> field("currentPageInChapter"));
            int percent=prefs.getInt("percent_"+book.getName(),-1);
            String y=js("Math.round(scrollY)");
            if("page".equals(mode)) check(page>1,"reference beyond first page");
            js("document.getElementById('"+id+"').click()");
            await(() -> field("footnotePreviewOverlay")!=null,"preview");
            check(((String)ui(() -> ((WebView)field("webView")).getUrl())).contains("chapter.xhtml"),"card did not navigate");
            check(page==(Integer)ui(() -> field("currentPageInChapter")),"card did not turn page");
            check(percent==prefs.getInt("percent_"+book.getName(),-1),"card preserved percent");
            ui(() -> {View go=find((View)field("root"),"Show on page");check(go!=null,"go button");go.performClick();return null;});ready("notes.xhtml");
            check(percent==prefs.getInt("percent_"+book.getName(),-1),"footnote visit did not overwrite progress");
            js("document.getElementById('back"+(id.equals("ref2")?"2":"1")+"').click()");ready("chapter.xhtml");
            check(page==(Integer)ui(() -> field("currentPageInChapter")),"return exact page");
            check("true".equals(js("(function(){var r=document.getElementById('"+id+"').getBoundingClientRect();return r.top>=-2&&r.top<innerHeight&&r.left>=-2&&r.left<innerWidth;})()")),"returned reference is visible");
        }
        ui(() -> {
            call("armFootnoteReturn", new Class[]{String.class}, "ref2");
            call("requestFootnotePreview", new Class[]{String.class,String.class,String.class}, "notes.xhtml#n1", "1", "ref1");
            call("requestFootnotePreview", new Class[]{String.class,String.class,String.class}, "notes.xhtml#2", "2", "ref2");
            return null;
        });
        await(() -> field("footnotePreviewOverlay") != null, "latest preview");
        SystemClock.sleep(400);
        check(((ReaderSearchIndex.Footnote)ui(() -> field("footnotePreviewNote"))).text.contains("Queen of Scotland"), "latest request wins");
        ui(() -> call("cancelFootnotePreview", new Class[]{}));
        // Open Notes independently: backlinks must use their actual href without a return-session flag.
        SystemClock.sleep(2000);
        ui(() -> call("navigateChapter",new Class[]{int.class,boolean.class},1,false));
        ready("notes.xhtml");js("document.getElementById('back2').click()");ready("chapter.xhtml");
        if("page".equals(mode)) check((Integer)ui(() -> field("currentPageInChapter"))>1,"direct Notes backlink beyond chapter start");
        check("true".equals(js("(function(){var r=document.getElementById('ref2').getBoundingClientRect();return r.top>=-2&&r.top<innerHeight&&r.left>=-2&&r.left<innerWidth;})()")),"reference visible after direct backlink");
        // Screen edge navigation is available again in scroll mode.
        if("scroll".equals(mode)) {
            SystemClock.sleep(2000);
            js("scrollTo(0,0)");SystemClock.sleep(200);
            ui(() -> {WebView w=(WebView)field("webView");return call("handleReaderTap",new Class[]{float.class,float.class},w.getWidth()*.97f,w.getHeight()*.8f);});
            ready("notes.xhtml");
        }
        ui(() -> {reader.finish();return null;});SystemClock.sleep(300);
    }
    private void testCalendarAndShelves() throws Exception {
        SharedPreferences p = getTargetContext().getSharedPreferences("regression_isolated", 0);
        p.edit().clear().commit();
        check(ReadingStatsStore.booksForDay(p,"2026-09-05").isEmpty(), "fresh calendar");
        p.edit().putString("reading_stats_day_books_json", "{\"2026-09-05\":{\"a.epub\":100}}")
          .putString("reading_stats_day_notes_json", "{\"2026-09-05\":\"local\"}").commit();
        org.json.JSONObject remote = new org.json.JSONObject();
        remote.put("reading_stats_day_books_json", new org.json.JSONObject().put("t","s").put("v","{\"2026-09-05\":{\"a.epub\":80,\"b.epub\":200},\"2026-09-04\":{\"c.epub\":300}}"));
        remote.put("reading_stats_day_notes_json", new org.json.JSONObject().put("t","s").put("v","{\"2026-09-05\":\"remote conflict\",\"2026-09-04\":\"remote note\"}"));
        CloudMergePolicy.mergePreferences(remote,p);
        check(ReadingStatsStore.booksForDay(p,"2026-09-05").size()==2,"calendar books union");
        check(ReadingStatsStore.bookTimeForDay(p,"2026-09-05","a.epub")==100,"duration not reduced");
        check(ReadingStatsStore.dailyNote(p,"2026-09-05").equals("local"),"local note conflict");
        check(ReadingStatsStore.dailyNote(p,"2026-09-04").equals("remote note"),"other date imported");
        ReadingStatsStore.setDailyNote(p,"2026-09-04","");
        CloudMergePolicy.mergePreferences(remote,p);
        check(ReadingStatsStore.dailyNote(p,"2026-09-04").isEmpty(),"deleted note stays deleted");
        LibraryShelfStore.createShelf(p,"Old");
        check(LibraryShelfStore.renameShelf(p,"Old","New"),"shelf rename");
        check(LibraryShelfStore.deleteShelf(p,"New"),"shelf delete");
        report.append("calendar merge / deletion / fresh defaults / shelves: PASS\n");
    }
    private File fixture() throws Exception {
        File dir=new File(getTargetContext().getFilesDir(),"library");dir.mkdirs();File f=new File(dir,"navigation-regression.epub");
        StringBuilder body=new StringBuilder();
        for(int i=0;i<110;i++) {
            body.append("<p>Paragraph ").append(i).append(". Reading navigation must preserve the exact reference and the page. This paragraph provides enough flowing text for a long chapter with many pages.</p>");
            if(i==15)body.append("<p>First reference <a id='ref1' epub:type='noteref' href='notes.xhtml#n1'><sup>1</sup></a></p>");
            if(i==70)body.append("<p>Queen of Scotland <a id='ref2' href='notes.xhtml#2'><sup>2</sup></a></p>");
        }
        try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))) {
            entry(z,"mimetype","application/epub+zip");
            entry(z,"META-INF/container.xml","<container xmlns='urn:oasis:names:tc:opendocument:xmlns:container'><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>");
            entry(z,"book.opf","<package xmlns='http://www.idpf.org/2007/opf' version='3.0'><metadata xmlns:dc='http://purl.org/dc/elements/1.1/'><dc:title>Navigation regression</dc:title></metadata><manifest><item id='i' href='intro.xhtml' media-type='application/xhtml+xml'/><item id='c' href='chapter.xhtml' media-type='application/xhtml+xml'/><item id='n' href='notes.xhtml' media-type='application/xhtml+xml'/></manifest><spine><itemref idref='i'/><itemref idref='c'/><itemref idref='n'/></spine></package>");
            entry(z,"intro.xhtml",html("Introduction","<h1>Introduction</h1><p>Open the next chapter.</p>"));
            entry(z,"chapter.xhtml",html("Chapter One",body.toString()));
            entry(z,"notes.xhtml",html("Notes","<h1>Notes</h1><p id='n1'><a id='back1' role='doc-backlink' href='chapter.xhtml#ref1'>←1</a> First note.</p><p id='2'><a id='back2' role='doc-backlink' href='chapter.xhtml#ref2'>←2</a> Queen of Scotland.</p>"));
        }
        return f;
    }
    private String html(String title,String body){return "<html xmlns='http://www.w3.org/1999/xhtml' xmlns:epub='http://www.idpf.org/2007/ops'><head><title>"+title+"</title></head><body>"+body+"</body></html>";}
    private void entry(ZipOutputStream z,String name,String value)throws Exception{z.putNextEntry(new ZipEntry(name));z.write(value.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
}
