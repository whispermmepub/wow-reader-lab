from pathlib import Path

MAIN = Path('app/src/main/java/com/whisper/wowreader/MainActivity.java')
DETAIL = Path('app/src/main/java/com/whisper/wowreader/ComingSoonDetailActivity.java')
LIST = Path('app/src/main/java/com/whisper/wowreader/ComingSoonActivity.java')


def must_replace(text, old, new, label, count=1):
    found = text.count(old)
    if found < count:
        raise SystemExit(f'{label}: expected at least {count}, found {found}')
    return text.replace(old, new, count)


def main():
    text = MAIN.read_text(encoding='utf-8')

    text = must_replace(
        text,
        '        addContinueReadingSection(outer);\n        addPremiumReadingStrip(outer);\n        addDiscoverySection(outer);\n        return outer;',
        '        addContinueReadingSection(outer);\n        addPremiumReadingStrip(outer);\n        addComingSoonSection(outer);\n        addDiscoverySection(outer);\n        return outer;',
        'home coming-soon insertion')

    methods = r'''
    private void addComingSoonSection(LinearLayout root) {
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(2), dp(14), dp(2), dp(8));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("Coming Soon");
        title.setTextSize(17.5f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(themePrimaryText());
        TextView sub = new TextView(this);
        sub.setText("Latest book notes from 3 WoW sources");
        sub.setTextSize(10.5f);
        sub.setTextColor(themeSecondaryText());
        titles.addView(title);
        titles.addView(sub);
        heading.addView(titles, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView all = new TextView(this);
        all.setText("View all  ›");
        all.setTextSize(12.5f);
        all.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        all.setTextColor(themeAccent());
        all.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        all.setOnClickListener(v -> showExploreHome());
        heading.addView(all, new LinearLayout.LayoutParams(dp(84), dp(48)));
        root.addView(heading);

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(0, 0, dp(12), dp(2));
        scroller.addView(strip, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView loading = new TextView(this);
        loading.setText("Loading latest posts…");
        loading.setTextSize(12.5f);
        loading.setTextColor(themeSecondaryText());
        loading.setGravity(Gravity.CENTER_VERTICAL);
        loading.setPadding(dp(18), 0, dp(18), 0);
        loading.setBackground(roundRect(themeCardSurface(), dp(20), dp(1), themeStroke()));
        strip.addView(loading, new LinearLayout.LayoutParams(dp(260), dp(132)));

        root.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(138)));

        new Thread(() -> {
            List<ComingSoonFeed.Post> posts = ComingSoonFeed.fetchLatest(this, 6, 6);
            runOnUiThread(() -> {
                if (isFinishing() || !homeMode) return;
                strip.removeAllViews();
                if (posts == null || posts.isEmpty()) {
                    TextView empty = new TextView(this);
                    empty.setText("Coming Soon posts are unavailable right now");
                    empty.setTextSize(12.5f);
                    empty.setTextColor(themeSecondaryText());
                    empty.setGravity(Gravity.CENTER_VERTICAL);
                    empty.setPadding(dp(18), 0, dp(18), 0);
                    empty.setBackground(roundRect(themeCardSurface(), dp(20), dp(1), themeStroke()));
                    empty.setOnClickListener(v -> showExploreHome());
                    strip.addView(empty, new LinearLayout.LayoutParams(dp(280), dp(132)));
                    return;
                }
                for (int i = 0; i < posts.size(); i++) {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(286), dp(132));
                    if (i > 0) lp.leftMargin = dp(10);
                    strip.addView(buildComingSoonPreviewCard(posts.get(i)), lp);
                }
            });
        }, "wow-home-coming-soon").start();
    }

    private View buildComingSoonPreviewCard(ComingSoonFeed.Post post) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(9), dp(9), dp(11), dp(9));
        card.setBackground(roundRect(themeCardSurface(), dp(20), dp(1), themeStroke()));
        card.setElevation(dp(1));
        card.setClickable(true);
        card.setOnClickListener(v -> openComingSoonPost(post));

        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(roundRect(themeControlSurface(), dp(13), 0, 0));
        cover.setClipToOutline(true);
        card.addView(cover, new LinearLayout.LayoutParams(dp(78), dp(114)));
        ComingSoonImageLoader.load(this, post.imageUrl, cover);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(dp(12), dp(1), 0, dp(1));

        TextView source = new TextView(this);
        source.setText(post.source + (post.published.isEmpty() ? "" : " · " + post.published));
        source.setTextSize(9.5f);
        source.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        source.setTextColor(themeAccent());
        source.setSingleLine(true);
        source.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(source);

        TextView title = new TextView(this);
        title.setText(post.title);
        title.setTextSize(14.5f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(themePrimaryText());
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(0, dp(4), 0, 0);
        applyBookTitleTypeface(title);
        copy.addView(title);

        TextView excerpt = new TextView(this);
        excerpt.setText(post.excerpt);
        excerpt.setTextSize(10.5f);
        excerpt.setTextColor(themeSecondaryText());
        excerpt.setMaxLines(3);
        excerpt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        excerpt.setPadding(0, dp(5), 0, 0);
        copy.addView(excerpt);

        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return card;
    }

    private void openComingSoonPost(ComingSoonFeed.Post post) {
        if (post == null) return;
        Intent intent = new Intent(this, ComingSoonDetailActivity.class);
        intent.putExtra("url", post.url);
        intent.putExtra("title", post.title);
        intent.putExtra("source", post.source);
        intent.putExtra("date", post.published);
        intent.putExtra("image", post.imageUrl);
        startActivity(intent);
    }

'''
    text = must_replace(text, '    private void addDiscoverySection(LinearLayout root) {', methods + '    private void addDiscoverySection(LinearLayout root) {', 'coming-soon methods')
    text = must_replace(text, '        heading.setText("Explore");', '        heading.setText("Quick links");', 'quick links title')
    text = must_replace(text, '{"review", "Book Reviews", "အညွှန်း & review", "https://whispermmepub.github.io/Review/"}', '{"review", "Book Reviews", "Coming Soon feed", "wow://coming-soon"}', 'review internal link')
    text = must_replace(text, '        card.setOnClickListener(v -> openExternal(url));', '        card.setOnClickListener(v -> {\n            if ("wow://coming-soon".equals(url)) showExploreHome();\n            else openExternal(url);\n        });', 'quick link internal action')

    old_show = '''    private void showExploreHome() {\n        if (!homeMode) {\n            homeMode = true;\n            buildUi();\n        }\n        if (libraryRecycler != null) libraryRecycler.smoothScrollToPosition(0);\n    }'''
    new_show = '''    private void showExploreHome() {\n        startActivity(new Intent(this, ComingSoonActivity.class));\n    }'''
    text = must_replace(text, old_show, new_show, 'explore navigation')

    MAIN.write_text(text, encoding='utf-8')

    detail = DETAIL.read_text(encoding='utf-8')
    detail = must_replace(detail, 'import android.content.Intent;\n', '', 'detail intent import')
    detail = must_replace(detail, 'import android.net.Uri;\n', '', 'detail uri import')
    detail = must_replace(detail, 'import android.text.method.LinkMovementMethod;\n', '', 'detail link movement import')
    detail = must_replace(detail, 'import android.graphics.Bitmap;\nimport android.graphics.BitmapFactory;\n', '', 'detail bitmap imports')
    detail = must_replace(detail, 'import java.io.InputStream;\nimport java.net.HttpURLConnection;\nimport java.net.URL;\n', '', 'detail network imports')

    web_block = '''        TextView web = smallButton("↗");\n        web.setContentDescription("Open original post");\n        web.setOnClickListener(v -> openOriginal());\n        header.addView(web, new LinearLayout.LayoutParams(dp(44), dp(44)));\n'''
    detail = must_replace(detail, web_block, '', 'remove original header button')
    detail = must_replace(detail, '        loadImage(fallbackImage, cover);', '        ComingSoonImageLoader.load(this, fallbackImage, cover);', 'cached skeleton image')
    detail = must_replace(detail, '                    status.setText("Couldn\'t load the full post. You can open the original source with ↗.");', '                    status.setText("Full post is not available right now. Showing the cached preview when available.");', 'offline detail message')
    detail = must_replace(detail, '            loadImage(post.imageUrl, cover);', '            ComingSoonImageLoader.load(this, post.imageUrl, cover);', 'cached post image')
    detail = must_replace(detail, '        body.setMovementMethod(LinkMovementMethod.getInstance());\n        body.setLinkTextColor(accent());', '        body.setMovementMethod(null);\n        body.setLinksClickable(false);\n        body.setLinkTextColor(primary());', 'disable external links')

    original_block = '''\n        TextView original = text("Read original post  ↗", 12.5f, accent(), true);\n        original.setGravity(Gravity.CENTER);\n        original.setBackground(roundRect(control(), dp(18), dp(1), stroke()));\n        original.setOnClickListener(v -> openOriginal());\n        LinearLayout.LayoutParams originalLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));\n        originalLp.topMargin = dp(16);\n        card.addView(original, originalLp);\n'''
    detail = must_replace(detail, original_block, '', 'remove original post button')

    start = detail.find('    private void openOriginal() {')
    end = detail.find('    private TextView smallButton(String value) {', start)
    if start < 0 or end < 0:
        raise SystemExit('detail helper block anchors missing')
    detail = detail[:start] + detail[end:]
    DETAIL.write_text(detail, encoding='utf-8')

    listing = LIST.read_text(encoding='utf-8')
    listing = must_replace(listing, 'import android.graphics.Bitmap;\nimport android.graphics.BitmapFactory;\n', '', 'list bitmap imports')
    listing = must_replace(listing, 'import java.io.InputStream;\nimport java.net.HttpURLConnection;\nimport java.net.URL;\n', '', 'list network imports')
    listing = must_replace(listing, '        loadImage(post.imageUrl, cover);', '        ComingSoonImageLoader.load(this, post.imageUrl, cover);', 'list cached images')
    start = listing.find('    private void loadImage(String url, ImageView view) {')
    end = listing.find('    private TextView smallButton(String value) {', start)
    if start < 0 or end < 0:
        raise SystemExit('list image helper anchors missing')
    listing = listing[:start] + listing[end:]
    LIST.write_text(listing, encoding='utf-8')

    print('v30 home/feed/detail transformation complete')


if __name__ == '__main__':
    main()

# Trigger v30 CI integration.
