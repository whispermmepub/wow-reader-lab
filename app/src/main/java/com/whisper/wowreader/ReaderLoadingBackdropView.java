package com.whisper.wowreader;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

final class ReaderLoadingBackdropView extends View {
    private final int theme;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wave = new Path();

    ReaderLoadingBackdropView(Context context, int theme) {
        super(context);
        this.theme = theme;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        int top;
        int bottom;
        if (theme == 2) {
            top = Color.rgb(5, 19, 52);
            bottom = Color.rgb(11, 28, 73);
        } else if (theme == 1) {
            top = Color.rgb(248, 240, 219);
            bottom = Color.rgb(236, 220, 189);
        } else {
            top = Color.rgb(247, 249, 255);
            bottom = Color.rgb(232, 238, 253);
        }
        paint.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        int glow1 = theme == 2 ? Color.argb(100, 74, 91, 231)
                : theme == 1 ? Color.argb(55, 211, 166, 94) : Color.argb(55, 107, 135, 238);
        int glow2 = theme == 2 ? Color.argb(78, 124, 73, 234)
                : theme == 1 ? Color.argb(42, 191, 136, 83) : Color.argb(48, 156, 108, 236);

        paint.setShader(new RadialGradient(w * .50f, h * .37f, w * .45f,
                glow1, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawCircle(w * .50f, h * .37f, w * .45f, paint);
        paint.setShader(new RadialGradient(w * .88f, h * .76f, w * .36f,
                glow2, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawCircle(w * .88f, h * .76f, w * .36f, paint);
        paint.setShader(null);

        int star = theme == 2 ? Color.argb(120, 129, 118, 255)
                : theme == 1 ? Color.argb(75, 158, 112, 58) : Color.argb(80, 108, 97, 215);
        paint.setColor(star);
        float[][] stars = {{.18f,.32f},{.76f,.29f},{.83f,.58f},{.20f,.61f},{.56f,.66f},{.37f,.73f}};
        for (float[] s : stars) {
            float x = w * s[0], y = h * s[1], r = Math.max(2f, w * .004f);
            canvas.drawCircle(x, y, r, paint);
            canvas.drawRect(x - r * .25f, y - r * 2.2f, x + r * .25f, y + r * 2.2f, paint);
        }

        int waveColor1 = theme == 2 ? Color.argb(95, 63, 79, 221)
                : theme == 1 ? Color.argb(65, 188, 143, 81) : Color.argb(65, 111, 132, 232);
        int waveColor2 = theme == 2 ? Color.argb(95, 118, 66, 232)
                : theme == 1 ? Color.argb(48, 160, 112, 71) : Color.argb(52, 157, 109, 235);
        wave.reset();
        wave.moveTo(0, h * .86f);
        wave.cubicTo(w * .20f, h * .82f, w * .38f, h * .96f, w * .62f, h * .91f);
        wave.cubicTo(w * .80f, h * .87f, w * .90f, h * .78f, w, h * .76f);
        wave.lineTo(w, h);
        wave.lineTo(0, h);
        wave.close();
        paint.setShader(new LinearGradient(0, h * .78f, w, h,
                waveColor1, waveColor2, Shader.TileMode.CLAMP));
        canvas.drawPath(wave, paint);
        paint.setShader(null);
    }
}

final class ReaderLoadingProgressView extends View {
    private final int theme;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float phase;
    private ValueAnimator animator;

    ReaderLoadingProgressView(Context context, int theme) {
        super(context);
        this.theme = theme;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animator == null) {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(1250L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a -> {
                phase = (float) a.getAnimatedValue();
                invalidate();
            });
        }
        animator.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float pad = Math.max(2f, h * .24f);
        float cy = h / 2f;
        float barH = Math.max(6f, h * .34f);
        rect.set(pad, cy - barH / 2f, w - pad, cy + barH / 2f);
        paint.setShader(null);
        paint.setColor(theme == 2 ? Color.argb(105, 134, 150, 190)
                : theme == 1 ? Color.argb(90, 150, 119, 83) : Color.argb(80, 113, 122, 154));
        canvas.drawRoundRect(rect, barH, barH, paint);

        float trackW = rect.width();
        float segW = trackW * .34f;
        float left = rect.left - segW + (trackW + segW) * phase;
        float right = left + segW;
        int a = theme == 1 ? Color.rgb(199, 148, 82) : Color.rgb(84, 170, 248);
        int b = theme == 1 ? Color.rgb(161, 111, 73) : Color.rgb(174, 98, 238);
        paint.setShader(new LinearGradient(left, 0, right, 0, a, b, Shader.TileMode.CLAMP));
        canvas.save();
        canvas.clipRect(rect);
        RectF seg = new RectF(left, rect.top, right, rect.bottom);
        canvas.drawRoundRect(seg, barH, barH, paint);
        canvas.restore();
        paint.setShader(null);
    }
}
