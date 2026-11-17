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
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Standalone Player ID finder. Default route: Android's own bugreport UI
 * and offline ZIP/TXT scan. Optional legacy live-log mode requires READ_LOGS.
 */
public final class MainActivity extends Activity {
    private static final int PICK_REPORT = 1307;
    private static final String ADB_COMMAND =
            "adb shell pm grant online.vaultx.playeridhelper android.permission.READ_LOGS";
    private TextView statusLabel, idLabel, permissionLabel;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refreshUi();
            handler.postDelayed(this, 1000);
        }
    };
    private volatile boolean reportBusy;

    private SharedPreferences prefs() {
        return getSharedPreferences("finder", Context.MODE_PRIVATE);
    }
    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }
    private TextView text(LinearLayout root, String content, int size, int color) {
        TextView t = new TextView(this);
        t.setText(content);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setPadding(0, dp(10), 0, dp(9));
        root.addView(t);
        return t;
    }
    private void button(LinearLayout root, String title, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setOnClickListener(onClick);
        root.addView(b);
    }
    private boolean canReadLogs() {
        return checkSelfPermission(Manifest.permission.READ_LOGS)
                == PackageManager.PERMISSION_GRANTED;
    }
    private void copy(String value) {
        ClipboardManager manager =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("Player ID", value));
        Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show();
    }
    private void refreshUi() {
        if (statusLabel == null) return;
        statusLabel.setText(prefs().getString("status",
                "Нажми «Открыть игру», затем создай отчёт Android."));
        String found = prefs().getString("candidate", "");
        long time = prefs().getLong("foundAt", 0);
        if (found.isEmpty()) {
            idLabel.setText("🔍 Player ID ещё не найден");
        } else {
            String when = time > 0 ?
                    new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                            .format(new Date(time)) : "";
            idLabel.setText("Возможный Player ID:\n" + found
                    + "\n\n" + when + "\nНе забудь подтвердить ID через VaultX.");
        }
        permissionLabel.setText(canReadLogs()
                ? "Дополнительный поиск в реальном времени доступен"
                : "Поиск в реальном времени недоступен без расширенного разрешения."
                        + " Основной поиск через отчёт Android работает без ADB.");
    }
    private void startGame() {
        Intent game = getPackageManager().getLaunchIntentForPackage(
                GameLogScannerService.GAME_PACKAGE);
        if (game == null) {
            Toast.makeText(this, "Оригинальная игра не найдена на устройстве",
                    Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(game);
    }
    private void openDeveloperSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }
    private void pickReport() {
        if (reportBusy) return;
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.setType("*/*");
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/zip", "application/x-zip-compressed",
                        "text/plain", "application/octet-stream"});
        try {
            startActivityForResult(pick, PICK_REPORT);
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть системный выбор файла",
                    Toast.LENGTH_LONG).show();
        }
    }
    private void scanReport(Uri uri) {
        if (uri == null || reportBusy) return;
        reportBusy = true;
        prefs().edit().putString("candidate", "").putLong("foundAt", 0)
                .putString("status", "🔎 Проверяю выбранный отчёт на Player ID…").apply();
        refreshUi();
        new Thread(() -> {
            BugReportScanner.Result result = BugReportScanner.read(getContentResolver(), uri);
            handler.post(() -> {
                SharedPreferences.Editor editor = prefs().edit()
                        .putString("status", result.message);
                if (result.candidate != null) {
                    editor.putString("candidate", result.candidate)
                            .putLong("foundAt", System.currentTimeMillis());
                }
                editor.apply();
                reportBusy = false;
                refreshUi();
            });
        }, "BugReport-PlayerId-Scanner").start();
    }
    private void handleShare(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri source = null;
            try {
                source = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            } catch (Exception ignored) {}
            if (source != null) scanReport(source);
        }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(28));
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root);
        text(root, "🔎 CaseOpener Player ID", 25, Color.rgb(40, 32, 85));
        text(root, "Поиск настоящего Player ID по диагностическим сообщениям игры. "
                + "Приложение не изменяет Case Opener и не требует ввода ID вручную.",
                16, Color.DKGRAY);
        idLabel = text(root, "🔍 Player ID ещё не найден", 19, Color.rgb(10, 90, 55));
        statusLabel = text(root, "", 15, Color.DKGRAY);
        button(root, "📋 Скопировать найденный Player ID", v -> {
            String id = prefs().getString("candidate", "");
            if (id.isEmpty()) {
                Toast.makeText(this, "Сначала нужно найти ID", Toast.LENGTH_SHORT).show();
            } else copy(id);
        });
        text(root, "Без ПК и ADB (основной способ)", 19, Color.rgb(42, 35, 88));
        text(root, "1. Открой оригинальную игру и зайди в профиль.\n"
                + "2. В настройках Android открой «Для разработчиков» → «Создать отчёт об ошибках» "
                + "(полный отчёт).\n"
                + "3. Когда отчёт будет готов, поделись ZIP с нашим приложением или выбери его здесь. "
                + "Мы найдём первый подходящий Player ID и покажем его.",
                15, Color.DKGRAY);
        button(root, "🎮 1. Открыть Case Opener", v -> startGame());
        button(root, "⚙️ 2. Открыть настройки разработчика", v -> openDeveloperSettings());
        button(root, "📂 3. Найти Player ID в отчёте ZIP/TXT", v -> pickReport());
        text(root, "Дополнительный поиск (если разрешён доступ к логам)", 17, Color.BLACK);
        permissionLabel = text(root, "", 14, Color.DKGRAY);
        button(root, "▶ Запустить поиск в реальном времени", v -> {
            if (!canReadLogs()) {
                Toast.makeText(this, "Для прямого чтения чужих логов Android требует особое разрешение. "
                                + "Используй поиск по отчёту выше.", Toast.LENGTH_LONG).show();
                return;
            }
            Intent scan = new Intent(this, GameLogScannerService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(scan);
            else startService(scan);
            startGame();
        });
        button(root, "⏹ Остановить прямой поиск", v -> {
            Intent stop = new Intent(this, GameLogScannerService.class);
            stop.setAction(GameLogScannerService.ACTION_STOP);
            startService(stop);
        });
        button(root, "💜 Перейти в Telegram-бот VaultX", v -> {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://t.me/VaultX_co_BOT?start=playerid")));
        });
        text(root, "Важно: Android не разрешает обычному приложению автоматически "
                + "считывать закрытые логи другой игры. Отчёт создаётся самим Android по "
                + "действию пользователя. Если Case Opener не пишет Player ID в журнал, "
                + "найти его по логам нельзя. Отчёты не отправляются на сервер.",
                13, Color.DKGRAY);
        setContentView(scroll);
        refreshUi();
        handleShare(getIntent());
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_REPORT && resultCode == RESULT_OK && data != null)
            scanReport(data.getData());
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShare(intent);
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
