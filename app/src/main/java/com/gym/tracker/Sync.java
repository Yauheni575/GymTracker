package com.gym.tracker;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDateTime;

final class Sync {

    private static final String NEW_UUID = "lower(hex(randomblob(16)))";
    private static final String QUIET = "(SELECT applying FROM sync_flag) = 0";

    private Sync() {
    }

    interface Row {
        JSONObject read(Cursor c) throws JSONException;
    }

    static void createSchema(SQLiteDatabase db) {
        for (String t : new String[]{"exercise", "entry", "food"}) {
            db.execSQL("ALTER TABLE " + t + " ADD COLUMN uuid TEXT");
            db.execSQL("UPDATE " + t + " SET uuid = " + NEW_UUID);
            db.execSQL("CREATE UNIQUE INDEX " + t + "_uuid ON " + t + " (uuid)");
            db.execSQL("CREATE TRIGGER " + t + "_new_uuid AFTER INSERT ON " + t + " WHEN NEW.uuid IS NULL BEGIN "
                    + "UPDATE " + t + " SET uuid = " + NEW_UUID + " WHERE id = NEW.id; END");
        }
        for (String t : new String[]{"exercise", "entry", "food", "body_weight", "profile"}) {
            db.execSQL("ALTER TABLE " + t + " ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1");
        }
        db.execSQL("CREATE TABLE sync_flag (id INTEGER PRIMARY KEY CHECK (id = 1), applying INTEGER NOT NULL)");
        db.execSQL("INSERT INTO sync_flag VALUES (1, 0)");
        db.execSQL("CREATE TABLE tombstone (user_id INTEGER, kind TEXT NOT NULL, uuid TEXT NOT NULL, "
                + "PRIMARY KEY (kind, uuid))");
        db.execSQL("CREATE TABLE cloud (user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, "
                + "login TEXT NOT NULL, token TEXT NOT NULL, cursor INTEGER NOT NULL DEFAULT 0, synced TEXT)");
        dirtyOnUpdate(db, "exercise", "name, favorite");
        dirtyOnUpdate(db, "entry", "exercise_id, date, weight, reps");
        dirtyOnUpdate(db, "food", "date, name, grams, kcal, protein, fat, carbs, code");
        tombstoneOnDelete(db, "exercise", "OLD.user_id");
        tombstoneOnDelete(db, "entry", "(SELECT user_id FROM exercise WHERE id = OLD.exercise_id)");
        tombstoneOnDelete(db, "food", "OLD.user_id");
    }

    private static void dirtyOnUpdate(SQLiteDatabase db, String table, String columns) {
        db.execSQL("CREATE TRIGGER " + table + "_dirty AFTER UPDATE OF " + columns + " ON " + table
                + " WHEN " + QUIET + " BEGIN UPDATE " + table + " SET dirty = 1 WHERE id = NEW.id; END");
    }

    private static void tombstoneOnDelete(SQLiteDatabase db, String table, String user) {
        db.execSQL("CREATE TRIGGER " + table + "_tombstone AFTER DELETE ON " + table + " WHEN " + QUIET
                + " BEGIN INSERT OR REPLACE INTO tombstone VALUES (" + user + ", '" + table + "', OLD.uuid); END");
    }

    static String[] account(Db db) {
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT login, token, cursor, synced FROM cloud WHERE user_id = ?", new String[]{uid(db)})) {
            return c.moveToFirst() ? new String[]{c.getString(0), c.getString(1), c.getString(2), c.getString(3)} : null;
        }
    }

    static void link(Db db, String login, String token) {
        SQLiteDatabase w = db.getWritableDatabase();
        String[] user = {uid(db)};
        w.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("user_id", db.userId());
            v.put("login", login);
            v.put("token", token);
            v.put("cursor", 0);
            w.insertWithOnConflict("cloud", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            w.execSQL("UPDATE exercise SET dirty = 1 WHERE user_id = ?", user);
            w.execSQL("UPDATE entry SET dirty = 1 WHERE exercise_id IN (SELECT id FROM exercise WHERE user_id = ?)", user);
            w.execSQL("UPDATE food SET dirty = 1 WHERE user_id = ?", user);
            w.execSQL("UPDATE body_weight SET dirty = 1 WHERE user_id = ?", user);
            w.execSQL("UPDATE profile SET dirty = 1 WHERE user_id = ?", user);
            w.setTransactionSuccessful();
        } finally {
            w.endTransaction();
        }
    }

    static void unlink(Db db) {
        db.getWritableDatabase().delete("cloud", "user_id = ?", new String[]{uid(db)});
    }

    static void run(Db db) throws IOException, JSONException {
        String[] account = account(db);
        if (account == null) return;
        JSONObject sent = changes(db, Long.parseLong(account[2]));
        JSONObject got = Cloud.sync(account[1], sent);
        apply(db, sent, got);
    }

    static JSONObject changes(Db db, long cursor) throws JSONException {
        SQLiteDatabase r = db.getReadableDatabase();
        String u = uid(db);
        JSONArray exercises = rows(r, "SELECT uuid, name, favorite FROM exercise WHERE user_id = ? AND dirty = 1", u,
                c -> new JSONObject().put("uuid", c.getString(0)).put("name", c.getString(1))
                        .put("favorite", c.getInt(2)).put("deleted", false));
        JSONArray entries = rows(r, "SELECT en.uuid, e.uuid, en.date, en.weight, en.reps FROM entry en "
                        + "JOIN exercise e ON e.id = en.exercise_id WHERE e.user_id = ? AND en.dirty = 1", u,
                c -> new JSONObject().put("uuid", c.getString(0)).put("exerciseUuid", c.getString(1))
                        .put("date", c.getString(2)).put("weight", c.getDouble(3)).put("reps", c.getInt(4))
                        .put("deleted", false));
        JSONArray bodyWeights = rows(r, "SELECT date, weight FROM body_weight WHERE user_id = ? AND dirty = 1", u,
                c -> new JSONObject().put("date", c.getString(0)).put("weight", c.getDouble(1)).put("deleted", false));
        JSONArray foods = rows(r, "SELECT uuid, date, name, grams, kcal, protein, fat, carbs, code FROM food "
                        + "WHERE user_id = ? AND dirty = 1", u,
                c -> new JSONObject().put("uuid", c.getString(0)).put("date", c.getString(1))
                        .put("name", c.getString(2)).put("grams", c.getDouble(3)).put("kcal", c.getDouble(4))
                        .put("protein", c.getDouble(5)).put("fat", c.getDouble(6)).put("carbs", c.getDouble(7))
                        .put("code", c.isNull(8) ? JSONObject.NULL : c.getString(8)).put("deleted", false));
        try (Cursor c = r.rawQuery("SELECT kind, uuid FROM tombstone WHERE user_id = ? OR user_id IS NULL",
                new String[]{u})) {
            while (c.moveToNext()) {
                JSONObject gone = new JSONObject().put("uuid", c.getString(1)).put("deleted", true);
                switch (c.getString(0)) {
                    case "exercise" -> exercises.put(gone);
                    case "entry" -> entries.put(gone);
                    default -> foods.put(gone);
                }
            }
        }
        JSONArray profile = rows(r, "SELECT name, sex, age, height, target, goal, experience, per_week, priorities "
                        + "FROM profile WHERE user_id = ? AND dirty = 1", u,
                c -> new JSONObject().put("name", c.getString(0)).put("sex", c.getString(1)).put("age", c.getInt(2))
                        .put("height", c.getInt(3)).put("target", c.getDouble(4)).put("goal", c.getString(5))
                        .put("experience", c.getString(6)).put("perWeek", c.getInt(7))
                        .put("priorities", c.getString(8)));
        return new JSONObject().put("cursor", cursor).put("exercises", exercises).put("entries", entries)
                .put("bodyWeights", bodyWeights).put("foods", foods)
                .put("profile", profile.length() > 0 ? profile.get(0) : JSONObject.NULL);
    }

    static void apply(Db db, JSONObject sent, JSONObject got) throws JSONException {
        SQLiteDatabase w = db.getWritableDatabase();
        String u = uid(db);
        w.beginTransaction();
        try {
            w.execSQL("UPDATE sync_flag SET applying = 1");
            clearSent(w, u, sent);
            applyExercises(w, db.userId(), got.getJSONArray("exercises"));
            applyEntries(w, u, got.getJSONArray("entries"));
            applyBodyWeights(w, db.userId(), got.getJSONArray("bodyWeights"));
            applyFoods(w, db.userId(), got.getJSONArray("foods"));
            JSONObject p = got.optJSONObject("profile");
            if (p != null) applyProfile(w, db.userId(), p);
            ContentValues v = new ContentValues();
            v.put("cursor", got.getLong("cursor"));
            v.put("synced", LocalDateTime.now().withNano(0).toString());
            w.update("cloud", v, "user_id = ?", new String[]{u});
            w.execSQL("UPDATE sync_flag SET applying = 0");
            w.setTransactionSuccessful();
        } finally {
            w.endTransaction();
        }
    }

    private static void clearSent(SQLiteDatabase w, String u, JSONObject sent) throws JSONException {
        String[][] kinds = {{"exercises", "exercise"}, {"entries", "entry"}, {"foods", "food"}};
        for (String[] kind : kinds) {
            JSONArray list = sent.getJSONArray(kind[0]);
            for (int i = 0; i < list.length(); i++) {
                String uuid = list.getJSONObject(i).getString("uuid");
                w.execSQL("UPDATE " + kind[1] + " SET dirty = 0 WHERE uuid = ?", new Object[]{uuid});
                w.delete("tombstone", "kind = ? AND uuid = ?", new String[]{kind[1], uuid});
            }
        }
        JSONArray weights = sent.getJSONArray("bodyWeights");
        for (int i = 0; i < weights.length(); i++) {
            w.execSQL("UPDATE body_weight SET dirty = 0 WHERE user_id = ? AND date = ?",
                    new Object[]{u, weights.getJSONObject(i).getString("date")});
        }
        if (!sent.isNull("profile")) w.execSQL("UPDATE profile SET dirty = 0 WHERE user_id = ?", new Object[]{u});
    }

    private static void applyExercises(SQLiteDatabase w, long user, JSONArray list) throws JSONException {
        for (int i = 0; i < list.length(); i++) {
            JSONObject e = list.getJSONObject(i);
            if (!e.getBoolean("deleted")) {
                w.execSQL("UPDATE exercise SET name = uuid WHERE uuid = ?", new Object[]{e.getString("uuid")});
            }
        }
        for (int i = 0; i < list.length(); i++) {
            JSONObject e = list.getJSONObject(i);
            String uuid = e.getString("uuid");
            if (e.getBoolean("deleted")) {
                w.delete("exercise", "uuid = ? AND user_id = ?", new String[]{uuid, String.valueOf(user)});
                continue;
            }
            ContentValues v = new ContentValues();
            v.put("name", e.getString("name"));
            v.put("favorite", e.getInt("favorite"));
            v.put("dirty", 0);
            if (w.update("exercise", v, "uuid = ?", new String[]{uuid}) == 0) {
                v.put("uuid", uuid);
                v.put("user_id", user);
                w.insertOrThrow("exercise", null, v);
            }
        }
    }

    private static void applyEntries(SQLiteDatabase w, String u, JSONArray list) throws JSONException {
        for (int i = 0; i < list.length(); i++) {
            JSONObject e = list.getJSONObject(i);
            String uuid = e.getString("uuid");
            if (e.getBoolean("deleted")) {
                w.delete("entry", "uuid = ?", new String[]{uuid});
                continue;
            }
            long exercise;
            try {
                exercise = DatabaseUtils.longForQuery(w, "SELECT id FROM exercise WHERE uuid = ? AND user_id = ?",
                        new String[]{e.getString("exerciseUuid"), u});
            } catch (android.database.sqlite.SQLiteDoneException missing) {
                continue;
            }
            ContentValues v = new ContentValues();
            v.put("exercise_id", exercise);
            v.put("date", e.getString("date"));
            v.put("weight", e.getDouble("weight"));
            v.put("reps", e.getInt("reps"));
            v.put("dirty", 0);
            if (w.update("entry", v, "uuid = ?", new String[]{uuid}) == 0) {
                v.put("uuid", uuid);
                w.insertOrThrow("entry", null, v);
            }
        }
    }

    private static void applyBodyWeights(SQLiteDatabase w, long user, JSONArray list) throws JSONException {
        for (int i = 0; i < list.length(); i++) {
            JSONObject b = list.getJSONObject(i);
            if (b.getBoolean("deleted")) {
                w.delete("body_weight", "user_id = ? AND date = ?", new String[]{String.valueOf(user), b.getString("date")});
                continue;
            }
            ContentValues v = new ContentValues();
            v.put("user_id", user);
            v.put("date", b.getString("date"));
            v.put("weight", b.getDouble("weight"));
            v.put("dirty", 0);
            w.insertWithOnConflict("body_weight", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private static void applyFoods(SQLiteDatabase w, long user, JSONArray list) throws JSONException {
        for (int i = 0; i < list.length(); i++) {
            JSONObject f = list.getJSONObject(i);
            String uuid = f.getString("uuid");
            if (f.getBoolean("deleted")) {
                w.delete("food", "uuid = ? AND user_id = ?", new String[]{uuid, String.valueOf(user)});
                continue;
            }
            ContentValues v = new ContentValues();
            v.put("date", f.getString("date"));
            v.put("name", f.getString("name"));
            v.put("grams", f.getDouble("grams"));
            v.put("kcal", f.getDouble("kcal"));
            v.put("protein", f.getDouble("protein"));
            v.put("fat", f.getDouble("fat"));
            v.put("carbs", f.getDouble("carbs"));
            v.put("code", f.isNull("code") ? null : f.getString("code"));
            v.put("dirty", 0);
            if (w.update("food", v, "uuid = ?", new String[]{uuid}) == 0) {
                v.put("uuid", uuid);
                v.put("user_id", user);
                w.insertOrThrow("food", null, v);
            }
        }
    }

    private static void applyProfile(SQLiteDatabase w, long user, JSONObject p) throws JSONException {
        ContentValues v = new ContentValues();
        v.put("user_id", user);
        v.put("name", p.optString("name", ""));
        v.put("sex", p.getString("sex"));
        v.put("age", p.getInt("age"));
        v.put("height", p.getInt("height"));
        v.put("target", p.getDouble("target"));
        v.put("goal", p.getString("goal"));
        v.put("experience", p.getString("experience"));
        v.put("per_week", p.getInt("perWeek"));
        v.put("priorities", p.optString("priorities", ""));
        v.put("dirty", 0);
        w.insertWithOnConflict("profile", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static JSONArray rows(SQLiteDatabase r, String sql, String user, Row row) throws JSONException {
        JSONArray list = new JSONArray();
        try (Cursor c = r.rawQuery(sql, new String[]{user})) {
            while (c.moveToNext()) list.put(row.read(c));
        }
        return list;
    }

    private static String uid(Db db) {
        return String.valueOf(db.userId());
    }
}
