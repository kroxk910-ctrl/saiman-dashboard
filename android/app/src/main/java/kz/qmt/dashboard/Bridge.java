package kz.qmt.dashboard;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.webkit.JavascriptInterface;

/** То, что сайт может вызвать в приложении: window.QmtApp.* */
public class Bridge {
    private final MainActivity act;

    Bridge(MainActivity act) { this.act = act; }

    /** Уведомление в шторке. view — какую вкладку открыть по нажатию. */
    @JavascriptInterface
    public void notify(String title, String body, String view) {
        Notifier.event(act, title, body, view);
    }

    /** "granted" / "denied" / "default" — как Notification.permission в браузере. */
    @JavascriptInterface
    public String notifState() {
        if (!Notifier.enabled(act)) return Build.VERSION.SDK_INT >= 33
            && act.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            && !act.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) ? "default" : "denied";
        return "granted";
    }

    @JavascriptInterface
    public void requestNotif() {
        String st = notifState();
        act.runOnUiThread(() -> {
            if ("default".equals(st)) act.askNotifications();
            else Notifier.openSettings(act);   // уже запрещено — включить можно только в настройках телефона
        });
    }

    /** Следить за заявками, когда приложение свёрнуто (значок в шторке). */
    @JavascriptInterface
    public boolean bgEnabled() { return WatchService.enabled(act); }

    @JavascriptInterface
    public void setBg(boolean on) {
        WatchService.setEnabled(act, on);
        act.runOnUiThread(() -> WatchService.sync(act));
    }

    @JavascriptInterface
    public String version() { return BuildConfig.VERSION_NAME; }
}
