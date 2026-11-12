package com.gym.tracker;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

public class AuthActivity extends AppCompatActivity {

    private boolean register;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        register = getIntent().getBooleanExtra("register", false);
        findViewById(R.id.primary).setOnClickListener(v -> submit());
        findViewById(R.id.secondary).setOnClickListener(v -> {
            if (register || answers() != null) {
                register = !register;
                render();
            } else {
                startActivity(new Intent(this, OnboardingActivity.class));
                finish();
            }
        });
        render();
    }

    private void render() {
        boolean answered = answers() != null;
        Ui.text(findViewById(android.R.id.content), R.id.auth_title, register ? "Почти готово!" : "С возвращением");
        Ui.text(findViewById(android.R.id.content), R.id.auth_subtitle, register
                ? "Создайте аккаунт, чтобы сохранить ответы и тренировки"
                : "Войдите, чтобы продолжить тренировки");
        ((MaterialButton) findViewById(R.id.primary)).setText(register ? "Создать аккаунт" : "Войти");
        ((MaterialButton) findViewById(R.id.secondary)).setText(register
                ? "Уже есть аккаунт? Войти"
                : answered ? "Нет аккаунта? Создать" : "Нет аккаунта? Пройти анкету");
        ((TextInputLayout) findViewById(R.id.password_box)).setError(null);
    }

    private void submit() {
        EditText loginField = findViewById(R.id.login);
        EditText passwordField = findViewById(R.id.password);
        String login = loginField.getText().toString().trim().toLowerCase(Locale.ROOT);
        String password = passwordField.getText().toString();
        String error = register ? Auth.register(this, login, password) : Auth.login(this, login, password);
        ((TextInputLayout) findViewById(R.id.password_box)).setError(error);
        if (error != null) return;

        Coach.Person person = answers();
        if (register && person != null) Db.get(this).saveProfile(person);
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    private Coach.Person answers() {
        return (Coach.Person) getIntent().getSerializableExtra("person");
    }
}
