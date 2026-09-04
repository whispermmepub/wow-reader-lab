package com.whisper.wowreader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SplashActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean opened = false;
    private boolean minimumSplashElapsed = false;
    private boolean autoLibraryFinished = false;

    private final Runnable openLibrary = () -> {
        if (opened || isFinishing() || !minimumSplashElapsed || !autoLibraryFinished) return;
        opened = true;
        startActivity(new Intent(this, MainActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    };

    private final Runnable forceOpenLibrary = () -> {
        autoLibraryFinished = true;
        openLibrary.run();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String appTheme = getSharedPreferences("wow_reader", MODE_PRIVATE).getString("app_theme", "white");
        boolean black = "black".equals(appTheme);
        boolean navy = "navy".equals(appTheme);
        int bg = black ? Color.rgb(12, 13, 16) : (navy ? Color.rgb(3, 28, 48) : Color.rgb(247, 248, 253));
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility((black || navy) ? 0 :
                (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));

        FrameLayout root = new FrameLayout(this);
        int[] splashColors = black
                ? new int[]{Color.rgb(23, 25, 30), Color.rgb(10, 11, 14), Color.rgb(18, 20, 24)}
                : (navy
                ? new int[]{Color.rgb(3, 41, 67), Color.rgb(2, 25, 44), Color.rgb(4, 51, 70)}
                : new int[]{Color.rgb(242, 245, 255), Color.rgb(252, 248, 255), Color.rgb(255, 248, 242)});
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, splashColors);
        root.setBackground(background);

        View glowOne = new View(this);
        glowOne.setBackground(circle(Color.argb(42, 91, 88, 220)));
        glowOne.setAlpha(0.7f);
        FrameLayout.LayoutParams glowOneLp = new FrameLayout.LayoutParams(dp(240), dp(240), Gravity.TOP | Gravity.END);
        glowOneLp.topMargin = -dp(88);
        glowOneLp.rightMargin = -dp(76);
        root.addView(glowOne, glowOneLp);

        View glowTwo = new View(this);
        glowTwo.setBackground(circle(Color.argb(36, 255, 155, 94)));
        FrameLayout.LayoutParams glowTwoLp = new FrameLayout.LayoutParams(dp(190), dp(190), Gravity.BOTTOM | Gravity.START);
        glowTwoLp.bottomMargin = -dp(72);
        glowTwoLp.leftMargin = -dp(68);
        root.addView(glowTwo, glowTwoLp);

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        center.setPadding(dp(30), dp(24), dp(30), dp(24));

        FrameLayout logoCard = new FrameLayout(this);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(black ? Color.rgb(31, 34, 40) : (navy ? Color.rgb(7, 48, 75) : Color.argb(238, 255, 255, 255)));
        cardBg.setCornerRadius(dp(31));
        cardBg.setStroke(dp(1), Color.argb(72, 120, 125, 150));
        logoCard.setBackground(cardBg);
        logoCard.setElevation(dp(12));
        logoCard.setAlpha(0f);
        logoCard.setScaleX(0.82f);
        logoCard.setScaleY(0.82f);
        logoCard.setTranslationY(dp(12));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.wow_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams logoLp = new FrameLayout.LayoutParams(dp(112), dp(112), Gravity.CENTER);
        logoCard.addView(logo, logoLp);
        center.addView(logoCard, new LinearLayout.LayoutParams(dp(146), dp(146)));

        TextView title = new TextView(this);
        title.setText("WoW Reader");
        title.setTextColor((black || navy) ? Color.rgb(244, 247, 250) : Color.rgb(28, 30, 38));
        title.setTextSize(31);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setAlpha(0f);
        title.setTranslationY(dp(12));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(22);
        center.addView(title, titleLp);

        TextView sub = new TextView(this);
        sub.setText("Your library · your reading space");
        sub.setTextColor(black ? Color.rgb(176, 181, 190) : (navy ? Color.rgb(161, 195, 213) : Color.rgb(102, 106, 120)));
        sub.setTextSize(13.5f);
        sub.setGravity(Gravity.CENTER);
        sub.setAlpha(0f);
        sub.setTranslationY(dp(8));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(5);
        center.addView(sub, subLp);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER);
        brand.setAlpha(0f);
        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextSize(8);
        dot.setTextColor(navy ? Color.rgb(239, 194, 91) : Color.rgb(83, 82, 211));
        brand.addView(dot);
        TextView whisper = new TextView(this);
        whisper.setText("  Whisper Of Words");
        whisper.setTextSize(11.5f);
        whisper.setTextColor(black ? Color.rgb(154, 159, 169) : (navy ? Color.rgb(178, 202, 214) : Color.rgb(122, 126, 140)));
        brand.addView(whisper);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brandLp.topMargin = dp(14);
        center.addView(brand, brandLp);

        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(center, centerLp);
        setContentView(root);
        AppWindowInsets.apply(this, root, bg, !black && !navy);

        logoCard.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(390L).setInterpolator(new OvershootInterpolator(0.82f)).start();
        title.animate().alpha(1f).translationY(0f).setStartDelay(115L).setDuration(320L)
                .setInterpolator(new DecelerateInterpolator(1.4f)).start();
        sub.animate().alpha(1f).translationY(0f).setStartDelay(175L).setDuration(300L)
                .setInterpolator(new DecelerateInterpolator()).start();
        brand.animate().alpha(1f).setStartDelay(245L).setDuration(260L).start();
        glowOne.animate().translationX(-dp(18)).translationY(dp(14)).setDuration(900L).start();
        glowTwo.animate().translationX(dp(14)).translationY(-dp(10)).setDuration(900L).start();

        // Lab v41: make the Telegram-backed Library current before Home opens.
        // Offline/misconfigured clients quietly fall through. The 30s ceiling
        // prevents a weak connection from trapping the user on the splash.
        AutoLibrarySync.sync(this, result -> handler.post(() -> {
            autoLibraryFinished = true;
            openLibrary.run();
        }));
        handler.postDelayed(() -> {
            minimumSplashElapsed = true;
            openLibrary.run();
        }, 720L);
        handler.postDelayed(forceOpenLibrary, 30_000L);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(openLibrary);
        handler.removeCallbacks(forceOpenLibrary);
        super.onDestroy();
    }

    private GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
