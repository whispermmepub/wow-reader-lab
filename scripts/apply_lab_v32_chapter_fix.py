from pathlib import Path
import re

READER = Path('app/src/main/java/com/whisper/wowreader/BookReaderActivity.java')


def replace_method(source: str, signature: str, replacement: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise SystemExit(f'Could not locate {signature}')
    brace = source.find('{', start)
    if brace < 0:
        raise SystemExit(f'Could not locate opening brace for {signature}')
    depth = 0
    end = -1
    for i in range(brace, len(source)):
        ch = source[i]
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


def method_text(source: str, signature: str):
    start = source.find(signature)
    if start < 0:
        raise SystemExit(f'Could not locate {signature}')
    brace = source.find('{', start)
    depth = 0
    for i in range(brace, len(source)):
        ch = source[i]
        if ch == '{':
            depth += 1
        elif ch == '}':
            depth -= 1
            if depth == 0:
                return start, i + 1, source[start:i + 1]
    raise SystemExit(f'Could not parse {signature}')


text = READER.read_text(encoding='utf-8')

# V31 already keeps the old chapter screenshot over the unstable new WebView.
# Keep that loading mask behavior, but remove every visual animation at the
# chapter boundary itself. Page animation remains untouched inside chapters.
start, end, reveal = method_text(text, '    private void revealStableChapter()')
old_boundary = re.compile(
    r'\n\s*if \(pendingChapterFade && "slide"\.equals\(pageAnimation\) && "page"\.equals\(readingMode\)\) \{'
    r'.*?'
    r'\n\s*\} else \{\s*\n\s*webView\.setTranslationX\(0f\);\s*\n\s*\}',
    re.S,
)
replacement = (
    '\n            // Chapter boundaries are intentionally animation-free.\n'
    '            // None/Slide still applies to normal pages inside a chapter.\n'
    '            webView.setTranslationX(0f);'
)
reveal2, count = old_boundary.subn(replacement, reveal, count=1)
if count != 1:
    raise SystemExit('Could not locate V31 chapter slide-in block')
text = text[:start] + reveal2 + text[end:]

text = replace_method(
    text,
    '    private void finishChapterFade()',
    '''    private void finishChapterFade() {
        // The V31 screenshot remains only while the new chapter stabilizes.
        // Once ready, remove it immediately: no fade, slide or translation.
        finishChapterFadeImmediate();
    }''',
)

required = [
    'chapterTransitionOverlay.setScaleType(ImageView.ScaleType.CENTER_CROP);',
    'webView.setAlpha(0f);',
    'hideInitialReaderLoading();',
    'pageTurnLocked = false;',
    'chapterLoading = false;',
    'pendingChapterCurlDirection = 0;',
    'prewarmAdjacentChapters();',
    'Chapter boundaries are intentionally animation-free.',
]
for token in required:
    if token not in text:
        raise SystemExit(f'Required V31/V32 behavior missing: {token}')

if 'webView.animate().translationX(0f).setDuration(175L)' in text:
    raise SystemExit('Old chapter slide-in still present')
if 'long duration = "slide".equals(pageAnimation)' in text:
    raise SystemExit('Old chapter overlay fade/slide still present')

READER.write_text(text, encoding='utf-8')
print('V32 chapter-boundary animation removal applied')
