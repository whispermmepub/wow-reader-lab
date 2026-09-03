from pathlib import Path

ROOT = Path('.')
READER = ROOT/'app/src/main/java/com/whisper/wowreader/BookReaderActivity.java'
FEED = ROOT/'app/src/main/java/com/whisper/wowreader/ComingSoonFeed.java'
LISTING = ROOT/'app/src/main/java/com/whisper/wowreader/ComingSoonActivity.java'
DETAIL = ROOT/'app/src/main/java/com/whisper/wowreader/ComingSoonDetailActivity.java'
GRADLE = ROOT/'app/build.gradle'


def rep(text, old, new, label, count=1):
    found = text.count(old)
    if found < count:
        raise SystemExit(f'{label}: expected >= {count}, found {found}')
    return text.replace(old, new, count)


def patch_reader():
    t = READER.read_text(encoding='utf-8')
    t = rep(t,
        'chapterTransitionOverlay.setScaleType(ImageView.ScaleType.FIT_XY);',
        'chapterTransitionOverlay.setScaleType(ImageView.ScaleType.CENTER_CROP);',
        'chapter transition scale type')

    t = rep(t,
        '        chapterTransitionOverlay.setVisibility(View.VISIBLE);\n        chapterTransitionOverlay.bringToFront();\n        pendingChapterFade = true;',
        '        chapterTransitionOverlay.setVisibility(View.VISIBLE);\n        chapterTransitionOverlay.bringToFront();\n        if (webView != null) {\n            webView.animate().cancel();\n            webView.setAlpha(0f);\n        }\n        pendingChapterFade = true;',
        'hide unstable chapter frame')

    old = '''                "var rx=/[\\\\u1000-\\\\u109F\\\\uA9E0-\\\\uA9FF\\\\uAA60-\\\\uAA7F]/g;" +\n                "var blocks=flow.querySelectorAll('p,li,blockquote,dd,dt,div');" +'''
    new = '''                "var rx=/[\\\\u1000-\\\\u109F\\\\uA9E0-\\\\uA9FF\\\\uAA60-\\\\uAA7F]/g;" +\n                "var baseW=Math.max(1,(st.pageWidth||flow.clientWidth||window.innerWidth||1));" +\n                "var wraps=flow.querySelectorAll('div,section,article,main');" +\n                "for(var wi=0;wi<wraps.length;wi++){var wn=wraps[wi],wt=(wn.textContent||'').replace(/\\\\s+/g,' ').trim();if(wt.length<180)continue;" +\n                "var wcs=getComputedStyle(wn);if(wcs.display!=='block')continue;var wr=wn.getBoundingClientRect();" +\n                "if(wr.width>0&&wr.width<baseW*0.84){wn.style.setProperty('width','auto','important');wn.style.setProperty('max-width','none','important');" +\n                "wn.style.setProperty('min-width','0','important');wn.style.setProperty('margin-left','0','important');wn.style.setProperty('margin-right','0','important');}}" +\n                "var blocks=flow.querySelectorAll('p,li,blockquote,dd,dt,div');" +'''
    t = rep(t, old, new, 'normalize narrow publisher wrappers')

    READER.write_text(t, encoding='utf-8')


def patch_feed():
    t = FEED.read_text(encoding='utf-8')
    t = rep(t, 'import android.text.Html;\n', 'import android.text.Html;\nimport android.text.SpannableStringBuilder;\n', 'spannable import')
    old = '''    static CharSequence richText(String html) {\n        if (html == null) return "";\n        if (android.os.Build.VERSION.SDK_INT >= 24)\n            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);\n        return Html.fromHtml(html);\n    }'''
    new = r'''    static CharSequence richText(String html) {
        if (html == null) return "";
        String cleaned = cleanContentHtml(html);
        CharSequence parsed;
        if (android.os.Build.VERSION.SDK_INT >= 24)
            parsed = Html.fromHtml(cleaned, Html.FROM_HTML_MODE_COMPACT);
        else
            parsed = Html.fromHtml(cleaned);
        SpannableStringBuilder compact = new SpannableStringBuilder(parsed);
        for (int i = compact.length() - 1; i > 0; i--) {
            if (compact.charAt(i) == '\n' && compact.charAt(i - 1) == '\n' &&
                    i > 1 && compact.charAt(i - 2) == '\n') compact.delete(i, i + 1);
        }
        for (int i = compact.length() - 1; i >= 0; i--) {
            char c = compact.charAt(i);
            if (c == '\uFFFC') compact.delete(i, i + 1);
        }
        return compact;
    }

    private static String cleanContentHtml(String html) {
        String out = html == null ? "" : html;
        out = out.replaceAll("(?is)<script\\b[^>]*>.*?</script>", "")
                 .replaceAll("(?is)<style\\b[^>]*>.*?</style>", "")
                 .replaceAll("(?is)<iframe\\b[^>]*>.*?</iframe>", "")
                 .replaceAll("(?is)<object\\b[^>]*>.*?</object>", "")
                 .replaceAll("(?is)<svg\\b[^>]*>.*?</svg>", "")
                 .replaceAll("(?is)<canvas\\b[^>]*>.*?</canvas>", "")
                 .replaceAll("(?is)<embed\\b[^>]*?/?>", "")
                 .replaceAll("(?is)<img\\b[^>]*>", "")
                 .replaceAll("(?is)<(?:p|div)\\b[^>]*>\\s*(?:&nbsp;|&#160;|<br\\s*/?>|\\s)*</(?:p|div)>", "")
                 .replaceAll("(?is)(<br\\s*/?>\\s*){3,}", "<br><br>")
                 .replace("[OBJ]", "")
                 .replace("\uFFFC", "");
        return out;
    }'''
    t = rep(t, old, new, 'compact rich text and strip broken embeds')
    FEED.write_text(t, encoding='utf-8')


def patch_listing():
    t = LISTING.read_text(encoding='utf-8')
    t = rep(t,
        '        titles.setPadding(dp(10), 0, 0, 0);\n        TextView title = text("Coming Soon", 24, primary(), true);',
        '        titles.setPadding(dp(10), dp(2), 0, dp(2));\n        TextView title = text("Coming Soon", 22, primary(), true);',
        'header title sizing')
    t = rep(t,
        '        header.addView(titles, new LinearLayout.LayoutParams(0, dp(52), 1f));',
        '        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));',
        'header titles wrap content')
    t = rep(t,
        '        content.addView(header, new LinearLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));',
        '        content.addView(header, new LinearLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));',
        'header row height')
    t = rep(t,
        '        status.setPadding(dp(4), dp(14), dp(4), dp(10));\n        content.addView(status, new LinearLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));',
        '        status.setPadding(dp(4), dp(4), dp(4), dp(8));\n        content.addView(status, new LinearLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));',
        'status spacing')
    LISTING.write_text(t, encoding='utf-8')


def patch_detail():
    t = DETAIL.read_text(encoding='utf-8')
    t = rep(t,
        '        body.setLineSpacing(dp(4), 1.18f);',
        '        body.setLineSpacing(dp(2), 1.10f);',
        'review body line spacing')
    t = rep(t,
        '        dividerLp.topMargin = dp(8);\n        dividerLp.bottomMargin = dp(14);',
        '        dividerLp.topMargin = dp(8);\n        dividerLp.bottomMargin = dp(9);',
        'detail divider spacing')
    DETAIL.write_text(t, encoding='utf-8')


def patch_gradle():
    t = GRADLE.read_text(encoding='utf-8')
    t = rep(t, 'versionCode 30', 'versionCode 31', 'version code')
    t = rep(t, "versionName '2.16.0-lab-v30'", "versionName '2.16.1-lab-v31'", 'version name')
    GRADLE.write_text(t, encoding='utf-8')


if __name__ == '__main__':
    patch_reader()
    patch_feed()
    patch_listing()
    patch_detail()
    patch_gradle()
    print('v31 reader/review fixes applied')
