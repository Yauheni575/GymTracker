package com.gym.tracker;

import android.database.Cursor;
import android.view.View;

import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

public class ProgressFragment extends Fragment {

    private Db db;
    private long selected = -1;

    public ProgressFragment() {
        super(R.layout.fragment_progress);
    }

    @Override
    public void onResume() {
        super.onResume();
        db = Db.get(requireContext());
        View strip = (View) requireView().findViewById(R.id.exercises).getParent();
        strip.setOnTouchListener((v, e) -> {
            if (v.canScrollHorizontally(1) || v.canScrollHorizontally(-1))
                v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        load();
    }

    private void load() {
        View root = requireView();
        ChipGroup chips = root.findViewById(R.id.exercises);
        chips.removeAllViews();
        Chip toSelect = null;

        try (Cursor c = db.progressExercises()) {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String name = c.getString(1);
                int sessions = c.getInt(3);
                boolean usesWeight = c.getInt(4) == 1;
                Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_group_chip, chips, false);
                chip.setText(c.getInt(2) == 1 ? "★ " + name : name);
                Runnable select = () -> {
                    selected = id;
                    show(id, name, sessions, usesWeight);
                };
                chip.setTag(select);
                chip.setOnClickListener(v -> select.run());
                chips.addView(chip);
                if (toSelect == null || id == selected) toSelect = chip;
            }
        }

        boolean empty = toSelect == null;
        root.findViewById(R.id.content).setVisibility(empty ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.empty).setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) return;

        toSelect.setChecked(true);
        ((Runnable) toSelect.getTag()).run();
        ((ChartView) root.findViewById(R.id.volume))
                .setPoints(db.weeklyVolume(), "График появится после 2 недель тренировок");
    }

    private void show(long id, String name, int sessions, boolean usesWeight) {
        View root = requireView();
        List<Double> points = db.progressByDate(id);
        String unit = usesWeight ? " кг" : " повт.";
        double first = points.get(0);
        double last = points.get(points.size() - 1);

        Ui.text(root, R.id.chart_title, name);
        Ui.text(root, R.id.chart_subtitle, (usesWeight ? "Лучший вес" : "Лучшее число повторений")
                + " за тренировку · " + sessions + " " + Coach.plural(sessions, "тренировка", "тренировки", "тренировок"));
        ((ChartView) root.findViewById(R.id.chart)).setPoints(points, "Нужно минимум 2 тренировки");
        Ui.stat(root, R.id.stat_first, Coach.kg(first) + unit, "было");
        Ui.stat(root, R.id.stat_last, Coach.kg(last) + unit, "сейчас");
        String percent = Coach.percent(first, last);
        Ui.stat(root, R.id.stat_delta, Coach.delta(first, last, ""),
                "рост," + unit + (percent.isEmpty() ? "" : " " + percent));
    }
}
