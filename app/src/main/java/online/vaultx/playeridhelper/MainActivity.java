package online.vaultx.playeridhelper;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** A local, privacy-aware logcat Player ID finder. No manual ID input. */
public final class MainActivity extends Activity {
    private static final String ADB_COMMAND =
            "adb shell pm grant online.vaultx.playeridhelper android.permission.READ_LOGS";
    private TextView permissionLabel;
    private TextView statusLabel;
    private TextView idLabel;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refreshUi();
            handler.postDelayed(this, 1000);
        }
    };

    private SharedPreferences prefs() {
        return getSharedPreferences("finder", Context.MODE_PRIVATE);
    }

    private TextView text(LinearLayout parent, String content, int size, int color) {
        TextView t = new TextView(this);
        t.setText(content);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setPadding(0, dp(10), 0, dp(7));
        parent.addView(t);
        return t;
    }

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void button(LinearLayout root, String title, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setOnClickListener(click);
        root.addView(b);
    }

    private boolean canReadLogs() {
        return Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.READ_LOGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void copy(String value, String toast) {
        ClipboardManager c = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        c.setPrimaryClip(ClipData.newPlainText("CaseOpener Player ID", value));
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
    }

    private void refreshUi() {
        boolean granted = canReadLogs();
        permissionLabel.setText(granted
            ? "✅ Разрешение на чтение журналов выдано"
            : "🔒 Android не разрешает читать журналы других приложений без настройки ADB");
        statusLabel.setText("Состояние: " + prefs().getString("status", "Поиск ещё не запускался"));
        String found = prefs().getString("candidate", "");
        long at = prefs().getLong("foundAt", 0);
        if (found.isEmpty()) {
            idLabel.setText("Player ID пока не найден");
        } else {
            String when = at > 0
                ? new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(new Date(at))
                : "";
            idLabel.setText("Возможный Player ID:\n" + found + "\nНайден: " + when);
        }
    }

    private Intent getGameIntent() {
        return getPackageManager().getLaunchIntentForPackage(GameLogScannerService.GAME_PACKAGE);
    }

    private void beginScanAndLaunch() {
        Intent game = getGameIntent();
        if (game == null) {
            Toast.makeText(this, "Оригинальный Case Opener не найден на устройстве",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!canReadLogs()) {
            Toast.makeText(this, "Сначала выдайте READ_LOGS через ADB",
                    Toast.LENGTH_LONG).show();
            refreshUi();
            return;
        }
        Intent scan = new Intent(this, GameLogScannerService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(scan);
        else startService(scan);
        startActivity(game);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(18), dp(20), dp(18), dp(24));
        scroll.addView(root);

        text(root, "🔎 CaseOpener Player ID", 25, Color.rgb(36, 30, 78));
        text(root, "Автоматический поиск ID по журналу оригинальной игры. "
                + "Никакого ручного ввода ID. Игра и её сохранения не изменяются.",
                16, Color.DKGRAY);

        permissionLabel = text(root, "", 15, Color.rgb(100, 68, 12));
        statusLabel = text(root, "", 15, Color.DKGRAY);
        idLabel = text(root, "", 19, Color.rgb(32, 80, 67));

        button(root, "▶ Начать поиск и открыть Case Opener", v -> beginScanAndLaunch());
        button(root, "📋 Скопировать найденный Player ID", v -> {
            String value = prefs().getString("candidate", "");
            if (value.isEmpty()) {
                Toast.makeText(this, "Player ID ещё не обнаружен", Toast.LENGTH_SHORT).show();
                return;
            }
            copy(value, "Player ID скопирован");
        });
        button(root, "⏹ Остановить поиск", v -> {
            Intent stop = new Intent(this, GameLogScannerService.class);
            stop.setAction(GameLogScannerService.ACTION_STOP);
            startService(stop);
            refreshUi();
        });
        button(root, "💬 Перейти в бот VaultX", v -> startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/VaultX_co_BOT?start=playerid"))
        ));

        text(root, "Первоначальная настройка доступа", 19, Color.BLACK);
        text(root, "1. Включите «Для разработчиков» и «Отладка по USB» на своём Android.\n"
                + "2. Подключите телефон к своему компьютеру с ADB.\n"
                + "3. Выполните команду ниже один раз. На Android 13+ также "
                + "подтверждайте системный запрос доступа к журналам.\n"
                + "4. Нажмите «Начать поиск», игра откроется автоматически. "
                + "Поиграйте немного и вернитесь сюда за результатом.",
                15, Color.DKGRAY);
        text(root, ADB_COMMAND, 13, Color.rgb(63, 65, 131));
        button(root, "📋 Скопировать команду ADB", v ->
                copy(ADB_COMMAND, "Команда скопирована"));

        text(root, "Важно: если сама игра не записывает Player ID в журнал, "
                + "Android не позволит получить его таким способом. "
                + "Найденное совпадение является предварительным и должно "
                + "проверяться через VaultX. Никакие логи не отправляются в интернет.",
                14, Color.DKGRAY);
        setContentView(scroll);
        refreshUi();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }
}
