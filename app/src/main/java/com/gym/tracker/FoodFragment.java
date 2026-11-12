package com.gym.tracker;

import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.DoubleConsumer;

public class FoodFragment extends Fragment {

    private static final int[] PORTIONS = {50, 100, 150, 200};

    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private EditText scanInput;
    private final ActivityResultLauncher<ScanOptions> scanner = registerForActivityResult(new ScanContract(), result -> {
        if (result.getContents() != null && scanInput != null) scanInput.setText(result.getContents());
    });
    private Db db;
    private WeekCalendar calendar;

    public FoodFragment() {
        super(R.layout.fragment_food);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        db = Db.get(requireContext());
        Food.load(requireContext());
        calendar = view.findViewById(R.id.calendar);
        calendar.setListener(day -> load());
        calendar.attach(view, getChildFragmentManager());
        view.findViewById(R.id.add).setOnClickListener(v -> {
            if (calendar.selected().isAfter(LocalDate.now())) {
                Toast.makeText(requireContext(), "Нельзя записать еду на будущую дату", Toast.LENGTH_SHORT).show();
                return;
            }
            search();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        net.shutdownNow();
    }

    private void load() {
        View root = requireView();
        LocalDate day = calendar.selected();
        calendar.setMarked(db.foodDates());
        Coach.Person person = db.person();
        long norm = Coach.calories(person);
        long[] target = Coach.macros(person);
        double[] total = new double[4];

        LinearLayout list = root.findViewById(R.id.list);
        list.removeAllViews();
        try (Cursor c = db.foods(day.toString())) {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String name = c.getString(1);
                double grams = c.getDouble(2);
                double[] eaten = new double[4];
                for (int i = 0; i < 4; i++) {
                    eaten[i] = c.getDouble(3 + i);
                    total[i] += eaten[i];
                }
                View item = row(list, name, Coach.kg(grams) + " г · " + macros(eaten[1], eaten[2], eaten[3]),
                        eaten[0], "ккал", false);
                Runnable delete = () -> Ui.confirm(requireContext(), "Удалить «" + name + "»?", () -> {
                    db.deleteFood(id);
                    load();
                });
                Food.Product p = new Food.Product(name, "", eaten[0] / grams * 100, eaten[1] / grams * 100,
                        eaten[2] / grams * 100, eaten[3] / grams * 100, false);
                item.setOnClickListener(v -> portion(p, grams, "Сохранить", g -> {
                    db.updateFood(id, g);
                    load();
                }, delete));
                item.setOnLongClickListener(v -> {
                    delete.run();
                    return true;
                });
                list.addView(item);
            }
        }
        int count = list.getChildCount();
        root.findViewById(R.id.empty).setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        Ui.text(root, R.id.count, count == 0 ? "" : count + " " + Coach.plural(count, "продукт", "продукта", "продуктов"));

        Ui.text(root, R.id.day, Coach.dayTitle(day));

        long eaten = Math.round(total[0]);
        Ui.text(root, R.id.eaten, eaten + " ккал");
        Ui.text(root, R.id.norm, norm > 0 ? norm + " ккал" : "—");
        Ui.text(root, R.id.left, String.valueOf(norm > 0 ? Math.abs(norm - eaten) : eaten));
        Ui.text(root, R.id.left_label, norm == 0 ? "ккал" : eaten > norm ? "сверх нормы" : "осталось");
        root.findViewById(R.id.hint).setVisibility(norm == 0 ? View.VISIBLE : View.GONE);
        ((RingView) root.findViewById(R.id.ring)).setProgress(norm > 0 ? eaten / (float) norm : 0);

        macro(root, R.id.protein, "Белки", total[1], target[0], R.color.macro_protein);
        macro(root, R.id.fat, "Жиры", total[2], target[1], R.color.macro_fat);
        macro(root, R.id.carbs, "Углеводы", total[3], target[2], R.color.macro_carbs);
    }

    private void macro(View root, int id, String label, double eaten, long target, int color) {
        View view = root.findViewById(id);
        Ui.text(view, R.id.label, label);
        Ui.text(view, R.id.value, Math.round(eaten) + " г");
        Ui.text(view, R.id.target, target > 0 ? "из " + target + " г" : "");
        int percent = target > 0 ? (int) Math.min(100, Math.round(eaten * 100 / target)) : 0;
        LinearProgressIndicator bar = view.findViewById(R.id.bar);
        bar.setIndicatorColor(requireContext().getColor(color));
        bar.setProgressCompat(percent, true);
    }

    private View row(LinearLayout parent, String name, String details, double kcal, String unit, boolean belarus) {
        View item = getLayoutInflater().inflate(R.layout.item_food, parent, false);
        Ui.text(item, R.id.name, name);
        Ui.text(item, R.id.details, details);
        Ui.text(item, R.id.kcal, String.valueOf(Math.round(kcal)));
        Ui.text(item, R.id.unit, unit);
        item.findViewById(R.id.badge).setVisibility(belarus ? View.VISIBLE : View.GONE);
        return item;
    }

    private static String macros(double protein, double fat, double carbs) {
        return "Б " + Math.round(protein) + " · Ж " + Math.round(fat) + " · У " + Math.round(carbs);
    }

    private void search() {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.sheet_food, null);
        sheet.setContentView(view);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);

        EditText input = view.findViewById(R.id.search);
        LinearLayout list = view.findViewById(R.id.list);
        View loading = view.findViewById(R.id.loading);
        TextView status = view.findViewById(R.id.status);
        int[] request = {0};

        Runnable query = () -> {
            String q = input.getText().toString().trim();
            int id = ++request[0];
            boolean barcode = Food.isBarcode(q);
            loading.setVisibility(View.INVISIBLE);
            if (q.length() < 2) {
                List<Food.Product> recent = db.eatenFoods(20);
                status.setText(recent.isEmpty() ? "Введите название или отсканируйте штрихкод" : "Недавние");
                fill(list, recent, sheet);
                return;
            }
            List<Food.Product> found = new ArrayList<>();
            if (barcode) {
                Food.Product saved = db.foodByCode(q);
                if (saved != null) found.add(saved);
            } else {
                found.addAll(Food.local(db.eatenFoods(300), q));
            }
            fill(list, found, sheet);
            if (barcode && !found.isEmpty()) {
                status.setText("Найден по штрихкоду");
                return;
            }
            loading.setVisibility(View.VISIBLE);
            status.setText(found.isEmpty() ? "Ищем «" + q + "»…" : "Найдено: " + found.size() + " · ищем ещё…");
            net.execute(() -> {
                List<Food.Product> online;
                boolean failed = false;
                try {
                    online = Food.search(q);
                } catch (Exception e) {
                    online = Collections.emptyList();
                    failed = true;
                }
                List<Food.Product> more = online;
                String empty = barcode ? "Штрихкод не найден. Нажмите «Свой продукт» и введите данные с упаковки — в следующий раз он найдётся сам"
                        : failed ? "Ничего не найдено. Проверьте интернет или добавьте свой продукт"
                        : "Ничего не найдено. Добавьте свой продукт";
                view.post(() -> {
                    if (id != request[0] || !isAdded()) return;
                    loading.setVisibility(View.INVISIBLE);
                    Food.merge(found, more);
                    status.setText(found.isEmpty() ? empty : "Найдено: " + found.size());
                    fill(list, found, sheet);
                });
            });
        };

        watch(input, () -> {
            view.removeCallbacks(query);
            view.postDelayed(query, 350);
        });
        ((TextInputLayout) view.findViewById(R.id.search_box)).setEndIconOnClickListener(v -> {
            scanInput = input;
            scanner.launch(new ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.PRODUCT_CODE_TYPES)
                    .setPrompt("Наведите камеру на штрихкод")
                    .setBeepEnabled(false)
                    .setOrientationLocked(true));
        });
        view.findViewById(R.id.custom).setOnClickListener(v -> custom(input.getText().toString().trim(), sheet));
        sheet.setOnDismissListener(d -> {
            view.removeCallbacks(query);
            request[0]++;
            scanInput = null;
        });

        query.run();
        sheet.show();
    }

    private void fill(LinearLayout list, List<Food.Product> products, BottomSheetDialog sheet) {
        list.removeAllViews();
        for (Food.Product p : products) {
            View item = row(list, p.title(), macros(p.protein, p.fat, p.carbs), p.kcal, "ккал / 100 г", p.belarus);
            item.setOnClickListener(v -> add(p, sheet));
            list.addView(item);
        }
    }

    private void add(Food.Product p, BottomSheetDialog sheet) {
        portion(p, 100, "Добавить", g -> {
            long id = db.addFood(calendar.selected().toString(), p, g);
            sheet.dismiss();
            load();
            Snackbar.make(requireView(), "Добавлено: " + Math.round(p.kcal * g / 100) + " ккал", Snackbar.LENGTH_LONG)
                    .setAnchorView(R.id.add)
                    .setAction("Отменить", v -> {
                        db.deleteFood(id);
                        load();
                    })
                    .show();
        }, null);
    }

    private void custom(String query, BottomSheetDialog sheet) {
        View form = getLayoutInflater().inflate(R.layout.dialog_product, null);
        EditText name = form.findViewById(R.id.name);
        boolean barcode = Food.isBarcode(query);
        if (!barcode) name.setText(query);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Свой продукт")
                .setView(form)
                .setPositiveButton("Далее", null)
                .setNegativeButton("Отмена", null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = name.getText().toString().trim();
            double protein = number(form.findViewById(R.id.protein));
            double fat = number(form.findViewById(R.id.fat));
            double carbs = number(form.findViewById(R.id.carbs));
            double kcal = number(form.findViewById(R.id.kcal));
            if (kcal == 0) kcal = protein * 4 + fat * 9 + carbs * 4;
            if (title.isEmpty() || kcal <= 0 || kcal > 950 || protein + fat + carbs > 100) {
                Toast.makeText(requireContext(), "Введите название и калории или БЖУ на 100 г", Toast.LENGTH_SHORT).show();
                return;
            }
            Food.Product p = new Food.Product(title, "", kcal, protein, fat, carbs, false);
            if (barcode) p.code = query;
            dialog.dismiss();
            add(p, sheet);
        });
        name.requestFocus();
    }

    private void portion(Food.Product p, double grams, String ok, DoubleConsumer save, Runnable delete) {
        View form = getLayoutInflater().inflate(R.layout.dialog_portion, null);
        EditText input = form.findViewById(R.id.input);
        ChipGroup portions = form.findViewById(R.id.portions);
        for (int g : PORTIONS) {
            Chip chip = new Chip(requireContext());
            chip.setText(g + " г");
            chip.setOnClickListener(v -> {
                input.setText(String.valueOf(g));
                input.setSelection(input.length());
            });
            portions.addView(chip);
        }

        watch(input, () -> {
            double g = number(input);
            Ui.text(form, R.id.total, Math.round(p.kcal * g / 100) + " ккал");
            Ui.text(form, R.id.details, macros(p.protein * g / 100, p.fat * g / 100, p.carbs * g / 100)
                    + " · в 100 г " + Math.round(p.kcal) + " ккал");
        });
        input.setText(Coach.kg(grams).replace(',', '.'));
        input.selectAll();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(p.title())
                .setView(form)
                .setPositiveButton(ok, (d, which) -> {
                    double g = number(input);
                    if (g <= 0 || g > 5000) {
                        Toast.makeText(requireContext(), "Введите вес порции от 1 до 5000 г", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    save.accept(g);
                })
                .setNegativeButton("Отмена", null);
        if (delete != null) builder.setNeutralButton("Удалить", (d, which) -> delete.run());
        builder.show();
        input.requestFocus();
    }

    private static void watch(EditText input, Runnable changed) {
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                changed.run();
            }
        });
    }

    private static double number(EditText input) {
        try {
            return Double.parseDouble(input.getText().toString().replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
