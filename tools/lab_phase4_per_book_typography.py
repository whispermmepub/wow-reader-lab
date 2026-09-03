from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
READER = ROOT / "app/src/main/java/com/whisper/wowreader/BookReaderActivity.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


reader = READER.read_text(encoding="utf-8")

reader = replace_once(
    reader,
    '''        applyWindowPreferences();\n        buildReaderUi();''',
    '''        if (!isPdf) {\n            BookTypographyStore.Values bookStyle = BookTypographyStore.load(\n                    prefs, bookFile.getName(), fontPercent, fontChoice, lineSpacing,\n                    marginPercent, textAlignment, autoSpacingAdjustment);\n            fontPercent = bookStyle.fontPercent;\n            fontChoice = bookStyle.fontChoice;\n            lineSpacing = bookStyle.lineSpacing;\n            marginPercent = bookStyle.marginPercent;\n            textAlignment = bookStyle.textAlignment;\n            autoSpacingAdjustment = bookStyle.autoSpacing;\n        }\n        applyWindowPreferences();\n        buildReaderUi();''',
    'load per-book typography before reader UI',
)

reader = replace_once(
    reader,
    '''    private void saveReaderPreferences() {\n        prefs.edit()''',
    '''    private void saveReaderPreferences() {\n        if (!isPdf && bookFile != null) {\n            BookTypographyStore.save(prefs, bookFile.getName(), fontPercent, fontChoice, lineSpacing,\n                    marginPercent, textAlignment, autoSpacingAdjustment);\n        }\n        prefs.edit()''',
    'save per-book typography',
)

READER.write_text(reader, encoding="utf-8")
print("Per-book typography patch applied successfully.")
