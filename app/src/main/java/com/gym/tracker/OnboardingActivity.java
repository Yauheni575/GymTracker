package com.gym.tracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class OnboardingActivity extends AppCompatActivity {

    static final String MODE = "mode";
    static final int NEW = 0;
    static final int EDIT = 1;
    static final int COMPLETE = 2;

    private final List<View> pages = new ArrayList<>();
    private int index;
    private int mode;
    private Coach.Person person;
    private View bodyPage;
    private View targetPage;

    private static final class Option {
        final String value;
        final String subtitle;
        final int icon;
        final int image;

        Option(String value, String subtitle, int icon, int image) {
            this.value = value;
            this.subtitle = subtitle;
            this.icon = icon;
            this.image = image;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        mode = getIntent().getIntExtra(MODE, NEW);
        person = Auth.user(this) == null ? new Coach.Person() : Db.get(this).person();
        FrameLayout container = findViewById(R.id.pages);

        if (mode == NEW) pages.add(inflate(R.layout.page_welcome, container));
        pages.add(options(container, "Ваш пол", "Нужен для точного расчёта калорий", new Option[]{
                new Option("Мужской", "", R.drawable.ic_male, 0),
                new Option("Женский", "", R.drawable.ic_female, 0)}, () -> person.sex, v -> person.sex = v));
        pages.add(bodyPage = body(container));
        pages.add(options(container, "Чего хотите добиться?", "Подберём число повторений и питание", new Option[]{
                new Option("Масса", "Набрать мышечную массу", 0, R.drawable.ex_barbell_curl_0),
                new Option("Сила", "Поднимать больше в базовых упражнениях", 0, R.drawable.ex_barbell_deadlift_0),
                new Option("Похудение", "Сбросить вес и подтянуть тело", 0, R.drawable.ex_dumbbell_lunges_0),
                new Option("Выносливость", "Дольше работать без усталости", 0, R.drawable.ex_pushups_0)},
                () -> person.goal, v -> person.goal = v));
        pages.add(targetPage = inflate(R.layout.page_target, container));
        pages.add(options(container, "Ваш опыт тренировок", "От этого зависят советы по нагрузке", new Option[]{
                new Option("Новичок", "Занимаюсь меньше полугода", R.drawable.ic_seedling, 0),
                new Option("Есть опыт", "От полугода до двух лет", R.drawable.ic_workouts, 0),
                new Option("Профи", "Тренируюсь больше двух лет", R.drawable.ic_trophy, 0)},
                () -> person.experience, v -> person.experience = v));
        pages.add(priorities(container));
        pages.add(frequency(container));

        for (View page : pages) {
            page.setVisibility(View.GONE);
            container.addView(page);
        }

        findViewById(R.id.next).setOnClickListener(v -> next());
        findViewById(R.id.back).setOnClickListener(v -> back());
        findViewById(R.id.login).setOnClickListener(v ->
                startActivity(new Intent(this, AuthActivity.class)));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                back();
            }
        });
        show(0);
    }

    private View inflate(int layout, ViewGroup container) {
        return getLayoutInflater().inflate(layout, container, false);
    }

    private View options(ViewGroup container, String title, String subtitle, Option[] options,
                         Supplier<String> get, Consumer<String> set) {
        View page = inflate(R.layout.page_options, container);
        Ui.text(page, R.id.title, title);
        Ui.text(page, R.id.subtitle, subtitle);
        LinearLayout list = page.findViewById(R.id.options);
        List<MaterialCardView> cards = new ArrayList<>();
        Runnable refresh = () -> {
            for (MaterialCardView card : cards) style(card, card.getTag().equals(get.get()));
        };
        for (Option o : options) {
            MaterialCardView card = (MaterialCardView) inflate(R.layout.item_option, list);
            card.setTag(o.value);
            Ui.text(card, R.id.title, o.value);
            TextView sub = card.findViewById(R.id.subtitle);
            sub.setText(o.subtitle);
            sub.setVisibility(o.subtitle.isEmpty() ? View.GONE : View.VISIBLE);
            ImageView image = card.findViewById(R.id.image);
            if (o.image != 0) image.setImageResource(o.image);
            image.setVisibility(o.image != 0 ? View.VISIBLE : View.GONE);
            ImageView icon = card.findViewById(R.id.icon);
            if (o.icon != 0) icon.setImageResource(o.icon);
            icon.setVisibility(o.icon != 0 ? View.VISIBLE : View.GONE);
            card.setOnClickListener(v -> {
                set.accept(o.value);
                refresh.run();
            });
            cards.add(card);
            list.addView(card);
        }
        refresh.run();
        return page;
    }

    private void style(MaterialCardView card, boolean selected) {
        card.setCardBackgroundColor(getColor(selected ? R.color.primary_container : R.color.surface_highest));
        card.setStrokeColor(getColor(R.color.primary));
        card.setStrokeWidth(selected ? Math.round(2 * getResources().getDisplayMetrics().density) : 0);
        View check = card.findViewById(R.id.check);
        if (check != null) check.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
    }

    private View body(ViewGroup container) {
        View page = inflate(R.layout.page_body, container);
        ((EditText) page.findViewById(R.id.name)).setText(person.name);
        fill(page, R.id.age, person.age);
        fill(page, R.id.height, person.height);
        fill(page, R.id.weight, person.weight);
        return page;
    }

    private View priorities(ViewGroup container) {
        View page = inflate(R.layout.page_options, container);
        Ui.text(page, R.id.title, "Какие мышцы в приоритете?");
        Ui.text(page, R.id.subtitle, "Можно выбрать несколько — подскажем упражнения для них");
        LinearLayout list = page.findViewById(R.id.options);
        LinearLayout row = null;
        float density = getResources().getDisplayMetrics().density;
        for (String group : Catalog.groups()) {
            if (group.equals(Catalog.ALL)) continue;
            if (row == null || row.getChildCount() == 3) {
                row = new LinearLayout(this);
                list.addView(row);
            }
            MaterialCardView card = (MaterialCardView) inflate(R.layout.item_group_icon, row);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            params.setMargins(Math.round(5 * density), Math.round(5 * density), Math.round(5 * density), Math.round(5 * density));
            card.setLayoutParams(params);
            MuscleMapView map = card.findViewById(R.id.map);
            map.getLayoutParams().height = Math.round(110 * density);
            map.setMuscles(Catalog.groupMuscles(group), Collections.emptyList());
            Ui.text(card, R.id.label, group);
            style(card, person.priorities.contains(group));
            card.setOnClickListener(v -> {
                if (!person.priorities.remove(group)) person.priorities.add(group);
                style(card, person.priorities.contains(group));
            });
            row.addView(card);
        }
        return page;
    }

    private View frequency(ViewGroup container) {
        View page = inflate(R.layout.page_frequency, container);
        Slider slider = page.findViewById(R.id.per_week);
        Runnable label = () -> {
            Ui.text(page, R.id.per_week_value, String.valueOf(person.perWeek));
            Ui.text(page, R.id.per_week_label, Coach.plural(person.perWeek, "тренировка", "тренировки", "тренировок") + " в неделю");
        };
        slider.setValue(person.perWeek);
        slider.addOnChangeListener((s, value, fromUser) -> {
            person.perWeek = (int) value;
            label.run();
        });
        label.run();
        return page;
    }

    private void show(int i) {
        hideKeyboard();
        pages.get(index).setVisibility(View.GONE);
        index = i;
        View page = pages.get(i);
        page.setVisibility(View.VISIBLE);
        page.setAlpha(0f);
        page.setTranslationX(40 * getResources().getDisplayMetrics().density);
        page.animate().alpha(1f).translationX(0).setDuration(220).start();
        ((ScrollView) findViewById(R.id.scroll)).scrollTo(0, 0);

        if (page == targetPage) Ui.text(page, R.id.current, Coach.kg(person.weight) + " кг");
        if (page == targetPage && person.target > 0) fill(page, R.id.target, person.target);

        boolean welcome = mode == NEW && i == 0;
        boolean last = i == pages.size() - 1;
        int first = mode == NEW ? 1 : 0;
        LinearProgressIndicator progress = findViewById(R.id.progress);
        progress.setMax(pages.size() - first);
        progress.setProgressCompat(Math.max(0, i - first + 1), true);
        progress.setVisibility(welcome ? View.INVISIBLE : View.VISIBLE);
        Ui.text(findViewById(android.R.id.content), R.id.step, welcome ? "" : (i - first + 1) + "/" + (pages.size() - first));
        findViewById(R.id.back).setVisibility(welcome || (i == 0 && mode == COMPLETE) ? View.INVISIBLE : View.VISIBLE);
        findViewById(R.id.login).setVisibility(welcome ? View.VISIBLE : View.GONE);
        ((MaterialButton) findViewById(R.id.next)).setText(welcome ? "Начать"
                : !last ? "Далее"
                : mode == NEW ? "Создать аккаунт" : "Сохранить");
    }

    private void back() {
        if (index > 0) show(index - 1);
        else if (mode == EDIT) finish();
        else if (mode == NEW) moveTaskToBack(true);
    }

    private void next() {
        View page = pages.get(index);
        if (page == bodyPage) {
            double age = number(page, R.id.age_box, 10, 100, true);
            double height = number(page, R.id.height_box, 100, 250, true);
            double weight = number(page, R.id.weight_box, 30, 300, true);
            if (Double.isNaN(age) || Double.isNaN(height) || Double.isNaN(weight)) return;
            person.age = (int) age;
            person.height = (int) height;
            person.weight = weight;
        }
        if (page == targetPage) {
            double target = number(page, R.id.target_box, 30, 300, false);
            if (Double.isNaN(target)) return;
            person.target = target;
        }
        if (index < pages.size() - 1) {
            show(index + 1);
            return;
        }
        finishFlow();
    }

    private void finishFlow() {
        person.name = ((EditText) bodyPage.findViewById(R.id.name)).getText().toString().trim();
        if (mode == NEW) {
            startActivity(new Intent(this, AuthActivity.class)
                    .putExtra("register", true)
                    .putExtra("person", person));
            return;
        }
        Db.get(this).saveProfile(person);
        if (mode == COMPLETE) startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void fill(View page, int id, double value) {
        if (value > 0) ((EditText) page.findViewById(id)).setText(Coach.kg(value).replace(',', '.'));
    }

    private double number(View page, int boxId, double min, double max, boolean required) {
        TextInputLayout box = page.findViewById(boxId);
        String text = box.getEditText().getText().toString().trim().replace(',', '.');
        box.setError(null);
        if (text.isEmpty() && !required) return 0;
        try {
            double v = Double.parseDouble(text);
            if (v >= min && v <= max) return v;
        } catch (NumberFormatException ignored) {
        }
        box.setError("Введите число от " + (int) min + " до " + (int) max);
        return Double.NaN;
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null) return;
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(), 0);
        focus.clearFocus();
    }
}
