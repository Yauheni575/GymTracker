package com.gym.tracker;

import android.content.Context;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class Auth {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String WRONG = "Неверный логин или пароль";

    private Auth() {
    }

    static String user(Context context) {
        return Db.get(context).login();
    }

    static String register(Context context, String login, String password) {
        if (!login.matches("[a-z0-9._-]{3,20}")) return "Логин: 3–20 латинских букв, цифр или символов . _ -";
        if (password.length() < 6) return "Пароль должен быть не короче 6 символов";
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        Db db = Db.get(context);
        long id = db.createUser(login, encode(salt), encode(hash(password, salt)));
        if (id == -1) return "Такой логин уже занят";
        db.startSession(id);
        return null;
    }

    static String login(Context context, String login, String password) {
        Db db = Db.get(context);
        String[] stored = db.credentials(login);
        if (stored == null) return WRONG;
        Base64.Decoder decoder = Base64.getDecoder();
        if (!MessageDigest.isEqual(decoder.decode(stored[2]), hash(password, decoder.decode(stored[1])))) return WRONG;
        db.startSession(Long.parseLong(stored[0]));
        return null;
    }

    static void logout(Context context) {
        Db.get(context).endSession();
    }

    private static byte[] hash(String password, byte[] salt) {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(password.toCharArray(), salt, 50_000, 256))
                    .getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
