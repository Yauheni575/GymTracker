package com.gym.tracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChartView extends View {

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint area = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path areaPath = new Path();
    private final float dp;
    private final int primary;
    private List<Double> points = new ArrayList<>();
    private String empty = "";

    public ChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        primary = context.getColor(R.color.primary);
        line.setColor(primary);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(3 * dp);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        dot.setColor(primary);
        grid.setColor(context.getColor(R.color.outline_variant));
        grid.setStrokeWidth(dp);
        text.setColor(context.getColor(R.color.on_surface_variant));
        text.setTextSize(12 * dp);
    }

    public void setPoints(List<Double> points, String empty) {
        this.points = points;
        this.empty = empty;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        area.setShader(new LinearGradient(0, 0, 0, h,
                (primary & 0x00FFFFFF) | 0x55000000, primary & 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = text.getTextSize();
        if (points.size() < 2) {
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(empty, getWidth() / 2f, getHeight() / 2f, text);
            text.setTextAlign(Paint.Align.LEFT);
            return;
        }

        double min = Collections.min(points);
        double max = Collections.max(points);
        if (max == min) {
            min -= 1;
            max += 1;
        }

        String[] labels = new String[3];
        float left = 0;
        for (int i = 0; i < 3; i++) {
            labels[i] = Coach.kg(max - (max - min) * i / 2);
            left = Math.max(left, text.measureText(labels[i]));
        }
        left += s;
        float right = getWidth() - 6 * dp;
        float top = s;
        float bottom = getHeight() - s;

        for (int i = 0; i < 3; i++) {
            float y = top + (bottom - top) * i / 2;
            canvas.drawLine(left, y, right, y, grid);
            canvas.drawText(labels[i], 0, y + s / 3, text);
        }

        int n = points.size();
        float[] xs = new float[n];
        float[] ys = new float[n];
        linePath.reset();
        for (int i = 0; i < n; i++) {
            xs[i] = left + (right - left) * i / (n - 1);
            ys[i] = (float) (bottom - (bottom - top) * (points.get(i) - min) / (max - min));
            if (i == 0) linePath.moveTo(xs[i], ys[i]);
            else linePath.lineTo(xs[i], ys[i]);
        }
        areaPath.set(linePath);
        areaPath.lineTo(right, bottom);
        areaPath.lineTo(left, bottom);
        areaPath.close();

        canvas.drawPath(areaPath, area);
        canvas.drawPath(linePath, line);
        for (int i = 0; i < n; i++) {
            canvas.drawCircle(xs[i], ys[i], (i == n - 1 ? 5 : 3.5f) * dp, dot);
        }
    }
}
