package com.example.medicinereminder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Simple DonutChartView stub showing a 63% green / 37% grey donut ring.
 */
public class DonutChartView extends View {

    private final Paint greenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint greyPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval       = new RectF();

    public DonutChartView(Context context) {
        super(context);
        init();
    }

    public DonutChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DonutChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        greenPaint.setColor(Color.parseColor("#2E7D47"));
        greenPaint.setStyle(Paint.Style.STROKE);
        greenPaint.setStrokeWidth(18f);
        greenPaint.setStrokeCap(Paint.Cap.ROUND);

        greyPaint.setColor(Color.parseColor("#EFEFEA"));
        greyPaint.setStyle(Paint.Style.STROKE);
        greyPaint.setStrokeWidth(18f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float margin = 20f;
        oval.set(margin, margin, w - margin, h - margin);

        // Background ring
        canvas.drawArc(oval, 0, 360, false, greyPaint);
        // Green arc = 63%
        canvas.drawArc(oval, -90, 360 * 0.63f, false, greenPaint);
    }
}
