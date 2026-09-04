from pathlib import Path

ROOT = Path('.')
PKG = ROOT / 'app/src/main/java/com/whisper/wowreader'
READER = PKG / 'BookReaderActivity.java'
MAIN = PKG / 'MainActivity.java'
COMING = PKG / 'ComingSoonActivity.java'
DETAIL = PKG / 'ComingSoonDetailActivity.java'
SPLASH = PKG / 'SplashActivity.java'
INSETS = PKG / 'AppWindowInsets.java'


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly 1 match, found {count}')
    return text.replace(old, new, 1)


def replace_method(source: str, signature: str, replacement: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise SystemExit(f'Could not locate method {signature}')
    brace = source.find('{', start)
    if brace < 0:
        raise SystemExit(f'Could not locate opening brace for {signature}')
    depth = 0
    end = -1
    in_string = False
    escaped = False
    quote = ''
    for i in range(brace, len(source)):
        ch = source[i]
        if in_string:
            if escaped:
                escaped = False
            elif ch == '\\':
                escaped = True
            elif ch == quote:
                in_string = False
            continue
        if ch in ('"', "'"):
            in_string = True
            quote = ch
            continue
        if ch == '{':
            depth += 1
        elif ch == '}':
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    if end < 0:
        raise SystemExit(f'Could not locate closing brace for {signature}')
    return source[:start] + replacement + source[end:]


def patch_reader():
    t = READER.read_text(encoding='utf-8')

    # The old Play-Books-style paper engine from v2.4/v2.5 is no longer a selectable
    # animation. Make it truly dormant instead of leaving an invisible overlay/touch path.
    t = replace_once(
        t,
        '''        webView.setOnTouchListener((v, event) -> {\n            boolean paperHandled = handlePaperGesture(event);\n            if (!paperHandled) readerTapDetector.onTouchEvent(event);\n            return paperHandled;\n        });''',
        '''        webView.setOnTouchListener((v, event) -> {\n            // Legacy v2.4/v2.5 paper-curl gesture is intentionally retired.\n            // None/Slide are the only live page animations.\n            readerTapDetector.onTouchEvent(event);\n            return false;\n        });''',
        'retire paper touch path')

    t = replace_once(
        t,
        '''        pageCurlView = new PageCurlView(this);\n        content.addView(pageCurlView, new FrameLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT,\n                ViewGroup.LayoutParams.MATCH_PARENT));''',
        '''        // Do not attach the legacy v2.4/v2.5 PageCurlView. It used full-screen\n        // bitmap transforms and is not part of the current None/Slide reader anymore.\n        pageCurlView = null;''',
        'retire PageCurlView overlay')

    t = replace_once(
        t,
        '''        WebSettings s = webView.getSettings();\n        s.setJavaScriptEnabled(true);''',
        '''        WebSettings s = webView.getSettings();\n        s.setJavaScriptEnabled(true);\n        // Keep every EPUB chapter at the physical WebView viewport. Author viewport\n        // metadata must not trigger overview zoom when moving between spine items.\n        s.setUseWideViewPort(false);\n        s.setLoadWithOverviewMode(false);\n        s.setTextZoom(Math.max(80, Math.min(200, fontPercent)));''',
        'stable WebView scale settings')

    t = replace_once(
        t,
        '''    private void applyReaderStyle(boolean restoreProgress, int styleToken) {\n        if (webView == null) return;''',
        '''    private void applyReaderStyle(boolean restoreProgress, int styleToken) {\n        if (webView == null) return;\n        // WebView textZoom scales publisher px/pt/% sizes too. Body-only CSS scaling did\n        // not affect many EPUBs in Scroll mode, so textZoom is the single font scale.\n        try { webView.getSettings().setTextZoom(Math.max(80, Math.min(200, fontPercent))); }\n        catch (Exception ignored) {}''',
        'uniform text zoom')

    t = replace_once(
        t,
        '''                "html,body{background:" + bg + " !important;color:" + fg + " !important;}" +''',
        '''                "html,body{background:" + bg + " !important;color:" + fg + " !important;transform:none !important;zoom:1 !important;-webkit-text-size-adjust:100% !important;text-size-adjust:100% !important;}" +''',
        'root EPUB scale normalization')

    # Body stays at 100%; WebSettings.textZoom now owns user font scaling in both modes.
    old_font = '''"body{font-size:" + fontPercent + "% !important;line-height:" + line + " !important;'''
    if t.count(old_font) != 2:
        raise SystemExit(f'body font scaling: expected 2 matches, found {t.count(old_font)}')
    t = t.replace(old_font, '''"body{font-size:100% !important;line-height:" + line + " !important;''')

    old_wrap = '''                "var baseW=Math.max(1,(st.pageWidth||flow.clientWidth||window.innerWidth||1));" +\n                "var wraps=flow.querySelectorAll('div,section,article,main');" +\n                "for(var wi=0;wi<wraps.length;wi++){var wn=wraps[wi],wt=(wn.textContent||'').replace(/\\\\s+/g,' ').trim();if(wt.length<180)continue;" +\n                "var wcs=getComputedStyle(wn);if(wcs.display!=='block')continue;var wr=wn.getBoundingClientRect();" +\n                "if(wr.width>0&&wr.width<baseW*0.84){wn.style.setProperty('width','auto','important');wn.style.setProperty('max-width','none','important');" +\n                "wn.style.setProperty('min-width','0','important');wn.style.setProperty('margin-left','0','important');wn.style.setProperty('margin-right','0','important');}}" +'''
    new_wrap = '''                "var baseW=Math.max(1,(st.pageWidth||flow.clientWidth||window.innerWidth||1));" +\n                "var wraps=flow.querySelectorAll('div,section,article,main,p,blockquote,dd,dt');" +\n                "for(var wi=0;wi<wraps.length;wi++){var wn=wraps[wi],wt=(wn.textContent||'').replace(/\\\\s+/g,' ').trim();if(wt.length<120)continue;" +\n                "var wcs=getComputedStyle(wn);if(wcs.display!=='block')continue;var wr=wn.getBoundingClientRect();" +\n                "var par=wn.parentElement,pr=par?par.getBoundingClientRect():null,parWide=!pr||pr.width>=baseW*0.86;" +\n                "if(parWide&&wr.width>0&&wr.width<baseW*0.90){wn.style.setProperty('width','auto','important');wn.style.setProperty('max-width','none','important');" +\n                "wn.style.setProperty('min-width','0','important');wn.style.setProperty('box-sizing','border-box','important');wn.style.setProperty('margin-left','0','important');wn.style.setProperty('margin-right','0','important');}}" +'''
    t = replace_once(t, old_wrap, new_wrap, 'strong publisher width normalization')

    # Replace cutout-only safe area code with real system-bar + cutout insets on API 23+.
    t = replace_method(
        t,
        '    private void installReaderSafeAreaHandling()',
        '''    private void installReaderSafeAreaHandling() {\n        if (root == null) return;\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {\n            WindowManager.LayoutParams attrs = getWindow().getAttributes();\n            attrs.layoutInDisplayCutoutMode =\n                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;\n            getWindow().setAttributes(attrs);\n        }\n\n        root.setOnApplyWindowInsetsListener((v, insets) -> {\n            int safeTop = 0;\n            int safeBottom = 0;\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {\n                android.graphics.Insets bars = insets.getInsetsIgnoringVisibility(\n                        android.view.WindowInsets.Type.systemBars() |\n                        android.view.WindowInsets.Type.displayCutout());\n                safeTop = bars.top;\n                safeBottom = bars.bottom;\n            } else {\n                safeTop = Math.max(insets.getSystemWindowInsetTop(), insets.getStableInsetTop());\n                safeBottom = Math.max(insets.getSystemWindowInsetBottom(), insets.getStableInsetBottom());\n                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && insets.getDisplayCutout() != null) {\n                    safeTop = Math.max(safeTop, insets.getDisplayCutout().getSafeInsetTop());\n                    safeBottom = Math.max(safeBottom, insets.getDisplayCutout().getSafeInsetBottom());\n                }\n            }\n            if (topBar != null) {\n                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) topBar.getLayoutParams();\n                int wanted = safeTop + dp(8);\n                if (p.topMargin != wanted) { p.topMargin = wanted; topBar.setLayoutParams(p); }\n            }\n            if (bottomBar != null) {\n                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) bottomBar.getLayoutParams();\n                int wanted = safeBottom + dp(12);\n                if (p.bottomMargin != wanted) { p.bottomMargin = wanted; bottomBar.setLayoutParams(p); }\n            }\n            if (readingSeek != null) {\n                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) readingSeek.getLayoutParams();\n                int wanted = safeBottom + dp(64);\n                if (p.bottomMargin != wanted) { p.bottomMargin = wanted; readingSeek.setLayoutParams(p); }\n            }\n            return insets;\n        });\n        root.requestApplyInsets();\n    }''')

    required = [
        'Legacy v2.4/v2.5 paper-curl gesture is intentionally retired',
        'pageCurlView = null;',
        's.setTextZoom(Math.max(80, Math.min(200, fontPercent)))',
        'body{font-size:100%',
        "querySelectorAll('div,section,article,main,p,blockquote,dd,dt')",
        'getInsetsIgnoringVisibility',
        'Chapter boundaries are intentionally animation-free',
    ]
    for token in required:
        if token not in t:
            raise SystemExit(f'Reader guard missing after patch: {token}')
    READER.write_text(t, encoding='utf-8')


def patch_normal_activity(path: Path, bg_expr: str, light_expr: str, label: str):
    t = path.read_text(encoding='utf-8')
    old = '        setContentView(root);'
    new = f'''        setContentView(root);\n        AppWindowInsets.apply(this, root, {bg_expr}, {light_expr});'''
    t = replace_once(t, old, new, f'{label} inset hook')
    path.write_text(t, encoding='utf-8')


def patch_normal_screens():
    patch_normal_activity(MAIN, 'themeBackground()', '!isBlackAppTheme() && !isNavyAppTheme()', 'MainActivity')
    patch_normal_activity(COMING, 'bg()', '!"black".equals(appTheme) && !"navy".equals(appTheme)', 'ComingSoonActivity')
    patch_normal_activity(DETAIL, 'bg()', '!"black".equals(appTheme) && !"navy".equals(appTheme)', 'ComingSoonDetailActivity')
    patch_normal_activity(SPLASH, 'bg', '!black && !navy', 'SplashActivity')


def write_insets_helper():
    content = r'''package com.whisper.wowreader;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

/**
 * Normal-screen edge-to-edge compatibility for gesture navigation, 3-button
 * navigation, status bars and display cutouts from API 23 through API 36.
 */
final class AppWindowInsets {
    private AppWindowInsets() {}

    static void apply(Activity activity, View root, int backgroundColor, boolean lightSystemIcons) {
        if (activity == null || root == null) return;
        final Window window = activity.getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(lightSystemIcons ? mask : 0, mask);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (lightSystemIcons && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (lightSystemIcons && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            window.getDecorView().setSystemUiVisibility(flags);
        }

        root.setBackgroundColor(backgroundColor);
        final int baseLeft = root.getPaddingLeft();
        final int baseTop = root.getPaddingTop();
        final int baseRight = root.getPaddingRight();
        final int baseBottom = root.getPaddingBottom();

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left = 0, top = 0, right = 0, bottom = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets safe = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = safe.left;
                top = safe.top;
                right = safe.right;
                bottom = safe.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && insets.getDisplayCutout() != null) {
                    left = Math.max(left, insets.getDisplayCutout().getSafeInsetLeft());
                    top = Math.max(top, insets.getDisplayCutout().getSafeInsetTop());
                    right = Math.max(right, insets.getDisplayCutout().getSafeInsetRight());
                    bottom = Math.max(bottom, insets.getDisplayCutout().getSafeInsetBottom());
                }
            }
            v.setPadding(baseLeft + left, baseTop + top, baseRight + right, baseBottom + bottom);
            return insets;
        });
        root.requestApplyInsets();
    }
}
'''
    INSETS.write_text(content, encoding='utf-8')


if __name__ == '__main__':
    patch_reader()
    patch_normal_screens()
    write_insets_helper()
    print('v32 root-cause reader/insets/font fixes applied')
