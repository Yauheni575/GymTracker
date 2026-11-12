package com.gym.tracker;

import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

public class ExerciseActivity extends AppCompatActivity {

    private Db db;
    private long exerciseId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exercise);
        db = Db.get(this);
        exerciseId = getIntent().getLongExtra("id", -1);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(getIntent().getStringExtra("name"));
        toolbar.setNavigationOnClickListener(v -> finish());

        String name = getIntent().getStringExtra("name");
        Ui.drawing(findViewById(R.id.drawing), name, true);
        ((MuscleMapView) findViewById(R.id.map)).setExercise(name);
        findViewById(R.id.map_card).setVisibility(Catalog.contains(name) ? View.VISIBLE : View.GONE);

        SetForm form = new SetForm(findViewById(android.R.id.content), db.lastSet(exerciseId));
        findViewById(R.id.add).setOnClickListener(v -> {
            double[] set = form.read();
            if (set == null) return;
            db.addEntry(exerciseId, set[0], (int) set[1]);
            load();
        });

        load();
    }

    private void load() {
        View root = findViewById(android.R.id.content);
        LinearLayout history = findViewById(R.id.history);
        history.removeAllViews();

        double bestWeight = -1;
        int bestReps = 0;
        double oneRepMax = 0;
        LinearLayout day = null;
        String lastDate = null;
        int n = 0;

        try (Cursor c = db.entries(exerciseId)) {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String date = c.getString(1);
                double w = c.getDouble(2);
                int r = c.getInt(3);

                if (!date.equals(lastDate)) {
                    View card = getLayoutInflater().inflate(R.layout.item_day, history, false);
                    Ui.text(card, R.id.date, Coach.day(date));
                    day = card.findViewById(R.id.sets);
                    history.addView(card);
                    lastDate = date;
                    n = 0;
                }

                n++;
                TextView row = (TextView) getLayoutInflater().inflate(R.layout.item_set, day, false);
                row.setText(n + ".   " + Coach.set(w, r));
                row.setOnLongClickListener(v -> {
                    Ui.confirm(this, "Удалить этот подход?", () -> {
                        db.deleteEntry(id);
                        load();
                    });
                    return true;
                });
                day.addView(row);

                if (w > bestWeight || (w == bestWeight && r > bestReps)) {
                    bestWeight = w;
                    bestReps = r;
                }
                oneRepMax = Math.max(oneRepMax, Coach.oneRepMax(w, r));
            }
        }

        boolean empty = bestWeight < 0;
        boolean ownWeight = bestWeight == 0;
        findViewById(R.id.empty).setVisibility(empty ? View.VISIBLE : View.GONE);
        if (ownWeight) {
            Ui.stat(root, R.id.stat_record, String.valueOf(bestReps), "рекорд повторений");
            Ui.stat(root, R.id.stat_max, "—", "свой вес");
        } else {
            Ui.stat(root, R.id.stat_record, empty ? "—" : Coach.kg(bestWeight) + " × " + bestReps, "рекорд, кг × повт.");
            Ui.stat(root, R.id.stat_max, empty ? "—" : "≈ " + Coach.kg(Math.round(oneRepMax)) + " кг", "максимум на 1 раз");
        }
        ((ChartView) findViewById(R.id.chart))
                .setPoints(db.progressByDate(exerciseId), "График появится после 2 тренировок");
    }
}
