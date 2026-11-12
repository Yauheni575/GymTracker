package com.gym.tracker;

import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

final class SetForm {

    private final EditText weight;
    private final EditText reps;

    SetForm(View root, double[] last) {
        weight = bind(root.findViewById(R.id.weight_stepper), "Вес, кг", last == null ? 20 : last[0], 2.5, true);
        reps = bind(root.findViewById(R.id.reps_stepper), "Повторения", last == null ? 10 : last[1], 1, false);
    }

    double[] read() {
        try {
            double w = parse(weight);
            int r = (int) parse(reps);
            if (r > 0 && w >= 0 && w < 1000) return new double[]{w, r};
        } catch (NumberFormatException ignored) {
        }
        Toast.makeText(weight.getContext(), "Проверьте вес и количество повторений", Toast.LENGTH_SHORT).show();
        return null;
    }

    private static EditText bind(View stepper, String label, double value, double step, boolean decimal) {
        Ui.text(stepper, R.id.label, label);
        EditText input = stepper.findViewById(R.id.value);
        input.setInputType(decimal
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                : InputType.TYPE_CLASS_NUMBER);
        input.setText(plain(value));
        stepper.findViewById(R.id.minus).setOnClickListener(v -> shift(input, -step));
        stepper.findViewById(R.id.plus).setOnClickListener(v -> shift(input, step));
        return input;
    }

    private static void shift(EditText input, double delta) {
        double v;
        try {
            v = parse(input);
        } catch (NumberFormatException e) {
            v = 0;
        }
        input.setText(plain(Math.max(0, v + delta)));
    }

    private static double parse(EditText input) {
        return Double.parseDouble(input.getText().toString().replace(',', '.'));
    }

    private static String plain(double v) {
        return Coach.kg(v).replace(',', '.');
    }
}
