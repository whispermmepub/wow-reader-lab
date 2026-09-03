from pathlib import Path

P = Path('app/src/main/java/com/whisper/wowreader/BookReaderActivity.java')
text = P.read_text(encoding='utf-8')


def replace_block(src, start, end, replacement, label):
    a = src.find(start)
    if a < 0:
        raise SystemExit(f'missing start marker: {label}')
    b = src.find(end, a)
    if b < 0:
        raise SystemExit(f'missing end marker: {label}')
    return src[:a] + replacement.rstrip() + '\n\n' + src[b:]

# Allow the compact book popup on Home to open directly into reader settings.
anchor = '''        if (!isPdf && getIntent().getBooleanExtra("open_annotations", false)) {\n            root.postDelayed(() -> {\n                if (!isFinishing()) showAnnotations();\n            }, 700L);\n        }'''
replacement = anchor + '''\n        if (!isPdf && getIntent().getBooleanExtra("open_reader_settings", false)) {\n            root.postDelayed(() -> {\n                if (!isFinishing()) showReaderSettings();\n            }, 760L);\n        }'''
if anchor in text and 'open_reader_settings' not in text:
    text = text.replace(anchor, replacement, 1)

selection_setup = r'''        selectionBar = new LinearLayout(this);
        selectionBar.setOrientation(LinearLayout.HORIZONTAL);
        selectionBar.setGravity(Gravity.CENTER);
        selectionBar.setPadding(dp(6), dp(3), dp(6), dp(3));
        selectionBar.setBackground(glassPanel(readerPanelBase(), dp(18), readerPanelStroke()));
        selectionBar.setElevation(dp(12));
        selectionBar.addView(selectionActionButton("Highlight", SEL_HIGHLIGHT));
        selectionBar.addView(selectionActionButton("Note", SEL_NOTE));
        selectionBar.addView(selectionActionButton("Translate", SEL_TRANSLATE));
        selectionBar.addView(selectionActionButton("Copy", SEL_COPY));
        selectionBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams selectionLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(50), Gravity.TOP | Gravity.START);
        selectionLp.leftMargin = dp(12);
        selectionLp.topMargin = dp(100);
        root.addView(selectionBar, selectionLp);
'''
text = replace_block(text, '        selectionBar = new LinearLayout(this);', '        if (isPdf) {', selection_setup,
                     'compact selection bar setup')

highlight_note = r'''    private void showHighlightColorDialog(SelectionData data) {
        final String[] colors = {
                "rgba(255,213,79,.46)",
                "rgba(128,203,196,.42)",
                "rgba(244,143,177,.42)",
                "rgba(149,117,205,.38)",
                "rgba(100,181,246,.40)"
        };
        final int[] swatches = {
                Color.rgb(255, 205, 70), Color.rgb(113, 201, 183), Color.rgb(239, 132, 172),
                Color.rgb(146, 112, 210), Color.rgb(108, 170, 232)
        };
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = readerSheetBase("Highlight", "Choose a color", dialog);
        LinearLayout palette = new LinearLayout(this);
        palette.setOrientation(LinearLayout.HORIZONTAL);
        palette.setGravity(Gravity.CENTER);
        palette.setPadding(dp(8), dp(8), dp(8), dp(10));
        for (int i = 0; i < colors.length; i++) {
            final int which = i;
            TextView swatch = new TextView(this);
            swatch.setText("●");
            swatch.setTextSize(31);
            swatch.setTextColor(swatches[i]);
            swatch.setGravity(Gravity.CENTER);
            swatch.setBackground(readerRoundRect(readerPanelBase(), dp(22), dp(1), readerPanelStroke()));
            swatch.setOnClickListener(v -> {
                dialog.dismiss();
                saveAnnotation(data, colors[which], "");
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(52), dp(52));
            if (i > 0) lp.leftMargin = dp(7);
            palette.addView(swatch, lp);
        }
        sheet.addView(palette, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));
        presentReaderSheet(dialog, sheet, false);
    }

    private void showNoteEditor(SelectionData data) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = readerSheetBase("Add note", shortQuote(data.text, 120), dialog);

        EditText input = new EditText(this);
        input.setHint("Write a note…");
        input.setTextSize(14.5f);
        input.setTextColor(readerPanelText());
        input.setHintTextColor(readerPanelSubText());
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(3);
        input.setMaxLines(7);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(readerRoundRect(readerPanelBase(), dp(16), dp(1), readerAccent()));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132));
        inputLp.topMargin = dp(6);
        sheet.addView(input, inputLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(10), 0, 0);
        TextView cancel = compactReaderButton("Cancel", false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        TextView save = compactReaderButton("Save note", true);
        save.setOnClickListener(v -> {
            String note = input.getText().toString();
            dialog.dismiss();
            saveAnnotation(data, "rgba(149,117,205,.36)", note);
        });
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(dp(96), dp(42));
        cancelLp.rightMargin = dp(8);
        actions.addView(cancel, cancelLp);
        actions.addView(save, new LinearLayout.LayoutParams(dp(124), dp(42)));
        sheet.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        presentReaderSheet(dialog, sheet, true);
        input.requestFocus();
    }

    private TextView compactReaderButton(String label, boolean primary) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(13f);
        button.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(primary ? Color.WHITE : readerAccent());
        button.setBackground(readerRoundRect(primary ? readerAccent() : readerPanelBase(), dp(16), dp(1), readerAccent()));
        return button;
    }

    private LinearLayout readerSheetBase(String title, String subtitle, Dialog dialog) {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(18), dp(9), dp(18), dp(18));
        sheet.setBackground(readerRoundRect(readerPanelBase(), dp(26), dp(1), readerPanelStroke()));
        sheet.setElevation(dp(14));

        TextView handle = new TextView(this);
        handle.setBackground(readerRoundRect(readerPanelSubText(), dp(2), 0, Color.TRANSPARENT));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(50), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(11);
        sheet.addView(handle, handleLp);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(20);
        heading.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        heading.setTextColor(readerPanelText());
        copy.addView(heading);
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView sub = new TextView(this);
            sub.setText(subtitle);
            sub.setTextSize(10.5f);
            sub.setTextColor(readerPanelSubText());
            sub.setMaxLines(2);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            sub.setPadding(0, dp(2), 0, 0);
            copy.addView(sub);
        }
        header.addView(copy, new LinearLayout.LayoutParams(0, dp(54), 1f));
        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(21);
        close.setTextColor(readerPanelSubText());
        close.setGravity(Gravity.CENTER);
        close.setBackground(readerRoundRect(readerPanelBase(), dp(18), dp(1), readerPanelStroke()));
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        sheet.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        return sheet;
    }

    private void presentReaderSheet(Dialog dialog, View content, boolean keyboard) {
        dialog.setContentView(content);
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.34f);
        window.setGravity(Gravity.BOTTOM);
        int sw = getResources().getDisplayMetrics().widthPixels;
        window.setLayout(Math.min(sw, dp(720)), ViewGroup.LayoutParams.WRAP_CONTENT);
        if (keyboard) window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            window.setBackgroundBlurRadius(dp(16));
        }
    }

    private GradientDrawable readerRoundRect(int fill, int radius, int strokeWidth, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }
'''
text = replace_block(text, '    private void showHighlightColorDialog(SelectionData data) {', '    private void saveAnnotation(SelectionData data, String color, String note) {',
                     highlight_note, 'premium highlight/note sheets')

selection_button = r'''    private View selectionActionButton(String label, int action) {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setClickable(true);
        button.setContentDescription(label);

        int iconColor = readerAccent();
        String iconText = "✎";
        if (action == SEL_HIGHLIGHT) iconText = "✎";
        else if (action == SEL_NOTE) iconText = "▤";
        else if (action == SEL_TRANSLATE) iconText = "A";
        else if (action == SEL_COPY) iconText = "▣";

        TextView icon = new TextView(this);
        icon.setText(iconText);
        icon.setTextSize(action == SEL_TRANSLATE ? 14 : 16);
        icon.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        icon.setTextColor(iconColor);
        icon.setGravity(Gravity.CENTER);
        button.addView(icon, new LinearLayout.LayoutParams(dp(26), dp(38)));

        TextView copy = new TextView(this);
        copy.setText(label);
        copy.setTextSize(10.5f);
        copy.setTextColor(readerPanelText());
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setSingleLine(true);
        button.addView(copy, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));

        button.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN)
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(45L).start();
            else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL)
                v.animate().scaleX(1f).scaleY(1f).setDuration(85L).start();
            return false;
        });
        button.setOnClickListener(v -> performSelectionAction(action));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(action == SEL_TRANSLATE ? 82 : 72), dp(42)));
        return button;
    }
'''
text = replace_block(text, '    private View selectionActionButton(String label, int action) {', '    private void installSelectionWatcher() {',
                     selection_button, 'compact selection buttons')

# A slightly longer debounce prevents Android/WebView selection handles from repeatedly rebuilding the toolbar.
text = text.replace('}},110);});', '}},180);});')

selection_behavior = r'''    private void onWebSelection(String text, int start, int end) {
        if (paperGestureActive || suppressingSelectionForPaperGesture()) {
            currentSelection = null;
            hideSelectionBar();
            clearWebSelection();
            return;
        }
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty() || end <= start) {
            currentSelection = null;
            hideSelectionBar();
            return;
        }
        if (paperGestureCandidate) {
            paperGestureCandidate = false;
            recyclePageVelocityTracker();
        }
        if (currentSelection != null && currentSelection.start == start && currentSelection.end == end && clean.equals(currentSelection.text)) {
            if (selectionBar != null && selectionBar.getVisibility() != View.VISIBLE) showSelectionBar();
            return;
        }
        SelectionData data = new SelectionData();
        data.text = clean;
        data.start = Math.max(0, start);
        data.end = Math.max(data.start, end);
        currentSelection = data;
        showSelectionBar();
    }

    private void showSelectionBar() {
        if (selectionBar == null || isPdf) return;
        selectionBar.animate().cancel();
        selectionBar.bringToFront();
        if (webView == null) {
            positionSelectionBarFallback();
            return;
        }
        String js = "(function(){try{var s=window.getSelection&&window.getSelection();" +
                "if(!s||s.rangeCount===0||s.isCollapsed)return null;var r=s.getRangeAt(0).getBoundingClientRect();" +
                "var d=window.devicePixelRatio||1;return JSON.stringify({x:((r.left+r.right)/2)*d,t:r.top*d,b:r.bottom*d});" +
                "}catch(e){return null;}})()";
        try { webView.evaluateJavascript(js, this::positionSelectionBarFromJs); }
        catch (Exception ignored) { positionSelectionBarFallback(); }
    }

    private void positionSelectionBarFromJs(String result) {
        if (selectionBar == null) return;
        try {
            if (result == null || "null".equals(result)) { positionSelectionBarFallback(); return; }
            Object decoded = new JSONTokener(result).nextValue();
            String raw = decoded instanceof String ? (String) decoded : String.valueOf(decoded);
            JSONObject o = new JSONObject(raw);
            float centerX = (float) o.optDouble("x", webView == null ? 0 : webView.getWidth() / 2f);
            float top = (float) o.optDouble("t", 0);
            float bottom = (float) o.optDouble("b", top);
            positionSelectionBar(centerX, top, bottom);
        } catch (Exception ignored) { positionSelectionBarFallback(); }
    }

    private void positionSelectionBar(float selectionCenterX, float selectionTop, float selectionBottom) {
        if (selectionBar == null || root == null) return;
        final boolean wasVisible = selectionBar.getVisibility() == View.VISIBLE;
        selectionBar.measure(
                View.MeasureSpec.makeMeasureSpec(Math.max(1, root.getWidth() - dp(20)), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(dp(50), View.MeasureSpec.EXACTLY));
        int barW = Math.max(dp(280), selectionBar.getMeasuredWidth());
        int barH = dp(50);
        int rootW = Math.max(1, root.getWidth());
        int rootH = Math.max(1, root.getHeight());
        int[] webLoc = new int[2];
        int[] rootLoc = new int[2];
        if (webView != null) webView.getLocationOnScreen(webLoc);
        root.getLocationOnScreen(rootLoc);
        int webOffsetX = webLoc[0] - rootLoc[0];
        int webOffsetY = webLoc[1] - rootLoc[1];
        int cx = webOffsetX + Math.round(selectionCenterX);
        int top = webOffsetY + Math.round(selectionTop);
        int bottom = webOffsetY + Math.round(selectionBottom);
        int x = Math.max(dp(10), Math.min(rootW - barW - dp(10), cx - barW / 2));
        int y = top - barH - dp(12);
        int safeTop = dp(68);
        int safeBottom = Math.max(safeTop, rootH - barH - dp(66));
        if (y < safeTop) y = bottom + dp(12);
        y = Math.max(safeTop, Math.min(safeBottom, y));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) selectionBar.getLayoutParams();
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.height = barH;
        lp.leftMargin = x;
        lp.topMargin = y;
        selectionBar.setLayoutParams(lp);
        selectionBar.setVisibility(View.VISIBLE);
        if (!wasVisible) {
            selectionBar.setAlpha(0f);
            selectionBar.setTranslationY(dp(3));
            selectionBar.animate().alpha(1f).translationY(0f).setDuration(105L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.35f)).start();
        } else {
            selectionBar.setAlpha(1f);
            selectionBar.setTranslationY(0f);
        }
    }

    private void positionSelectionBarFallback() {
        if (selectionBar == null || root == null) return;
        selectionBar.post(() -> {
            int rootW = Math.max(1, root.getWidth());
            selectionBar.measure(
                    View.MeasureSpec.makeMeasureSpec(Math.max(1, rootW - dp(20)), View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(dp(50), View.MeasureSpec.EXACTLY));
            int barW = Math.max(dp(280), selectionBar.getMeasuredWidth());
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) selectionBar.getLayoutParams();
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.height = dp(50);
            lp.leftMargin = Math.max(dp(10), (rootW - barW) / 2);
            lp.topMargin = Math.max(dp(74), root.getHeight() - dp(150));
            selectionBar.setLayoutParams(lp);
            selectionBar.setAlpha(1f);
            selectionBar.setTranslationY(0f);
            selectionBar.setVisibility(View.VISIBLE);
        });
    }

    private void hideSelectionBar() {
        if (selectionBar != null) {
            selectionBar.animate().cancel();
            selectionBar.setVisibility(View.GONE);
            selectionBar.setAlpha(1f);
            selectionBar.setTranslationY(0f);
        }
    }

    private void performSelectionAction(int action) {
        SelectionData data = currentSelection;
        if (data == null || data.text == null || data.text.trim().isEmpty()) {
            hideSelectionBar();
            return;
        }
        currentSelection = null;
        hideSelectionBar();
        clearWebSelection();
        if (action == SEL_HIGHLIGHT) showHighlightColorDialog(data);
        else if (action == SEL_NOTE) showNoteEditor(data);
        else if (action == SEL_TRANSLATE) showTranslateDialog(data.text);
        else if (action == SEL_COPY) copySelectedText(data.text);
    }
'''
text = replace_block(text, '    private void onWebSelection(String text, int start, int end) {', '    private void setupPdfView(FrameLayout content) {',
                     selection_behavior, 'stable selection bar behavior')

annotations = r'''    private void showAnnotations() {
        if (isPdf) return;
        List<ReaderAnnotationStore.Annotation> items = ReaderAnnotationStore.load(prefs, bookFile.getName());
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = readerSheetBase("Notes & highlights", items.isEmpty() ? "Nothing saved in this book yet" : items.size() + " saved items", dialog);
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Select text while reading, then choose Highlight or Note.");
            empty.setTextSize(13);
            empty.setTextColor(readerPanelSubText());
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(24), dp(20), dp(24));
            sheet.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96)));
        } else {
            ScrollView scroll = new ScrollView(this);
            scroll.setVerticalScrollBarEnabled(false);
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            for (ReaderAnnotationStore.Annotation a : items) {
                String chapter = a.chapter >= 0 && a.chapter < spine.size() ? chapterDisplayTitle(a.chapter) : "Chapter " + (a.chapter + 1);
                boolean isNote = a.note != null && !a.note.trim().isEmpty();
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10), dp(7), dp(9), dp(7));
                row.setBackground(readerRoundRect(readerPanelBase(), dp(15), dp(1), readerPanelStroke()));
                TextView icon = new TextView(this);
                icon.setText(isNote ? "▤" : "✎");
                icon.setTextSize(17);
                icon.setTextColor(readerAccent());
                icon.setGravity(Gravity.CENTER);
                row.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(48)));
                LinearLayout copy = new LinearLayout(this);
                copy.setOrientation(LinearLayout.VERTICAL);
                TextView quote = new TextView(this);
                quote.setText(shortQuote(a.quote, 92));
                quote.setTextSize(12.5f);
                quote.setTextColor(readerPanelText());
                quote.setMaxLines(2);
                quote.setEllipsize(android.text.TextUtils.TruncateAt.END);
                copy.addView(quote);
                TextView sub = new TextView(this);
                sub.setText((isNote ? "Note" : "Highlight") + "  ·  " + chapter);
                sub.setTextSize(9.5f);
                sub.setTextColor(readerPanelSubText());
                copy.addView(sub);
                row.addView(copy, new LinearLayout.LayoutParams(0, dp(50), 1f));
                TextView arrow = new TextView(this);
                arrow.setText("›");
                arrow.setTextSize(20);
                arrow.setTextColor(readerPanelSubText());
                arrow.setGravity(Gravity.CENTER);
                row.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(48)));
                row.setOnClickListener(v -> { dialog.dismiss(); showAnnotationDetail(a); });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
                lp.topMargin = dp(6);
                list.addView(row, lp);
            }
            int h = Math.min(dp(430), Math.max(dp(96), items.size() * dp(70)));
            sheet.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        }
        presentReaderSheet(dialog, sheet, false);
    }
'''
text = replace_block(text, '    private void showAnnotations() {', '    private void showAnnotationDetail(ReaderAnnotationStore.Annotation a) {',
                     annotations, 'annotations sheet')

annotation_detail = r'''    private void showAnnotationDetail(ReaderAnnotationStore.Annotation a) {
        String chapter = a.chapter >= 0 && a.chapter < spine.size() ? chapterDisplayTitle(a.chapter) : "Chapter " + (a.chapter + 1);
        boolean isNote = a.note != null && !a.note.trim().isEmpty();
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = readerSheetBase(isNote ? "Note" : "Highlight", chapter, dialog);
        TextView quote = new TextView(this);
        quote.setText("“" + a.quote + "”");
        quote.setTextSize(14);
        quote.setTextColor(readerPanelText());
        quote.setLineSpacing(dp(2), 1.12f);
        quote.setPadding(dp(13), dp(11), dp(13), dp(11));
        quote.setBackground(readerRoundRect(readerPanelBase(), dp(15), dp(1), readerPanelStroke()));
        sheet.addView(quote);
        if (isNote) {
            TextView note = new TextView(this);
            note.setText(a.note);
            note.setTextSize(13);
            note.setTextColor(readerPanelText());
            note.setPadding(dp(13), dp(11), dp(13), dp(11));
            note.setBackground(readerRoundRect(readerPanelBase(), dp(15), dp(1), readerAccent()));
            LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            noteLp.topMargin = dp(8);
            sheet.addView(note, noteLp);
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView remove = compactReaderButton("Delete", false);
        remove.setTextColor(Color.rgb(211, 65, 65));
        remove.setOnClickListener(v -> {
            dialog.dismiss();
            ReaderAnnotationStore.remove(prefs, bookFile.getName(), a.id);
            GoogleAutoSync.scheduleSoon(this);
            applySavedAnnotations();
            updateAnnotationButton();
            Toast.makeText(this, "Removed", Toast.LENGTH_SHORT).show();
        });
        TextView go = compactReaderButton("Go to text", true);
        go.setOnClickListener(v -> { dialog.dismiss(); goToAnnotation(a); });
        LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(dp(92), dp(42)); removeLp.rightMargin = dp(8);
        actions.addView(remove, removeLp);
        actions.addView(go, new LinearLayout.LayoutParams(dp(126), dp(42)));
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)); actionsLp.topMargin = dp(9);
        sheet.addView(actions, actionsLp);
        presentReaderSheet(dialog, sheet, false);
    }
'''
text = replace_block(text, '    private void showAnnotationDetail(ReaderAnnotationStore.Annotation a) {', '    private void goToAnnotation(ReaderAnnotationStore.Annotation a) {',
                     annotation_detail, 'annotation detail sheet')

P.write_text(text, encoding='utf-8')
print('Premium reader interaction patch applied')
