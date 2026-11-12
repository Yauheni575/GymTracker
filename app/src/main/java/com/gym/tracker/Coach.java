package com.gym.tracker;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Coach {

    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(RU);

    public static final int STATUS = 0;
    public static final int WEEK = 1;
    public static final int EXERCISE = 2;
    public static final int OTHER = 3;
    public static final int FOOD = 4;
    public static final int RAISE_AT = 9;
    public static final int NONE = 0;
    public static final int UP = 1;
    public static final int FLAT = 2;
    public static final int DOWN = 3;

    public static final class Tip {
        public final String icon;
        public final String title;
        public final String text;
        public final int section;
        public final String exercise;
        public final String next;
        public final int trend;
        public final String status;

        Tip(String icon, String title, String text) {
            this(icon, title, text, sectionOf(icon), null, null, NONE, null);
        }

        Tip(String icon, String title, String text, int section, String exercise, String next, int trend, String status) {
            this.icon = icon;
            this.title = title;
            this.text = text;
            this.section = section;
            this.exercise = exercise;
            this.next = next;
            this.trend = trend;
            this.status = status;
        }

        private static int sectionOf(String icon) {
            switch (icon) {
                case "flag":
                case "alarm":
                case "sleep":
                    return STATUS;
                case "fire":
                case "calendar":
                    return WEEK;
                default:
                    return OTHER;
            }
        }
    }

    private Coach() {
    }

    public static final class Person implements java.io.Serializable {
        public String name = "";
        public String sex = "Мужской";
        public int age;
        public int height;
        public double weight;
        public double target;
        public String goal = "Масса";
        public String experience = "Новичок";
        public int perWeek = 3;
        public Set<String> priorities = new LinkedHashSet<>();
    }

    public static String protein(Person p) {
        return p.weight <= 0 ? "—" : Math.round(p.weight * 1.6) + "–" + Math.round(p.weight * 2) + " г";
    }

    public static String water(Person p) {
        return p.weight <= 0 ? "—" : new DecimalFormat("0.0", SYMBOLS).format(p.weight * 0.033) + " л";
    }

    public static long[] macros(Person p) {
        long kcal = calories(p);
        if (kcal == 0) return new long[]{0, 0, 0};
        long protein = Math.round(p.weight * 1.8);
        long fat = Math.round(p.weight * 0.9);
        long carbs = Math.max(0, Math.round((kcal - protein * 4 - fat * 9) / 4.0));
        return new long[]{protein, fat, carbs};
    }

    public static long calories(Person p) {
        if (p.weight <= 0 || p.height <= 0 || p.age <= 0) return 0;
        double bmr = 10 * p.weight + 6.25 * p.height - 5 * p.age + ("Женский".equals(p.sex) ? -161 : 5);
        double total = bmr * (1.2 + 0.05 * p.perWeek);
        if ("Масса".equals(p.goal)) total *= 1.1;
        else if ("Похудение".equals(p.goal)) total *= 0.85;
        return Math.round(total / 50) * 50;
    }

    static String nutrition(List<Tip> tips, Person p, double[] food) {
        long norm = calories(p);
        if (norm == 0) return null;
        if (food[0] < 3) {
            tips.add(meal("restaurant", "Записывайте питание",
                    "Отмечайте еду хотя бы 3 дня в неделю на вкладке «Питание» — тогда советы будут учитывать калории и белок."));
            return null;
        }
        long kcal = Math.round(food[1]);
        long protein = Math.round(food[2]);
        long target = macros(p)[0];
        boolean mass = "Масса".equals(p.goal);
        boolean cut = "Похудение".equals(p.goal);
        double ratio = kcal / (double) norm;
        double high = cut ? 1.05 : mass ? 1.15 : 1.1;
        long gap = Math.max(50, Math.round(Math.abs(norm - kcal) / 50.0) * 50);
        String average = "В среднем за неделю вы едите " + kcal + " ккал при норме " + norm + ". ";
        String fuel = null;

        if (ratio < (cut ? 0.8 : mass ? 0.95 : 0.9)) {
            tips.add(meal("restaurant", "Мало калорий", average + (cut
                    ? "Дефицит слишком большой: вместе с жиром уходят мышцы и падает сила. Добавьте около " + gap + " ккал в день."
                    : mass ? "Для набора массы этого не хватает — мышцам не из чего расти. Добавьте около " + gap
                    + " ккал в день: например, порцию каши с мясом или творог с бананом."
                    : "На таком питании сила не растёт. Добавьте около " + gap + " ккал в день.")));
            fuel = "Вы едите около " + kcal + " ккал при норме " + norm + " — начните с питания, без него результат не вырастет. ";
        } else if (ratio > high) {
            tips.add(meal("restaurant", "Калорий больше нормы", average + (cut
                    ? "Так вес уходить не будет. Уберите около " + gap + " ккал в день — проще всего за счёт сладкого и напитков."
                    : "Лишнее откладывается в жир, а не в мышцы. Уберите около " + gap + " ккал в день.")));
        }
        if (protein < target * 0.8) {
            tips.add(meal("protein", "Мало белка", "В среднем " + protein + " г белка в день при норме " + target
                    + " г. Добавьте около " + (target - protein) + " г: 200 г творога дают примерно 34 г, 150 г куриной грудки — 45 г."));
            if (fuel == null) {
                fuel = "Белка в среднем " + protein + " г при норме " + target + " г — мышцам не хватает материала для восстановления. ";
            }
        }
        if (fuel == null && ratio <= high) {
            tips.add(meal("check", "Питание в норме", average + "Белка " + protein + " г в день — достаточно. Продолжайте так же."));
        }
        return fuel;
    }

    private static Tip meal(String icon, String title, String text) {
        return new Tip(icon, title, text, FOOD, null, null, NONE, null);
    }

    public static List<Tip> tips(Person p, int thisWeek, long daysSinceLast,
                                 Map<String, List<double[]>> sessions, Set<String> exercises, double[] food) {
        List<Tip> tips = new ArrayList<>();
        String goal = p.goal;
        int perWeek = p.perWeek;
        double bodyWeight = p.weight;

        if (sessions.isEmpty()) {
            tips.add(new Tip("flag", "Начните первую тренировку",
                    "Добавьте упражнение на вкладке «Тренировки» и запишите подходы — здесь появятся советы по прогрессии."));
        } else if (daysSinceLast >= 4) {
            tips.add(new Tip("alarm", "Пора в зал",
                    "Последняя тренировка была " + daysSinceLast + " " + plural(daysSinceLast, "день", "дня", "дней")
                            + " назад. Перерывы дольше 3–4 дней тормозят прогресс."));
        } else if (daysSinceLast == 0) {
            tips.add(new Tip("sleep", "Сегодня тренировка уже была",
                    "Мышцы растут во время отдыха: спите 7–9 часов и не нагружайте завтра те же группы мышц."));
        }

        String week = "Тренировок на этой неделе: " + thisWeek + " из " + perWeek + ".";
        if (thisWeek >= perWeek) {
            tips.add(new Tip("fire", "План недели выполнен", week + " Так держать!"));
        } else {
            tips.add(new Tip("calendar", "План на неделю", week + " Осталось ещё " + (perWeek - thisWeek) + "."));
        }

        for (String group : p.priorities) {
            List<String> missing = new ArrayList<>();
            boolean covered = false;
            for (String name : Catalog.exercises(group)) {
                if (exercises.contains(name)) covered = true;
                else if (missing.size() < 2) missing.add("«" + name + "»");
            }
            if (!covered && !missing.isEmpty()) {
                tips.add(new Tip("workouts", "Приоритет: " + group,
                        "Вы отметили эту группу как приоритетную, но упражнений на неё пока нет. Добавьте "
                                + String.join(" и ", missing) + " и делайте 10–20 подходов на неё в неделю."));
            }
        }

        String fuel = nutrition(tips, p, food);
        int falling = 0;
        for (Map.Entry<String, List<double[]>> e : sessions.entrySet()) {
            Tip tip = progression(e.getKey(), e.getValue(), goal, fuel);
            if (tip.trend == DOWN) falling++;
            tips.add(tip);
        }
        if (falling >= 2) {
            tips.add(new Tip("sleep", "Накопилась усталость",
                    "Результаты падают сразу в " + falling + " упражнениях — дело не в технике, а в восстановлении. "
                            + (fuel != null ? fuel : "") + "Неделю делайте на треть меньше подходов и спите 7–9 часов."));
        }

        if ("Новичок".equals(p.experience)) {
            tips.add(new Tip("school", "Первые месяцы",
                    "Техника важнее веса: прибавляйте 2,5 кг, только когда все подходы даются уверенно и без рывков."));
        } else if ("Профи".equals(p.experience)) {
            tips.add(new Tip("tips", "Периодизация",
                    "Чередуйте тяжёлые недели (4–6 повторений) и объёмные (8–" + RAISE_AT + "), чтобы прогресс не останавливался."));
        }

        if (p.target > 0 && bodyWeight > 0) {
            double diff = p.target - bodyWeight;
            if (Math.abs(diff) < 0.5) {
                tips.add(new Tip("trophy", "Цель по весу достигнута", "Вы весите " + kg(bodyWeight) + " кг — ровно столько, сколько хотели."));
            } else {
                double pace = diff < 0 ? 0.5 : 0.25;
                long weeks = (long) Math.ceil(Math.abs(diff) / pace);
                tips.add(new Tip("target", "Цель по весу",
                        "До " + kg(p.target) + " кг осталось " + (diff < 0 ? "сбросить " : "набрать ") + kg(Math.abs(diff))
                                + " кг. При безопасном темпе " + kg(pace) + " кг в неделю это примерно " + weeks + " "
                                + plural(weeks, "неделя", "недели", "недель") + "."));
            }
        }

        if (bodyWeight <= 0) {
            tips.add(new Tip("scale", "Укажите вес тела",
                    "Запишите вес в профиле — посчитаю норму калорий, белка и воды."));
        }
        return tips;
    }

    public static int minReps(String goal) {
        return "Сила".equals(goal) ? 4 : 6;
    }

    static Tip progression(String name, List<double[]> sessions, String goal, String fuel) {
        int lo = minReps(goal);
        List<double[]> solid = new ArrayList<>();
        for (double[] s : sessions) {
            if (s[0] == 0 || s[1] >= lo) solid.add(s);
        }
        boolean lastSolid = !solid.isEmpty() && solid.get(solid.size() - 1) == sessions.get(sessions.size() - 1);
        int trend = lastSolid ? trend(solid) : NONE;
        int stall = stall(solid);
        String[] plan = plan(sessions, goal, trend, stall, fuel);
        return new Tip(plan[0], name, plan[1], EXERCISE, name, plan[2], trend, status(solid, trend, stall));
    }

    private static String[] plan(List<double[]> sessions, String goal, int trend, int stall, String fuel) {
        int n = sessions.size();
        double[] last = sessions.get(n - 1);
        double[] prev = n >= 2 ? sessions.get(n - 2) : last;
        double w = last[0];
        int r = (int) last[1];
        int lo = minReps(goal);
        String range = lo + "–" + RAISE_AT;
        String was = "В прошлый раз: " + set(w, r) + ". ";
        String rest = fuel != null ? fuel : "Спите 7–9 часов и ешьте не меньше своей нормы калорий. ";

        if (w == 0) {
            if (trend == DOWN) {
                return plan("sleep", was + "Повторений становится меньше — мышцы не успевают восстановиться. "
                        + rest + "Отдохните лишний день перед следующей тренировкой.", set(0, r));
            }
            if (trend == FLAT) {
                return plan("repeat", was + "Повторения не растут. " + (fuel != null ? fuel : "")
                        + "Добавьте ещё один подход и отдыхайте между подходами 2–3 минуты.", set(0, r + 1));
            }
            if (r >= 12) {
                return plan("progress", was + "Уже легко — добавьте отягощение 2,5–5 кг и работайте на " + range + " повторений.",
                        "+2,5 кг × " + range);
            }
            return plan(trend == UP ? "check" : "add", was + (trend == UP ? "Прогресс идёт — ничего не меняйте, прибавляйте"
                    : "Прибавляйте") + " по одному повторению.", set(0, r + 1));
        }

        if (prev[0] > w && prev[1] >= lo) {
            int reps = (int) Math.min(RAISE_AT, prev[1] + 1);
            return plan("repeat", was + "Это была облегчённая тренировка. Возвращайтесь к " + kg(prev[0])
                    + " кг и попробуйте сделать больше, чем раньше.", kg(prev[0]) + " кг × " + reps);
        }
        if (r >= RAISE_AT) {
            if (r < 12 && failedHeavier(sessions, w, lo)) {
                return plan("add", was + "Вес выше недавно не пошёл — шаг слишком большой. Доведите " + kg(w)
                        + " кг до 12 повторений и пробуйте повышать снова.", kg(w) + " кг × " + (r + 1));
            }
            return plan("progress", was + "Сделано " + RAISE_AT + " и больше повторений — вес освоен, повышайте.",
                    kg(w + 2.5) + " кг × " + range);
        }
        if (r < lo) {
            double down = prev[0] > 0 && prev[0] < w ? prev[0] : lighter(w);
            return plan("target", was + "Меньше " + lo + " повторений — вес слишком тяжёлый: страдает техника и растёт риск травмы. "
                    + "Снизьте до " + kg(down) + " кг и наберите " + range + " повторений.", kg(down) + " кг × " + range);
        }
        if (trend == DOWN) {
            return plan("sleep", was + "Результат падает вторую тренировку подряд — обычно это недовосстановление. " + rest
                    + "Неделю делайте на треть меньше подходов с " + kg(lighter(w)) + " кг, затем возвращайтесь к " + kg(w) + " кг."
                    + ("Похудение".equals(goal) ? " На дефиците калорий небольшой спад силы — это нормально." : ""),
                    kg(lighter(w)) + " кг × " + range);
        }
        if (trend == FLAT) {
            if (stall >= 5) {
                return plan("repeat", was + "Вес и повторения давно стоят на месте. " + (fuel != null ? fuel : "")
                        + "Смените стимул: на 3–4 недели замените упражнение похожим (например, штангу на гантели), потом вернитесь.",
                        "смените упражнение");
            }
            return plan("repeat", was + "Вес и повторения не растут. " + (fuel != null ? fuel : "Проверьте сон и питание. ")
                    + "Сделайте одну облегчённую тренировку с " + kg(lighter(w)) + " кг, затем вернитесь к " + kg(w)
                    + " кг и добавьте повтор.", kg(lighter(w)) + " кг × " + RAISE_AT);
        }
        return plan(trend == UP ? "check" : "add", was + (trend == UP ? "Прогресс идёт — ничего не меняйте. " : "")
                + "Добавляйте по повторению, на " + RAISE_AT + " повторениях повышайте вес до " + kg(w + 2.5) + " кг.",
                kg(w) + " кг × " + (r + 1));
    }

    private static String[] plan(String icon, String text, String next) {
        return new String[]{icon, text, next};
    }

    static int trend(List<double[]> sessions) {
        int n = sessions.size();
        if (n < 3) return NONE;
        double[] a = sessions.get(n - 3);
        double[] b = sessions.get(n - 2);
        double[] c = sessions.get(n - 1);
        if (compare(c, b) < 0 && compare(b, a) < 0) return DOWN;
        if (compare(c, b) > 0 && compare(b, a) > 0) return UP;
        if (stall(sessions) >= 2) return FLAT;
        return compare(c, a) > 0 ? UP : NONE;
    }

    static int stall(List<double[]> sessions) {
        int n = sessions.size();
        int best = Math.max(0, n - 8);
        for (int i = best + 1; i < n; i++) {
            if (compare(sessions.get(i), sessions.get(best)) > 0) best = i;
        }
        return n - 1 - best;
    }

    private static int compare(double[] x, double[] y) {
        return x[0] != y[0] ? Double.compare(x[0], y[0]) : Double.compare(x[1], y[1]);
    }

    private static boolean failedHeavier(List<double[]> sessions, double weight, int lo) {
        for (int i = Math.max(0, sessions.size() - 5); i < sessions.size(); i++) {
            if (sessions.get(i)[0] > weight && sessions.get(i)[1] < lo) return true;
        }
        return false;
    }

    private static String status(List<double[]> sessions, int trend, int stall) {
        int n = sessions.size();
        if (trend == NONE) return null;
        if (trend == FLAT) {
            int count = stall + 1;
            return "Застой: " + count + " " + plural(count, "тренировка", "тренировки", "тренировок") + " без роста";
        }
        double[] a = sessions.get(n - 3);
        double[] c = sessions.get(n - 1);
        return (trend == UP ? "Прогресс: " : "Спад: ") + shortSet(a[0], (int) a[1]) + " → " + shortSet(c[0], (int) c[1]);
    }

    static double lighter(double w) {
        return Math.max(0, Math.min(Math.round(w * 0.9 / 2.5) * 2.5, w - 2.5));
    }

    public static double oneRepMax(double weight, int reps) {
        return reps <= 1 ? weight : weight * (1 + reps / 30.0);
    }

    public static String set(double weight, int reps) {
        return weight > 0
                ? kg(weight) + " кг × " + reps
                : reps + " " + plural(reps, "повторение", "повторения", "повторений");
    }

    public static String shortSet(double weight, int reps) {
        return weight > 0 ? kg(weight) + " × " + reps : reps + " повт.";
    }

    public static String delta(double first, double last, String unit) {
        double d = last - first;
        return (d > 0 ? "+" : "") + kg(d) + unit;
    }

    public static String percent(double first, double last) {
        if (first <= 0) return "";
        long p = Math.round((last - first) / first * 100);
        return (p > 0 ? "+" : "") + p + "%";
    }

    public static String kg(double w) {
        return new DecimalFormat("0.##", SYMBOLS).format(w);
    }

    public static String bmi(double weight, int heightCm) {
        double h = heightCm / 100.0;
        double v = weight / (h * h);
        String kind = v < 18.5 ? "недостаток" : v < 25 ? "норма" : v < 30 ? "избыток" : "ожирение";
        return new DecimalFormat("0.0", SYMBOLS).format(v) + " (" + kind + ")";
    }

    public static String day(String iso) {
        LocalDate date = LocalDate.parse(iso);
        LocalDate today = LocalDate.now();
        if (date.equals(today)) return "Сегодня";
        if (date.equals(today.minusDays(1))) return "Вчера";
        String pattern = date.getYear() == today.getYear() ? "d MMMM" : "d MMMM yyyy";
        return date.format(DateTimeFormatter.ofPattern(pattern, RU));
    }

    public static String monthTitle(LocalDate date) {
        return capitalize(date.format(DateTimeFormatter.ofPattern("LLLL yyyy", RU)));
    }

    public static String dayTitle(LocalDate date) {
        String day = date.format(DateTimeFormatter.ofPattern("d MMMM", RU));
        if (date.equals(LocalDate.now())) return "Сегодня, " + day;
        return capitalize(date.format(DateTimeFormatter.ofPattern("EEEE", RU))) + ", " + day;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(RU) + s.substring(1);
    }

    public static String plural(long n, String one, String few, String many) {
        long m = Math.abs(n) % 100;
        if (m >= 11 && m <= 14) return many;
        if (m % 10 == 1) return one;
        if (m % 10 >= 2 && m % 10 <= 4) return few;
        return many;
    }
}
