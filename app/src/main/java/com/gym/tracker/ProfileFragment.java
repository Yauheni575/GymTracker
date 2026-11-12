package com.gym.tracker;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

public class ProfileFragment extends Fragment {

    private Db db;

    public ProfileFragment() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        db = Db.get(requireContext());
        view.findViewById(R.id.log_weight).setOnClickListener(v -> weightDialog());
        view.findViewById(R.id.edit).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), OnboardingActivity.class)
                        .putExtra(OnboardingActivity.MODE, OnboardingActivity.EDIT)));
        Ui.text(view, R.id.account, "Вы вошли как " + Auth.user(requireContext()));
        view.findViewById(R.id.logout).setOnClickListener(v -> {
            Auth.logout(requireContext());
            startActivity(new Intent(requireContext(), AuthActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        View v = requireView();
        Coach.Person p = db.person();
        String shown = p.name.isEmpty() ? Auth.user(requireContext()) : p.name;
        Ui.text(v, R.id.avatar, Ui.initial(shown));
        Ui.text(v, R.id.title, shown);
        Ui.text(v, R.id.subtitle, "Цель: " + p.goal + " · " + p.perWeek + " "
                + Coach.plural(p.perWeek, "тренировка", "тренировки", "тренировок") + " в неделю");

        try (Cursor c = db.stats()) {
            c.moveToFirst();
            long workouts = c.getLong(0);
            long sets = c.getLong(1);
            double tonnage = c.getDouble(2);
            Ui.stat(v, R.id.stat_workouts, String.valueOf(workouts),
                    Coach.plural(workouts, "тренировка", "тренировки", "тренировок"));
            Ui.stat(v, R.id.stat_sets, String.valueOf(sets), Coach.plural(sets, "подход", "подхода", "подходов"));
            Ui.stat(v, R.id.stat_tonnage, tonnage >= 1000
                    ? Coach.kg(Math.round(tonnage / 100) / 10.0) + " т"
                    : Coach.kg(tonnage) + " кг", "поднято всего");
        }
        Ui.stat(v, R.id.stat_week, db.workoutsThisWeek() + " / " + p.perWeek, "на этой неделе");

        String info = p.weight > 0 ? Coach.kg(p.weight) + " кг" : "Вес ещё не записан";
        if (p.weight > 0 && p.height >= 100) info += " · ИМТ " + Coach.bmi(p.weight, p.height);
        Ui.text(v, R.id.bmi, info);
        ((ChartView) v.findViewById(R.id.weight_chart))
                .setPoints(db.bodyWeights(), "Записывайте вес, чтобы видеть динамику");

        StringBuilder about = new StringBuilder();
        about.append(p.sex).append(" · ").append(p.age).append(" ")
                .append(Coach.plural(p.age, "год", "года", "лет")).append(" · ").append(p.height).append(" см");
        if (p.target > 0) about.append("\nЖелаемый вес: ").append(Coach.kg(p.target)).append(" кг");
        about.append("\nОпыт: ").append(p.experience);
        if (!p.priorities.isEmpty()) about.append("\nПриоритет: ").append(String.join(", ", p.priorities));
        long kcal = Coach.calories(p);
        if (kcal > 0) about.append("\nНорма калорий: ≈ ").append(kcal).append(" ккал в день");
        Ui.text(v, R.id.about, about);
    }

    private void weightDialog() {
        View form = getLayoutInflater().inflate(R.layout.dialog_input, null);
        TextInputLayout box = form.findViewById(R.id.box);
        EditText input = form.findViewById(R.id.input);
        box.setHint("Вес тела, кг");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        double last = db.lastBodyWeight();
        if (last > 0) input.setText(Coach.kg(last).replace(',', '.'));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Вес сегодня")
                .setView(form)
                .setPositiveButton("Сохранить", (d, which) -> {
                    double w;
                    try {
                        w = Double.parseDouble(input.getText().toString().replace(',', '.'));
                    } catch (NumberFormatException e) {
                        w = 0;
                    }
                    if (w < 20 || w > 400) {
                        Toast.makeText(requireContext(), "Введите вес от 20 до 400 кг", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    db.setBodyWeight(w);
                    load();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
}
