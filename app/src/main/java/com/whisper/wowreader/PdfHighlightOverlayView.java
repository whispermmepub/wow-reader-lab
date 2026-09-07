package com.whisper.wowreader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Draws persistent PDF highlights and the current long-press selection. */
final class PdfHighlightOverlayView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<PdfHighlightStore.Highlight> highlights = new ArrayList<>();
    private final List<RectF> selected = new ArrayList<>();
    private float pageWidth = 1f;
    private float pageHeight = 1.4142f;

    PdfHighlightOverlayView(Context context) {
        super(context);
        selectedPaint.setColor(Color.argb(72, 72, 132, 230));
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setPageText(PdfTextRepository.PageText text) {
        if (text != null) {
            pageWidth = Math.max(1f, text.width);
            pageHeight = Math.max(1f, text.height);
        }
        invalidate();
    }

    void setHighlights(List<PdfHighlightStore.Highlight> values) {
        highlights.clear();
        if (values != null) highlights.addAll(values);
        invalidate();
    }

    void setSelected(List<RectF> rects) {
        selected.clear();
        if (rects != null) for (RectF r : rects) if (r != null) selected.add(new RectF(r));
        invalidate();
    }

    void clearSelected() {
        if (selected.isEmpty()) return;
        selected.clear();
        invalidate();
    }

    PointF toNormalized(float x, float y) {
        RectF content = contentRect();
        if (content.width() <= 0f || content.height() <= 0f || !content.contains(x, y)) return null;
        return PdfTextRepository.clampPoint((x - content.left) / content.width(), (y - content.top) / content.height());
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        RectF content = contentRect();
        for (PdfHighlightStore.Highlight h : highlights) {
            paint.setColor(h.color);
            for (RectF n : h.rects) canvas.drawRoundRect(map(n, content), dp(2), dp(2), paint);
        }
        for (RectF n : selected) canvas.drawRoundRect(map(n, content), dp(2), dp(2), selectedPaint);
    }

    private RectF contentRect() {
        float vw = Math.max(1f, getWidth());
        float vh = Math.max(1f, getHeight());
        float pageAspect = pageWidth / pageHeight;
        float viewAspect = vw / vh;
        if (viewAspect > pageAspect) {
            float w = vh * pageAspect;
            float left = (vw - w) / 2f;
            return new RectF(left, 0f, left + w, vh);
        }
        float h = vw / Math.max(0.01f, pageAspect);
        float top = (vh - h) / 2f;
        return new RectF(0f, top, vw, top + h);
    }

    private RectF map(RectF n, RectF c) {
        return new RectF(
                c.left + n.left * c.width(),
                c.top + n.top * c.height(),
                c.left + n.right * c.width(),
                c.top + n.bottom * c.height());
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
