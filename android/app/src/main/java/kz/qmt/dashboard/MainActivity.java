package kz.qmt.dashboard;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Дашборд в приложении: сайт открывается во встроенном браузере, плюс уведомления, загрузка файлов и кнопка «Назад». */
public class MainActivity extends Activity {

    private static final int REQ_FILE = 11;
    private static final int REQ_NOTIF = 12;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingView;   // вкладка, которую открыть после нажатия на уведомление

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.parseColor("#0f1214"));
        getWindow().setNavigationBarColor(Color.parseColor("#0f1214"));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0f1214"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage — последние данные и настройки
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);   // звуковой сигнал уведомлений
        s.setUserAgentString(s.getUserAgentString() + " QmtApp/" + BuildConfig.VERSION_NAME);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(this), "QmtApp");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());

        Notifier.ensureChannels(this);
        pendingView = viewFrom(getIntent());
        if (state != null) web.restoreState(state);
        else web.loadUrl(BuildConfig.SITE_URL);
        askNotifications();
    }

    // ---------- уведомления ----------
    void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_NOTIF) {
            web.evaluateJavascript("window.qmtAppChanged && qmtAppChanged()", null);
            WatchService.sync(this);
        }
    }

    private static String viewFrom(Intent i) {
        return i == null ? null : i.getStringExtra(Notifier.EXTRA_VIEW);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String v = viewFrom(intent);
        if (v != null) openTab(v);
    }

    private void openTab(String view) {
        String safe = view.replaceAll("[^a-zA-Z0-9_-]", "");
        web.evaluateJavascript("window.qmtOpenTab && qmtOpenTab('" + safe + "')", null);
    }

    // ---------- жизненный цикл ----------
    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        WatchService.sync(this);       // значок «следит в фоне», если включено
        Notifier.clearEvents(this);
    }

    @Override
    protected void onPause() {
        // web.onPause() не останавливает JS-таймеры: дашборд продолжает обновляться и присылать уведомления,
        // а страница видит, что она свёрнута (document.hidden)
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        WatchService.stop(this);
        web.destroy();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    // ---------- кнопка «Назад»: закрыть окно на сайте → назад по страницам → свернуть ----------
    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.qmtBack && qmtBack()) ? 'y' : 'n'", res -> {
            if (res != null && res.contains("y")) return;
            if (web.canGoBack()) web.goBack();
            else moveTaskToBack(true);   // не закрываем — пусть следит за заявками дальше
        });
    }

    // ---------- загрузка PDF с телефона ----------
    private class Chrome extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = cb;
            try {
                Intent i = params.createIntent();
                i.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(i, "Выберите файл"), REQ_FILE);
                return true;
            } catch (Exception e) {
                fileCallback = null;
                return false;
            }
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        if (code == REQ_FILE) {
            if (fileCallback != null) fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileCallback = null;
            return;
        }
        super.onActivityResult(code, result, data);
    }

    // ---------- ссылки и ошибки сети ----------
    private class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            String site = Uri.parse(BuildConfig.SITE_URL).getHost();
            if (("https".equals(u.getScheme()) || "http".equals(u.getScheme())) && site != null && site.equals(u.getHost())) return false;
            // Telegram, WhatsApp, телефон, почта и другие сайты — во внешних приложениях
            try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (pendingView != null) { openTab(pendingView); pendingView = null; }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
            if (!req.isForMainFrame()) return;
            String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                + "<body style='background:#0f1214;color:#e6e9eb;font-family:sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;height:90vh;text-align:center;padding:16px'>"
                + "<div style='font-size:48px'>📡</div><h2>Нет связи с дашбордом</h2>"
                + "<p style='color:#9aa4ab'>Проверьте интернет и попробуйте ещё раз.</p>"
                + "<button onclick=\"location.href='" + BuildConfig.SITE_URL + "'\" style='margin-top:12px;padding:12px 28px;font-size:16px;border:0;border-radius:10px;background:#4a9eda;color:#0a1620'>Повторить</button>"
                + "</body></html>";
            view.loadDataWithBaseURL(BuildConfig.SITE_URL, html, "text/html", "utf-8", null);
        }
    }

}
