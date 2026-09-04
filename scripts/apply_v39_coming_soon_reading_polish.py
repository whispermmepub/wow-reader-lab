from pathlib import Path

ROOT = Path('.')
feed_path = ROOT / 'app/src/main/java/com/whisper/wowreader/ComingSoonFeed.java'
detail_path = ROOT / 'app/src/main/java/com/whisper/wowreader/ComingSoonDetailActivity.java'
gradle_path = ROOT / 'app/build.gradle'


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly 1 match, found {count}')
    return text.replace(old, new, 1)

# Idempotent version guard.
gradle = gradle_path.read_text()
if 'versionCode 38' in gradle:
    gradle = replace_once(gradle, 'versionCode 38', 'versionCode 39', 'versionCode')
    gradle = replace_once(gradle, "versionName '2.16.8-lab-v38'", "versionName '2.16.9-lab-v39'", 'versionName')
elif 'versionCode 39' not in gradle or "versionName '2.16.9-lab-v39'" not in gradle:
    raise SystemExit('unexpected version identity')
gradle_path.write_text(gradle)

# Restore the richer paragraph semantics that v30 used.
feed = feed_path.read_text()
if 'Html.FROM_HTML_MODE_COMPACT' in feed:
    feed = replace_once(feed,
                        'parsed = Html.fromHtml(cleaned, Html.FROM_HTML_MODE_COMPACT);',
                        'parsed = Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY);',
                        'review html paragraph mode')
elif 'Html.FROM_HTML_MODE_LEGACY' not in feed:
    raise SystemExit('review html mode missing')
feed_path.write_text(feed)

# Review body only: 15sp + native justification. Avoid forcing line-break constants;
# Android's default breaker is more compatible across API/OEM versions.
detail = detail_path.read_text()
if 'import android.os.Build;' not in detail:
    detail = replace_once(detail,
'''import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;''',
'''import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;''',
'detail imports')

old_body = '''        TextView body = text("", 16, primary(), false);
        body.setLineSpacing(dp(2), 1.10f);
        body.setText(ComingSoonFeed.richText(post.contentHtml));'''
new_body = '''        TextView body = text("", 15, primary(), false);
        body.setLineSpacing(dp(2), 1.10f);
        if (Build.VERSION.SDK_INT >= 26) {
            body.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD);
        }
        body.setText(ComingSoonFeed.richText(post.contentHtml));'''
if old_body in detail:
    detail = replace_once(detail, old_body, new_body, 'review body typography')
else:
    detail = detail.replace('''        if (Build.VERSION.SDK_INT >= 23) {
            body.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY);
            body.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        }
''', '', 1)
    if 'TextView body = text("", 15, primary(), false);' not in detail or 'JUSTIFICATION_MODE_INTER_WORD' not in detail:
        raise SystemExit('v39 review body contract missing')
detail_path.write_text(detail)

print('v39 Coming Soon reading polish applied')
