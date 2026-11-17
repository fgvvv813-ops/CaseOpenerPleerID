package online.vaultx.playeridhelper;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * User-initiated, opt-in foreground log scanner.
 * Scans logcat locally; does not save or transmit raw system logs.
 * Needs READ_LOGS (normally provided through authorized ADB/developer settings).
 */
public final class GameLogScannerService extends Service {
    public static final String GAME_PACKAGE = "com.jakpok.casesimulator";
    public static final String ACTION_STOP = "online.vaultx.playeridhelper.STOP";
    private static final String CHANNEL_ID = "game_id_scanner";
    private static final int ONGOING_NOTIFICATION = 82;
    private static final int RESULT_NOTIFICATION = 83;

    private volatile boolean scanning;
    private volatile java.lang.Process logcatProcess;

    @Override public IBinder onBind(Intent intent) { return null; }

    private SharedPreferences prefs() {
        return getSharedPreferences("finder", Context.MODE_PRIVATE);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            scanning = false;
            if (logcatProcess != null) logcatProcess.destroy();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (scanning) return START_NOT_STICKY;
        if (checkSelfPermission(Manifest.permission.READ_LOGS)
                != PackageManager.PERMISSION_GRANTED) {
            prefs().edit().putString("status", "Доступ к логам не выдан — нужна настройка ADB").apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        scanning = true;
        createChannel();
        startForeground(ONGOING_NOTIFICATION, notification("Идёт поиск Player ID…", true));
        prefs().edit().putString("status", "Поиск запущен — откройте Case Opener")
            .putBoolean("scanning", true).apply();
        Thread worker = new Thread(this::scan, "CaseOpener-LogScanner");
        worker.setDaemon(true);
        worker.start();
        return START_NOT_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(
                CHANNEL_ID, "Поиск Player ID", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    private Notification notification(String message, boolean ongoing) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_menu_search)
                .setContentTitle("CaseOpener ID Finder")
                .setContentText(message)
                .setContentIntent(pi)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing)
                .build();
    }

    private static int findGamePid() {
        java.lang.Process p = null;
        try {
            p = new ProcessBuilder("pidof", GAME_PACKAGE).redirectErrorStream(true).start();
            BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = input.readLine();
            if (line != null && !line.trim().isEmpty()) {
                return Integer.parseInt(line.trim().split("\\s+")[0]);
            }
        } catch (Exception ignored) {
            // Some Android devices hide other apps' PIDs. Fall back to game-tag matches.
        } finally {
            if (p != null) p.destroy();
        }
        return 0;
    }

    private void scan() {
        String endStatus = "Поиск остановлен";
        try {
            // This reads logcat only after the user has explicitly enabled READ_LOGS.
            java.lang.Process p = new ProcessBuilder("logcat", "-b", "main", "-v",
                    "threadtime", "-T", "1").start();
            logcatProcess = p;
            BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
            int gamePid = 0;
            long lastPidCheck = 0;
            String line;
            while (scanning && (line = input.readLine()) != null) {
                long now = SystemClock.elapsedRealtime();
                if (now - lastPidCheck >= 3000) {
                    gamePid = findGamePid();
                    lastPidCheck = now;
                }
                String id = GameLogParser.findCandidate(line, gamePid);
                if (id != null) {
                    prefs().edit()
                            .putString("candidate", id)
                            .putLong("foundAt", System.currentTimeMillis())
                            .putString("status", "Найден возможный Player ID в журнале игры")
                            .apply();
                    NotificationManager nm =
                            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    nm.notify(RESULT_NOTIFICATION, notification("Возможный Player ID найден!", false));
                    endStatus = "Найден возможный Player ID";
                    break;
                }
            }
            if (scanning && !"Найден возможный Player ID".equals(endStatus)) {
                endStatus = "Журнал завершился без найденного Player ID";
            }
        } catch (Exception e) {
            // No raw log lines or exceptions are saved to protect sensitive information.
            endStatus = "Не удалось прочитать журнал: проверьте доступ READ_LOGS";
        } finally {
            scanning = false;
            if (logcatProcess != null) {
                logcatProcess.destroy();
                logcatProcess = null;
            }
            prefs().edit().putBoolean("scanning", false)
                    .putString("status", endStatus).apply();
            stopForeground(true);
            stopSelf();
        }
    }

    @Override public void onDestroy() {
        scanning = false;
        if (logcatProcess != null) logcatProcess.destroy();
        super.onDestroy();
    }
}
