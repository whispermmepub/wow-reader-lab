from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, got {count}")
    return text.replace(old, new, 1)


def main() -> None:
    gradle = Path("app/build.gradle")
    g = gradle.read_text()
    g = replace_once(g, "versionCode 58", "versionCode 59", "versionCode")
    g = replace_once(g, "versionName '2.18.8'", "versionName '2.18.9'", "versionName")
    gradle.write_text(g)

    p = Path("app/src/main/java/com/whisper/wowreader/BookReaderActivity.java")
    s = p.read_text()

    s = replace_once(
        s,
        '        readerTheme = prefs.getInt("reader_theme", 0);',
        '        readerTheme = Math.max(0, Math.min(4, prefs.getInt("reader_theme", 0)));',
        "reader theme clamp",
    )

    helper_pattern = re.compile(
        r"    private int readerPanelBase\(\) \{.*?    private void refreshSelectionBarTheme\(\) \{",
        re.S,
    )
    helper_replacement = """    private boolean isReaderDarkTheme() {
        if (readerTheme == 2) return true;
        if (readerTheme == 4 && prefs != null) return !AppThemePalette.custom(prefs).darkSystemIcons;
        return false;
    }

    private int readerBookBackgroundColor() {
        if (readerTheme == 2) return Color.rgb(18, 18, 18);
        if (readerTheme == 1) return Color.rgb(244, 236, 216);
        if (readerTheme == 3) return Color.rgb(230, 229, 222);
        if (readerTheme == 4) return AppThemePalette.custom(prefs).background;
        return Color.WHITE;
    }

    private int readerBookTextColor() {
        if (readerTheme == 2) return Color.rgb(232, 234, 237);
        if (readerTheme == 1) return Color.rgb(74, 64, 51);
        if (readerTheme == 3) return Color.rgb(44, 49, 47);
        if (readerTheme == 4) return AppThemePalette.custom(prefs).primary;
        return Color.rgb(32, 33, 36);
    }

    private int readerBookHeadingColor() {
        if (readerTheme == 2) return Color.rgb(241, 243, 244);
        if (readerTheme == 1) return Color.rgb(59, 49, 40);
        if (readerTheme == 3) return Color.rgb(31, 37, 35);
        if (readerTheme == 4) return AppThemePalette.custom(prefs).primary;
        return readerBookTextColor();
    }

    private int readerBookLinkColor() {
        if (readerTheme == 2) return Color.rgb(174, 203, 250);
        if (readerTheme == 1) return Color.rgb(138, 90, 53);
        if (readerTheme == 3) return Color.rgb(75, 105, 97);
        if (readerTheme == 4) return AppThemePalette.custom(prefs).accent;
        return Color.rgb(25, 103, 210);
    }

    private String colorHex(int color) {
        return String.format(Locale.US, "#%02X%02X%02X", Color.red(color), Color.green(color), Color.blue(color));
    }

    private String readerBookBackgroundHex() { return colorHex(readerBookBackgroundColor()); }
    private String readerBookTextHex() { return colorHex(readerBookTextColor()); }
    private String readerBookHeadingHex() { return colorHex(readerBookHeadingColor()); }
    private String readerBookLinkHex() { return colorHex(readerBookLinkColor()); }

    private int readerPanelBase() {
        if (readerTheme == 2) return Color.rgb(28, 29, 33);
        if (readerTheme == 1) return Color.rgb(249, 243, 226);
        if (readerTheme == 3) return Color.rgb(238, 237, 230);
        if (readerTheme == 4) return AppThemePalette.custom(prefs).card;
        return Color.rgb(253, 253, 255);
    }

    private int readerPanelText() {
        if (readerTheme == 2) return Color.rgb(240, 242, 247);
        if (readerTheme == 1) return Color.rgb(66, 54, 40);
        if (readerTheme == 3) return Color.rgb(45, 50, 48);
        if (readerTheme == 4) return AppThemePalette.custom(prefs).primary;
        return Color.rgb(31, 33, 39);
    }

    private int readerPanelSubText() {
        if (readerTheme == 2) return Color.rgb(181, 186, 197);
        if (readerTheme == 1) return Color.rgb(126, 105, 78);
        if (readerTheme == 3) return Color.rgb(101, 111, 106);
        if (readerTheme == 4) return AppThemePalette.custom(prefs).secondary;
        return Color.rgb(101, 106, 118);
    }

    private int readerAccent() {
        if (readerTheme == 2) return Color.rgb(142, 163, 255);
        if (readerTheme == 1) return Color.rgb(164, 111, 67);
        if (readerTheme == 3) return Color.rgb(75, 105, 97);
        if (readerTheme == 4) return AppThemePalette.custom(prefs).accent;
        return Color.rgb(103, 80, 190);
    }

    private int readerPanelStroke() {
        if (readerTheme == 2) return Color.rgb(68, 72, 82);
        if (readerTheme == 1) return Color.rgb(222, 205, 172);
        if (readerTheme == 3) return Color.rgb(198, 199, 191);
        if (readerTheme == 4) return AppThemePalette.custom(prefs).stroke;
        return Color.rgb(225, 225, 234);
    }

    private int readerSoftSurface() {
        if (readerTheme == 2) return Color.rgb(40, 42, 48);
        if (readerTheme == 1) return Color.rgb(245, 236, 216);
        if (readerTheme == 3) return Color.rgb(244, 243, 236);
        if (readerTheme == 4) return AppThemePalette.custom(prefs).control;
        return Color.rgb(250, 250, 253);
    }

    private int readerSelectedSurface() {
        if (readerTheme == 2) return Color.rgb(60, 57, 86);
        if (readerTheme == 1) return Color.rgb(243, 229, 206);
        if (readerTheme == 3) return Color.rgb(220, 228, 223);
        if (readerTheme == 4) {
            AppThemePalette palette = AppThemePalette.custom(prefs);
            return AppThemePalette.blend(palette.control, palette.accent, 0.12f);
        }
        return Color.rgb(244, 240, 255);
    }

    private void refreshSelectionBarTheme() {"""
    s, count = helper_pattern.subn(helper_replacement, s, count=1)
    if count != 1:
        raise SystemExit(f"palette helper block: expected 1 match, got {count}")

    old_settings_palette = """        int panel = readerTheme == 2 ? Color.rgb(28, 29, 32) :
                readerTheme == 1 ? Color.rgb(249, 243, 226) : Color.rgb(250, 250, 252);
        int text = readerTheme == 2 ? Color.rgb(241, 243, 247) : Color.rgb(35, 37, 43);
        int sub = readerTheme == 2 ? Color.rgb(184, 188, 196) : Color.rgb(103, 108, 119);"""
    new_settings_palette = """        int panel = readerPanelBase();
        int text = readerPanelText();
        int sub = readerPanelSubText();"""
    s = replace_once(s, old_settings_palette, new_settings_palette, "display options palette")

    old_theme_chips = '        TextView[] themeChips = {sheetChip("Light", readerTheme == 0), sheetChip("Sepia", readerTheme == 1), sheetChip("Dark", readerTheme == 2)};'
    new_theme_chips = '        TextView[] themeChips = {sheetChip("Light", readerTheme == 0), sheetChip("Sepia", readerTheme == 1), sheetChip("Dark", readerTheme == 2), sheetChip("E-Ink", readerTheme == 3), sheetChip("Custom", readerTheme == 4)};'
    s = replace_once(s, old_theme_chips, new_theme_chips, "theme chips")

    s = replace_once(
        s,
        '        String[] labels = {"Light", "Sepia", "Dark"};',
        '        String[] labels = {"Light", "Sepia", "Dark", "E-Ink Color", "Custom"};',
        "theme dialog labels",
    )

    s = replace_once(
        s,
        """    private String themeDisplayName() {
        if (readerTheme == 1) return "Sepia";
        if (readerTheme == 2) return "Dark";
        return "Light";
    }""",
        """    private String themeDisplayName() {
        if (readerTheme == 1) return "Sepia";
        if (readerTheme == 2) return "Dark";
        if (readerTheme == 3) return "E-Ink Color";
        if (readerTheme == 4) return "Custom";
        return "Light";
    }""",
        "theme display name",
    )

    s = replace_once(
        s,
        '        if ("Dark".equals(label)) return "☾  Dark";',
        '        if ("Dark".equals(label)) return "☾  Dark";\n        if ("E-Ink".equals(label)) return "▧  E-Ink";\n        if ("Custom".equals(label)) return "◈  Custom";',
        "theme chip decorations",
    )

    main_colors = """        String bg = readerTheme == 2 ? "#121212" :
                readerTheme == 1 ? "#F4ECD8" : "#FFFFFF";
        String fg = readerTheme == 2 ? "#E8EAED" :
                readerTheme == 1 ? "#4A4033" : "#202124";
        String headingFg = readerTheme == 2 ? "#F1F3F4" :
                readerTheme == 1 ? "#3B3128" : fg;
        String link = readerTheme == 2 ? "#AECBFA" :
                readerTheme == 1 ? "#8A5A35" : "#1967D2";"""
    main_colors_new = """        String bg = readerBookBackgroundHex();
        String fg = readerBookTextHex();
        String headingFg = readerBookHeadingHex();
        String link = readerBookLinkHex();"""
    s = replace_once(s, main_colors, main_colors_new, "reader content colors")

    preload_colors = """        String bg = readerTheme == 2 ? "#121212" : (readerTheme == 1 ? "#F4ECD8" : "#FFFFFF");
        String fg = readerTheme == 2 ? "#E8EAED" : (readerTheme == 1 ? "#4A4033" : "#202124");"""
    if preload_colors in s:
        s = replace_once(
            s,
            preload_colors,
            """        String bg = readerBookBackgroundHex();
        String fg = readerBookTextHex();""",
            "preload colors",
        )

    dark_pattern = re.compile(
        r'''        String darkCss = readerTheme == 2\n                \? .*?                : "";''',
        re.S,
    )
    dark_new = """        String darkCss = readerTheme == 0 ? "" :
                "body,body p,body div,body span,body section,body article,body li,body dd,body dt,body blockquote,body td,body th,body figcaption{color:" + fg + " !important;}" +
                "h1,h2,h3,h4,h5,h6,strong,b{color:" + headingFg + " !important;}" +
                "a{color:" + link + " !important;}";
        String eInkCss = readerTheme == 3
                ? "img{filter:saturate(.72) contrast(.97) brightness(.99) !important;}"
                : "";"""
    s, count = dark_pattern.subn(dark_new, s, count=1)
    if count != 1:
        raise SystemExit(f"theme override css: expected 1 match, got {count}")

    old_typography_css = """                ".wow-reader-block{line-height:" + line + " !important;letter-spacing:normal !important;}" +
                ".wow-reader-block *{line-height:inherit !important;}" +
                ".wow-align-justify{text-align:justify !important;text-align-last:start !important;}" +
                ".wow-align-left{text-align:left !important;text-align-last:auto !important;}" +
                ".wow-align-right{text-align:right !important;text-align-last:auto !important;}" +
                ".wow-mm-smart{text-justify:inter-character !important;word-spacing:0 !important;letter-spacing:normal !important;overflow-wrap:anywhere !important;word-break:normal !important;hyphens:none !important;}" +
                "h1,h2,h3,h4,h5,h6{break-after:avoid-column !important;page-break-after:avoid !important;}" +
                darkCss + familyCss;"""
    new_typography_css = """                ".wow-reader-block{line-height:" + line + " !important;letter-spacing:normal !important;}" +
                ".wow-reader-block *{line-height:inherit !important;}" +
                ".wow-align-justify{text-align:justify !important;text-align-last:start !important;}" +
                ".wow-align-left{text-align:left !important;text-align-last:auto !important;}" +
                ".wow-align-right{text-align:right !important;text-align-last:auto !important;}" +
                ".wow-smart{font-kerning:normal !important;font-variant-ligatures:common-ligatures contextual !important;text-rendering:optimizeLegibility;orphans:2;widows:2;}" +
                ".wow-mm-smart{text-justify:inter-character !important;word-spacing:0 !important;letter-spacing:normal !important;overflow-wrap:break-word !important;word-break:normal !important;line-break:loose !important;hyphens:none !important;}" +
                ".wow-latin-smart{text-justify:inter-word !important;word-spacing:normal !important;overflow-wrap:break-word !important;word-break:normal !important;hyphens:auto !important;}" +
                ".wow-rhythm{orphans:2 !important;widows:2 !important;}" +
                ".wow-rhythm-indent{margin-block-start:0 !important;margin-block-end:0 !important;}" +
                ".wow-rhythm-indent + .wow-rhythm-indent{text-indent:1.05em !important;}" +
                "h1,h2,h3,h4,h5,h6{break-after:avoid-column !important;page-break-after:avoid !important;orphans:3;widows:3;}" +
                eInkCss + darkCss + familyCss;"""
    s = replace_once(s, old_typography_css, new_typography_css, "typography css")

    old_typography_js = """                \"var blocks=flow.querySelectorAll('p,li,blockquote,dd,dt,div');\" +
                \"for(var i=0;i<blocks.length;i++){var n=blocks[i],txt=(n.textContent||'').trim();if(txt.length<8)continue;\" +
                \"if(n.tagName==='DIV'&&n.querySelector('p,div,li,blockquote,dd,dt'))continue;\" +
                \"var cs=getComputedStyle(n);if(cs.display==='none')continue;\" +
                \"var centered=(cs.textAlign==='center');if(centered&&txt.length<180)continue;\" +
                \"n.classList.add('wow-reader-block');n.classList.remove('wow-align-justify','wow-align-left','wow-align-right','wow-mm-smart');\" +
                \"n.classList.add(align==='right'?'wow-align-right':(align==='left'?'wow-align-left':'wow-align-justify'));\" +
                \"var mm=(txt.match(rx)||[]).length;var visible=txt.replace(/\\\\s/g,'').length;\" +
                \"if(align==='justify'&&smart&&visible>0&&mm/visible>0.18)n.classList.add('wow-mm-smart');\" +
                \"}\" +"""
    new_typography_js = """                \"var blocks=flow.querySelectorAll('p,li,blockquote,dd,dt,div');\" +
                \"for(var i=0;i<blocks.length;i++){var n=blocks[i],txt=(n.textContent||'').trim();if(txt.length<8)continue;\" +
                \"if(n.tagName==='DIV'&&n.querySelector('p,div,li,blockquote,dd,dt'))continue;\" +
                \"var cs=getComputedStyle(n);if(cs.display==='none')continue;\" +
                \"var centered=(cs.textAlign==='center');if(centered&&txt.length<180)continue;\" +
                \"n.classList.add('wow-reader-block');n.classList.remove('wow-align-justify','wow-align-left','wow-align-right','wow-smart','wow-mm-smart','wow-latin-smart','wow-rhythm','wow-rhythm-indent');\" +
                \"n.classList.add(align==='right'?'wow-align-right':(align==='left'?'wow-align-left':'wow-align-justify'));\" +
                \"var mm=(txt.match(rx)||[]).length,latin=(txt.match(/[A-Za-z]/g)||[]).length,visible=txt.replace(/\\\\s/g,'').length;\" +
                \"if(n.getAttribute('data-wow-lang-added')==='1'&&(!smart||visible===0||mm/visible>0.18)){n.removeAttribute('lang');n.removeAttribute('data-wow-lang-added');}\" +
                \"if(smart&&visible>0){n.classList.add('wow-smart');\" +
                \"if(align==='justify'&&mm/visible>0.18)n.classList.add('wow-mm-smart');\" +
                \"else if(align==='justify'&&latin/visible>0.55){n.classList.add('wow-latin-smart');if(!n.getAttribute('lang')){n.setAttribute('lang','en');n.setAttribute('data-wow-lang-added','1');}}\" +
                \"var cname=((n.className||'')+'').toLowerCase(),special=/poem|verse|stanza|caption|title|heading|credit|signature/.test(cname)||!!n.closest('blockquote,li,table,figure,pre,code');\" +
                \"if(n.tagName==='P'&&!special&&visible>=24){n.classList.add('wow-rhythm');var fs=parseFloat(cs.fontSize)||16,mt=Math.abs(parseFloat(cs.marginTop)||0),mb=Math.abs(parseFloat(cs.marginBottom)||0),ti=Math.abs(parseFloat(cs.textIndent)||0);\" +
                \"if(mt<fs*0.12&&mb<fs*0.12&&ti<fs*0.08)n.classList.add('wow-rhythm-indent');}\" +
                \"}\" +
                \"}\" +"""
    s = replace_once(s, old_typography_js, new_typography_js, "typography javascript")

    chrome_old = """        } else if (readerTheme == 2) {
            solid = Color.rgb(18, 18, 18);
            fg = Color.rgb(240, 242, 246);
            glass = Color.argb(232, 28, 29, 33);
            stroke = Color.argb(56, 255, 255, 255);
        } else if (readerTheme == 1) {
            solid = Color.rgb(244, 236, 216);
            fg = Color.rgb(74, 64, 51);
            glass = Color.argb(238, 250, 244, 228);
            stroke = Color.argb(92, 168, 153, 126);
        } else {
            solid = Color.WHITE;
            fg = Color.rgb(32, 33, 36);
            glass = Color.argb(238, 255, 255, 255);
            stroke = Color.argb(74, 175, 181, 193);
        }"""
    chrome_new = """        } else {
            solid = readerBookBackgroundColor();
            fg = readerPanelText();
            int panel = readerPanelBase();
            glass = Color.argb(isReaderDarkTheme() ? 232 : 244,
                    Color.red(panel), Color.green(panel), Color.blue(panel));
            stroke = readerPanelStroke();
        }"""
    s = replace_once(s, chrome_old, chrome_new, "reader chrome palette")

    s = replace_once(
        s,
        "        if (readerTheme == 2) active = false;",
        "        if (isReaderDarkTheme()) active = false;",
        "night light dark-theme guard",
    )

    transition_old = """                        shot.eraseColor(readerTheme == 2 ? Color.rgb(18, 18, 18) :
                                (readerTheme == 1 ? Color.rgb(244, 236, 216) : Color.WHITE));"""
    if transition_old in s:
        s = replace_once(
            s,
            transition_old,
            "                        shot.eraseColor(readerBookBackgroundColor());",
            "transition fallback color",
        )

    highlight_old = """        final int[] swatches = {
                Color.rgb(255, 205, 70), Color.rgb(113, 201, 183), Color.rgb(239, 132, 172),
                Color.rgb(146, 112, 210), Color.rgb(108, 170, 232)
        };
        Dialog dialog = new Dialog(this);"""
    highlight_new = """        final int[] swatches = {
                Color.rgb(255, 205, 70), Color.rgb(113, 201, 183), Color.rgb(239, 132, 172),
                Color.rgb(146, 112, 210), Color.rgb(108, 170, 232)
        };
        if (readerTheme == 3) {
            colors[0] = "rgba(196,178,91,.36)";
            colors[1] = "rgba(104,151,132,.34)";
            colors[2] = "rgba(176,126,137,.33)";
            colors[3] = "rgba(139,126,160,.32)";
            colors[4] = "rgba(105,139,158,.33)";
            swatches[0] = Color.rgb(196, 178, 91);
            swatches[1] = Color.rgb(104, 151, 132);
            swatches[2] = Color.rgb(176, 126, 137);
            swatches[3] = Color.rgb(139, 126, 160);
            swatches[4] = Color.rgb(105, 139, 158);
        }
        Dialog dialog = new Dialog(this);"""
    s = replace_once(s, highlight_old, highlight_new, "E-Ink highlight palette")

    marker = "                    refreshSelectionBarTheme();\n                    dialog.dismiss();"
    replacement = "                    refreshSelectionBarTheme();\n                    if (readingRulerView != null) readingRulerView.configure(readingRulerEnabled, readingRulerLines, readerTheme);\n                    dialog.dismiss();"
    count = s.count(marker)
    if count < 1:
        raise SystemExit("theme-change ruler refresh marker missing")
    s = s.replace(marker, replacement)

    p.write_text(s)

    dpath = Path("app/src/main/java/com/whisper/wowreader/DictionaryDialog.java")
    d = dpath.read_text()
    d = replace_once(d, "        Palette p = palette(theme);", "        Palette p = palette(theme, prefs);", "dictionary palette call")
    d = replace_once(d, "    private static Palette palette(int theme) {", "    private static Palette palette(int theme, SharedPreferences prefs) {", "dictionary palette signature")
    anchor = """        if (theme == 1) {
            return new Palette(Color.rgb(244, 235, 214), Color.rgb(238, 226, 199),
                    Color.rgb(61, 48, 34), Color.rgb(113, 92, 67),
                    Color.rgb(145, 95, 45), Color.rgb(207, 188, 154));
        }
        return new Palette(Color.WHITE, Color.rgb(249, 249, 251),"""
    repl = """        if (theme == 1) {
            return new Palette(Color.rgb(244, 235, 214), Color.rgb(238, 226, 199),
                    Color.rgb(61, 48, 34), Color.rgb(113, 92, 67),
                    Color.rgb(145, 95, 45), Color.rgb(207, 188, 154));
        }
        if (theme == 3) {
            return new Palette(Color.rgb(230, 229, 222), Color.rgb(238, 237, 230),
                    Color.rgb(44, 49, 47), Color.rgb(101, 111, 106),
                    Color.rgb(75, 105, 97), Color.rgb(198, 199, 191));
        }
        if (theme == 4) {
            AppThemePalette c = AppThemePalette.custom(prefs);
            return new Palette(c.background, c.card, c.primary, c.secondary, c.accent, c.stroke);
        }
        return new Palette(Color.WHITE, Color.rgb(249, 249, 251),"""
    d = replace_once(d, anchor, repl, "dictionary E-Ink/custom palettes")
    dpath.write_text(d)

    rpath = Path("app/src/main/java/com/whisper/wowreader/ReadingRulerView.java")
    r = rpath.read_text()
    ruler_anchor = """        } else if (theme == 1) {
            fillColor = Color.argb(34, 169, 116, 56);
            edgeColor = Color.argb(95, 145, 92, 45);
        } else {
            fillColor = Color.argb(28, 60, 108, 210);"""
    ruler_repl = """        } else if (theme == 1) {
            fillColor = Color.argb(34, 169, 116, 56);
            edgeColor = Color.argb(95, 145, 92, 45);
        } else if (theme == 3) {
            fillColor = Color.argb(30, 75, 105, 97);
            edgeColor = Color.argb(94, 75, 105, 97);
        } else {
            fillColor = Color.argb(28, 60, 108, 210);"""
    r = replace_once(r, ruler_anchor, ruler_repl, "ruler E-Ink palette")
    rpath.write_text(r)


if __name__ == "__main__":
    main()
