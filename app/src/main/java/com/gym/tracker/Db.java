package com.gym.tracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Db extends SQLiteOpenHelper {

    private static final String MINE = "FROM entry en JOIN exercise e ON e.id = en.exercise_id WHERE e.user_id = ?";
    private static final int ALL_DAYS = 127;
    private static Db instance;

    private long userId = -1;
    private String login;

    private Db(Context context) {
        super(context, "tracker.db", null, 5);
    }

    public static synchronized Db get(Context context) {
        if (instance == null) {
            instance = new Db(context.getApplicationContext());
            instance.loadSession();
            Legacy.migrate(context.getApplicationContext(), instance);
        }
        return instance;
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY, login TEXT NOT NULL UNIQUE, "
                + "salt TEXT NOT NULL, hash TEXT NOT NULL, created TEXT NOT NULL)");
        db.execSQL("CREATE TABLE session (id INTEGER PRIMARY KEY CHECK (id = 1), "
                + "user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE profile (user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, "
                + "name TEXT NOT NULL DEFAULT '', sex TEXT NOT NULL, age INTEGER NOT NULL, height INTEGER NOT NULL, "
                + "target REAL NOT NULL DEFAULT 0, goal TEXT NOT NULL, experience TEXT NOT NULL, "
                + "per_week INTEGER NOT NULL, priorities TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE exercise (id INTEGER PRIMARY KEY, "
                + "user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, "
                + "name TEXT NOT NULL, favorite INTEGER NOT NULL DEFAULT 0, UNIQUE (user_id, name))");
        db.execSQL("CREATE TABLE entry (id INTEGER PRIMARY KEY, "
                + "exercise_id INTEGER NOT NULL REFERENCES exercise(id) ON DELETE CASCADE, "
                + "date TEXT NOT NULL, weight REAL NOT NULL, reps INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX entry_exercise_date ON entry (exercise_id, date)");
        db.execSQL("CREATE TABLE body_weight (user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, "
                + "date TEXT NOT NULL, weight REAL NOT NULL, PRIMARY KEY (user_id, date))");
        createFood(db);
        Sync.createSchema(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createFood(db);
        else if (oldVersion < 3) db.execSQL("ALTER TABLE food ADD COLUMN code TEXT");
        if (oldVersion < 4) db.execSQL("UPDATE exercise SET favorite = " + ALL_DAYS + " WHERE favorite = 1");
        if (oldVersion < 5) Sync.createSchema(db);
    }

    long userId() {
        return userId;
    }

    private static void createFood(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE food (id INTEGER PRIMARY KEY, "
                + "user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, "
                + "date TEXT NOT NULL, name TEXT NOT NULL, grams REAL NOT NULL, "
                + "kcal REAL NOT NULL, protein REAL NOT NULL, fat REAL NOT NULL, carbs REAL NOT NULL, code TEXT)");
        db.execSQL("CREATE INDEX food_user_date ON food (user_id, date)");
    }

    private void loadSession() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT u.id, u.login FROM session s JOIN users u ON u.id = s.user_id", null)) {
            if (c.moveToFirst()) {
                userId = c.getLong(0);
                login = c.getString(1);
            }
        }
    }

    public String login() {
        return login;
    }

    public long createUser(String login, String salt, String hash) {
        ContentValues v = new ContentValues();
        v.put("login", login);
        v.put("salt", salt);
        v.put("hash", hash);
        v.put("created", today());
        return getWritableDatabase().insert("users", null, v);
    }

    public String[] credentials(String login) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, salt, hash FROM users WHERE login = ?", new String[]{login})) {
            return c.moveToFirst() ? new String[]{c.getString(0), c.getString(1), c.getString(2)} : null;
        }
    }

    public void startSession(long id) {
        ContentValues v = new ContentValues();
        v.put("id", 1);
        v.put("user_id", id);
        getWritableDatabase().insertWithOnConflict("session", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        loadSession();
    }

    public void endSession() {
        getWritableDatabase().delete("session", null, null);
        userId = -1;
        login = null;
    }

    public boolean hasProfile() {
        return DatabaseUtils.longForQuery(getReadableDatabase(),
                "SELECT COUNT(*) FROM profile WHERE user_id = ?", new String[]{uid()}) > 0;
    }

    public Coach.Person person() {
        Coach.Person p = new Coach.Person();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT name, sex, age, height, target, goal, experience, per_week, priorities "
                        + "FROM profile WHERE user_id = ?", new String[]{uid()})) {
            if (c.moveToFirst()) {
                p.name = c.getString(0);
                p.sex = c.getString(1);
                p.age = c.getInt(2);
                p.height = c.getInt(3);
                p.target = c.getDouble(4);
                p.goal = c.getString(5);
                p.experience = c.getString(6);
                p.perWeek = c.getInt(7);
                String priorities = c.getString(8);
                if (!priorities.isEmpty()) p.priorities = new LinkedHashSet<>(Arrays.asList(priorities.split(",")));
            }
        }
        p.weight = lastBodyWeight();
        return p;
    }

    public void saveProfile(Coach.Person p) {
        saveProfile(userId, p);
        if (p.weight > 0 && p.weight != lastBodyWeight()) setBodyWeight(p.weight);
    }

    void saveProfile(long user, Coach.Person p) {
        ContentValues v = new ContentValues();
        v.put("user_id", user);
        v.put("name", p.name);
        v.put("sex", p.sex);
        v.put("age", p.age);
        v.put("height", p.height);
        v.put("target", p.target);
        v.put("goal", p.goal);
        v.put("experience", p.experience);
        v.put("per_week", p.perWeek);
        v.put("priorities", String.join(",", p.priorities));
        getWritableDatabase().insertWithOnConflict("profile", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void importLegacy(long user, File file) {
        SQLiteDatabase old = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Map<Long, Long> ids = new HashMap<>();
            try (Cursor c = old.rawQuery("SELECT * FROM exercise", null)) {
                int favorite = c.getColumnIndex("favorite");
                while (c.moveToNext()) {
                    ContentValues v = new ContentValues();
                    v.put("user_id", user);
                    v.put("name", c.getString(c.getColumnIndexOrThrow("name")));
                    v.put("favorite", favorite < 0 || c.getInt(favorite) == 0 ? 0 : ALL_DAYS);
                    ids.put(c.getLong(c.getColumnIndexOrThrow("id")), db.insert("exercise", null, v));
                }
            }
            try (Cursor c = old.rawQuery("SELECT exercise_id, date, weight, reps FROM entry", null)) {
                while (c.moveToNext()) {
                    Long id = ids.get(c.getLong(0));
                    if (id == null) continue;
                    ContentValues v = new ContentValues();
                    v.put("exercise_id", id);
                    v.put("date", c.getString(1));
                    v.put("weight", c.getDouble(2));
                    v.put("reps", c.getInt(3));
                    db.insert("entry", null, v);
                }
            }
            boolean hasWeights = DatabaseUtils.longForQuery(old,
                    "SELECT COUNT(*) FROM sqlite_master WHERE name = 'body_weight'", null) > 0;
            if (hasWeights) {
                try (Cursor c = old.rawQuery("SELECT date, weight FROM body_weight", null)) {
                    while (c.moveToNext()) {
                        ContentValues v = new ContentValues();
                        v.put("user_id", user);
                        v.put("date", c.getString(0));
                        v.put("weight", c.getDouble(1));
                        db.insertWithOnConflict("body_weight", null, v, SQLiteDatabase.CONFLICT_REPLACE);
                    }
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            old.close();
        }
    }

    private static int bit(DayOfWeek day) {
        return 1 << (day.getValue() - 1);
    }

    public void setFavorite(long id, DayOfWeek day, boolean favorite) {
        getWritableDatabase().execSQL(favorite
                        ? "UPDATE exercise SET favorite = favorite | ? WHERE id = ? AND user_id = ?"
                        : "UPDATE exercise SET favorite = favorite & ~? WHERE id = ? AND user_id = ?",
                new Object[]{bit(day), id, uid()});
    }

    public Cursor progressExercises() {
        return getReadableDatabase().rawQuery(
                "SELECT e.id, e.name, e.favorite > 0, COUNT(DISTINCT en.date), MAX(en.weight) > 0 "
                        + MINE + " GROUP BY e.id ORDER BY e.favorite > 0 DESC, e.name", new String[]{uid()});
    }

    public List<Double> weeklyVolume() {
        return doubles("SELECT v FROM (SELECT date(en.date, 'weekday 0', '-6 days') AS week, "
                + "SUM(en.weight * en.reps) AS v " + MINE + " GROUP BY week ORDER BY week DESC LIMIT 8) ORDER BY week", uid());
    }

    public long addExercise(String name) {
        ContentValues v = new ContentValues();
        v.put("user_id", userId);
        v.put("name", name);
        return getWritableDatabase().insert("exercise", null, v);
    }

    public void deleteExercise(long id) {
        getWritableDatabase().delete("exercise", "id = ? AND user_id = ?", new String[]{String.valueOf(id), uid()});
    }

    public Set<String> favoriteNames(DayOfWeek day) {
        Set<String> names = new HashSet<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT name FROM exercise WHERE user_id = ? AND favorite & ? != 0", new String[]{uid(), String.valueOf(bit(day))})) {
            while (c.moveToNext()) names.add(c.getString(0));
        }
        return names;
    }

    public void toggleFavorite(String name, DayOfWeek day) {
        long id = exerciseId(name);
        if (id == -1) id = addExercise(name);
        setFavorite(id, day, !favoriteNames(day).contains(name));
    }

    public long exerciseId(String name) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id FROM exercise WHERE user_id = ? AND name = ?", new String[]{uid(), name})) {
            return c.moveToFirst() ? c.getLong(0) : -1;
        }
    }

    public Cursor exerciseRows(boolean favoritesOnly, String onlyDate) {
        String bit = String.valueOf(bit(LocalDate.parse(onlyDate).getDayOfWeek()));
        return getReadableDatabase().rawQuery(
                "SELECT e.id, e.name, "
                        + "(SELECT MAX(weight) FROM entry WHERE exercise_id = e.id), "
                        + "(SELECT MAX(reps) FROM entry WHERE exercise_id = e.id), "
                        + "(SELECT weight FROM entry WHERE exercise_id = e.id ORDER BY id DESC LIMIT 1), "
                        + "(SELECT reps FROM entry WHERE exercise_id = e.id ORDER BY id DESC LIMIT 1), "
                        + "(SELECT COUNT(*) FROM entry WHERE exercise_id = e.id AND date = ?1), "
                        + "(SELECT MAX(date) FROM entry WHERE exercise_id = e.id), e.favorite & ?5 != 0 AS fav "
                        + "FROM exercise e WHERE e.user_id = ?2 AND (fav OR ?3 = '0') "
                        + "AND (?4 = '' OR ?3 = '1' OR EXISTS (SELECT 1 FROM entry WHERE exercise_id = e.id AND date = ?4)) "
                        + "ORDER BY fav DESC, e.name",
                new String[]{today(), uid(), favoritesOnly ? "1" : "0", onlyDate, bit});
    }

    public Set<String> exerciseNames() {
        Set<String> names = new HashSet<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT name FROM exercise WHERE user_id = ?", new String[]{uid()})) {
            while (c.moveToNext()) names.add(c.getString(0));
        }
        return names;
    }

    public Cursor dayTotals(String date) {
        return getReadableDatabase().rawQuery(
                "SELECT COUNT(*), COALESCE(SUM(en.weight * en.reps), 0) " + MINE + " AND en.date = ?",
                new String[]{uid(), date});
    }

    public Set<String> workoutDates() {
        Set<String> dates = new HashSet<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT DISTINCT en.date " + MINE, new String[]{uid()})) {
            while (c.moveToNext()) dates.add(c.getString(0));
        }
        return dates;
    }

    public Map<Long, List<double[]>> daySets(String date) {
        Map<Long, List<double[]>> sets = new HashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT en.exercise_id, en.weight, en.reps " + MINE + " AND en.date = ? ORDER BY en.id",
                new String[]{uid(), date})) {
            while (c.moveToNext()) {
                sets.computeIfAbsent(c.getLong(0), k -> new ArrayList<>())
                        .add(new double[]{c.getDouble(1), c.getInt(2)});
            }
        }
        return sets;
    }

    public double[] lastSet(long exerciseId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT weight, reps FROM entry WHERE exercise_id = ? ORDER BY id DESC LIMIT 1",
                new String[]{String.valueOf(exerciseId)})) {
            return c.moveToFirst() ? new double[]{c.getDouble(0), c.getInt(1)} : null;
        }
    }

    public long addEntry(long exerciseId, double weight, int reps) {
        return addEntry(exerciseId, weight, reps, today());
    }

    public long addEntry(long exerciseId, double weight, int reps, String date) {
        ContentValues v = new ContentValues();
        v.put("exercise_id", exerciseId);
        v.put("date", date);
        v.put("weight", weight);
        v.put("reps", reps);
        return getWritableDatabase().insert("entry", null, v);
    }

    public void deleteEntry(long id) {
        getWritableDatabase().delete("entry", "id = ?", new String[]{String.valueOf(id)});
    }

    public Cursor entries(long exerciseId) {
        return getReadableDatabase().rawQuery(
                "SELECT id, date, weight, reps FROM entry WHERE exercise_id = ? ORDER BY date DESC, id",
                new String[]{String.valueOf(exerciseId)});
    }

    public List<Double> progressByDate(long exerciseId) {
        return doubles("SELECT CASE WHEN (SELECT MAX(weight) FROM entry WHERE exercise_id = ?1) > 0 "
                        + "THEN MAX(weight) ELSE MAX(reps) END "
                        + "FROM entry WHERE exercise_id = ?1 GROUP BY date ORDER BY date",
                String.valueOf(exerciseId));
    }

    public Map<String, List<double[]>> topSetsByExercise() {
        Map<String, List<double[]>> sessions = new LinkedHashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT e.name, t.w, MAX(en.reps) FROM "
                        + "(SELECT exercise_id, date, MAX(weight) AS w FROM entry GROUP BY exercise_id, date) t "
                        + "JOIN entry en ON en.exercise_id = t.exercise_id AND en.date = t.date AND en.weight = t.w "
                        + "JOIN exercise e ON e.id = t.exercise_id WHERE e.user_id = ? "
                        + "GROUP BY t.exercise_id, t.date ORDER BY "
                        + "(SELECT MAX(date) FROM entry WHERE exercise_id = e.id) DESC, e.name, t.date", new String[]{uid()})) {
            while (c.moveToNext()) {
                sessions.computeIfAbsent(c.getString(0), k -> new ArrayList<>())
                        .add(new double[]{c.getDouble(1), c.getInt(2)});
            }
        }
        return sessions;
    }

    public Cursor stats() {
        return getReadableDatabase().rawQuery(
                "SELECT COUNT(DISTINCT en.date), COUNT(*), COALESCE(SUM(en.weight * en.reps), 0) " + MINE,
                new String[]{uid()});
    }

    public int workoutsThisWeek() {
        return (int) DatabaseUtils.longForQuery(getReadableDatabase(),
                "SELECT COUNT(DISTINCT en.date) " + MINE + " AND en.date >= ?",
                new String[]{uid(), LocalDate.now().with(DayOfWeek.MONDAY).toString()});
    }

    public long daysSinceLastWorkout() {
        String last = DatabaseUtils.stringForQuery(getReadableDatabase(), "SELECT MAX(en.date) " + MINE, new String[]{uid()});
        return last == null ? -1 : ChronoUnit.DAYS.between(LocalDate.parse(last), LocalDate.now());
    }

    public void setBodyWeight(double weight) {
        ContentValues v = new ContentValues();
        v.put("user_id", userId);
        v.put("date", today());
        v.put("weight", weight);
        getWritableDatabase().insertWithOnConflict("body_weight", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<Double> bodyWeights() {
        return doubles("SELECT weight FROM body_weight WHERE user_id = ? ORDER BY date", uid());
    }

    public double lastBodyWeight() {
        List<Double> all = bodyWeights();
        return all.isEmpty() ? 0 : all.get(all.size() - 1);
    }

    public long addFood(String date, Food.Product p, double grams) {
        ContentValues v = new ContentValues();
        v.put("user_id", userId);
        v.put("date", date);
        v.put("name", p.title());
        v.put("grams", grams);
        v.put("kcal", p.kcal);
        v.put("protein", p.protein);
        v.put("fat", p.fat);
        v.put("carbs", p.carbs);
        v.put("code", p.code);
        return getWritableDatabase().insert("food", null, v);
    }

    public void updateFood(long id, double grams) {
        ContentValues v = new ContentValues();
        v.put("grams", grams);
        getWritableDatabase().update("food", v, "id = ?", new String[]{String.valueOf(id)});
    }

    public void deleteFood(long id) {
        getWritableDatabase().delete("food", "id = ?", new String[]{String.valueOf(id)});
    }

    public Cursor foods(String date) {
        return getReadableDatabase().rawQuery(
                "SELECT id, name, grams, kcal * grams / 100, protein * grams / 100, fat * grams / 100, carbs * grams / 100 "
                        + "FROM food WHERE user_id = ? AND date = ? ORDER BY id", new String[]{uid(), date});
    }

    public Set<String> foodDates() {
        Set<String> dates = new HashSet<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT DISTINCT date FROM food WHERE user_id = ?", new String[]{uid()})) {
            while (c.moveToNext()) dates.add(c.getString(0));
        }
        return dates;
    }

    public double[] foodWeek() {
        LocalDate today = LocalDate.now();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*), COALESCE(AVG(k), 0), COALESCE(AVG(p), 0) FROM (SELECT SUM(kcal * grams / 100) AS k, "
                        + "SUM(protein * grams / 100) AS p FROM food WHERE user_id = ? AND date >= ? AND date < ? GROUP BY date)",
                new String[]{uid(), today.minusDays(7).toString(), today.toString()})) {
            c.moveToFirst();
            return new double[]{c.getInt(0), c.getDouble(1), c.getDouble(2)};
        }
    }

    public List<Food.Product> eatenFoods(int limit) {
        return products("GROUP BY name ORDER BY MAX(id) DESC LIMIT " + limit);
    }

    public Food.Product foodByCode(String code) {
        List<Food.Product> found = products("AND code = ? ORDER BY id DESC LIMIT 1", code);
        return found.isEmpty() ? null : found.get(0);
    }

    private List<Food.Product> products(String tail, String... args) {
        List<Food.Product> list = new ArrayList<>();
        String[] all = new String[args.length + 1];
        all[0] = uid();
        System.arraycopy(args, 0, all, 1, args.length);
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT name, kcal, protein, fat, carbs, code FROM food WHERE user_id = ? " + tail, all)) {
            while (c.moveToNext()) {
                Food.Product p = new Food.Product(c.getString(0), "", c.getDouble(1), c.getDouble(2),
                        c.getDouble(3), c.getDouble(4), false);
                p.code = c.getString(5);
                list.add(p);
            }
        }
        return list;
    }

    private List<Double> doubles(String sql, String... args) {
        List<Double> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) list.add(c.getDouble(0));
        }
        return list;
    }

    private String uid() {
        return String.valueOf(userId);
    }

    private static String today() {
        return LocalDate.now().toString();
    }
}
