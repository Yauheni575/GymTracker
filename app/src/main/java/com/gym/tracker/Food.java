package com.gym.tracker;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;

final class Food {

    private static final String SEARCH = "https://search.openfoodfacts.org/search?langs=ru&page_size=30&fields="
            + "code,product_name,brands,nutriments,countries_tags&q=";
    private static final String PRODUCT = "https://world.openfoodfacts.org/api/v2/product/";
    private static final String BELARUS = "en:belarus";
    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final int LIMIT = 40;
    private static final List<Product> BASE = new ArrayList<>();

    private Food() {
    }

    static final class Product {
        final String name;
        final String brand;
        final double kcal;
        final double protein;
        final double fat;
        final double carbs;
        final boolean belarus;
        String code;

        Product(String name, String brand, double kcal, double protein, double fat, double carbs, boolean belarus) {
            this.name = name;
            this.brand = brand;
            this.kcal = kcal;
            this.protein = protein;
            this.fat = fat;
            this.carbs = carbs;
            this.belarus = belarus;
        }

        String title() {
            return brand.isEmpty() ? name : name + " · " + brand;
        }
    }

    static boolean isBarcode(String query) {
        return query.matches("\\d{8,14}");
    }

    static synchronized void load(Context context) {
        if (!BASE.isEmpty()) return;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open("foods.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] f = line.split("\\|");
                if (f.length != 6) continue;
                BASE.add(new Product(f[0], f[1], Double.parseDouble(f[2]), Double.parseDouble(f[3]),
                        Double.parseDouble(f[4]), Double.parseDouble(f[5]), !f[1].isEmpty()));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static List<Product> local(List<Product> eaten, String query) {
        String q = normalize(query);
        String[] words = q.split("\\s+");
        for (int i = 0; i < words.length; i++) words[i] = stem(words[i]);

        List<Product> first = new ArrayList<>();
        List<Product> rest = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (List<Product> source : Arrays.asList(eaten, BASE)) {
            for (Product p : source) {
                String title = normalize(p.title());
                if (!matches(title, words) || !seen.add(title)) continue;
                (title.startsWith(words[0]) ? first : rest).add(p);
            }
        }
        first.addAll(rest);
        return new ArrayList<>(first.subList(0, Math.min(LIMIT, first.size())));
    }

    static void merge(List<Product> found, List<Product> more) {
        Set<String> seen = new HashSet<>();
        for (Product p : found) seen.add(normalize(p.title()));
        for (Product p : more) {
            if (seen.add(normalize(p.title()))) found.add(p);
        }
    }

    private static boolean matches(String title, String[] words) {
        for (String word : words) {
            if (!title.contains(word)) return false;
        }
        return true;
    }

    private static String normalize(String s) {
        return s.toLowerCase(RU).replace('ё', 'е').replace("«", "").replace("»", "").trim();
    }

    private static String stem(String word) {
        int n = word.length();
        return word.substring(0, n >= 6 ? n - 2 : n >= 4 ? n - 1 : n);
    }

    static List<Product> search(String query) throws IOException, JSONException {
        List<Product> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (isBarcode(query)) {
            try {
                JSONObject json = new JSONObject(get(PRODUCT + query + ".json?fields=product_name,brands,nutriments,countries_tags"));
                if (json.optInt("status") == 1) add(found, seen, json.getJSONObject("product"));
            } catch (FileNotFoundException missing) {
                return found;
            }
            for (Product p : found) p.code = query;
            return found;
        }
        JSONArray local = new JSONObject(get(SEARCH + encode(query + " countries_tags:\"" + BELARUS + "\""))).getJSONArray("hits");
        for (int i = 0; i < local.length(); i++) add(found, seen, local.getJSONObject(i));
        if (found.size() < 15) {
            JSONArray world = new JSONObject(get(SEARCH + encode(query))).getJSONArray("hits");
            for (int i = 0; i < world.length(); i++) add(found, seen, world.getJSONObject(i));
        }
        return found;
    }

    static Product parse(JSONObject p) {
        String name = p.optString("product_name").trim();
        JSONObject n = p.optJSONObject("nutriments");
        if (name.isEmpty() || n == null) return null;
        double kcal = n.optDouble("energy-kcal_100g", Double.NaN);
        if (Double.isNaN(kcal)) kcal = n.optDouble("energy-kj_100g", Double.NaN) / 4.184;
        if (Double.isNaN(kcal) || kcal <= 0 || kcal > 950) return null;

        JSONArray brands = p.optJSONArray("brands");
        String brand = brands != null ? brands.optString(0) : p.optString("brands").split(",")[0];
        JSONArray countries = p.optJSONArray("countries_tags");
        boolean belarus = countries != null && countries.toString().contains(BELARUS);
        return new Product(name, brand.trim(), kcal, value(n, "proteins_100g"), value(n, "fat_100g"),
                value(n, "carbohydrates_100g"), belarus);
    }

    private static void add(List<Product> found, Set<String> seen, JSONObject json) {
        Product p = parse(json);
        if (p != null && seen.add(p.title().toLowerCase(Locale.ROOT))) found.add(p);
    }

    private static double value(JSONObject n, String key) {
        double v = n.optDouble(key, 0);
        return Double.isNaN(v) || v < 0 ? 0 : v;
    }

    private static String encode(String s) throws IOException {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "GymTracker/1.0 (Android)");
        try (InputStream in = c.getInputStream(); Scanner s = new Scanner(in, "UTF-8")) {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            return s.useDelimiter("\\A").hasNext() ? s.next() : "";
        } finally {
            c.disconnect();
        }
    }
}
