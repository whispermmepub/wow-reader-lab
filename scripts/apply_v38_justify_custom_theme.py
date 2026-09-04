from pathlib import Path
import re

ROOT = Path('.')
reader_path = ROOT / 'app/src/main/java/com/whisper/wowreader/BookReaderActivity.java'
main_path = ROOT / 'app/src/main/java/com/whisper/wowreader/MainActivity.java'
coming_path = ROOT / 'app/src/main/java/com/whisper/wowreader/ComingSoonActivity.java'
detail_path = ROOT / 'app/src/main/java/com/whisper/wowreader/ComingSoonDetailActivity.java'
gradle_path = ROOT / 'app/build.gradle'


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly 1 match, found {count}')
    return text.replace(old, new, 1)


def regex_once(text, pattern, repl, label, flags=0):
    out, count = re.subn(pattern, repl, text, count=1, flags=flags)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly 1 regex match, found {count}')
    return out

# ---------------------------------------------------------------------------
# Version
# ---------------------------------------------------------------------------
gradle = gradle_path.read_text()
gradle = replace_once(gradle, 'versionCode 37', 'versionCode 38', 'versionCode')
gradle = replace_once(gradle, "versionName '2.16.7-lab-v37'", "versionName '2.16.8-lab-v38'", 'versionName')
gradle_path.write_text(gradle)

# ---------------------------------------------------------------------------
# Reader: make the two justify modes explicit in one Text alignment chooser.
# Preserve the existing per-book textAlignment + autoSpacing storage format so
# old books migrate with zero data loss.
# ---------------------------------------------------------------------------
reader = reader_path.read_text()
old_options = '''        String[] options = new String[]{
                "Reading mode · " + readingModeDisplayName(),
                "Page animation · " + pageAnimationDisplayName(),
                "Text alignment · " + alignmentDisplayName(),
                "Auto spacing adjustment · " + onOff(autoSpacingAdjustment),
                "Font size · " + fontPercent + "%",
                "Font · " + fontDisplayName(),
                "Line spacing · " + lineSpacingDisplay(),
                "Margins · " + marginPercent + "%",
                "Theme · " + themeDisplayName(),
                "Brightness · " + brightnessDisplayName(),
                "Keep screen on · " + onOff(keepScreenOn),
                "Lock orientation · " + onOff(lockOrientation),
                "Volume keys navigate · " + onOff(volumeChapterKeys),
                "Reset reader settings"
        };'''
new_options = '''        String[] options = new String[]{
                "Reading mode · " + readingModeDisplayName(),
                "Page animation · " + pageAnimationDisplayName(),
                "Text alignment · " + alignmentDisplayName(),
                "Font size · " + fontPercent + "%",
                "Font · " + fontDisplayName(),
                "Line spacing · " + lineSpacingDisplay(),
                "Margins · " + marginPercent + "%",
                "Theme · " + themeDisplayName(),
                "Brightness · " + brightnessDisplayName(),
                "Keep screen on · " + onOff(keepScreenOn),
                "Lock orientation · " + onOff(lockOrientation),
                "Volume keys navigate · " + onOff(volumeChapterKeys),
                "Reset reader settings"
        };'''
reader = replace_once(reader, old_options, new_options, 'reader settings options')

reader = regex_once(
    reader,
    r'''                    switch \(which\) \{\n                        case 0: showReadingModeDialog\(\); break;\n                        case 1: showPageAnimationDialog\(\); break;\n                        case 2: showAlignmentDialog\(\); break;\n                        case 3:\n                            autoSpacingAdjustment = !autoSpacingAdjustment;\n                            saveReaderPreferences\(\);\n                            applyReaderStyleSmooth\(true\);\n                            showReaderSettings\(\);\n                            break;\n                        case 4: showFontSizeDialog\(\); break;\n                        case 5: showFontDialog\(\); break;\n                        case 6: showLineSpacingDialog\(\); break;\n                        case 7: showMarginDialog\(\); break;\n                        case 8: showThemeDialog\(\); break;\n                        case 9: showBrightnessDialog\(\); break;\n                        case 10:\n                            keepScreenOn = !keepScreenOn;\n                            saveReaderPreferences\(\);\n                            applyWindowPreferences\(\);\n                            showReaderSettings\(\);\n                            break;\n                        case 11:\n                            lockOrientation = !lockOrientation;\n                            saveReaderPreferences\(\);\n                            applyWindowPreferences\(\);\n                            showReaderSettings\(\);\n                            break;\n                        case 12:\n                            volumeChapterKeys = !volumeChapterKeys;\n                            saveReaderPreferences\(\);\n                            showReaderSettings\(\);\n                            break;\n                        case 13: resetReaderPreferences\(\); break;\n                    \}''',
    '''                    switch (which) {
                        case 0: showReadingModeDialog(); break;
                        case 1: showPageAnimationDialog(); break;
                        case 2: showAlignmentDialog(); break;
                        case 3: showFontSizeDialog(); break;
                        case 4: showFontDialog(); break;
                        case 5: showLineSpacingDialog(); break;
                        case 6: showMarginDialog(); break;
                        case 7: showThemeDialog(); break;
                        case 8: showBrightnessDialog(); break;
                        case 9:
                            keepScreenOn = !keepScreenOn;
                            saveReaderPreferences();
                            applyWindowPreferences();
                            showReaderSettings();
                            break;
                        case 10:
                            lockOrientation = !lockOrientation;
                            saveReaderPreferences();
                            applyWindowPreferences();
                            showReaderSettings();
                            break;
                        case 11:
                            volumeChapterKeys = !volumeChapterKeys;
                            saveReaderPreferences();
                            showReaderSettings();
                            break;
                        case 12: resetReaderPreferences(); break;
                    }''',
    'reader settings switch')

reader = regex_once(
    reader,
    r'''    private void showAlignmentDialog\(\) \{.*?\n    \}\n\n    private void showFontSizeDialog\(\) \{''',
    '''    private void showAlignmentDialog() {
        String[] labels = {"Justify · Normal", "Justify · Auto spacing", "Left", "Right"};
        int selected;
        if ("left".equals(textAlignment)) selected = 2;
        else if ("right".equals(textAlignment)) selected = 3;
        else selected = autoSpacingAdjustment ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle("Text alignment")
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) {
                        textAlignment = "justify";
                        autoSpacingAdjustment = false;
                    } else if (which == 1) {
                        textAlignment = "justify";
                        autoSpacingAdjustment = true;
                    } else if (which == 2) {
                        textAlignment = "left";
                    } else {
                        textAlignment = "right";
                    }
                    saveReaderPreferences();
                    applyReaderStyleSmooth(true);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showFontSizeDialog() {''',
    'alignment dialog', flags=re.S)

reader = replace_once(reader,
'''    private String alignmentDisplayName() {
        if ("left".equals(textAlignment)) return "Left";
        if ("right".equals(textAlignment)) return "Right";
        return "Justify";
    }''',
'''    private String alignmentDisplayName() {
        if ("left".equals(textAlignment)) return "Left";
        if ("right".equals(textAlignment)) return "Right";
        return autoSpacingAdjustment ? "Justify · Auto spacing" : "Justify · Normal";
    }''', 'alignment display name')
reader_path.write_text(reader)

# ---------------------------------------------------------------------------
# Main app theme: add Custom option and derive every major palette surface from
# the chosen seed. Existing White/Black/Navy values are intentionally retained.
# ---------------------------------------------------------------------------
main = main_path.read_text()
main = replace_once(main,
'''        appTheme = prefs.getString("app_theme", "white");
        if (!"white".equals(appTheme) && !"black".equals(appTheme) && !"navy".equals(appTheme)) appTheme = "white";''',
'''        appTheme = prefs.getString("app_theme", "white");
        if (!AppThemePalette.isSupportedTheme(appTheme)) appTheme = "white";''', 'main theme validation')
main = replace_once(main,
'        AppWindowInsets.apply(this, root, themeBackground(), !isBlackAppTheme() && !isNavyAppTheme());',
'        AppWindowInsets.apply(this, root, themeBackground(), themeUsesDarkSystemIcons());',
'main insets theme')
main = main.replace('themeButton = iconButton("navy".equals(appTheme) ? "✦" : "◐");',
                    'themeButton = iconButton("navy".equals(appTheme) ? "✦" : ("custom".equals(appTheme) ? "◆" : "◐"));')

old_helpers = '''    private boolean isBlackAppTheme() { return "black".equals(appTheme); }
    private boolean isNavyAppTheme() { return "navy".equals(appTheme); }

    private int themeBackground() {
        if (isBlackAppTheme()) return Color.rgb(12, 13, 16);
        if (isNavyAppTheme()) return Color.rgb(3, 28, 48);
        return Color.rgb(247, 248, 251);
    }

    private int themeCardSurface() {
        if (isBlackAppTheme()) return Color.rgb(27, 29, 34);
        if (isNavyAppTheme()) return Color.rgb(7, 44, 70);
        return Color.WHITE;
    }

    private int themeControlSurface() {
        if (isBlackAppTheme()) return Color.rgb(35, 37, 43);
        if (isNavyAppTheme()) return Color.rgb(10, 51, 79);
        return Color.argb(232, 255, 255, 255);
    }

    private int themeSearchSurface() {
        if (isBlackAppTheme()) return Color.rgb(28, 30, 35);
        if (isNavyAppTheme()) return Color.rgb(6, 42, 67);
        return Color.argb(232, 255, 255, 255);
    }

    private int themePrimaryText() {
        return (isBlackAppTheme() || isNavyAppTheme()) ? Color.rgb(244, 247, 250) : Color.rgb(31, 34, 40);
    }

    private int themeSecondaryText() {
        if (isBlackAppTheme()) return Color.rgb(178, 183, 192);
        if (isNavyAppTheme()) return Color.rgb(165, 196, 213);
        return Color.rgb(105, 110, 122);
    }

    private int themeAccent() {
        if (isBlackAppTheme()) return Color.rgb(151, 166, 255);
        if (isNavyAppTheme()) return Color.rgb(239, 194, 91);
        return Color.rgb(82, 82, 214);
    }

    private int themeStroke() {
        if (isBlackAppTheme()) return Color.rgb(55, 59, 68);
        if (isNavyAppTheme()) return Color.rgb(26, 91, 120);
        return Color.rgb(224, 227, 234);
    }

    private int themeTrackColor() {
        if (isBlackAppTheme()) return Color.rgb(50, 53, 61);
        if (isNavyAppTheme()) return Color.rgb(18, 67, 91);
        return Color.rgb(236, 238, 243);
    }

    private int[] themeHeroColors() {
        if (isBlackAppTheme()) return new int[]{Color.rgb(30, 32, 39), Color.rgb(19, 20, 25)};
        if (isNavyAppTheme()) return new int[]{Color.rgb(4, 45, 73), Color.rgb(2, 29, 51), Color.rgb(4, 52, 74)};
        return new int[]{Color.rgb(239, 243, 255), Color.rgb(255, 247, 242)};
    }

    private int[] themeFabColors() {
        if (isBlackAppTheme()) return new int[]{Color.rgb(104, 91, 226), Color.rgb(63, 79, 170)};
        if (isNavyAppTheme()) return new int[]{Color.rgb(8, 174, 199), Color.rgb(10, 105, 145)};
        return new int[]{Color.rgb(92, 76, 226), Color.rgb(71, 113, 236)};
    }

    private int themeDiscoverySurface(int lightFallback) {
        if (isBlackAppTheme()) return Color.rgb(29, 32, 38);
        if (isNavyAppTheme()) return Color.rgb(7, 49, 77);
        return lightFallback;
    }

    private void applySystemBarTheme() {
        int bg = themeBackground();
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        int flags = 0;
        if (!isBlackAppTheme() && !isNavyAppTheme()) flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }'''
new_helpers = '''    private boolean isBlackAppTheme() { return "black".equals(appTheme); }
    private boolean isNavyAppTheme() { return "navy".equals(appTheme); }
    private boolean isCustomAppTheme() { return "custom".equals(appTheme); }
    private AppThemePalette customPalette() { return AppThemePalette.custom(prefs); }
    private boolean isDarkAppTheme() {
        if (isBlackAppTheme() || isNavyAppTheme()) return true;
        return isCustomAppTheme() && !customPalette().darkSystemIcons;
    }
    private boolean themeUsesDarkSystemIcons() { return !isDarkAppTheme(); }

    private int themeBackground() {
        if (isCustomAppTheme()) return customPalette().background;
        if (isBlackAppTheme()) return Color.rgb(12, 13, 16);
        if (isNavyAppTheme()) return Color.rgb(3, 28, 48);
        return Color.rgb(247, 248, 251);
    }

    private int themeCardSurface() {
        if (isCustomAppTheme()) return customPalette().card;
        if (isBlackAppTheme()) return Color.rgb(27, 29, 34);
        if (isNavyAppTheme()) return Color.rgb(7, 44, 70);
        return Color.WHITE;
    }

    private int themeControlSurface() {
        if (isCustomAppTheme()) return customPalette().control;
        if (isBlackAppTheme()) return Color.rgb(35, 37, 43);
        if (isNavyAppTheme()) return Color.rgb(10, 51, 79);
        return Color.argb(232, 255, 255, 255);
    }

    private int themeSearchSurface() {
        if (isCustomAppTheme()) return customPalette().control;
        if (isBlackAppTheme()) return Color.rgb(28, 30, 35);
        if (isNavyAppTheme()) return Color.rgb(6, 42, 67);
        return Color.argb(232, 255, 255, 255);
    }

    private int themePrimaryText() {
        if (isCustomAppTheme()) return customPalette().primary;
        return (isBlackAppTheme() || isNavyAppTheme()) ? Color.rgb(244, 247, 250) : Color.rgb(31, 34, 40);
    }

    private int themeSecondaryText() {
        if (isCustomAppTheme()) return customPalette().secondary;
        if (isBlackAppTheme()) return Color.rgb(178, 183, 192);
        if (isNavyAppTheme()) return Color.rgb(165, 196, 213);
        return Color.rgb(105, 110, 122);
    }

    private int themeAccent() {
        if (isCustomAppTheme()) return customPalette().accent;
        if (isBlackAppTheme()) return Color.rgb(151, 166, 255);
        if (isNavyAppTheme()) return Color.rgb(239, 194, 91);
        return Color.rgb(82, 82, 214);
    }

    private int themeStroke() {
        if (isCustomAppTheme()) return customPalette().stroke;
        if (isBlackAppTheme()) return Color.rgb(55, 59, 68);
        if (isNavyAppTheme()) return Color.rgb(26, 91, 120);
        return Color.rgb(224, 227, 234);
    }

    private int themeTrackColor() {
        if (isCustomAppTheme()) return AppThemePalette.blend(customPalette().control, customPalette().background, 0.45f);
        if (isBlackAppTheme()) return Color.rgb(50, 53, 61);
        if (isNavyAppTheme()) return Color.rgb(18, 67, 91);
        return Color.rgb(236, 238, 243);
    }

    private int[] themeHeroColors() {
        if (isCustomAppTheme()) {
            AppThemePalette p = customPalette();
            return p.darkSystemIcons
                    ? new int[]{AppThemePalette.blend(p.accent, p.background, 0.90f), p.card}
                    : new int[]{p.control, p.background};
        }
        if (isBlackAppTheme()) return new int[]{Color.rgb(30, 32, 39), Color.rgb(19, 20, 25)};
        if (isNavyAppTheme()) return new int[]{Color.rgb(4, 45, 73), Color.rgb(2, 29, 51), Color.rgb(4, 52, 74)};
        return new int[]{Color.rgb(239, 243, 255), Color.rgb(255, 247, 242)};
    }

    private int[] themeFabColors() {
        if (isCustomAppTheme()) {
            AppThemePalette p = customPalette();
            return new int[]{p.accent, AppThemePalette.blend(p.accent, p.background, 0.28f)};
        }
        if (isBlackAppTheme()) return new int[]{Color.rgb(104, 91, 226), Color.rgb(63, 79, 170)};
        if (isNavyAppTheme()) return new int[]{Color.rgb(8, 174, 199), Color.rgb(10, 105, 145)};
        return new int[]{Color.rgb(92, 76, 226), Color.rgb(71, 113, 236)};
    }

    private int themeDiscoverySurface(int lightFallback) {
        if (isCustomAppTheme()) return customPalette().control;
        if (isBlackAppTheme()) return Color.rgb(29, 32, 38);
        if (isNavyAppTheme()) return Color.rgb(7, 49, 77);
        return lightFallback;
    }

    private int themeSelectedSurface() {
        if (isCustomAppTheme()) return AppThemePalette.blend(themeAccent(), themeCardSurface(), 0.82f);
        if (isBlackAppTheme()) return Color.rgb(49, 48, 75);
        if (isNavyAppTheme()) return Color.rgb(18, 48, 75);
        return Color.rgb(246, 244, 255);
    }

    private void applySystemBarTheme() {
        int bg = themeBackground();
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        int flags = 0;
        if (themeUsesDarkSystemIcons()) flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }'''
main = replace_once(main, old_helpers, new_helpers, 'main theme helpers')

main = replace_once(main,
'''        final String[] labels = {"White", "Black", "Navy Premium"};
        final String[] values = {"white", "black", "navy"};
        final String[] icons = {"☀", "☾", "✦"};
        int selected = isBlackAppTheme() ? 1 : (isNavyAppTheme() ? 2 : 0);''',
'''        final String[] labels = {"White", "Black", "Navy Premium", "Custom"};
        final String[] values = {"white", "black", "navy", "custom"};
        final String[] icons = {"☀", "☾", "✦", "◆"};
        int selected = isBlackAppTheme() ? 1 : (isNavyAppTheme() ? 2 : (isCustomAppTheme() ? 3 : 0));''', 'theme chooser options')
main = replace_once(main, '        int accent = Color.rgb(111, 78, 202);', '        int accent = themeAccent();', 'theme chooser accent')
main = replace_once(main,
'''            if (active) {
                fill = isBlackAppTheme() ? Color.rgb(43, 40, 58)
                        : isNavyAppTheme() ? Color.rgb(18, 48, 75)
                        : Color.rgb(248, 246, 255);
            } else fill = themeControlSurface();''',
'''            if (active) {
                fill = isCustomAppTheme() ? themeSelectedSurface()
                        : isBlackAppTheme() ? Color.rgb(43, 40, 58)
                        : isNavyAppTheme() ? Color.rgb(18, 48, 75)
                        : Color.rgb(248, 246, 255);
            } else fill = themeControlSurface();''', 'theme active fill')
main = replace_once(main,
'''            row.setOnClickListener(v -> {
                String chosen = values[which];
                dialog.dismiss();
                if (!chosen.equals(appTheme)) {
                    appTheme = chosen;
                    prefs.edit().putString("app_theme", appTheme).apply();
                    recreate();
                }
            });''',
'''            row.setOnClickListener(v -> {
                String chosen = values[which];
                dialog.dismiss();
                if ("custom".equals(chosen)) {
                    showCustomThemeDialog();
                    return;
                }
                if (!chosen.equals(appTheme)) {
                    appTheme = chosen;
                    prefs.edit().putString("app_theme", appTheme)
                            .putLong("sync_updated_ms", System.currentTimeMillis()).apply();
                    recreate();
                }
            });''', 'theme chooser click')

custom_dialog = r'''
    private void showCustomThemeDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = premiumSheet("Custom theme", "Choose any colour · readable contrast is generated automatically", dialog);

        int currentSeed = prefs.getInt(AppThemePalette.PREF_CUSTOM_SEED, AppThemePalette.DEFAULT_CUSTOM_SEED);

        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(dp(16), dp(12), dp(16), dp(12));
        TextView previewTitle = new TextView(this);
        previewTitle.setText("WoW Reader");
        previewTitle.setTextSize(18);
        previewTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView previewSub = new TextView(this);
        previewSub.setText("Custom theme preview");
        previewSub.setTextSize(11.5f);
        previewSub.setPadding(0, dp(3), 0, 0);
        TextView previewAccent = new TextView(this);
        previewAccent.setText("Accent · buttons · links");
        previewAccent.setTextSize(11.5f);
        previewAccent.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        previewAccent.setPadding(0, dp(8), 0, 0);
        preview.addView(previewTitle);
        preview.addView(previewSub);
        preview.addView(previewAccent);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92));
        previewLp.topMargin = dp(6);
        sheet.addView(preview, previewLp);

        TextView label = new TextView(this);
        label.setText("HEX COLOR");
        label.setTextSize(10.5f);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setTextColor(themeSecondaryText());
        label.setPadding(dp(2), dp(12), dp(2), dp(5));
        sheet.addView(label);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(AppThemePalette.toHex(currentSeed));
        input.setTextSize(15f);
        input.setTextColor(themePrimaryText());
        input.setHintTextColor(themeSecondaryText());
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setSelectAllOnFocus(true);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(7)});
        input.setBackground(roundRect(themeControlSurface(), dp(16), dp(1), themeStroke()));
        sheet.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        HorizontalScrollView presetScroll = new HorizontalScrollView(this);
        presetScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.setPadding(0, dp(10), dp(12), dp(4));
        int[] presetColors = {
                Color.rgb(111, 78, 209), Color.rgb(51, 102, 204), Color.rgb(15, 130, 154),
                Color.rgb(42, 132, 92), Color.rgb(210, 119, 46), Color.rgb(190, 65, 76),
                Color.rgb(190, 73, 140), Color.rgb(120, 83, 62), Color.rgb(77, 86, 105)
        };
        for (int color : presetColors) {
            TextView swatch = new TextView(this);
            swatch.setText("●");
            swatch.setTextSize(31);
            swatch.setTextColor(color);
            swatch.setGravity(Gravity.CENTER);
            swatch.setContentDescription("Use " + AppThemePalette.toHex(color));
            swatch.setBackground(roundRect(themeCardSurface(), dp(18), dp(1), themeStroke()));
            swatch.setOnClickListener(v -> {
                input.setText(AppThemePalette.toHex(color));
                input.setSelection(input.length());
            });
            LinearLayout.LayoutParams swatchLp = new LinearLayout.LayoutParams(dp(48), dp(48));
            if (presets.getChildCount() > 0) swatchLp.leftMargin = dp(7);
            presets.addView(swatch, swatchLp);
        }
        presetScroll.addView(presets);
        sheet.addView(presetScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));

        TextWatcher previewWatcher = new TextWatcher() {
            private void update(CharSequence value) {
                Integer seed = AppThemePalette.parseHex(value == null ? null : value.toString());
                if (seed == null) return;
                AppThemePalette p = AppThemePalette.customFromSeed(seed);
                preview.setBackground(roundRect(p.card, dp(18), dp(1), p.stroke));
                previewTitle.setTextColor(p.primary);
                previewSub.setTextColor(p.secondary);
                previewAccent.setTextColor(p.accent);
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { update(s); }
            @Override public void afterTextChanged(Editable s) {}
        };
        input.addTextChangedListener(previewWatcher);
        Integer initial = AppThemePalette.parseHex(input.getText().toString());
        if (initial != null) {
            AppThemePalette p = AppThemePalette.customFromSeed(initial);
            preview.setBackground(roundRect(p.card, dp(18), dp(1), p.stroke));
            previewTitle.setTextColor(p.primary);
            previewSub.setTextColor(p.secondary);
            previewAccent.setTextColor(p.accent);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = filterChoice("Cancel", false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        TextView apply = filterChoice("Apply theme", true);
        apply.setTextColor(Color.WHITE);
        apply.setBackground(roundRect(themeAccent(), dp(17), 0, 0));
        apply.setOnClickListener(v -> {
            Integer seed = AppThemePalette.parseHex(input.getText().toString());
            if (seed == null) {
                Toast.makeText(this, "Enter a valid HEX colour, for example #6F4ED1", Toast.LENGTH_LONG).show();
                return;
            }
            prefs.edit().putInt(AppThemePalette.PREF_CUSTOM_SEED, seed)
                    .putString("app_theme", "custom")
                    .putLong("sync_updated_ms", System.currentTimeMillis()).apply();
            appTheme = "custom";
            dialog.dismiss();
            recreate();
        });
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(dp(94), dp(40));
        cancelLp.rightMargin = dp(8);
        actions.addView(cancel, cancelLp);
        actions.addView(apply, new LinearLayout.LayoutParams(dp(120), dp(40)));
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        actionsLp.topMargin = dp(8);
        sheet.addView(actions, actionsLp);

        presentBottomSheet(dialog, sheet, 0.80f);
    }

'''
main = replace_once(main, '    private GradientDrawable gradientRoundRect(int[] colors, int radius) {', custom_dialog + '    private GradientDrawable gradientRoundRect(int[] colors, int radius) {', 'custom theme dialog insertion')

# Selected-state surfaces should stay readable on a dark custom palette.
main = main.replace('selected ? (isBlackAppTheme() ? Color.rgb(49, 48, 75) : Color.rgb(245, 243, 255)) : themeControlSurface()',
                    'selected ? themeSelectedSurface() : themeControlSurface()')
main = main.replace('selected ? (isBlackAppTheme() ? Color.rgb(49, 48, 75) : Color.rgb(246, 244, 255)) : themeControlSurface()',
                    'selected ? themeSelectedSurface() : themeControlSurface()')
main_path.write_text(main)

# ---------------------------------------------------------------------------
# Coming Soon surfaces: keep existing themes unchanged, but make Custom use the
# same generated palette and system-bar contrast as MainActivity.
# ---------------------------------------------------------------------------
def patch_companion(path, label):
    text = path.read_text()
    text = replace_once(text,
'''        appTheme = prefs.getString("app_theme", "white");
        applyBars();''',
'''        appTheme = prefs.getString("app_theme", "white");
        if (!AppThemePalette.isSupportedTheme(appTheme)) appTheme = "white";
        applyBars();''', f'{label} validation')
    text = text.replace('AppWindowInsets.apply(this, root, bg(), !"black".equals(appTheme) && !"navy".equals(appTheme));',
                        'AppWindowInsets.apply(this, root, bg(), useDarkSystemIcons());')
    text = replace_once(text,
'''        if (!"black".equals(appTheme) && !"navy".equals(appTheme))
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        else getWindow().getDecorView().setSystemUiVisibility(0);''',
'''        if (useDarkSystemIcons())
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        else getWindow().getDecorView().setSystemUiVisibility(0);''', f'{label} system bars')
    text = replace_once(text, '    private int bg() {', '''    private boolean customTheme() { return "custom".equals(appTheme); }
    private AppThemePalette customPalette() { return AppThemePalette.custom(prefs); }
    private boolean useDarkSystemIcons() {
        if (customTheme()) return customPalette().darkSystemIcons;
        return !"black".equals(appTheme) && !"navy".equals(appTheme);
    }

    private int bg() {''', f'{label} custom helpers')
    text = text.replace('    private int bg() {\n        if ("black".equals(appTheme))', '    private int bg() {\n        if (customTheme()) return customPalette().background;\n        if ("black".equals(appTheme))')
    text = text.replace('    private int card() {\n        if ("black".equals(appTheme))', '    private int card() {\n        if (customTheme()) return customPalette().card;\n        if ("black".equals(appTheme))')
    text = text.replace('    private int control() {\n        if ("black".equals(appTheme))', '    private int control() {\n        if (customTheme()) return customPalette().control;\n        if ("black".equals(appTheme))')
    # compact one-line and multi-line variants
    text = text.replace('    private int primary() {\n        return ("black".equals(appTheme) || "navy".equals(appTheme))', '    private int primary() {\n        if (customTheme()) return customPalette().primary;\n        return ("black".equals(appTheme) || "navy".equals(appTheme))')
    text = text.replace('    private int primary() { return ("black".equals(appTheme) || "navy".equals(appTheme)) ? Color.rgb(245, 246, 249) : Color.rgb(31, 33, 40); }',
                        '    private int primary() { return customTheme() ? customPalette().primary : (("black".equals(appTheme) || "navy".equals(appTheme)) ? Color.rgb(245, 246, 249) : Color.rgb(31, 33, 40)); }')
    text = text.replace('    private int secondary() {\n        return ("black".equals(appTheme) || "navy".equals(appTheme))', '    private int secondary() {\n        if (customTheme()) return customPalette().secondary;\n        return ("black".equals(appTheme) || "navy".equals(appTheme))')
    text = text.replace('    private int secondary() { return ("black".equals(appTheme) || "navy".equals(appTheme)) ? Color.rgb(176, 181, 194) : Color.rgb(104, 109, 124); }',
                        '    private int secondary() { return customTheme() ? customPalette().secondary : (("black".equals(appTheme) || "navy".equals(appTheme)) ? Color.rgb(176, 181, 194) : Color.rgb(104, 109, 124)); }')
    text = text.replace('    private int stroke() {\n        return ("black".equals(appTheme) || "navy".equals(appTheme))', '    private int stroke() {\n        if (customTheme()) return customPalette().stroke;\n        return ("black".equals(appTheme) || "navy".equals(appTheme))')
    text = text.replace('    private int stroke() { return ("black".equals(appTheme) || "navy".equals(appTheme)) ? Color.rgb(55, 60, 74) : Color.rgb(226, 228, 236); }',
                        '    private int stroke() { return customTheme() ? customPalette().stroke : (("black".equals(appTheme) || "navy".equals(appTheme)) ? Color.rgb(55, 60, 74) : Color.rgb(226, 228, 236)); }')
    text = text.replace('    private int accent() { return Color.rgb(111, 78, 209); }',
                        '    private int accent() { return customTheme() ? customPalette().accent : Color.rgb(111, 78, 209); }')
    path.write_text(text)

patch_companion(coming_path, 'ComingSoonActivity')
patch_companion(detail_path, 'ComingSoonDetailActivity')

print('v38 patch applied')
