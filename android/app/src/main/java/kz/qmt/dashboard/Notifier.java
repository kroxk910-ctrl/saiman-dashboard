package kz.qmt.dashboard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

final class Notifier {
    static final String CH_EVENTS = "events";
    static final String CH_WATCH = "watch";
    static final String EXTRA_VIEW = "view";
    private static int nextId = 100;

    private Notifier() { }

    static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ev = new NotificationChannel(CH_EVENTS, "Заявки и события", NotificationManager.IMPORTANCE_HIGH);
        ev.setDescription("Новые заявки, смена статуса, реализация");
        nm.createNotificationChannel(ev);
        NotificationChannel w = new NotificationChannel(CH_WATCH, "Работа в фоне", NotificationManager.IMPORTANCE_MIN);
        w.setDescription("Значок, пока дашборд следит за заявками в свёрнутом виде");
        w.setShowBadge(false);
        nm.createNotificationChannel(w);
    }

    static boolean enabled(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm.areNotificationsEnabled();
    }

    static PendingIntent openApp(Context c, String view, int req) {
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (view != null) i.putExtra(EXTRA_VIEW, view);
        return PendingIntent.getActivity(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static synchronized void event(Context c, String title, String body, String view) {
        if (!enabled(c)) return;
        int id = nextId++;
        if (nextId > 190) nextId = 100;
        Notification n = new Notification.Builder(c, CH_EVENTS)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openApp(c, view == null || view.isEmpty() ? "requests" : view, id))
            .build();
        c.getSystemService(NotificationManager.class).notify(id, n);
    }

    static void clearEvents(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        for (int id = 100; id <= 190; id++) nm.cancel(id);
    }

    static void openSettings(Context c) {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, c.getPackageName())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }
}
