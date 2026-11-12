package com.gym.tracker;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Map;

final class Legacy {

    private Legacy() {
    }

    static void migrate(Context context, Db db) {
        SharedPreferences auth = context.getSharedPreferences("auth", Context.MODE_PRIVATE);
        Map<String, ?> all = auth.getAll();
        if (all.isEmpty()) return;
        String current = auth.getString("current", null);

        for (Map.Entry<String, ?> e : all.entrySet()) {
            if (!e.getKey().startsWith("user_")) continue;
            String login = e.getKey().substring(5);
            String[] parts = String.valueOf(e.getValue()).split(":");
            long id = db.createUser(login, parts[0], parts[1]);
            if (id == -1) continue;

            SharedPreferences p = context.getSharedPreferences("profile_" + login, Context.MODE_PRIVATE);
            if (p.getBoolean("onboarded", false)) {
                Coach.Person person = new Coach.Person();
                person.name = p.getString("name", "");
                person.sex = p.getString("sex", person.sex);
                person.age = p.getInt("age", 0);
                person.height = p.getInt("height", 0);
                person.target = p.getFloat("target", 0);
                person.goal = p.getString("goal", person.goal);
                person.experience = p.getString("experience", person.experience);
                person.perWeek = p.getInt("perWeek", person.perWeek);
                person.priorities = new LinkedHashSet<>(p.getStringSet("priorities", new LinkedHashSet<>()));
                db.saveProfile(id, person);
            }
            p.edit().clear().apply();

            File file = context.getDatabasePath("gym_" + login + ".db");
            if (file.exists()) {
                db.importLegacy(id, file);
                context.deleteDatabase(file.getName());
            }
            if (login.equals(current)) db.startSession(id);
        }
        auth.edit().clear().apply();
    }
}
