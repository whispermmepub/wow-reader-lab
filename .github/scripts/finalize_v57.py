from pathlib import Path


def rep(s, old, new, label, count=1):
    if old not in s:
        raise SystemExit("missing marker: " + label)
    return s.replace(old, new, count)


# Reuse the already-reviewed v57 base patch embedded in the temporary workflow.
workflow = Path('.github/workflows/build-v57-dictionary-reader-temp.yml').read_text(encoding='utf-8')
start_marker = "          python3 - <<'PY'\n"
end_marker = "          PY\n"
start = workflow.find(start_marker)
if start < 0:
    raise SystemExit('missing v57 base patch start')
start += len(start_marker)
end = workflow.find(end_marker, start)
if end < 0:
    raise SystemExit('missing v57 base patch end')
lines = workflow[start:end].splitlines()
base_patch = '\n'.join(line[10:] if line.startswith('          ') else line for line in lines) + '\n'
exec(compile(base_patch, 'v57_base_patch.py', 'exec'), {'__name__': '__main__'})

# Kindle dictionary API 23 compatibility: use the legacy Html.fromHtml overload below API 24.
p = Path('app/src/main/java/com/whisper/wowreader/KindleDictionaryStore.java')
s = p.read_text(encoding='utf-8')
s = rep(s, 'import android.net.Uri;\n', 'import android.net.Uri;\nimport android.os.Build;\n', 'kindle Build import')
s = rep(
    s,
    'CharSequence cs = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY);',
    'CharSequence cs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N\n'
    '                    ? Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY)\n'
    '                    : Html.fromHtml(raw);',
    'kindle Html API23',
)
p.write_text(s, encoding='utf-8')

# Library view: keep old grid/list compatibility and add a small-grid preference.
p = Path('app/src/main/java/com/whisper/wowreader/MainActivity.java')
s = p.read_text(encoding='utf-8')
s = rep(s, '    private boolean gridMode;\n',
        '    private boolean gridMode;\n    private boolean smallGridMode;\n', 'small grid field')
s = rep(s, '        gridMode = prefs.getBoolean("library_grid", true);\n',
        '        gridMode = prefs.getBoolean("library_grid", true);\n'
        '        smallGridMode = gridMode && prefs.getBoolean("library_small_grid", false);\n',
        'load small grid')

old_button = '''        viewModeButton = iconButton(gridMode ? "▦" : "☷");
        viewModeButton.setTextSize(16);
        viewModeButton.setContentDescription("Change library view");
        viewModeButton.setOnClickListener(v -> {
            gridMode = !gridMode;
            prefs.edit().putBoolean("library_grid", gridMode).apply();
            viewModeButton.setText(gridMode ? "▦" : "☷");
            configureLibraryLayout();
            if (libraryAdapter != null) libraryAdapter.notifyDataSetChanged();
        });'''
new_button = '''        viewModeButton = iconButton(libraryViewIcon());
        viewModeButton.setTextSize(16);
        updateLibraryViewButton();
        viewModeButton.setOnClickListener(v -> cycleLibraryViewMode());'''
if s.count(old_button) != 2:
    raise SystemExit('expected two library view buttons, found ' + str(s.count(old_button)))
s = s.replace(old_button, new_button)

marker = '    private int calculateLibraryColumns(int widthPx) {'
helpers = '''    private String libraryViewIcon() {
        if (!gridMode) return "☷";
        return smallGridMode ? "▦" : "▥";
    }

    private String libraryViewName() {
        if (!gridMode) return "List";
        return smallGridMode ? "Small Grid" : "Large Grid";
    }

    private void updateLibraryViewButton() {
        if (viewModeButton == null) return;
        viewModeButton.setText(libraryViewIcon());
        viewModeButton.setContentDescription("Library view · " + libraryViewName());
    }

    private void setLibraryViewMode(boolean grid, boolean small) {
        gridMode = grid;
        smallGridMode = grid && small;
        prefs.edit().putBoolean("library_grid", gridMode)
                .putBoolean("library_small_grid", smallGridMode).apply();
        updateLibraryViewButton();
        configureLibraryLayout();
        if (libraryAdapter != null) libraryAdapter.notifyDataSetChanged();
    }

    private void cycleLibraryViewMode() {
        if (!gridMode) setLibraryViewMode(true, false);
        else if (!smallGridMode) setLibraryViewMode(true, true);
        else setLibraryViewMode(false, false);
    }

''' + marker
s = rep(s, marker, helpers, 'library view helpers')

s = rep(
    s,
    '        final float minCardDp = 154f;\n'
    '        float usable = Math.max(minCardDp, widthDp - sideDp);\n'
    '        int columns = (int) Math.floor((usable + gapDp) / (minCardDp + gapDp));\n'
    '        return Math.max(2, Math.min(6, columns));',
    '        final float minCardDp = smallGridMode ? 108f : 154f;\n'
    '        float usable = Math.max(minCardDp, widthDp - sideDp);\n'
    '        int columns = (int) Math.floor((usable + gapDp) / (minCardDp + gapDp));\n'
    '        int minimum = smallGridMode ? 3 : 2;\n'
    '        int maximum = smallGridMode ? 8 : 6;\n'
    '        return Math.max(minimum, Math.min(maximum, columns));',
    'grid column sizes',
)
s = rep(
    s,
    '        return Math.max(dp(118), (screen - side * 2 - gap * (columns - 1)) / columns);',
    '        int minimum = smallGridMode ? dp(86) : dp(118);\n'
    '        return Math.max(minimum, (screen - side * 2 - gap * (columns - 1)) / columns);',
    'grid card min width',
)

s = rep(s, '        card.setPadding(dp(7), dp(7), dp(7), dp(9));',
        '        int cardPad = smallGridMode ? dp(5) : dp(7);\n'
        '        card.setPadding(cardPad, cardPad, cardPad, smallGridMode ? dp(7) : dp(9));',
        'grid card padding')
s = rep(s, '        int innerWidth = Math.max(dp(96), cellWidth - dp(26));',
        '        int innerWidth = Math.max(smallGridMode ? dp(78) : dp(96),\n'
        '                cellWidth - (smallGridMode ? dp(18) : dp(26)));',
        'grid inner width')
s = rep(s, '        title.setTextSize(14.5f);',
        '        title.setTextSize(smallGridMode ? 12.5f : 14.5f);', 'grid title size')
s = rep(s, '        title.setPadding(dp(2), dp(9), dp(2), 0);',
        '        title.setPadding(dp(2), smallGridMode ? dp(6) : dp(9), dp(2), 0);',
        'grid title padding')
s = rep(s, '        meta.setTextSize(10.5f);',
        '        meta.setTextSize(smallGridMode ? 9.25f : 10.5f);', 'grid meta size')

old_filter = '''        TextView grid = filterChoice("Grid", gridMode);
        TextView list = filterChoice("List", !gridMode);
        grid.setOnClickListener(v -> { if (!gridMode) { gridMode = true; prefs.edit().putBoolean("library_grid", true).apply(); configureLibraryLayout(); refreshLibrary(); dialog.dismiss(); } });
        list.setOnClickListener(v -> { if (gridMode) { gridMode = false; prefs.edit().putBoolean("library_grid", false).apply(); configureLibraryLayout(); refreshLibrary(); dialog.dismiss(); } });
        LinearLayout.LayoutParams half1 = new LinearLayout.LayoutParams(0, dp(40), 1f); half1.rightMargin = dp(5);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(40), 1f); half2.leftMargin = dp(5);
        viewRow.addView(grid, half1); viewRow.addView(list, half2); sheet.addView(viewRow);'''
new_filter = '''        TextView largeGrid = filterChoice("Large Grid", gridMode && !smallGridMode);
        TextView smallGrid = filterChoice("Small Grid", gridMode && smallGridMode);
        TextView list = filterChoice("List", !gridMode);
        largeGrid.setOnClickListener(v -> { setLibraryViewMode(true, false); refreshLibrary(); dialog.dismiss(); });
        smallGrid.setOnClickListener(v -> { setLibraryViewMode(true, true); refreshLibrary(); dialog.dismiss(); });
        list.setOnClickListener(v -> { setLibraryViewMode(false, false); refreshLibrary(); dialog.dismiss(); });
        LinearLayout.LayoutParams third1 = new LinearLayout.LayoutParams(0, dp(40), 1f); third1.rightMargin = dp(4);
        LinearLayout.LayoutParams third2 = new LinearLayout.LayoutParams(0, dp(40), 1f); third2.leftMargin = dp(4); third2.rightMargin = dp(4);
        LinearLayout.LayoutParams third3 = new LinearLayout.LayoutParams(0, dp(40), 1f); third3.leftMargin = dp(4);
        viewRow.addView(largeGrid, third1); viewRow.addView(smallGrid, third2); viewRow.addView(list, third3); sheet.addView(viewRow);'''
s = rep(s, old_filter, new_filter, 'three library view filters')

p.write_text(s, encoding='utf-8')
print('v57 final patch applied')
