package com.gym.tracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MuscleMapView extends View {

    private static List<Part> parts;

    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint warm = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Set<String> primary = Collections.emptySet();
    private Set<String> secondary = Collections.emptySet();

    private static final class Part {
        final boolean back;
        final String muscle;
        final Path path;

        Part(boolean back, String muscle, Path path) {
            this.back = back;
            this.muscle = muscle;
            this.path = path;
        }
    }

    public MuscleMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        base.setColor(context.getColor(R.color.muscle_base));
        hot.setColor(context.getColor(R.color.muscle_hot));
        warm.setColor(context.getColor(R.color.muscle_warm));
        if (parts == null) parts = load(context);
    }

    public void setMuscles(Collection<String> primary, Collection<String> secondary) {
        this.primary = new HashSet<>(primary);
        this.secondary = new HashSet<>(secondary);
        invalidate();
    }

    public void setExercise(String name) {
        setMuscles(Catalog.primary(name), Catalog.secondary(name));
    }

    private static List<Part> load(Context context) {
        List<Part> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getResources().openRawResource(R.raw.body), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] t = line.trim().split(" ");
                if (t.length < 6) continue;
                Path path = new Path();
                for (int i = 2; i + 1 < t.length; i += 2) {
                    float x = Float.parseFloat(t[i]);
                    float y = Float.parseFloat(t[i + 1]);
                    if (i == 2) path.moveTo(x, y);
                    else path.lineTo(x, y);
                }
                path.close();
                list.add(new Part(t[0].equals("B"), t[1], path));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return list;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float scale = Math.min(getWidth() / 210f, getHeight() / 200f);
        float dx = (getWidth() - 210 * scale) / 2;
        float dy = (getHeight() - 200 * scale) / 2;
        for (Part p : parts) {
            canvas.save();
            canvas.translate(dx + (p.back ? 110 * scale : 0), dy);
            canvas.scale(scale, scale);
            canvas.drawPath(p.path, primary.contains(p.muscle) ? hot : secondary.contains(p.muscle) ? warm : base);
            canvas.restore();
        }
    }
}
