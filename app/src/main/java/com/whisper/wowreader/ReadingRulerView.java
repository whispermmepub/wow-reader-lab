package com.whisper.wowreader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Non-interactive reading ruler; text scrolls underneath the centered guide. */
final class ReadingRulerView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int lineCount = 1;
    private int theme = 0;
    private boolean rulerEnabled = false;

    ReadingRulerView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    void configure(boolean enabled, int lines, int readerTheme) {
        rulerEnabled = enabled;
        lineCount = lines <= 1 ? 1 : lines <= 3 ? 3 : 5;
        theme = readerTheme;
        setVisibility(enabled ? VISIBLE : GONE);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!rulerEnabled || getWidth() <= 0 || getHeight() <= 0) return;
        float density = getResources().getDisplayMetrics().density;
        float bandHeight = (lineCount == 1 ? 38f : lineCount == 3 ? 74f : 110f) * density;
        float top = (getHeight() - bandHeight) / 2f;
        float bottom = top + bandHeight;

        int fillColor;
        int edgeColor;
        if (theme == 2) {
            fillColor = Color.argb(35, 93, 158, 255);
            edgeColor = Color.argb(105, 112, 176, 255);
        } else if (theme == 1) {
            fillColor = Color.argb(34, 169, 116, 56);
            edgeColor = Color.argb(95, 145, 92, 45);
        } else {
            fillColor = Color.argb(28, 60, 108, 210);
            edgeColor = Color.argb(85, 68, 112, 214);
        }
        fill.setColor(fillColor);
        edge.setColor(edgeColor);
        edge.setStrokeWidth(Math.max(1f, density));
        canvas.drawRect(0f, top, getWidth(), bottom, fill);
        canvas.drawLine(0f, top, getWidth(), top, edge);
        canvas.drawLine(0f, bottom, getWidth(), bottom, edge);
    }
}
