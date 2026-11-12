package com.gym.tracker;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class RingView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private float progress;

    public RingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float stroke = 10 * getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setColor(context.getColor(R.color.surface_lowest));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(stroke);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(context.getColor(R.color.primary));
    }

    public void setProgress(float value) {
        ValueAnimator animator = ValueAnimator.ofFloat(0, Math.min(1, value));
        animator.setDuration(700);
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = track.getStrokeWidth() / 2;
        bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
        canvas.drawArc(bounds, 0, 360, false, track);
        if (progress > 0) canvas.drawArc(bounds, -90, 360 * progress, false, arc);
    }
}
