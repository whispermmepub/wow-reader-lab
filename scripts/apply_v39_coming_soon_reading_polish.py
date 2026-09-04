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

# Version bump
gradle = gradle_path.read_text()
gradle = replace_once(gradle, 'versionCode 38', 'versionCode 39', 'versionCode')
gradle = replace_once(gradle, "versionName '2.16.8-lab-v38'", "versionName '2.16.9-lab-v39'", 'versionName')
gradle_path.write_text(gradle)

# Restore the richer paragraph semantics that v30 used. Keep the existing cleanup
# that removes broken embedded objects and caps excessive blank lines.
feed = feed_path.read_text()
feed = replace_once(feed,
                    'parsed = Html.fromHtml(cleaned, Html.FROM_HTML_MODE_COMPACT);',
                    'parsed = Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY);',
                    'review html paragraph mode')
feed_path.write_text(feed)

# Review body reading polish only: slightly smaller text and native justification.
detail = detail_path.read_text()
detail = replace_once(detail,
'''import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;''',
'''import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;''',
'detail imports')
detail = replace_once(detail,
'''        TextView body = text("", 16, primary(), false);
        body.setLineSpacing(dp(2), 1.10f);
        body.setText(ComingSoonFeed.richText(post.contentHtml));''',
'''        TextView body = text("", 15, primary(), false);
        body.setLineSpacing(dp(2), 1.10f);
        if (Build.VERSION.SDK_INT >= 23) {
            body.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY);
            body.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            // Android's layout engine expands available word/phrase spacing so
            // Myanmar review lines read with the same balanced feel as Auto-spacing Justify.
            body.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD);
        }
        body.setText(ComingSoonFeed.richText(post.contentHtml));''',
'review body typography')
detail_path.write_text(detail)

print('v39 Coming Soon reading polish applied')
