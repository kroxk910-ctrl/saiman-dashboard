package kz.qmt.dashboard;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Пока приложение свёрнуто, телефон может «заморозить» его — и дашборд перестанет проверять заявки.
 * Эта служба держит приложение живым (тихий значок в шторке). Смахнули приложение — служба тоже останавливается.
 */
public class WatchService extends Service {
    private static final String PREFS = "app";
    private static final String KEY = "bg_watch";

    static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY, true);
    }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY, on).apply();
    }

    /** Запускает или останавливает службу по настройке. Вызывать, когда приложение на экране. */
    static void sync(Context c) {
        if (enabled(c)) {
            try { c.startForegroundService(new Intent(c, WatchService.class)); } catch (Exception ignored) { }
        } else stop(c);
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, WatchService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = new Notification.Builder(this, Notifier.CH_WATCH)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Следит за заявками")
            .setOngoing(true)
            .setContentIntent(Notifier.openApp(this, null, 1))
            .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, n);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
