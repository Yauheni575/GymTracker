package com.gym.tracker;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

final class Cloud {

    static final String API = BuildConfig.API_URL;

    private Cloud() {
    }

    static final class Unauthorized extends IOException {
        Unauthorized(String message) {
            super(message);
        }
    }

    static String token(boolean register, String login, String password) throws IOException, JSONException {
        JSONObject body = new JSONObject().put("login", login).put("password", password);
        return post(register ? "/api/auth/register" : "/api/auth/login", null, body).getString("token");
    }

    static JSONObject sync(String token, JSONObject changes) throws IOException, JSONException {
        return post("/api/sync", token, changes);
    }

    private static JSONObject post(String path, String token, JSONObject body) throws IOException, JSONException {
        HttpURLConnection c = (HttpURLConnection) new URL(API + path).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(90000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("User-Agent", "GymTracker/2.1 (Android)");
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
        try {
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            String text = read(code < 400 ? c.getInputStream() : c.getErrorStream());
            if (code < 400) return new JSONObject(text);
            String detail = detail(text);
            if (code == 401) throw new Unauthorized(detail != null ? detail : "Войдите в облако заново");
            throw new IOException(detail != null ? detail : "Ошибка сервера " + code);
        } finally {
            c.disconnect();
        }
    }

    private static String detail(String text) {
        try {
            JSONObject problem = new JSONObject(text);
            JSONObject errors = problem.optJSONObject("errors");
            if (errors != null && errors.keys().hasNext()) {
                String field = errors.keys().next();
                return field + ": " + errors.getString(field);
            }
            return problem.has("detail") ? problem.getString("detail") : null;
        } catch (JSONException e) {
            return null;
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        try (Scanner s = new Scanner(in, "UTF-8")) {
            return s.useDelimiter("\\A").hasNext() ? s.next() : "";
        }
    }
}
