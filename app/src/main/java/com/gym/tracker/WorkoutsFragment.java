package com.gym.tracker;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class WorkoutsFragment extends Fragment {

    private static final String FAVORITES = "Избранные";

    private Db db;
    private boolean favoritesOnly;
    private WeekCalendar calendar;

    public WorkoutsFragment() {
        super(R.layout.fragment_workouts);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        db = Db.get(requireContext());
        calendar = view.findViewById(R.id.calendar);
        calendar.setListener(day -> load());
        calendar.attach(view, getChildFragmentManager());

        view.findViewById(R.id.add).setOnClickListener(v -> catalog());

        ChipGroup filter = view.findViewById(R.id.filter);
        filter.setOnCheckedStateChangeListener((group, ids) -> {
            favoritesOnly = ids.contains(R.id.filter_favorites);
            load();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        View root = requireView();
        LocalDate day = calendar.selected();
        String date = day.toString();
        calendar.setMarked(db.workoutDates());
        Ui.text(root, R.id.day_title, Coach.dayTitle(day));
        Ui.text(root, R.id.filter_favorites, "★ Избранные · " + weekday(day.getDayOfWeek()));

        Map<Long, List<double[]>> daySets = db.daySets(date);
        LinearLayout list = root.findViewById(R.id.list);
        list.removeAllViews();
        try (Cursor c = db.exerciseRows(favoritesOnly, date)) {
            while (c.moveToNext()) list.addView(row(list, c, daySets.get(c.getLong(0))));
        }

        boolean noExercises = db.exerciseNames().isEmpty();
        boolean empty = list.getChildCount() == 0;
        root.findViewById(R.id.filter).setVisibility(noExercises ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.table).setVisibility(empty ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.hint).setVisibility(empty ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.empty).setVisibility(noExercises ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.no_favorites).setVisibility(empty && !noExercises ? View.VISIBLE : View.GONE);
        Ui.text(root, R.id.no_favorites, favoritesOnly
                ? "На " + weekdayAcc(day.getDayOfWeek()) + " избранного пока нет.\nНажмите «Упражнение» и отметьте ☆ то, что делаете в этот день."
                : day.isAfter(LocalDate.now()) ? "Этот день ещё не наступил."
                : day.equals(LocalDate.now()) ? "Сегодня подходов ещё нет.\nВыберите упражнение во вкладке «★ Избранные» или нажмите «Упражнение»."
                : "В этот день тренировки не было.\nЧтобы записать подход задним числом, нажмите «Упражнение».");

        int perWeek = db.person().perWeek;
        Ui.text(root, R.id.week, "Тренировок на этой неделе: " + db.workoutsThisWeek() + " из " + perWeek);
        try (Cursor c = db.dayTotals(date)) {
            c.moveToFirst();
            long sets = c.getLong(0);
            Ui.stat(root, R.id.stat_today_sets, String.valueOf(sets),
                    Coach.plural(sets, "подход", "подхода", "подходов") + " за день");
            Ui.stat(root, R.id.stat_today_volume, Coach.kg(c.getDouble(1)), "кг поднято за день");
        }
    }

    private View row(LinearLayout list, Cursor c, List<double[]> daySets) {
        long id = c.getLong(0);
        String name = c.getString(1);
        boolean hasSets = !c.isNull(4);
        double lastWeight = c.getDouble(4);
        int lastReps = c.getInt(5);
        boolean favorite = c.getInt(8) == 1;

        View row = getLayoutInflater().inflate(R.layout.item_exercise_row, list, false);
        Ui.text(row, R.id.name, name);
        ImageView star = row.findViewById(R.id.star);
        star.setImageResource(favorite ? R.drawable.ic_star : R.drawable.ic_star_outline);
        star.setImageTintList(ColorStateList.valueOf(requireContext().getColor(
                favorite ? R.color.primary : R.color.on_surface_variant)));
        star.setContentDescription(favorite ? "Убрать из избранного" : "Добавить в избранное");
        star.setOnClickListener(v -> {
            db.setFavorite(id, calendar.selected().getDayOfWeek(), !favorite);
            load();
        });

        TextView info = row.findViewById(R.id.info);
        if (daySets != null) {
            List<String> sets = new ArrayList<>();
            for (double[] s : daySets) sets.add(Coach.shortSet(s[0], (int) s[1]));
            info.setText(String.join(", ", sets));
            info.setTextColor(requireContext().getColor(R.color.primary));
        } else {
            info.setText(hasSets ? Coach.day(c.getString(7)).toLowerCase() : "нет подходов");
        }
        boolean otherDay = daySets == null;
        if (otherDay) info.setText(calendar.selected().equals(LocalDate.now()) ? "сегодня ещё не делали" : "в этот день не делали");
        Ui.text(row, R.id.last, hasSets && !otherDay ? Coach.shortSet(lastWeight, lastReps) : "—");
        Ui.text(row, R.id.record, c.isNull(2) || otherDay ? "—"
                : c.getDouble(2) > 0 ? Coach.kg(c.getDouble(2)) + " кг"
                : c.getInt(3) + " повт.");

        double[] last = hasSets ? new double[]{lastWeight, lastReps} : null;
        row.setOnClickListener(v -> sheet(id, name, last));
        row.findViewById(R.id.repeat).setOnClickListener(v -> {
            if (hasSets) log(id, lastWeight, lastReps);
            else sheet(id, name, null);
        });
        row.setOnLongClickListener(v -> {
            Ui.confirm(requireContext(), "Удалить «" + name + "» вместе со всей историей?", () -> {
                db.deleteExercise(id);
                load();
            });
            return true;
        });
        return row;
    }

    private static String weekday(DayOfWeek day) {
        return day.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.forLanguageTag("ru"));
    }

    private static String weekdayAcc(DayOfWeek day) {
        return new String[]{"понедельник", "вторник", "среду", "четверг", "пятницу", "субботу", "воскресенье"}[day.getValue() - 1];
    }

    private void sheet(long id, String name, double[] last) {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.sheet_set, null);
        sheet.setContentView(view);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);

        Ui.picture(view, name, true);
        view.findViewById(R.id.picture).setVisibility(Catalog.contains(name) ? View.VISIBLE : View.GONE);
        Ui.text(view, R.id.title, name);
        Ui.text(view, R.id.subtitle, (last == null
                ? "Первый подход в этом упражнении"
                : "В прошлый раз: " + Coach.set(last[0], (int) last[1]))
                + (calendar.selected().equals(LocalDate.now()) ? "" : "\nЗапись на " + Coach.day(calendar.selected().toString()).toLowerCase()));
        SetForm form = new SetForm(view, last);
        view.findViewById(R.id.save).setOnClickListener(v -> {
            double[] set = form.read();
            if (set == null) return;
            sheet.dismiss();
            log(id, set[0], (int) set[1]);
        });
        view.findViewById(R.id.details).setOnClickListener(v -> {
            sheet.dismiss();
            startActivity(new Intent(requireContext(), ExerciseActivity.class)
                    .putExtra("id", id)
                    .putExtra("name", name));
        });
        sheet.show();
    }

    private void log(long exerciseId, double weight, int reps) {
        LocalDate day = calendar.selected();
        if (day.isAfter(LocalDate.now())) {
            Toast.makeText(requireContext(), "Нельзя записать подход на будущую дату", Toast.LENGTH_SHORT).show();
            return;
        }
        long entry = db.addEntry(exerciseId, weight, reps, day.toString());
        load();
        Snackbar.make(requireView(), "Записано: " + Coach.set(weight, reps), Snackbar.LENGTH_LONG)
                .setAnchorView(R.id.add)
                .setAction("Отменить", v -> {
                    db.deleteEntry(entry);
                    load();
                })
                .show();
    }

    private void catalog() {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.sheet_catalog, null);
        sheet.setContentView(view);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);

        EditText search = view.findViewById(R.id.search);
        LinearLayout groups = view.findViewById(R.id.groups);
        LinearLayout list = view.findViewById(R.id.list);
        MaterialButton custom = view.findViewById(R.id.custom);
        Set<String> added = db.exerciseNames();
        DayOfWeek weekday = calendar.selected().getDayOfWeek();
        Set<String> favorites = db.favoriteNames(weekday);
        String[] group = {Catalog.ALL};
        Runnable[] self = new Runnable[1];

        Runnable refresh = () -> {
            added.clear();
            added.addAll(db.exerciseNames());
            favorites.clear();
            favorites.addAll(db.favoriteNames(weekday));
            for (int i = 0; i < groups.getChildCount(); i++) {
                MaterialCardView card = (MaterialCardView) groups.getChildAt(i);
                boolean on = card.getTag().equals(group[0]);
                card.setCardBackgroundColor(requireContext().getColor(on ? R.color.primary_container : R.color.surface_highest));
            }

            String query = search.getText().toString().trim();
            list.removeAllViews();
            List<String[]> found = new ArrayList<>();
            List<String[]> rest = new ArrayList<>();
            for (String[] exercise : Catalog.find(FAVORITES.equals(group[0]) ? Catalog.ALL : group[0], query)) {
                if (favorites.contains(exercise[0])) found.add(exercise);
                else if (!FAVORITES.equals(group[0])) rest.add(exercise);
            }
            if (Catalog.ALL.equals(group[0]) || FAVORITES.equals(group[0])) {
                for (String name : favorites) {
                    if (!Catalog.contains(name) && name.toLowerCase().contains(query.toLowerCase())) {
                        found.add(0, new String[]{name, "Своё упражнение"});
                    }
                }
            }
            found.addAll(rest);
            Ui.text(view, R.id.favorites_empty, query.isEmpty()
                    ? "На " + weekdayAcc(weekday) + " избранного пока нет. Нажмите ☆ на карточке упражнения, чтобы добавить его."
                    : "Среди избранных ничего не найдено.");
            view.findViewById(R.id.favorites_empty).setVisibility(FAVORITES.equals(group[0]) && found.isEmpty() ? View.VISIBLE : View.GONE);
            LinearLayout pair = null;
            for (String[] exercise : found) {
                if (pair == null || pair.getChildCount() == 2) {
                    pair = new LinearLayout(requireContext());
                    list.addView(pair);
                }
                String name = exercise[0];
                boolean exists = added.contains(name);
                View item = getLayoutInflater().inflate(R.layout.item_catalog, pair, false);
                Ui.picture(item, name, false);
                Ui.text(item, R.id.name, name);
                Ui.text(item, R.id.group, exists ? "уже добавлено" : exercise[1]);
                boolean favorite = favorites.contains(name);
                ImageView star = item.findViewById(R.id.star);
                star.setImageResource(favorite ? R.drawable.ic_star : R.drawable.ic_star_outline);
                star.setImageTintList(ColorStateList.valueOf(requireContext().getColor(
                        favorite ? R.color.primary : R.color.on_surface_variant)));
                star.setContentDescription(favorite ? "Убрать из избранного" : "Добавить в избранное");
                star.setOnClickListener(v -> {
                    db.toggleFavorite(name, weekday);
                    self[0].run();
                    load();
                });
                item.setOnClickListener(v -> preview(name, exists, sheet, self[0]));
                pair.addView(item);
            }
            if (pair != null && pair.getChildCount() == 1) {
                View spacer = new View(requireContext());
                pair.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1));
            }

            boolean showCustom = !query.isEmpty() && !Catalog.contains(query);
            custom.setVisibility(showCustom ? View.VISIBLE : View.GONE);
            custom.setText("Добавить своё: «" + query + "»");
        };

        self[0] = refresh;
        List<String> groupNames = new ArrayList<>(Catalog.groups());
        groupNames.add(1, FAVORITES);
        String favoritesLabel = FAVORITES + " · " + weekday(weekday);
        for (String name : groupNames) {
            View card = getLayoutInflater().inflate(R.layout.item_group_icon, groups, false);
            card.setTag(name);
            boolean star = FAVORITES.equals(name);
            card.findViewById(R.id.map).setVisibility(star ? View.GONE : View.VISIBLE);
            card.findViewById(R.id.icon).setVisibility(star ? View.VISIBLE : View.GONE);
            if (!star) ((MuscleMapView) card.findViewById(R.id.map)).setMuscles(Catalog.groupMuscles(name), Collections.emptyList());
            Ui.text(card, R.id.label, star ? favoritesLabel : name);
            card.setOnClickListener(v -> {
                group[0] = name;
                refresh.run();
            });
            groups.addView(card);
        }

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refresh.run();
            }
        });
        custom.setOnClickListener(v -> {
            sheet.dismiss();
            add(search.getText().toString().trim());
        });

        refresh.run();
        sheet.show();
    }

    private void preview(String name, boolean exists, BottomSheetDialog catalog, Runnable changed) {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.sheet_preview, null);
        sheet.setContentView(view);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);

        Ui.text(view, R.id.title, name);
        Ui.text(view, R.id.subtitle, Catalog.contains(name) ? Catalog.group(name) : "Своё упражнение");
        MaterialButton star = view.findViewById(R.id.favorite);
        DayOfWeek weekday = calendar.selected().getDayOfWeek();
        Runnable paint = () -> {
            boolean on = db.favoriteNames(weekday).contains(name);
            star.setIconResource(on ? R.drawable.ic_star : R.drawable.ic_star_outline);
            star.setText((on ? "В избранном на " : "В избранное на ") + weekdayAcc(weekday));
        };
        paint.run();
        star.setOnClickListener(v -> {
            db.toggleFavorite(name, weekday);
            paint.run();
            changed.run();
            load();
        });
        ImageView drawing = view.findViewById(R.id.drawing);
        if (!Ui.drawing(drawing, name, true)) ((View) drawing.getParent()).setVisibility(View.GONE);
        ((MuscleMapView) view.findViewById(R.id.muscles)).setExercise(name);

        MaterialButton add = view.findViewById(R.id.add);
        add.setText(exists ? "Записать подход" : "Добавить упражнение");
        add.setOnClickListener(v -> {
            sheet.dismiss();
            catalog.dismiss();
            if (db.exerciseId(name) != -1) {
                long id = db.exerciseId(name);
                sheet(id, name, db.lastSet(id));
                return;
            }
            add(name);
        });
        sheet.show();
    }

    private void add(String name) {
        long id = db.addExercise(name);
        if (id == -1) {
            Toast.makeText(requireContext(), "Такое упражнение уже есть", Toast.LENGTH_SHORT).show();
            return;
        }
        load();
        sheet(id, name, null);
    }
}
