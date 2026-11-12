package com.gym.tracker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class Catalog {

    static final String ALL = "Все";
    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();
    private static final Map<String, List<String>> GROUP_MUSCLES = new HashMap<>();
    private static final Map<String, String[]> INFO = new HashMap<>();

    static {
        group("Грудь", "chest",
                "Жим лёжа|chest|triceps,front-deltoids|Barbell_Bench_Press_-_Medium_Grip",
                "Жим лёжа на наклонной скамье|chest,front-deltoids|triceps|Barbell_Incline_Bench_Press_-_Medium_Grip",
                "Жим лёжа головой вниз|chest|triceps|Decline_Barbell_Bench_Press",
                "Жим гантелей лёжа|chest|triceps,front-deltoids|Dumbbell_Bench_Press",
                "Жим гантелей на наклонной скамье|chest,front-deltoids|triceps|Incline_Dumbbell_Press",
                "Разводка гантелей лёжа|chest|front-deltoids|Dumbbell_Flyes",
                "Сведение рук в кроссовере|chest|front-deltoids|Cable_Crossover",
                "Бабочка в тренажёре|chest||Butterfly",
                "Жим в тренажёре на грудь|chest|triceps,front-deltoids|Leverage_Chest_Press",
                "Отжимания на брусьях|chest,triceps|front-deltoids|Dips_-_Chest_Version",
                "Отжимания от пола|chest|triceps,front-deltoids,abs|Pushups",
                "Пуловер с гантелью|chest|upper-back,triceps|Bent-Arm_Dumbbell_Pullover");
        group("Спина", "upper-back,lower-back,trapezius",
                "Становая тяга|lower-back,hamstring,gluteal|trapezius,forearm,quadriceps|Barbell_Deadlift",
                "Подтягивания|upper-back|biceps,forearm,back-deltoids|Pullups",
                "Подтягивания обратным хватом|upper-back,biceps|forearm|Chin-Up",
                "Тяга штанги в наклоне|upper-back|back-deltoids,biceps,lower-back|Bent_Over_Barbell_Row",
                "Тяга гантели одной рукой|upper-back|back-deltoids,biceps|One-Arm_Dumbbell_Row",
                "Тяга верхнего блока|upper-back|biceps,back-deltoids|Wide-Grip_Lat_Pulldown",
                "Тяга верхнего блока узким хватом|upper-back|biceps|V-Bar_Pulldown",
                "Тяга нижнего блока|upper-back|back-deltoids,biceps|Seated_Cable_Rows",
                "Т-тяга|upper-back|back-deltoids,lower-back,biceps|T-Bar_Row_with_Handle",
                "Тяга в рычажном тренажёре|upper-back|biceps,back-deltoids|Leverage_High_Row",
                "Гиперэкстензия|lower-back|gluteal,hamstring|Hyperextensions_Back_Extensions",
                "Шраги со штангой|trapezius|forearm|Barbell_Shrug",
                "Шраги с гантелями|trapezius|forearm|Dumbbell_Shrug");
        group("Ноги", "quadriceps,hamstring,gluteal,calves,adductor,abductors",
                "Приседания|quadriceps,gluteal|adductor,lower-back,hamstring|Barbell_Squat",
                "Фронтальные приседания|quadriceps|gluteal,abs|Front_Barbell_Squat",
                "Гакк-приседания|quadriceps|gluteal|Hack_Squat",
                "Жим ногами|quadriceps,gluteal|adductor,hamstring|Leg_Press",
                "Выпады с гантелями|quadriceps,gluteal|hamstring,calves|Dumbbell_Lunges",
                "Болгарские сплит-приседания|quadriceps,gluteal|hamstring|Split_Squat_with_Dumbbells",
                "Румынская тяга|hamstring,gluteal|lower-back|Romanian_Deadlift",
                "Становая тяга сумо|gluteal,adductor|quadriceps,hamstring,lower-back|Sumo_Deadlift",
                "Разгибание ног в тренажёре|quadriceps||Leg_Extensions",
                "Сгибание ног в тренажёре|hamstring|calves|Lying_Leg_Curls",
                "Ягодичный мост|gluteal|hamstring|Barbell_Glute_Bridge",
                "Отведение ноги в тренажёре|abductors|gluteal|Thigh_Abductor",
                "Подъём на носки стоя|calves|left-soleus,right-soleus|Standing_Calf_Raises",
                "Подъём на носки сидя|left-soleus,right-soleus|calves|Seated_Calf_Raise");
        group("Плечи", "front-deltoids,back-deltoids",
                "Жим штанги стоя|front-deltoids|triceps,trapezius|Standing_Military_Press",
                "Жим гантелей сидя|front-deltoids|triceps|Seated_Dumbbell_Press",
                "Жим Арнольда|front-deltoids|triceps,back-deltoids|Arnold_Dumbbell_Press",
                "Жим в тренажёре на плечи|front-deltoids|triceps|Machine_Shoulder_Military_Press",
                "Махи гантелями в стороны|front-deltoids,back-deltoids|trapezius|Side_Lateral_Raise",
                "Махи гантелями в наклоне|back-deltoids|upper-back,trapezius|Seated_Bent-Over_Rear_Delt_Raise",
                "Подъём гантелей перед собой|front-deltoids|chest|Front_Dumbbell_Raise",
                "Тяга штанги к подбородку|front-deltoids,trapezius|biceps|Upright_Barbell_Row",
                "Обратная бабочка|back-deltoids|upper-back|Reverse_Machine_Flyes",
                "Тяга каната к лицу|back-deltoids|trapezius,upper-back|Face_Pull");
        group("Руки", "biceps,triceps,forearm",
                "Подъём штанги на бицепс|biceps|forearm|Barbell_Curl",
                "Подъём гантелей на бицепс|biceps|forearm|Dumbbell_Bicep_Curl",
                "Молотки|biceps,forearm||Hammer_Curls",
                "Сгибания на скамье Скотта|biceps|forearm|Preacher_Curl",
                "Концентрированный подъём на бицепс|biceps|forearm|Concentration_Curls",
                "Сгибания рук на блоке|biceps|forearm|Standing_Biceps_Cable_Curl",
                "Французский жим|triceps||Lying_Triceps_Press",
                "Разгибание рук на блоке|triceps||Triceps_Pushdown",
                "Жим лёжа узким хватом|triceps|chest,front-deltoids|Close-Grip_Barbell_Bench_Press",
                "Разгибание гантели из-за головы|triceps||Standing_Dumbbell_Triceps_Extension",
                "Отжимания от скамьи|triceps|chest,front-deltoids|Bench_Dips",
                "Сгибание запястий со штангой|forearm||Palms-Up_Barbell_Wrist_Curl_Over_A_Bench");
        group("Пресс", "abs,obliques",
                "Скручивания|abs|obliques|Crunches",
                "Скручивания на блоке|abs|obliques|Cable_Crunch",
                "Обратные скручивания|abs|obliques|Reverse_Crunch",
                "Подъём ног в висе|abs|obliques,forearm|Hanging_Leg_Raise",
                "Подъём коленей в упоре|abs|obliques|Knee_Hip_Raise_On_Parallel_Bars",
                "Ролик для пресса|abs|obliques,front-deltoids|Ab_Roller",
                "Велосипед|abs,obliques||Air_Bike",
                "Русские скручивания|obliques|abs|Russian_Twist");
    }

    private Catalog() {
    }

    private static void group(String group, String muscles, String... exercises) {
        GROUP_MUSCLES.put(group, Arrays.asList(muscles.split(",")));
        List<String> names = new ArrayList<>();
        for (String e : exercises) {
            String[] parts = e.split("\\|", -1);
            names.add(parts[0]);
            INFO.put(normalize(parts[0]), new String[]{parts[1], parts[2], parts[3], group});
        }
        GROUPS.put(group, names);
    }

    static List<String> groups() {
        List<String> names = new ArrayList<>();
        names.add(ALL);
        names.addAll(GROUPS.keySet());
        return names;
    }

    static List<String> exercises(String group) {
        List<String> names = GROUPS.get(group);
        return names == null ? Collections.emptyList() : names;
    }

    static List<String> primary(String exercise) {
        return field(exercise, 0);
    }

    static List<String> secondary(String exercise) {
        return field(exercise, 1);
    }

    static String image(String exercise, int frame) {
        String[] info = INFO.get(normalize(exercise));
        if (info == null || info[2].isEmpty()) return null;
        return "ex_" + info[2].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("_+$", "") + "_" + frame;
    }

    static String group(String exercise) {
        String[] info = INFO.get(normalize(exercise));
        return info == null ? null : info[3];
    }

    private static List<String> field(String exercise, int index) {
        String[] info = INFO.get(normalize(exercise));
        if (info == null || info[index].isEmpty()) return Collections.emptyList();
        return Arrays.asList(info[index].split(","));
    }

    static List<String> groupMuscles(String group) {
        List<String> m = GROUP_MUSCLES.get(group);
        return m == null ? Collections.emptyList() : m;
    }

    static List<String[]> find(String group, String query) {
        String q = normalize(query);
        List<String[]> found = new ArrayList<>();
        for (Map.Entry<String, List<String>> g : GROUPS.entrySet()) {
            if (!group.equals(ALL) && !group.equals(g.getKey())) continue;
            for (String name : g.getValue()) {
                if (normalize(name).contains(q)) found.add(new String[]{name, g.getKey()});
            }
        }
        return found;
    }

    static boolean contains(String name) {
        return INFO.containsKey(normalize(name));
    }

    private static String normalize(String s) {
        return s.trim().toLowerCase(RU).replace('ё', 'е');
    }
}
