package com.whisper.wowreader;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.PathInterpolator;

final class PageCurlView extends View {
    // Fine enough to keep the fold smooth while it follows a finger at 60 Hz.
    private static final int MESH_W = 60;
    private static final int MESH_H = 24;

    private final Paint pagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint castShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint foldShadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backsidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint creasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] verts = new float[(MESH_W + 1) * (MESH_H + 1) * 2];
    private final Path foldPath = new Path();
    private final Path creasePath = new Path();
    private final Path outerPath = new Path();

    private Bitmap fromBitmap;
    private Bitmap toBitmap;
    private ValueAnimator animator;
    private float progress;
    private float touchY = 0.5f;
    private int direction = 1;
    private Runnable completion;

    PageCurlView(Context context) {
        super(context);
        setVisibility(GONE);
        setClickable(false);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        float d = getResources().getDisplayMetrics().density;
        creasePaint.setStyle(Paint.Style.STROKE);
        creasePaint.setStrokeWidth(Math.max(1f, d * 1.05f));
        outerEdgePaint.setStyle(Paint.Style.STROKE);
        outerEdgePaint.setStrokeWidth(Math.max(1f, d * 0.72f));
    }

    boolean isBusy() {
        return getVisibility() == VISIBLE || (animator != null && animator.isRunning());
    }

    void hold(Bitmap current) {
        cancelAnimator(false);
        recycleBitmaps();
        fromBitmap = current;
        toBitmap = null;
        progress = 0f;
        touchY = 0.5f;
        direction = 1;
        setAlpha(1f);
        setVisibility(VISIBLE);
        bringToFront();
        invalidate();
    }

    void beginInteractive(Bitmap target, int direction, float startProgress, float touchY) {
        if (fromBitmap == null || target == null) {
            if (target != null && !target.isRecycled()) target.recycle();
            return;
        }
        cancelAnimator(false);
        if (toBitmap != null && toBitmap != fromBitmap && !toBitmap.isRecycled()) toBitmap.recycle();
        toBitmap = target;
        this.direction = direction < 0 ? -1 : 1;
        this.progress = clamp(startProgress, 0f, 1f);
        this.touchY = clamp(touchY, 0.07f, 0.93f);
        setVisibility(VISIBLE);
        bringToFront();
        invalidate();
    }

    void updateInteractive(float progress, float touchY) {
        if (fromBitmap == null || toBitmap == null || animator != null) return;
        this.progress = clamp(progress, 0f, 1f);
        this.touchY = clamp(touchY, 0.07f, 0.93f);
        postInvalidateOnAnimation();
    }

    void replaceTarget(Bitmap target) {
        if (target == null) return;
        if (toBitmap != null && toBitmap != fromBitmap && !toBitmap.isRecycled()) toBitmap.recycle();
        toBitmap = target;
        postInvalidateOnAnimation();
    }

    void settleInteractive(boolean completeTurn, float velocityX, Runnable completion) {
        if (fromBitmap == null || toBitmap == null) {
            finishImmediately(completion);
            return;
        }

        cancelAnimator(false);
        this.completion = completion;
        float start = progress;
        float end = completeTurn ? 1f : 0f;
        float distance = Math.abs(end - start);
        if (distance < 0.002f) {
            progress = end;
            invalidate();
            finishSettle(completeTurn);
            return;
        }

        float width = Math.max(1f, getWidth());
        float normalizedVelocity = Math.abs(velocityX) / width;
        long duration;
        if (normalizedVelocity > 0.30f) {
            duration = (long) (distance / normalizedVelocity * 1000f * 0.48f);
        } else {
            duration = (long) (115f + distance * (completeTurn ? 235f : 190f));
        }
        if (completeTurn) duration = Math.max(68L, Math.min(330L, duration));
        else duration = Math.max(82L, Math.min(250L, duration));

        animator = ValueAnimator.ofFloat(start, end);
        animator.setDuration(duration);
        animator.setInterpolator(completeTurn
                ? new PathInterpolator(0.16f, 0.00f, 0.16f, 1.00f)
                : new PathInterpolator(0.28f, 0.00f, 0.40f, 1.00f));
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            postInvalidateOnAnimation();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override public void onAnimationEnd(Animator animation) {
                PageCurlView.this.animator = null;
                if (!cancelled) finishSettle(completeTurn);
            }
        });
        animator.start();
    }

    void startTapCurl(Bitmap target, int direction, float touchY, Runnable completion) {
        // Edge taps use the exact same page mesh as finger drags. Starting a hair inside
        // the edge makes the first rendered frame visibly curl instead of looking like a cut.
        beginInteractive(target, direction, 0.012f, clamp(touchY, 0.10f, 0.90f));
        float syntheticVelocity = (direction < 0 ? 1f : -1f) * Math.max(1250f, getWidth() * 1.55f);
        settleInteractive(true, syntheticVelocity, completion);
    }

    void startCurl(Bitmap target, int direction, Runnable completion) {
        startTapCurl(target, direction, 0.5f, completion);
    }

    void release() {
        cancelAnimator(false);
        completion = null;
        setVisibility(GONE);
        recycleBitmaps();
    }

    private void finishSettle(boolean completeTurn) {
        Runnable done = completion;
        completion = null;
        if (completeTurn) {
            setVisibility(GONE);
            recycleBitmaps();
        } else {
            // On cancellation keep the old page covering the WebView until the activity
            // restores the original WebView page, preventing a one-frame target-page flash.
            progress = 0f;
            invalidate();
        }
        if (done != null) done.run();
    }

    private void finishImmediately(Runnable done) {
        setVisibility(GONE);
        recycleBitmaps();
        if (done != null) done.run();
    }

    private void cancelAnimator(boolean notify) {
        if (animator == null) return;
        Runnable old = completion;
        completion = null;
        ValueAnimator a = animator;
        animator = null;
        a.cancel();
        if (notify && old != null) old.run();
    }

    private void recycleBitmaps() {
        if (fromBitmap != null && !fromBitmap.isRecycled()) fromBitmap.recycle();
        if (toBitmap != null && toBitmap != fromBitmap && !toBitmap.isRecycled()) toBitmap.recycle();
        fromBitmap = null;
        toBitmap = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (fromBitmap == null || fromBitmap.isRecycled()) return;
        if (toBitmap == null || toBitmap.isRecycled()) {
            canvas.drawBitmap(fromBitmap, 0f, 0f, pagePaint);
            return;
        }

        // Like Play Books, the destination page is already underneath while the
        // current sheet itself follows the finger and curls away.
        canvas.drawBitmap(toBitmap, 0f, 0f, pagePaint);
        drawNaturalCurl(canvas, fromBitmap, progress, direction, touchY);
    }

    private void drawNaturalCurl(Canvas canvas, Bitmap bitmap, float amount,
                                 int turnDirection, float touchYNorm) {
        float q = clamp(amount, 0f, 1f);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;
        if (q < 0.001f) {
            canvas.drawBitmap(bitmap, 0f, 0f, pagePaint);
            return;
        }

        // The crease follows drag distance linearly. A small finger-dependent tilt
        // and z-like perspective bow stop the fold looking like a flat wipe.
        float creaseBase = width * (1f - q * 1.035f);
        float wave = (float) Math.sin(Math.PI * q);
        float anchor = clamp(touchYNorm, 0.10f, 0.90f);
        float bow = width * 0.0105f * wave;
        float tilt = (anchor - 0.5f) * width * 0.090f * wave;
        float backRatio = 0.70f + 0.26f * q;
        float anchorY = anchor * height;
        int out = 0;

        for (int row = 0; row <= MESH_H; row++) {
            float v = row / (float) MESH_H;
            float y = height * v;
            float creaseLogical = creaseBase
                    + (float) Math.sin(Math.PI * v) * bow
                    + (v - anchor) * tilt;
            float foldedWidth = Math.max(1f, width - creaseLogical);

            for (int col = 0; col <= MESH_W; col++) {
                float u = col / (float) MESH_W;
                float sourceX = width * u;
                float logicalX = turnDirection > 0 ? sourceX : width - sourceX;
                float nxLogical = logicalX;
                float ny = y;

                if (logicalX > creaseLogical) {
                    float t = clamp((logicalX - creaseLogical) / foldedWidth, 0f, 1f);
                    float cylinder = (float) Math.sin(Math.PI * t);
                    float shoulder = (float) Math.sin(Math.PI * 0.5f * t);
                    float depth = cylinder * wave;

                    nxLogical = creaseLogical
                            - t * foldedWidth * backRatio
                            + cylinder * foldedWidth * (0.092f + 0.022f * wave)
                            - shoulder * width * 0.0075f * q;

                    // 2D projection of a shallow 3D bulge around the user's grab line.
                    float perspective = 1f + depth * 0.030f;
                    ny = anchorY + (y - anchorY) * perspective;
                    ny += cylinder * wave * height * 0.010f * ((v - anchor) * 2f);
                }

                verts[out++] = turnDirection > 0 ? nxLogical : width - nxLogical;
                verts[out++] = ny;
            }
        }

        canvas.drawBitmapMesh(bitmap, MESH_W, MESH_H, verts, 0, null, 0, pagePaint);
        if (q > 0.006f && q < 0.996f) {
            drawFoldLighting(canvas, q, wave, creaseBase, bow, tilt,
                    backRatio, turnDirection, anchor);
        }
    }

    private void drawFoldLighting(Canvas canvas, float q, float wave, float creaseBase,
                                  float bow, float tilt, float backRatio,
                                  int turnDirection, float anchor) {
        int width = getWidth();
        int height = getHeight();
        foldPath.reset();
        creasePath.reset();
        outerPath.reset();

        final int samples = 30;
        float midV = anchor;
        float midCreaseLogical = creaseBase
                + (float) Math.sin(Math.PI * midV) * bow
                + (midV - anchor) * tilt;
        float midFolded = Math.max(1f, width - midCreaseLogical);
        float midOuterLogical = midCreaseLogical
                - midFolded * backRatio
                - width * 0.0075f * q;
        float midCrease = screenX(midCreaseLogical, width, turnDirection);
        float midOuter = screenX(midOuterLogical, width, turnDirection);

        for (int i = 0; i <= samples; i++) {
            float v = i / (float) samples;
            float y = height * v;
            float creaseLogical = creaseBase
                    + (float) Math.sin(Math.PI * v) * bow
                    + (v - anchor) * tilt;
            float folded = Math.max(1f, width - creaseLogical);
            float outerLogical = creaseLogical
                    - folded * backRatio
                    - width * 0.0075f * q;
            float cx = screenX(creaseLogical, width, turnDirection);
            if (i == 0) {
                foldPath.moveTo(cx, y);
                creasePath.moveTo(cx, y);
            } else {
                foldPath.lineTo(cx, y);
                creasePath.lineTo(cx, y);
            }
        }
        for (int i = samples; i >= 0; i--) {
            float v = i / (float) samples;
            float y = height * v;
            float creaseLogical = creaseBase
                    + (float) Math.sin(Math.PI * v) * bow
                    + (v - anchor) * tilt;
            float folded = Math.max(1f, width - creaseLogical);
            float outerLogical = creaseLogical
                    - folded * backRatio
                    - width * 0.0075f * q;
            float ox = screenX(outerLogical, width, turnDirection);
            foldPath.lineTo(ox, y);
            if (i == samples) outerPath.moveTo(ox, y); else outerPath.lineTo(ox, y);
        }
        foldPath.close();

        // The reverse side is slightly translucent so reversed page ink shows through,
        // which reads much more like thin paper than a solid grey polygon.
        backsidePaint.setShader(new LinearGradient(
                midOuter, 0f, midCrease, 0f,
                new int[]{
                        Color.argb((int) (168f * wave), 205, 207, 211),
                        Color.argb((int) (82f * wave), 247, 247, 244),
                        Color.argb((int) (46f * wave), 255, 255, 252),
                        Color.argb((int) (132f * wave), 190, 193, 198)
                },
                new float[]{0f, 0.25f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(foldPath, backsidePaint);
        backsidePaint.setShader(null);

        float castWidth = Math.max(16f,
                Math.min(width * 0.145f, width * (0.028f + 0.102f * wave)));
        float castEnd = turnDirection > 0 ? midCrease + castWidth : midCrease - castWidth;
        castShadowPaint.setShader(new LinearGradient(
                midCrease, 0f, castEnd, 0f,
                new int[]{
                        Color.argb((int) (154f * wave), 0, 0, 0),
                        Color.argb((int) (62f * wave), 0, 0, 0),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.30f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(Math.min(midCrease, castEnd), 0f,
                Math.max(midCrease, castEnd), height, castShadowPaint);
        castShadowPaint.setShader(null);

        float selfWidth = Math.max(11f,
                Math.min(width * 0.085f, Math.abs(midCrease - midOuter) * 0.24f));
        float selfEnd = turnDirection > 0 ? midCrease - selfWidth : midCrease + selfWidth;
        foldShadePaint.setShader(new LinearGradient(
                selfEnd, 0f, midCrease, 0f,
                new int[]{
                        Color.TRANSPARENT,
                        Color.argb((int) (102f * wave), 42, 43, 46),
                        Color.argb((int) (42f * wave), 255, 255, 255)
                },
                new float[]{0f, 0.70f, 1f}, Shader.TileMode.CLAMP));
        canvas.save();
        canvas.clipPath(foldPath);
        canvas.drawRect(Math.min(selfEnd, midCrease), 0f,
                Math.max(selfEnd, midCrease), height, foldShadePaint);
        canvas.restore();
        foldShadePaint.setShader(null);

        creasePaint.setColor(Color.argb((int) (192f * wave), 255, 255, 255));
        canvas.drawPath(creasePath, creasePaint);
        outerEdgePaint.setColor(Color.argb((int) (88f * wave), 35, 36, 38));
        canvas.drawPath(outerPath, outerEdgePaint);
    }

    private float screenX(float logical, int width, int turnDirection) {
        return turnDirection > 0 ? logical : width - logical;
    }

    private float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    protected void onDetachedFromWindow() {
        release();
        super.onDetachedFromWindow();
    }
}
