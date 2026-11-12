package com.gym.tracker;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;

public class TipsFragment extends Fragment {

    public TipsFragment() {
        super(R.layout.fragment_tips);
    }

    @Override
    public void onResume() {
        super.onResume();
        View root = requireView();
        Db db = Db.get(requireContext());
        Coach.Person person = db.person();
        int thisWeek = db.workoutsThisWeek();

        Ui.text(root, R.id.greeting, "Привет, " + (person.name.isEmpty() ? db.login() : person.name) + "!");
        Ui.text(root, R.id.ring_value, thisWeek + "/" + person.perWeek);
        ((RingView) root.findViewById(R.id.ring)).setProgress(thisWeek / (float) Math.max(1, person.perWeek));

        long kcal = Coach.calories(person);
        metric(root, R.id.metric_kcal, R.drawable.ic_restaurant, kcal > 0 ? String.valueOf(kcal) : "—", "ккал");
        metric(root, R.id.metric_protein, R.drawable.ic_protein, Coach.protein(person), "белка");
        metric(root, R.id.metric_water, R.drawable.ic_water, Coach.water(person), "воды");

        LinearLayout status = root.findViewById(R.id.status);
        LinearLayout exercises = root.findViewById(R.id.exercises);
        LinearLayout other = root.findViewById(R.id.other);
        LinearLayout food = root.findViewById(R.id.food);
        food.removeAllViews();
        status.removeAllViews();
        exercises.removeAllViews();
        other.removeAllViews();

        for (Coach.Tip tip : Coach.tips(person, thisWeek, db.daysSinceLastWorkout(),
                db.topSetsByExercise(), db.exerciseNames(), db.foodWeek())) {
            if (tip.section == Coach.WEEK) {
                Ui.text(root, R.id.week_title, tip.title);
                Ui.text(root, R.id.week_text, tip.text);
                continue;
            }
            LinearLayout list = tip.section == Coach.STATUS ? status
                    : tip.section == Coach.EXERCISE ? exercises
                    : tip.section == Coach.FOOD ? food : other;
            list.addView(card(list, tip));
        }

        int shown = exercises.getChildCount() == 0 ? View.GONE : View.VISIBLE;
        root.findViewById(R.id.exercises_title).setVisibility(shown);
        root.findViewById(R.id.exercises_rule).setVisibility(shown);
        Ui.text(root, R.id.exercises_rule, "Правило: " + Coach.RAISE_AT + " и больше повторений в лучшем подходе — повышайте вес на 2,5 кг, меньше "
                + Coach.minReps(person.goal) + " — снижайте");
        root.findViewById(R.id.other_title).setVisibility(other.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private View card(LinearLayout list, Coach.Tip tip) {
        MaterialCardView card = (MaterialCardView) getLayoutInflater().inflate(R.layout.item_tip, list, false);
        int icon = getResources().getIdentifier("ic_" + tip.icon, "drawable", requireContext().getPackageName());
        if (icon == 0) icon = R.drawable.ic_tips;

        ShapeableImageView drawing = card.findViewById(R.id.drawing);
        boolean photo = tip.exercise != null && Ui.drawing(drawing, tip.exercise, false);
        ImageView iconView = card.findViewById(R.id.icon);
        ImageView badge = card.findViewById(R.id.badge);
        iconView.setImageResource(icon);
        iconView.setVisibility(photo ? View.GONE : View.VISIBLE);
        badge.setImageResource(icon);
        badge.setVisibility(photo ? View.VISIBLE : View.GONE);

        Ui.text(card, R.id.title, tip.title);
        Ui.text(card, R.id.text, tip.text);
        TextView trend = card.findViewById(R.id.trend);
        trend.setVisibility(tip.status == null ? View.GONE : View.VISIBLE);
        trend.setText(tip.status);
        trend.setTextColor(requireContext().getColor(tip.trend == Coach.DOWN ? R.color.primary
                : tip.trend == Coach.FLAT ? R.color.trend_flat : R.color.trend_up));
        TextView next = card.findViewById(R.id.next);
        next.setVisibility(tip.next == null ? View.GONE : View.VISIBLE);
        next.setText("Дальше: " + tip.next);
        if (tip.section == Coach.STATUS) {
            card.setStrokeColor(requireContext().getColor(R.color.primary));
            card.setStrokeWidth(Math.round(2 * getResources().getDisplayMetrics().density));
        }
        return card;
    }

    private void metric(View root, int id, int icon, String value, String label) {
        View card = root.findViewById(id);
        ((ImageView) card.findViewById(R.id.icon)).setImageResource(icon);
        Ui.text(card, R.id.value, value);
        Ui.text(card, R.id.label, label);
    }
}
