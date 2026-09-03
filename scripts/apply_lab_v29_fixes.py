from pathlib import Path

MAIN = Path('app/src/main/java/com/whisper/wowreader/MainActivity.java')
READER = Path('app/src/main/java/com/whisper/wowreader/BookReaderActivity.java')
GRADLE = Path('app/build.gradle')

main = MAIN.read_text(encoding='utf-8')
reader = READER.read_text(encoding='utf-8')
gradle = GRADLE.read_text(encoding='utf-8')

# The main v29 transformation is already committed. This guarded follow-up repairs
# Android WebView selection integration without touching the reader/library logic.
required_main = [
    'private boolean homeMode = true;',
    '"Add book", false, this::chooseBook',
    'https://t.me/TheBookR',
    'https://t.me/+rUiqzi2mdhNiNGZl',
    'https://saroatsin.com',
    'https://whispermmepub.github.io/Review/',
    'private View buildLibraryOnlyHeader()',
    'return homeMode ? Math.min(4, items.size()) : items.size();'
]
for needle in required_main:
    if needle not in main:
        raise SystemExit('v29 Main source is incomplete: ' + needle)
if 'versionCode 29' not in gradle or "versionName '2.15.0-lab-v29'" not in gradle:
    raise SystemExit('v29 Gradle identity is missing')

# WebView itself does not expose TextView#setCustomSelectionActionModeCallback.
# Intercept startActionMode instead, delegate Chromium's callback to preserve
# selection handles/state, then clear + hide only the native floating toolbar.
invalid = '        webView.setCustomSelectionActionModeCallback(createSelectionActionModeCallback());\n'
if invalid in reader:
    reader = reader.replace(invalid, '', 1)

if 'webView = new WebView(this);' in reader:
    reader = reader.replace('        webView = new WebView(this);\n', '        webView = new ReaderWebView(this);\n', 1)

if 'private final class ReaderWebView extends WebView' not in reader:
    marker = '    private void setupWebView(FrameLayout content) {'
    if marker not in reader:
        raise SystemExit('setupWebView marker missing')
    subclass = r'''    private final class ReaderWebView extends WebView {
        ReaderWebView(android.content.Context context) {
            super(context);
        }

        private ActionMode.Callback suppressNativeToolbar(ActionMode.Callback delegate) {
            return new ActionMode.Callback() {
                @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    boolean created = delegate == null || delegate.onCreateActionMode(mode, menu);
                    nativeSelectionActionMode = mode;
                    menu.clear();
                    try { mode.hide(3000L); } catch (Exception ignored) {}
                    return created;
                }

                @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    boolean changed = delegate != null && delegate.onPrepareActionMode(mode, menu);
                    nativeSelectionActionMode = mode;
                    menu.clear();
                    try { mode.hide(3000L); } catch (Exception ignored) {}
                    return changed || true;
                }

                @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    // Native items are intentionally removed; WoW's compact bar owns actions.
                    return true;
                }

                @Override public void onDestroyActionMode(ActionMode mode) {
                    if (delegate != null) delegate.onDestroyActionMode(mode);
                    if (nativeSelectionActionMode == mode) nativeSelectionActionMode = null;
                }
            };
        }

        @Override public ActionMode startActionMode(ActionMode.Callback callback) {
            return super.startActionMode(suppressNativeToolbar(callback));
        }

        @Override public ActionMode startActionMode(ActionMode.Callback callback, int type) {
            return super.startActionMode(suppressNativeToolbar(callback), type);
        }
    }

'''
    reader = reader.replace(marker, subclass + marker, 1)

# Keep the existing custom callback helper harmless/available, but all live WebView
# selection ActionModes now flow through ReaderWebView above.
READER.write_text(reader, encoding='utf-8')

reader_now = READER.read_text(encoding='utf-8')
for needle in [
    'private final class ReaderWebView extends WebView',
    'webView = new ReaderWebView(this);',
    'suppressNativeToolbar(ActionMode.Callback delegate)',
    'keepNativeSelectionToolbarHidden()',
    'webView.setAlpha(0f);',
    'public void onScrollReady(int generation)',
    '#4A4033',
    'stableHits<2&&attempt<9',
    'finish();\n        overridePendingTransition'
]:
    if needle not in reader_now:
        raise SystemExit('Reader guard missing: ' + needle)
if 'setCustomSelectionActionModeCallback' in reader_now:
    raise SystemExit('invalid WebView selection API still present')

print('v29 WebView selection integration repaired')
