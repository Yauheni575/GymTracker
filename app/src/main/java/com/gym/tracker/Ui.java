package com.gym.tracker;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

final class Ui {

    private Ui() {
    }

    static void text(View root, int id, CharSequence value) {
        ((TextView) root.findViewById(id)).setText(value);
    }

    static void stat(View root, int id, String value, String label) {
        View card = root.findViewById(id);
        text(card, R.id.value, value);
        text(card, R.id.label, label);
    }

    static boolean drawing(ImageView view, String exercise, boolean animate) {
        int first = resource(view, Catalog.image(exercise, 0));
        int second = resource(view, Catalog.image(exercise, 1));
        if (first != 0) view.setImageResource(first);
        view.setVisibility(first != 0 ? View.VISIBLE : View.GONE);
        if (animate && first != 0 && second != 0) {
            boolean[] showSecond = {false};
            Runnable[] flip = new Runnable[1];
            flip[0] = () -> {
                if (!view.isAttachedToWindow()) return;
                showSecond[0] = !showSecond[0];
                view.setImageResource(showSecond[0] ? second : first);
                view.postDelayed(flip[0], 1100);
            };
            view.postDelayed(flip[0], 1100);
        }
        return first != 0;
    }

    private static int resource(View view, String name) {
        return name == null ? 0 : view.getResources().getIdentifier(name, "drawable", view.getContext().getPackageName());
    }

    static void picture(View root, String exercise, boolean animate) {
        boolean hasDrawing = drawing(root.findViewById(R.id.drawing), exercise, animate);
        MuscleMapView map = root.findViewById(R.id.map);
        map.setExercise(exercise);
        map.setVisibility(hasDrawing || Catalog.primary(exercise).isEmpty() ? View.GONE : View.VISIBLE);
    }

    static String initial(String s) {
        return s.substring(0, s.offsetByCodePoints(0, 1)).toUpperCase();
    }

    static void confirm(Context context, String message, Runnable action) {
        new MaterialAlertDialogBuilder(context)
                .setMessage(message)
                .setPositiveButton("Удалить", (d, which) -> action.run())
                .setNegativeButton("Отмена", null)
                .show();
    }

    static void check(ChipGroup group, Collection<String> values) {
        for (int i = 0; i < group.getChildCount(); i++) {
            Chip chip = (Chip) group.getChildAt(i);
            chip.setChecked(values.contains(chip.getText().toString()));
        }
    }

    static Set<String> checked(ChipGroup group) {
        Set<String> values = new LinkedHashSet<>();
        for (int i = 0; i < group.getChildCount(); i++) {
            Chip chip = (Chip) group.getChildAt(i);
            if (chip.isChecked()) values.add(chip.getText().toString());
        }
        return values;
    }
}
