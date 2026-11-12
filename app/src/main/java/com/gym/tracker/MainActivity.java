package com.gym.tracker;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class MainActivity extends AppCompatActivity {

    private static final String[] LABELS = {"Тренировки", "Питание", "Прогресс", "Советы", "Профиль"};
    private static final int[] ICONS = {R.drawable.ic_workouts, R.drawable.ic_restaurant,
            R.drawable.ic_progress, R.drawable.ic_tips, R.drawable.ic_profile};
    private static final List<Supplier<Fragment>> PAGES = Arrays.asList(
            WorkoutsFragment::new, FoodFragment::new, ProgressFragment::new, TipsFragment::new, ProfileFragment::new);

    private LinearLayout nav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Auth.user(this) == null) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }
        if (!Db.get(this).hasProfile()) {
            startActivity(new Intent(this, OnboardingActivity.class)
                    .putExtra(OnboardingActivity.MODE, OnboardingActivity.COMPLETE));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);

        ViewPager2 pager = findViewById(R.id.pager);
        pager.setOffscreenPageLimit(PAGES.size() - 1);
        pager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return PAGES.get(position).get();
            }

            @Override
            public int getItemCount() {
                return PAGES.size();
            }
        });

        nav = findViewById(R.id.nav);
        for (int i = 0; i < LABELS.length; i++) {
            int index = i;
            LinearLayout item = (LinearLayout) getLayoutInflater().inflate(R.layout.item_nav, nav, false);
            ((ImageView) item.findViewById(R.id.bubble)).setImageResource(ICONS[i]);
            ((TextView) item.findViewById(R.id.label)).setText(LABELS[i]);
            item.setContentDescription(LABELS[i]);
            item.setOnClickListener(v -> pager.setCurrentItem(index, false));
            nav.addView(item);
        }

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                highlight(position);
            }
        });
        highlight(pager.getCurrentItem());
    }

    private void highlight(int index) {
        for (int i = 0; i < nav.getChildCount(); i++) {
            boolean on = i == index;
            LinearLayout item = (LinearLayout) nav.getChildAt(i);
            ImageView bubble = item.findViewById(R.id.bubble);
            TextView label = item.findViewById(R.id.label);
            bubble.setBackgroundTintList(ColorStateList.valueOf(getColor(on ? R.color.primary : R.color.surface_highest)));
            bubble.setImageTintList(ColorStateList.valueOf(getColor(on ? R.color.on_primary : R.color.on_surface_variant)));
            bubble.animate().scaleX(on ? 1f : 0.82f).scaleY(on ? 1f : 0.82f).setDuration(150).start();
            label.setTextColor(getColor(on ? R.color.primary : R.color.on_surface_variant));
            label.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
    }
}
