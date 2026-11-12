package com.gym.tracker;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;

public class WeekCalendar extends LinearLayout {

    private static final String[] DAYS = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};

    private LocalDate selected = LocalDate.now();
    private boolean expanded;
    private Set<String> marked = Collections.emptySet();
    private Consumer<LocalDate> listener = d -> {
    };
    private TextView month;

    public WeekCalendar(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        render();
    }

    public LocalDate selected() {
        return selected;
    }

    public boolean expanded() {
        return expanded;
    }

    public void setListener(Consumer<LocalDate> listener) {
        this.listener = listener;
    }

    public void setMarked(Set<String> marked) {
        this.marked = marked;
        render();
    }

    public void select(LocalDate day) {
        selected = day;
        render();
        listener.accept(day);
    }

    public void shift(int direction) {
        select(expanded ? selected.plusMonths(direction) : selected.plusWeeks(direction));
    }

    public void toggle() {
        expanded = !expanded;
        render();
    }

    public void attach(View card, FragmentManager fragments) {
        month = card.findViewById(R.id.month);
        card.findViewById(R.id.prev).setOnClickListener(v -> shift(-1));
        card.findViewById(R.id.next).setOnClickListener(v -> shift(1));
        card.findViewById(R.id.pick_date).setOnClickListener(v -> {
            MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Выберите дату")
                    .setSelection(selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                    .build();
            picker.addOnPositiveButtonClickListener(ms -> select(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()));
            picker.show(fragments, "date");
        });
        MaterialButton expand = card.findViewById(R.id.expand);
        expand.setOnClickListener(v -> {
            toggle();
            expand.setIconResource(expanded ? R.drawable.ic_collapse : R.drawable.ic_expand);
        });
        render();
    }

    private void render() {
        removeAllViews();
        if (month != null) month.setText(Coach.monthTitle(selected));
        LinearLayout header = new LinearLayout(getContext());
        for (String name : DAYS) {
            TextView t = new TextView(getContext());
            t.setText(name);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(getContext().getColor(R.color.on_surface_variant));
            header.addView(t, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        }
        addView(header);

        LocalDate start = expanded
                ? selected.withDayOfMonth(1).with(DayOfWeek.MONDAY)
                : selected.with(DayOfWeek.MONDAY);
        LocalDate monthEnd = selected.withDayOfMonth(selected.lengthOfMonth());
        LayoutInflater inflater = LayoutInflater.from(getContext());
        LocalDate today = LocalDate.now();

        for (LocalDate week = start; expanded ? !week.isAfter(monthEnd) : week.equals(start); week = week.plusWeeks(1)) {
            LinearLayout row = new LinearLayout(getContext());
            for (int i = 0; i < 7; i++) {
                LocalDate day = week.plusDays(i);
                View cell = inflater.inflate(R.layout.item_day_cell, row, false);
                TextView number = cell.findViewById(R.id.number);
                number.setText(String.valueOf(day.getDayOfMonth()));

                if (day.equals(selected)) {
                    number.setBackgroundResource(R.drawable.bg_circle);
                    number.setBackgroundTintList(ColorStateList.valueOf(getContext().getColor(R.color.primary)));
                    number.setTextColor(getContext().getColor(R.color.on_primary));
                } else if (day.equals(today)) {
                    number.setBackgroundResource(R.drawable.bg_ring);
                    number.setTextColor(getContext().getColor(R.color.primary));
                } else {
                    number.setTextColor(getContext().getColor(R.color.on_surface));
                }
                if (expanded && day.getMonth() != selected.getMonth()) cell.setAlpha(0.35f);
                cell.findViewById(R.id.dot).setVisibility(marked.contains(day.toString()) ? VISIBLE : INVISIBLE);
                cell.setOnClickListener(v -> select(day));
                row.addView(cell);
            }
            addView(row);
        }
    }
}
