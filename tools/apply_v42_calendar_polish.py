from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def write(path, content):
    (ROOT / path).write_text(content, encoding="utf-8")


def replace_once(path, old, new):
    text = read(path)
    if old not in text:
        raise SystemExit(f"Expected snippet not found in {path}: {old[:100]!r}")
    if text.count(old) != 1:
        raise SystemExit(f"Expected exactly one match in {path}, got {text.count(old)}")
    write(path, text.replace(old, new, 1))


calendar = "app/src/main/java/com/whisper/wowreader/ReadingCalendarActivity.java"
day = "app/src/main/java/com/whisper/wowreader/ReadingDayActivity.java"
memory = "app/src/main/java/com/whisper/wowreader/ReadingMemoryActivity.java"

# Calendar should remain usable on short phones and six-row months.
replace_once(calendar,
             "import android.widget.LinearLayout;\nimport android.widget.TextView;",
             "import android.widget.LinearLayout;\nimport android.widget.ScrollView;\nimport android.widget.TextView;")

replace_once(calendar,
             "        render();\n    }\n\n    private void render() {",
             "        render();\n    }\n\n    @Override protected void onRestart() {\n        super.onRestart();\n        render();\n    }\n\n    private void render() {")

replace_once(calendar,
             "        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(54)));\n\n        LinearLayout monthCard = new LinearLayout(this);",
             "        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(54)));\n\n        ScrollView scroll = new ScrollView(this);\n        scroll.setVerticalScrollBarEnabled(false);\n        scroll.setFillViewport(true);\n        LinearLayout body = new LinearLayout(this);\n        body.setOrientation(LinearLayout.VERTICAL);\n        body.setPadding(0, ui.dp(2), 0, ui.dp(16));\n        scroll.addView(body, new ScrollView.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));\n\n        LinearLayout monthCard = new LinearLayout(this);")

text = read(calendar)
text = text.replace("monthLp.topMargin = ui.dp(6); root.addView(monthCard, monthLp);",
                    "monthLp.topMargin = ui.dp(6); body.addView(monthCard, monthLp);")
text = text.replace("root.addView(dow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(30)));",
                    "body.addView(dow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(30)));")
text = text.replace("root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));",
                    "body.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));")
text = text.replace("sumLp.topMargin = ui.dp(7); root.addView(summary, sumLp);",
                    "sumLp.topMargin = ui.dp(7); body.addView(summary, sumLp);")
text = text.replace("weightedCell(ui.dp(78))", "weightedCell(ui.dp(72))")
write(calendar, text)

replace_once(calendar,
             "        setContentView(root);\n        AppWindowInsets.apply(this, root, ui.background, ui.darkSystemIcons);",
             "        root.addView(scroll, new LinearLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));\n        setContentView(root);\n        AppWindowInsets.apply(this, root, ui.background, ui.darkSystemIcons);")

replace_once(calendar,
             "        TextView number = label(String.valueOf(day), 10.8f, isToday ? ui.accent : ui.primary, true);",
             "        boolean hasDailyNote = !ReadingStatsStore.dailyNote(prefs, key).isEmpty();\n        String numberText = hasDailyNote ? day + \" •\" : String.valueOf(day);\n        TextView number = label(numberText, 10.8f, isToday || hasDailyNote ? ui.accent : ui.primary, true);")

# Avoid rebuilding these complete screens twice during their initial launch.
replace_once(day,
             "    @Override protected void onResume() { super.onResume(); if (ui != null) render(); }",
             "    @Override protected void onRestart() { super.onRestart(); if (ui != null) render(); }")
replace_once(memory,
             "    @Override protected void onResume() { super.onResume(); if (ui != null) render(); }",
             "    @Override protected void onRestart() { super.onRestart(); if (ui != null) render(); }")

print("v42 calendar polish patch prepared")
