from pathlib import Path

p = Path('app/src/main/java/com/whisper/wowreader/BookReaderActivity.java')
g = Path('app/build.gradle')
w = Path('.github/workflows/build-apk.yml')
template = Path('tools/v45/ReaderSearchIndex.java.txt')
out_helper = Path('app/src/main/java/com/whisper/wowreader/ReaderSearchIndex.java')

s = p.read_text()
grad = g.read_text()
wf = w.read_text()


def once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, got {count}')
    return text.replace(old, new, 1)


def between(text, start, end, new, label):
    a = text.find(start)
    if a < 0:
        raise SystemExit(f'{label}: start not found')
    b = text.find(end, a)
    if b < 0:
        raise SystemExit(f'{label}: end not found')
    return text[:a] + new + text[b:]


grad = once(grad, 'versionCode 44', 'versionCode 45', 'versionCode')
grad = once(grad, "versionName '2.17.4'", "versionName '2.17.5'", 'versionName')

field_marker = '    private String footnotePreviewLabel = "";\n'
field_new = '''    private String footnotePreviewLabel = "";
    private ReaderSearchIndex.Footnote footnotePreviewNote = null;
    private boolean footnoteReturnPending = false;

    // Search remains transient until the user intentionally closes it on a result page.
    private Dialog bookSearchDialog = null;
    private final List<ReaderSearchIndex.Hit> bookSearchResults = new ArrayList<>();
    private String bookSearchQuery = "";
    private boolean searchNavigationActive = false;
    private int searchCurrentIndex = -1;
    private int searchReturnSpine = -1;
    private int searchReturnProgressPermille = 0;
    private int searchReturnPage = 1;
    private String pendingSearchQuery = "";
    private int pendingSearchOccurrence = -1;
    private LinearLayout searchNavigationBar = null;
    private TextView searchNavigationLabel = null;
'''
s = once(s, field_marker, field_new, 'reader navigation fields')

old_ref = '''        return frag.startsWith("fn") || frag.startsWith("ftn") || frag.contains("footnote") ||
                frag.startsWith("note-") || frag.startsWith("note_") || frag.startsWith("endnote");'''
new_ref = '''        frag = Uri.decode(frag).toLowerCase(Locale.ROOT);
        return frag.startsWith("fn") || frag.startsWith("_fn") || frag.startsWith("ftn") || frag.startsWith("_ftn") ||
                frag.contains("footnote") || frag.contains("noteref") || frag.startsWith("note") ||
                frag.startsWith("endnote") || frag.startsWith("_edn");'''
s = once(s, old_ref, new_ref, 'expanded footnote ids')

foot_block = '''    private void requestFootnotePreview(String href, String label) {
        if (webView == null || href == null || href.trim().isEmpty() || spine.isEmpty()) return;
        footnotePreviewHref = href.trim();
        footnotePreviewLabel = label == null ? "" : label.trim();
        final int sourceSpine = currentSpine;
        final String sourceId = footnoteReturnSourceId;
        new Thread(() -> {
            ReaderSearchIndex.Footnote note = ReaderSearchIndex.resolveFootnote(spine, sourceSpine, footnotePreviewHref, sourceId);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                footnotePreviewNote = note;
                showFootnotePreview(note, footnotePreviewLabel);
            });
        }, "wow-footnote-preview").start();
    }

    private void showFootnotePreview(ReaderSearchIndex.Footnote note, String label) {
        if (isFinishing() || note == null) return;
        if (footnotePreviewDialog != null) {
            try { footnotePreviewDialog.dismiss(); } catch (Exception ignored) {}
            footnotePreviewDialog = null;
        }
        final Dialog dialog = new Dialog(this);
        footnotePreviewDialog = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(10), dp(18), dp(14));
        card.setBackground(glassPanel(readerPanelBase(), dp(24), readerPanelStroke()));
        card.setElevation(dp(16));

        View handle = new View(this);
        handle.setBackground(glassPanel(readerPanelStroke(), dp(3), Color.TRANSPARENT));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(38), dp(5));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(8);
        card.addView(handle, handleLp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        String cleanLabel = label == null ? "" : label.trim();
        title.setText(cleanLabel.isEmpty() ? "Footnote" : "Footnote " + cleanLabel);
        title.setTextSize(16f);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setTextColor(readerPanelText());
        head.addView(title, new LinearLayout.LayoutParams(0, dp(40), 1f));
        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(24f);
        close.setTextColor(readerPanelSubText());
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(42), dp(40)));
        card.addView(head);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        TextView body = new TextView(this);
        String text = note.text == null ? "" : note.text.trim();
        if (text.length() > 7000) text = text.substring(0, 7000).trim() + "…";
        if (text.isEmpty()) text = "Footnote text could not be previewed. You can still open it on the page.";
        body.setText(text);
        body.setTextSize(15.5f);
        body.setTextColor(readerPanelText());
        body.setLineSpacing(dp(3), 1.08f);
        body.setPadding(dp(2), dp(4), dp(2), dp(8));
        scroll.addView(body, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int maxBody = Math.max(dp(100), (int) (getResources().getDisplayMetrics().heightPixels * 0.34f));
        card.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxBody));

        TextView show = new TextView(this);
        show.setText("Show on page");
        show.setTextSize(14.5f);
        show.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        show.setTextColor(readerAccent());
        show.setGravity(Gravity.CENTER);
        show.setBackground(glassPanel(readerSelectedSurface(), dp(20), readerPanelStroke()));
        show.setOnClickListener(v -> { dialog.dismiss(); navigateToFootnote(note); });
        LinearLayout.LayoutParams showLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        showLp.topMargin = dp(8);
        card.addView(show, showLp);

        dialog.setContentView(card);
        dialog.setOnDismissListener(d -> { if (footnotePreviewDialog == dialog) footnotePreviewDialog = null; });
        dialog.show();
        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            win.setDimAmount(0.10f);
            WindowManager.LayoutParams lp = win.getAttributes();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.gravity = Gravity.BOTTOM;
            win.setAttributes(lp);
        }
    }

    private void navigateToFootnote(ReaderSearchIndex.Footnote note) {
        if (webView == null || note == null || spine.isEmpty()) return;
        footnoteNavigationActive = true;
        footnoteReturnPending = false;
        footnoteReturnArmed = false;
        footnoteArmToken++;
        int target = Math.max(0, Math.min(spine.size() - 1, note.spineIndex));
        pendingTocFragment = note.fragment == null ? "" : note.fragment;
        if (target == currentSpine) {
            jumpToPendingTocFragment(() -> updateBookmarkIcon());
            return;
        }
        currentSpine = target;
        currentProgressPermille = 0;
        loadCurrentEpubChapter();
    }

'''
s = between(s, '    private void requestFootnotePreview(', '    private static String navLower', foot_block, 'footnote preview methods')

old_bridge = '''            if (looksLikeFootnoteReference(href, epubType, role, rel, cssClass)) { armFootnoteReturn(sourceId); requestFootnotePreview(href, label); return true; }
            if (footnoteNavigationActive && looksLikeFootnoteBacklink(href, epubType, role, rel, cssClass)) { restoreFootnoteReturn(); return true; }
            return false;'''
new_bridge = '''            if (footnoteNavigationActive && looksLikeFootnoteBacklink(href, epubType, role, rel, cssClass)) { restoreFootnoteReturn(); return true; }
            if (looksLikeFootnoteReference(href, epubType, role, rel, cssClass)) { armFootnoteReturn(sourceId); requestFootnotePreview(href, label); return true; }
            return false;'''
s = once(s, old_bridge, new_bridge, 'footnote bridge order')

restore_block = '''    private void restoreFootnoteReturn() {
        runOnUiThread(() -> {
            if ((!footnoteNavigationActive && !footnoteReturnArmed && !footnoteReturnPending) || webView == null || spine.isEmpty()) return;
            int targetSpine = Math.max(0, Math.min(spine.size() - 1, footnoteReturnSpine));
            int targetProgress = Math.max(0, Math.min(1000, footnoteReturnProgressPermille));
            int targetPage = Math.max(1, footnoteReturnPage);
            footnoteNavigationActive = false;
            footnoteReturnArmed = false;
            footnoteReturnPending = true;
            footnoteArmToken++;
            currentSpine = targetSpine;
            currentProgressPermille = targetProgress;

            String expected = Uri.fromFile(spine.get(targetSpine)).toString();
            String actual = webView.getUrl();
            if (actual != null) { int hash = actual.indexOf('#'); if (hash >= 0) actual = actual.substring(0, hash); }
            boolean sameDocument = expected.equals(actual);
            if (!sameDocument) {
                pendingTocFragment = footnoteReturnSourceId == null ? "" : footnoteReturnSourceId;
                loadCurrentEpubChapter();
                return;
            }
            finishFootnoteReturnOnReady(targetPage, targetProgress);
        });
    }

    private void finishFootnoteReturnOnReady(int targetPage, int targetProgress) {
        if (webView == null) { footnoteReturnPending = false; return; }
        if ("page".equals(readingMode)) {
            int pageZero = Math.max(0, targetPage - 1);
            String jump = "(function(){var st=window.__wowPageEngine;if(!st||st.mode!=='page')return false;" +
                    "st.page=st.clamp(" + pageZero + ",0,(st.count||1)-1);st.apply(false);st.report();return true;})()";
            try { webView.evaluateJavascript(jump, null); } catch (Exception ignored) {}
        } else {
            String jump = "(function(){var h=Math.max(0,document.documentElement.scrollHeight-window.innerHeight);" +
                    "window.scrollTo(0,h*" + (targetProgress / 1000.0) + ");return true;})()";
            try { webView.evaluateJavascript(jump, null); } catch (Exception ignored) {}
        }
        currentProgressPermille = targetProgress;
        footnoteReturnPending = false;
        updateEpubProgress(targetProgress);
        saveEpubStateOnly();
    }

'''
s = between(s, '    private void restoreFootnoteReturn() {', '    private ReaderWebView createPreloadWebView()', restore_block, 'footnote return')

s = once(s,
         'if (!footnoteNavigationActive) ReadingProgressStore.set(prefs, bookFile.getName(), percent);',
         'if (!footnoteNavigationActive && !footnoteReturnPending && !searchNavigationActive) ReadingProgressStore.set(prefs, bookFile.getName(), percent);',
         'progress transient guard')
s = once(s,
         'if (footnoteNavigationActive) return;\n        prefs.edit()',
         'if (footnoteNavigationActive || footnoteReturnPending || searchNavigationActive) return;\n        prefs.edit()',
         'state transient guard')

old_ready = '''        jumpToPendingTocFragment(() -> {
            if (paperGestureChapterBoundary && paperGestureReleased && paperGestureCommit) {'''
new_ready = '''        jumpToPendingTocFragment(() -> {
            if (footnoteReturnPending) finishFootnoteReturnOnReady(footnoteReturnPage, footnoteReturnProgressPermille);
            if (searchNavigationActive && pendingSearchOccurrence >= 0) applyPendingSearchHit();
            if (paperGestureChapterBoundary && paperGestureReleased && paperGestureCommit) {'''
s = once(s, old_ready, new_ready, 'page ready transient navigation')

search_block = '''    private void searchInBook() {
        if (isPdf || webView == null || spine.isEmpty()) return;
        if (!searchNavigationActive) {
            searchReturnSpine = currentSpine;
            searchReturnProgressPermille = currentProgressPermille;
            searchReturnPage = currentPageInChapter;
        }
        showBookSearch(bookSearchQuery, !bookSearchResults.isEmpty());
    }

    private void showBookSearch(String initialQuery, boolean useCachedResults) {
        if (isFinishing()) return;
        if (bookSearchDialog != null) {
            try { bookSearchDialog.dismiss(); } catch (Exception ignored) {}
            bookSearchDialog = null;
        }
        hideSearchNavigationBar();
        final Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        bookSearchDialog = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);

        int bg = Color.rgb(22, 23, 26);
        int surface = Color.rgb(35, 36, 40);
        int text = Color.rgb(244, 245, 247);
        int sub = Color.rgb(178, 181, 189);
        int accent = Color.rgb(128, 203, 196);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(bg);
        page.setPadding(dp(10), dp(12), dp(10), dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackground(glassPanel(surface, dp(22), Color.rgb(55, 57, 64)));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(32);
        back.setTextColor(text);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> { dialog.dismiss(); restorePreSearchLocation(); });
        header.addView(back, new LinearLayout.LayoutParams(dp(50), dp(54)));

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Search in book");
        input.setHintTextColor(sub);
        input.setTextColor(text);
        input.setTextSize(17);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(4), 0, dp(4), 0);
        input.setText(initialQuery == null ? "" : initialQuery);
        input.setSelection(input.length());
        header.addView(input, new LinearLayout.LayoutParams(0, dp(54), 1f));

        TextView clear = new TextView(this);
        clear.setText("×");
        clear.setTextSize(24);
        clear.setTextColor(sub);
        clear.setGravity(Gravity.CENTER);
        clear.setOnClickListener(v -> input.setText(""));
        header.addView(clear, new LinearLayout.LayoutParams(dp(50), dp(54)));
        page.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        TextView status = new TextView(this);
        status.setTextSize(12.5f);
        status.setTextColor(sub);
        status.setPadding(dp(12), dp(12), dp(12), dp(8));
        page.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(dp(4), 0, dp(4), dp(20));
        scroll.addView(results, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dialog.setContentView(page);
        dialog.setOnDismissListener(d -> { if (bookSearchDialog == dialog) bookSearchDialog = null; });
        dialog.show();
        Window win = dialog.getWindow();
        if (win != null) {
            win.setStatusBarColor(bg);
            win.setNavigationBarColor(bg);
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }

        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable[] pending = new Runnable[1];
        final int[] localGeneration = {0};
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                if (pending[0] != null) handler.removeCallbacks(pending[0]);
                String q = value == null ? "" : value.toString().trim();
                bookSearchQuery = q;
                if (q.isEmpty()) {
                    bookSearchResults.clear();
                    results.removeAllViews();
                    status.setText("Type a word or phrase");
                    return;
                }
                int token = ++localGeneration[0];
                pending[0] = () -> performBookSearch(q, token, localGeneration, results, status, accent, text, sub, surface, dialog);
                handler.postDelayed(pending[0], 260L);
            }
            @Override public void afterTextChanged(android.text.Editable e) {}
        });

        if (useCachedResults && initialQuery != null && !initialQuery.trim().isEmpty() && !bookSearchResults.isEmpty()) {
            renderBookSearchResults(results, status, accent, text, sub, surface, dialog);
        } else if (initialQuery != null && !initialQuery.trim().isEmpty()) {
            int token = ++localGeneration[0];
            performBookSearch(initialQuery.trim(), token, localGeneration, results, status, accent, text, sub, surface, dialog);
        } else {
            status.setText("Type a word or phrase");
        }

        input.requestFocus();
        input.postDelayed(() -> {
            try {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            } catch (Exception ignored) {}
        }, 160L);
    }

    private void performBookSearch(String q, int token, int[] localGeneration, LinearLayout results, TextView status,
                                   int accent, int text, int sub, int surface, Dialog dialog) {
        status.setText("Searching the whole book…");
        results.removeAllViews();
        new Thread(() -> {
            List<ReaderSearchIndex.Hit> found = ReaderSearchIndex.search(spine, chapterTitles, q, 350);
            runOnUiThread(() -> {
                if (dialog != bookSearchDialog || !dialog.isShowing() || token != localGeneration[0]) return;
                bookSearchQuery = q;
                bookSearchResults.clear();
                bookSearchResults.addAll(found);
                renderBookSearchResults(results, status, accent, text, sub, surface, dialog);
            });
        }, "wow-book-search").start();
    }

    private void renderBookSearchResults(LinearLayout results, TextView status, int accent, int text, int sub, int surface, Dialog dialog) {
        results.removeAllViews();
        int count = bookSearchResults.size();
        status.setText(count == 0 ? "No matches" : count + (count == 1 ? " result" : " results") + " in this book");
        for (int i = 0; i < count; i++) {
            ReaderSearchIndex.Hit hit = bookSearchResults.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(13), dp(14), dp(13));
            row.setBackground(glassPanel(surface, dp(15), Color.rgb(52, 54, 60)));

            TextView snippet = new TextView(this);
            snippet.setTextSize(16f);
            snippet.setTextColor(text);
            snippet.setLineSpacing(dp(2), 1.05f);
            snippet.setText(highlightSearchText(hit.snippet, bookSearchQuery, accent));
            row.addView(snippet, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView where = new TextView(this);
            where.setText("⌕  " + hit.chapter);
            where.setTextSize(12.5f);
            where.setTextColor(sub);
            where.setPadding(0, dp(7), 0, 0);
            row.addView(where, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            final int index = i;
            row.setOnClickListener(v -> { dialog.dismiss(); navigateToSearchHit(index); });
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(8);
            results.addView(row, rowLp);
        }
    }

    private CharSequence highlightSearchText(String source, String query, int accent) {
        String value = source == null ? "" : source;
        android.text.SpannableString span = new android.text.SpannableString(value);
        if (query == null || query.trim().isEmpty()) return span;
        String low = value.toLowerCase(Locale.ROOT);
        String q = query.trim().toLowerCase(Locale.ROOT);
        int from = 0;
        while (from <= low.length() - q.length()) {
            int at = low.indexOf(q, from);
            if (at < 0) break;
            span.setSpan(new android.text.style.BackgroundColorSpan(Color.argb(105, Color.red(accent), Color.green(accent), Color.blue(accent))),
                    at, at + q.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            from = at + Math.max(1, q.length());
        }
        return span;
    }

    private void navigateToSearchHit(int index) {
        if (index < 0 || index >= bookSearchResults.size() || spine.isEmpty()) return;
        searchNavigationActive = true;
        searchCurrentIndex = index;
        ReaderSearchIndex.Hit hit = bookSearchResults.get(index);
        pendingSearchQuery = bookSearchQuery;
        pendingSearchOccurrence = hit.occurrence;
        hideControls();
        int target = Math.max(0, Math.min(spine.size() - 1, hit.spineIndex));
        if (target != currentSpine) {
            currentSpine = target;
            currentProgressPermille = 0;
            loadCurrentEpubChapter();
        } else {
            applyPendingSearchHit();
        }
        showSearchNavigationBar();
    }

    private void applyPendingSearchHit() {
        if (!searchNavigationActive || webView == null || pendingSearchOccurrence < 0 || pendingSearchQuery == null || pendingSearchQuery.isEmpty()) return;
        final String query = pendingSearchQuery;
        final int wanted = pendingSearchOccurrence;
        pendingSearchOccurrence = -1;
        String script = "(function(){try{" +
                "var root=document.getElementById('wow-page-flow')||document.body;if(!root)return 'no-root';" +
                "var old=root.querySelectorAll('span.wow-search-hit');for(var z=0;z<old.length;z++){var o=old[z],p=o.parentNode;while(o.firstChild)p.insertBefore(o.firstChild,o);p.removeChild(o);}" +
                "var q=" + jsQuote(query.toLowerCase(Locale.ROOT)) + ",target=" + wanted + ",seen=0,w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,null,false),n;" +
                "var pick=function(n,at){var r=document.createRange();r.setStart(n,at);r.setEnd(n,at+q.length);var sp=document.createElement('span');sp.className='wow-search-hit';sp.style.background='rgba(128,203,196,.50)';sp.style.borderRadius='3px';r.surroundContents(sp);" +
                "var st=window.__wowPageEngine;if(st&&st.mode==='page'){var cp=st.physical?st.physical():(st.page||0),bb=sp.getBoundingClientRect(),docX=(bb.left-(st.marginPx||0))+(cp*(st.step||window.innerWidth||1)),phys=Math.max(0,Math.floor((docX+2)/(st.step||window.innerWidth||1)));st.page=st.nearestLogical?st.nearestLogical(phys):phys;st.apply(false);st.report();}else sp.scrollIntoView({block:'center'});return true;};" +
                "while((n=w.nextNode())){var raw=n.nodeValue||'',low=raw.toLocaleLowerCase(),from=0,at;while((at=low.indexOf(q,from))>=0){if(seen===target)return pick(n,at)?'ok':'fail';seen++;from=at+Math.max(1,q.length);}}" +
                "return 'missing';}catch(e){return 'error';}})()";
        try { webView.evaluateJavascript(script, null); } catch (Exception ignored) {}
        updateSearchNavigationLabel();
    }

    private void showSearchNavigationBar() {
        if (root == null) return;
        if (searchNavigationBar == null) {
            LinearLayout bar = new LinearLayout(this);
            searchNavigationBar = bar;
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(dp(6), dp(4), dp(6), dp(4));
            bar.setBackground(glassPanel(Color.rgb(28, 29, 32), dp(20), Color.rgb(70, 72, 78)));
            bar.setElevation(dp(18));

            TextView close = new TextView(this);
            close.setText("×");
            close.setTextSize(25);
            close.setTextColor(Color.WHITE);
            close.setGravity(Gravity.CENTER);
            close.setOnClickListener(v -> closeSearchNavigation(false));
            bar.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));

            searchNavigationLabel = new TextView(this);
            searchNavigationLabel.setTextSize(13.5f);
            searchNavigationLabel.setTextColor(Color.WHITE);
            searchNavigationLabel.setMaxLines(2);
            searchNavigationLabel.setGravity(Gravity.CENTER_VERTICAL);
            bar.addView(searchNavigationLabel, new LinearLayout.LayoutParams(0, dp(48), 1f));

            TextView prev = new TextView(this);
            prev.setText("‹");
            prev.setTextSize(28);
            prev.setTextColor(Color.WHITE);
            prev.setGravity(Gravity.CENTER);
            prev.setOnClickListener(v -> {
                if (!bookSearchResults.isEmpty()) navigateToSearchHit((searchCurrentIndex - 1 + bookSearchResults.size()) % bookSearchResults.size());
            });
            TextView next = new TextView(this);
            next.setText("›");
            next.setTextSize(28);
            next.setTextColor(Color.WHITE);
            next.setGravity(Gravity.CENTER);
            next.setOnClickListener(v -> {
                if (!bookSearchResults.isEmpty()) navigateToSearchHit((searchCurrentIndex + 1) % bookSearchResults.size());
            });
            bar.addView(prev, new LinearLayout.LayoutParams(dp(48), dp(48)));
            bar.addView(next, new LinearLayout.LayoutParams(dp(48), dp(48)));

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58), Gravity.BOTTOM);
            lp.leftMargin = dp(12);
            lp.rightMargin = dp(12);
            lp.bottomMargin = dp(18);
            root.addView(bar, lp);
        }
        searchNavigationBar.setVisibility(View.VISIBLE);
        searchNavigationBar.bringToFront();
        updateSearchNavigationLabel();
    }

    private void updateSearchNavigationLabel() {
        if (searchNavigationLabel == null) return;
        int count = bookSearchResults.size();
        String where = currentSpine >= 0 && currentSpine < spine.size() ? chapterDisplayTitle(currentSpine) : "";
        searchNavigationLabel.setText(bookSearchQuery + "\n" + (searchCurrentIndex + 1) + " of " + Math.max(1, count) +
                (where.isEmpty() ? "" : " · " + where));
    }

    private void hideSearchNavigationBar() {
        if (searchNavigationBar != null) searchNavigationBar.setVisibility(View.GONE);
    }

    private void closeSearchNavigation(boolean restoreOriginal) {
        hideSearchNavigationBar();
        searchNavigationActive = false;
        pendingSearchOccurrence = -1;
        clearSearchHighlight();
        if (restoreOriginal) {
            restorePreSearchLocation();
        } else {
            updateEpubProgress(currentProgressPermille);
            saveEpubStateOnly();
            showControls();
        }
    }

    private void clearSearchHighlight() {
        if (webView == null) return;
        try {
            webView.evaluateJavascript("(function(){var a=document.querySelectorAll('span.wow-search-hit');for(var i=0;i<a.length;i++){var s=a[i],p=s.parentNode;while(s.firstChild)p.insertBefore(s.firstChild,s);p.removeChild(s);}return true;})()", null);
        } catch (Exception ignored) {}
    }

    private void restorePreSearchLocation() {
        hideSearchNavigationBar();
        searchNavigationActive = false;
        pendingSearchOccurrence = -1;
        clearSearchHighlight();
        if (searchReturnSpine < 0 || spine.isEmpty()) {
            showControls();
            return;
        }
        int target = Math.max(0, Math.min(spine.size() - 1, searchReturnSpine));
        currentProgressPermille = searchReturnProgressPermille;
        if (target != currentSpine) {
            currentSpine = target;
            loadCurrentEpubChapter();
        } else if ("page".equals(readingMode)) {
            int pageZero = Math.max(0, searchReturnPage - 1);
            try {
                webView.evaluateJavascript("(function(){var st=window.__wowPageEngine;if(!st)return;st.page=st.clamp(" + pageZero + ",0,(st.count||1)-1);st.apply(false);st.report();})()", null);
            } catch (Exception ignored) {}
        }
        showControls();
    }

'''
s = between(s, '    private void searchInBook() {', '    private void toggleBookmark()', search_block, 'book search')

old_back = '''    @Override
    public void onBackPressed() {
        if (!isPdf && footnoteNavigationActive) { restoreFootnoteReturn(); return; }
        if (!isPdf) saveEpubState();
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }'''
new_back = '''    @Override
    public void onBackPressed() {
        if (bookSearchDialog != null && bookSearchDialog.isShowing()) { bookSearchDialog.dismiss(); restorePreSearchLocation(); return; }
        if (!isPdf && searchNavigationActive) { showBookSearch(bookSearchQuery, true); return; }
        if (!isPdf && (footnoteNavigationActive || footnoteReturnPending)) { restoreFootnoteReturn(); return; }
        if (!isPdf) saveEpubState();
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }'''
s = once(s, old_back, new_back, 'back behavior')

wf = wf.replace('v2.17.4', 'v2.17.5')
wf = wf.replace('versionCode 44', 'versionCode 45')
wf = wf.replace("versionName '2.17.4'", "versionName '2.17.5'")
wf = wf.replace("versionCode='44'", "versionCode='45'")
wf = wf.replace("versionName='2.17.4'", "versionName='2.17.5'")
wf = wf.replace('WoW-Reader-Lab-v2.17.4-v44', 'WoW-Reader-Lab-v2.17.5-v45')
wf = wf.replace('test -s app/src/main/java/com/whisper/wowreader/ReadingProgressStore.java',
                'test -s app/src/main/java/com/whisper/wowreader/ReadingProgressStore.java\n          test -s app/src/main/java/com/whisper/wowreader/ReaderSearchIndex.java')
wf = wf.replace('grep -q "onReaderLinkTap" app/src/main/java/com/whisper/wowreader/BookReaderActivity.java',
                'grep -q "onReaderLinkTap" app/src/main/java/com/whisper/wowreader/BookReaderActivity.java\n          grep -q "Show on page" app/src/main/java/com/whisper/wowreader/BookReaderActivity.java\n          grep -q "renderBookSearchResults" app/src/main/java/com/whisper/wowreader/BookReaderActivity.java\n          grep -q "ReaderSearchIndex.search" app/src/main/java/com/whisper/wowreader/BookReaderActivity.java')

p.write_text(s)
g.write_text(grad)
w.write_text(wf)
out_helper.write_text(template.read_text())
