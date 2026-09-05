from pathlib import Path

p = Path('app/src/main/java/com/whisper/wowreader/BookReaderActivity.java')
s = p.read_text()

# Version.
g = Path('app/build.gradle')
b = g.read_text()
assert 'versionCode 48' in b and "versionName '2.17.8'" in b
b = b.replace('versionCode 48', 'versionCode 49', 1)
b = b.replace("versionName '2.17.8'", "versionName '2.17.9'", 1)
g.write_text(b)

# Exact-backlink state. This is transient and never becomes a saved reading position until return completes.
field = '    private boolean footnoteReturnPending = false;'
assert field in s
s = s.replace(field, field + '\n    private boolean footnoteExactBacklinkPending = false;', 1)

# Install link interception on BOTH the active and preloaded WebViews. Previously a preloaded chapter
# could be activated without ever receiving the footnote click listener.
needle = '''                super.onPageFinished(view, url);\n                if (view != webView) {'''
assert needle in s
s = s.replace(needle, '''                super.onPageFinished(view, url);\n                installReaderLinkNavigation(view);\n                if (view != webView) {''', 1)

start = s.index('    private void installReaderLinkNavigation() {')
end = s.index('    private void requestFootnotePreview(', start)
new_install = r'''    private void installReaderLinkNavigation() {
        installReaderLinkNavigation(webView);
    }

    private void installReaderLinkNavigation(WebView targetView) {
        if (targetView == null) return;
        String js = "(function(){try{" +
                "if(window.__wowReaderLinkNavInstalled)return true;window.__wowReaderLinkNavInstalled=true;" +
                "document.addEventListener('click',function(ev){try{" +
                "var t=ev.target,a=t&&t.closest?t.closest('a[href]'):null;if(!a)return;" +
                "var href=a.getAttribute('href')||'',ep=a.getAttribute('epub:type')||a.getAttribute('type')||'';" +
                "try{ep=ep||a.getAttributeNS('http://www.idpf.org/2007/ops','type')||'';}catch(_e){}" +
                "var role=a.getAttribute('role')||'',rel=a.getAttribute('rel')||'',cls=(typeof a.className==='string'?a.className:'');" +
                "var sid='',n=a;for(var i=0;i<6&&n;i++,n=n.parentElement){if(n.id){sid=n.id;break;}}" +
                "var label=(a.textContent||'').replace(/\\s+/g,' ').trim();" +
                "if(WoW.onReaderLinkTap(href,ep,role,rel,cls,sid,label)){ev.preventDefault();ev.stopImmediatePropagation();return false;}" +
                "}catch(_e){}},true);return true;}catch(e){return false;}})()";
        try { targetView.evaluateJavascript(js, null); } catch (Exception ignored) {}
    }

'''
s = s[:start] + new_install + s[end:]

# Keep the source snapshot armed while the preview is visible. Do not expire it on a timer:
# a reader may leave the card open before choosing Show on page.
start = s.index('    private synchronized void armFootnoteReturn(String sourceId) {')
end = s.index('    private synchronized void onReaderVisitedUrl(', start)
new_arm = r'''    private synchronized void armFootnoteReturn(String sourceId) {
        if (webView == null || spine.isEmpty() || footnoteNavigationActive) return;
        footnoteReturnSpine = currentSpine;
        footnoteReturnProgressPermille = currentProgressPermille;
        footnoteReturnPage = currentPageInChapter;
        footnoteReturnSourceId = sourceId == null ? "" : sourceId.trim();
        String url = webView.getUrl();
        footnoteReturnSourceUrl = url == null ? "" : url;
        footnoteReturnArmed = true;
        footnoteReturnPending = false;
        footnoteExactBacklinkPending = false;
        footnoteArmToken++;
    }

'''
s = s[:start] + new_arm + s[end:]

# Preview overlay + Play Books-style Show on page. Closing the card disarms the transient source;
# Show on page preserves it and starts controlled note navigation.
start = s.index('    private void dismissFootnotePreview() {')
end = s.index('    private void navigateToFootnote(ReaderSearchIndex.Footnote note) {', start)
new_preview = r'''    private void dismissFootnotePreview() {
        if (footnotePreviewOverlay == null) return;
        try {
            ViewGroup parent = (ViewGroup) footnotePreviewOverlay.getParent();
            if (parent != null) parent.removeView(footnotePreviewOverlay);
        } catch (Exception ignored) {}
        footnotePreviewOverlay = null;
    }

    private void cancelFootnotePreview() {
        dismissFootnotePreview();
        if (!footnoteNavigationActive && !footnoteReturnPending && !footnoteExactBacklinkPending) {
            footnoteReturnArmed = false;
            footnoteArmToken++;
        }
    }

    private void showFootnotePreview(ReaderSearchIndex.Footnote note, String label) {
        if (isFinishing() || note == null || root == null) return;
        dismissFootnotePreview();

        final FrameLayout overlay = new FrameLayout(this);
        footnotePreviewOverlay = overlay;
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setBackgroundColor(Color.argb(22, 0, 0, 0));
        overlay.setOnClickListener(v -> cancelFootnotePreview());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(10), dp(18), dp(16));
        card.setBackground(glassPanel(readerPanelBase(), dp(24), readerPanelStroke()));
        card.setElevation(dp(16));
        card.setClickable(true);
        card.setOnClickListener(v -> { });

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
        close.setOnClickListener(v -> cancelFootnotePreview());
        head.addView(close, new LinearLayout.LayoutParams(dp(42), dp(40)));
        card.addView(head);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        TextView body = new TextView(this);
        String text = cleanFootnoteDisplayText(note.text);
        if (text.length() > 7000) text = text.substring(0, 7000).trim() + "…";
        if (text.isEmpty()) text = "Footnote text could not be previewed.";
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
        show.setOnClickListener(v -> {
            ReaderSearchIndex.Footnote target = footnotePreviewNote;
            if (target == null) return;
            navigateToFootnote(target);
            dismissFootnotePreview();
        });
        LinearLayout.LayoutParams showLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        showLp.topMargin = dp(8);
        card.addView(show, showLp);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        cardLp.leftMargin = dp(4);
        cardLp.rightMargin = dp(4);
        cardLp.bottomMargin = dp(4);
        overlay.addView(card, cardLp);
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.bringToFront();
    }

'''
s = s[:start] + new_preview + s[end:]

# Add robust backlink classification and exact internal-link navigation before reference detection.
marker = '    private boolean looksLikeFootnoteReference(String href, String epubType, String role, String rel, String cssClass) {'
idx = s.index(marker)
helpers = r'''    private String footnoteHrefFragment(String href) {
        if (href == null) return "";
        int hash = href.indexOf('#');
        if (hash < 0 || hash + 1 >= href.length()) return "";
        try { return Uri.decode(href.substring(hash + 1)).trim(); }
        catch (Exception ignored) { return href.substring(hash + 1).trim(); }
    }

    private boolean looksLikeExplicitBacklinkHref(String href) {
        String frag = navLower(footnoteHrefFragment(href)).replace('-', '_');
        if (frag.isEmpty()) return false;
        return frag.startsWith("_ftnref") || frag.startsWith("ftnref") ||
                frag.startsWith("_fnref") || frag.startsWith("fnref") ||
                frag.startsWith("_ednref") || frag.startsWith("ednref") ||
                frag.startsWith("noteref") || frag.startsWith("note_ref") ||
                frag.contains("footnote_back") || frag.contains("note_back") || frag.contains("backlink");
    }

    private boolean isNotesSpine(int index) {
        if (index < 0 || index >= spine.size()) return false;
        String title = index < chapterTitles.size() ? chapterTitles.get(index) : "";
        String file = spine.get(index) == null ? "" : spine.get(index).getName();
        String meta = navLower(title + " " + file).replace('_', ' ').replace('-', ' ').replace('.', ' ');
        return meta.matches(".*\\b(footnotes?|endnotes?|notes?)\\b.*");
    }

    private void finishExactFootnoteBacklink() {
        if (!footnoteExactBacklinkPending) return;
        footnoteExactBacklinkPending = false;
        if (webView == null) {
            footnoteNavigationActive = false;
            footnoteReturnArmed = false;
            footnoteReturnPending = false;
            return;
        }
        webView.postDelayed(() -> {
            footnoteNavigationActive = false;
            footnoteReturnArmed = false;
            footnoteReturnPending = false;
            updateEpubProgress(currentProgressPermille);
            saveEpubStateOnly();
            updateBookmarkIcon();
        }, 150L);
    }

    private boolean navigateExactFootnoteBacklink(String href) {
        if (webView == null || spine.isEmpty() || href == null || href.trim().isEmpty()) return false;
        int target = ReaderSearchIndex.resolveTargetSpine(spine, currentSpine, href);
        String fragment = footnoteHrefFragment(href);
        if (target < 0 || target >= spine.size() || fragment.isEmpty()) return false;
        footnoteNavigationActive = true;
        footnoteReturnArmed = false;
        footnoteReturnPending = false;
        footnoteExactBacklinkPending = true;
        footnoteArmToken++;
        pendingTocFragment = fragment;
        if (target == currentSpine) {
            jumpToPendingTocFragment(this::finishExactFootnoteBacklink);
            return true;
        }
        currentSpine = target;
        currentProgressPermille = 0;
        loadCurrentEpubChapter();
        return true;
    }

    private void handleFootnoteBacklink(String href) {
        if (navigateExactFootnoteBacklink(href)) return;
        restoreFootnoteReturn();
    }

'''
s = s[:idx] + helpers + s[idx:]

# Explicit backlink ids such as _ftnref2 must never be reclassified as a new footnote reference.
needle = marker + '\n        String meta = navLower(epubType + " " + role + " " + rel + " " + cssClass);'
assert needle in s
s = s.replace(needle, marker + '\n        if (looksLikeExplicitBacklinkHref(href)) return false;\n        String meta = navLower(epubType + " " + role + " " + rel + " " + cssClass);', 1)

# Replace backlink recognition: works both after Show on page and when a user manually opens a Notes chapter.
start = s.index('    private boolean looksLikeFootnoteBacklink(String href, String epubType, String role, String rel, String cssClass) {')
end = s.index('    private synchronized void armFootnoteReturn(', start)
new_backlink = r'''    private boolean looksLikeFootnoteBacklink(String href, String epubType, String role, String rel, String cssClass) {
        String meta = navLower(epubType + " " + role + " " + rel + " " + cssClass);
        if (meta.contains("backlink") || meta.contains("doc-backlink") || meta.contains("footnote-back") ||
                meta.contains("note-back") || meta.contains("fnback")) return true;
        if (looksLikeExplicitBacklinkHref(href)) return true;

        String source = footnoteReturnSourceId == null ? "" : footnoteReturnSourceId.trim();
        if (!source.isEmpty() && href != null) {
            String fragment = footnoteHrefFragment(href);
            if (source.equals(fragment)) return true;
        }

        if (href != null && href.indexOf('#') >= 0 && !spine.isEmpty()) {
            int target = ReaderSearchIndex.resolveTargetSpine(spine, currentSpine, href);
            if (target >= 0 && target < spine.size()) {
                if (footnoteNavigationActive && footnoteReturnSpine >= 0 && target == footnoteReturnSpine) return true;
                if (isNotesSpine(currentSpine) && target != currentSpine && !isNotesSpine(target)) return true;
            }
        }
        return false;
    }

'''
s = s[:start] + new_backlink + s[end:]

# Restore from Android Back: prefer the exact source anchor. Only use saved page/progress as fallback
# when the EPUB does not provide a source id.
start = s.index('    private void restoreFootnoteReturn() {')
end = s.index('    private void finishFootnoteReturnOnReady(', start)
new_restore = r'''    private void restoreFootnoteReturn() {
        runOnUiThread(() -> {
            if ((!footnoteNavigationActive && !footnoteReturnArmed && !footnoteReturnPending) || webView == null || spine.isEmpty()) return;
            int targetSpine = Math.max(0, Math.min(spine.size() - 1, footnoteReturnSpine));
            int targetProgress = Math.max(0, Math.min(1000, footnoteReturnProgressPermille));
            int targetPage = Math.max(1, footnoteReturnPage);
            String sourceId = footnoteReturnSourceId == null ? "" : footnoteReturnSourceId.trim();
            footnoteReturnArmed = false;
            footnoteArmToken++;
            currentSpine = targetSpine;
            currentProgressPermille = targetProgress;

            String expected = Uri.fromFile(spine.get(targetSpine)).toString();
            String actual = webView.getUrl();
            if (actual != null) { int hash = actual.indexOf('#'); if (hash >= 0) actual = actual.substring(0, hash); }
            boolean sameDocument = expected.equals(actual);

            if (!sourceId.isEmpty()) {
                footnoteNavigationActive = true;
                footnoteReturnPending = false;
                footnoteExactBacklinkPending = true;
                pendingTocFragment = sourceId;
                if (!sameDocument) {
                    loadCurrentEpubChapter();
                    return;
                }
                jumpToPendingTocFragment(this::finishExactFootnoteBacklink);
                return;
            }

            footnoteNavigationActive = false;
            footnoteReturnPending = true;
            footnoteExactBacklinkPending = false;
            if (!sameDocument) {
                pendingTocFragment = "";
                loadCurrentEpubChapter();
                return;
            }
            finishFootnoteReturnOnReady(targetPage, targetProgress);
        });
    }

'''
s = s[:start] + new_restore + s[end:]

# When an exact backlink is pending, the fragment jump is authoritative. Do NOT overwrite it with the
# old approximate page/progress snapshot (that was the main reason return landed at chapter starts/wrong pages).
old = '''        jumpToPendingTocFragment(() -> {\n            if (footnoteReturnPending) finishFootnoteReturnOnReady(footnoteReturnPage, footnoteReturnProgressPermille);'''
assert old in s
s = s.replace(old, '''        jumpToPendingTocFragment(() -> {\n            if (footnoteExactBacklinkPending) finishExactFootnoteBacklink();\n            else if (footnoteReturnPending) finishFootnoteReturnOnReady(footnoteReturnPage, footnoteReturnProgressPermille);''', 1)

# Scroll mode keeps its tap-to-previous/next-chapter behavior. Link taps are now isolated separately.
old_scroll = '''                } else {\n                    toggleControls();\n                }'''
assert old_scroll in s
s = s.replace(old_scroll, '''                } else {\n                    if (ratio < 0.24f) navigateChapter(-1, true);\n                    else if (ratio > 0.76f) navigateChapter(1, false);\n                    else toggleControls();\n                }''', 1)

# DOM hit test coordinates are CSS pixels, while MotionEvent coordinates are physical WebView pixels.
old_hit = '"var n=document.elementFromPoint(" + px + "," + py + ");" +'
assert old_hit in s
s = s.replace(old_hit, '"var d=window.devicePixelRatio||1,n=document.elementFromPoint((" + px + ")/d,(" + py + ")/d);" +', 1)

# A fling that starts on a link or inside the footnote suppression window must not turn a page.
old_fling = '''                if (!"page".equals(readingMode) || e1 == null || e2 == null || chapterLoading || pageTurnLocked)\n                    return false;'''
assert old_fling in s
s = s.replace(old_fling, '''                if (!"page".equals(readingMode) || e1 == null || e2 == null || chapterLoading || pageTurnLocked ||\n                        readerTouchStartedOnLink || android.os.SystemClock.uptimeMillis() < footnoteTapSuppressUntilMs)\n                    return false;''', 1)

# Replace bridge link handling. Backlinks are checked FIRST and in every navigation state.
start = s.index('        @JavascriptInterface\n        public boolean onReaderLinkTap(')
end = s.index('        @JavascriptInterface\n        public void onSelection(', start)
new_bridge = r'''        @JavascriptInterface
        public boolean onReaderLinkTap(String href, String epubType, String role, String rel, String cssClass, String sourceId, String label) {
            lastReaderLinkTapMs = android.os.SystemClock.elapsedRealtime();
            if (owner != webView) return false;
            final String targetHref = href == null ? "" : href;
            final String targetLabel = label == null ? "" : label;
            final String targetSourceId = sourceId == null ? "" : sourceId;

            if (looksLikeFootnoteBacklink(targetHref, epubType, role, rel, cssClass)) {
                footnoteTapSuppressUntilMs = android.os.SystemClock.uptimeMillis() + 1800L;
                runOnUiThread(() -> {
                    if (isFinishing() || owner != webView) return;
                    handleFootnoteBacklink(targetHref);
                });
                return true;
            }

            if (looksLikeFootnoteReference(targetHref, epubType, role, rel, cssClass)) {
                footnoteTapSuppressUntilMs = android.os.SystemClock.uptimeMillis() + 1800L;
                runOnUiThread(() -> {
                    if (isFinishing() || owner != webView) return;
                    armFootnoteReturn(targetSourceId);
                    requestFootnotePreview(targetHref, targetLabel, targetSourceId);
                });
                return true;
            }
            return false;
        }

'''
s = s[:start] + new_bridge + s[end:]

# Back closes the preview without leaving a stale return snapshot.
s = s.replace('if (footnotePreviewOverlay != null) { dismissFootnotePreview(); return; }',
              'if (footnotePreviewOverlay != null) { cancelFootnotePreview(); return; }', 1)

# Sanity guards.
assert 'Show on page' in s
assert 'installReaderLinkNavigation(view);' in s
assert 'looksLikeExplicitBacklinkHref' in s
assert 'handleFootnoteBacklink' in s
assert 'footnoteExactBacklinkPending' in s
assert 'navigateChapter(-1, true)' in s
assert 'requestFootnotePreview(targetHref, targetLabel, targetSourceId)' in s

p.write_text(s)
