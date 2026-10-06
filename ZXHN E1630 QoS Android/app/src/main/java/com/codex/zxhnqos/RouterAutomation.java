package com.codex.zxhnqos;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.math.BigDecimal;

/** Shared by the visible manual page and the scheduled service. Main thread only. */
final class RouterAutomation {
    interface Listener {
        void progress(String message);
        void finished(boolean success, String message);
    }
    private static RouterAutomation active;
    private final Context context;
    private final WebView web;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::runStep;
    private String upload, routerUrl, username, password, lastStep = "正在检查 Wi-Fi";
    private boolean stopped = true, loading, evaluating, verifying, awaitingReload;
    private int navigation;
    private long deadline;
    private JSONArray preserved = new JSONArray();

    RouterAutomation(Context context, WebView web, Listener listener) {
        this.context = context.getApplicationContext();
        this.web = web;
        this.listener = listener;
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setOffscreenPreRaster(true);
        web.getSettings().setUseWideViewPort(true);
        web.getSettings().setLoadWithOverviewMode(false);
        web.getSettings().setBuiltInZoomControls(false);
        web.getSettings().setDisplayZoomControls(false);
        web.getSettings().setTextZoom(100);
        web.getSettings().setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
        web.onResume();
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                loading = true;
                if (!stopped) show(verifying ? "正在重新读取路由器设置…" : "正在加载路由器页面…");
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (stopped) return;
                // The router renders a mobile menu when the WebView reports a phone.
                // Force its desktop header/menu before the state machine inspects controls.
                view.evaluateJavascript("(function(){var m=document.querySelector('meta[name=viewport]');if(m)m.setAttribute('content','width=1280, initial-scale=1');var s=document.getElementById('codex-desktop-layout');if(!s){s=document.createElement('style');s.id='codex-desktop-layout';s.textContent='.mobile{display:none!important}.computer{display:block!important}.zte-customer.computer{display:block!important}';document.head.appendChild(s);}return 'desktop';})()", value -> {
                    if (!stopped) { loading = false; schedule(500); }
                });
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request.isForMainFrame() && !sameOrigin(request.getUrl().toString())) {
                    finish(false, "页面跳转到了其他地址，请确认路由器地址。");
                    return true;
                }
                return false;
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && !stopped) finish(false, "打不开路由器：" + error.getDescription());
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && !stopped) finish(false, "路由器页面返回 HTTP " + response.getStatusCode());
            }
        });
    }

    void start(String value) {
        if (active != null && active != this) {
            listener.finished(false, "另一个带宽任务正在执行，请稍后重试。");
            return;
        }
        active = this;
        stopped = false;
        upload = value;
        routerUrl = context.getSharedPreferences("settings", 0).getString("router", "http://router.ctc/").trim();
        username = context.getSharedPreferences("settings", 0).getString("username", "admin");
        password = new SecretStore(context).get("router_password");
        try {
            if (upload == null || !upload.trim().matches("\\d+(?:\\.\\d+)?")) {
                finish(false, "定时任务收到的上传带宽无效（" + String.valueOf(upload) + "）。请打开主界面检查并重新保存该时间点。");
                return;
            }
            upload = new BigDecimal(upload.trim()).stripTrailingZeros().toPlainString();
        } catch (Exception e) {
            finish(false, "上传带宽数值无法解析（" + String.valueOf(upload) + "）。请使用 Mbps 数字，例如 20 或 2.5。");
            return;
        }
        Uri uri;
        try { uri = Uri.parse(routerUrl); }
        catch (Exception e) { finish(false, "路由器地址格式无法解析，请检查主界面的路由器地址。"); return; }
        if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getUserInfo() != null) {
            finish(false, "路由器地址无效：需要填写 http:// 或 https:// 开头且不含用户名密码的地址。");
            return;
        }
        if (password.isEmpty()) {
            finish(false, "没有已保存的路由器密码，请在主界面输入管理密码并保存。");
            return;
        }
        deadline = SystemClock.elapsedRealtime() + 180000;
        waitForWifi();
    }

    private void waitForWifi() {
        if (stopped) return;
        if (SystemClock.elapsedRealtime() > deadline) {
            finish(false, "等待目标 Wi-Fi 超时。请确认连接、定位权限及定位开关。");
            return;
        }
        boolean connected = false;
        String name = "";
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) connected = true;
            }
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wm.getConnectionInfo();
            name = info == null ? "" : info.getSSID();
            if (name == null) name = "";
            name = name.replace("\"", "");
        } catch (Exception ignored) { }
        String expected = context.getSharedPreferences("settings", 0).getString("ssid", "ChinaNet-kydAhx-5G");
        boolean known = !name.isEmpty() && !"<unknown ssid>".equalsIgnoreCase(name) && !"0x".equals(name);
        if (connected && known && !name.equals(expected)) {
            show("当前 Wi-Fi：" + name + "，等待连接 " + expected + "…");
        } else if (connected) {
            show(known ? "已连接目标 Wi-Fi，正在打开路由器…" : "系统未提供 Wi-Fi 名称，正在检查配置的路由器…");
            web.onResume();
            web.loadUrl(routerUrl);
            schedule(1500);
            return;
        } else show("等待 Wi-Fi：" + expected + "…");
        handler.postDelayed(this::waitForWifi, 2000);
    }

    private boolean sameOrigin(String url) {
        try {
            Uri a = Uri.parse(routerUrl), b = Uri.parse(url);
            return a.getScheme().equalsIgnoreCase(b.getScheme()) && a.getHost().equalsIgnoreCase(b.getHost())
                    && port(a) == port(b);
        } catch (Exception ignored) { return false; }
    }
    private int port(Uri u) { return u.getPort() >= 0 ? u.getPort() : ("https".equalsIgnoreCase(u.getScheme()) ? 443 : 80); }
    private void show(String message) { lastStep = message; listener.progress(message); }
    private void finish(boolean success, String message) {
        if (stopped) return;
        stopped = true;
        handler.removeCallbacksAndMessages(null);
        if (active == this) active = null;
        listener.finished(success, message);
    }
    private void schedule(long delay) {
        handler.removeCallbacks(tick);
        if (!stopped && !awaitingReload) handler.postDelayed(tick, delay);
    }
    private void runStep() {
        if (stopped || awaitingReload) return;
        if (SystemClock.elapsedRealtime() > deadline) {
            finish(false, "操作超时，最后一步：" + lastStep);
            return;
        }
        if (loading || evaluating) { schedule(600); return; }
        if (!sameOrigin(web.getUrl())) { finish(false, "当前页面与配置的路由器地址不一致。"); return; }
        evaluating = true;
        web.evaluateJavascript(PageAutomation.script(username, password, upload, navigation, verifying, preserved), value -> {
            if (stopped) return;
            evaluating = false;
            try {
                Object decoded = new JSONTokener(value == null ? "null" : value).nextValue();
                if (!(decoded instanceof String)) {
                    finish(false, "网页脚本未返回结果，请检查 Android System WebView。");
                    return;
                }
                JSONObject result = new JSONObject((String) decoded);
                navigation = result.optInt("navigation", navigation);
                String type = result.optString("state");
                String message = result.optString("message", "等待网页响应…");
                if ("error".equals(type)) { finish(false, message); return; }
                if ("done".equals(type)) { finish(true, "已读取确认并退出路由器：JER-TN10 上传 " + upload + " Mbps。"); return; }
                if ("loggedout".equals(type)) { show(message); schedule(2000); return; }
                if ("logoutdelay".equals(type)) { show(message); schedule(3000); return; }
                show(message);
                if ("submitted".equals(type)) {
                    preserved = result.optJSONArray("preserved");
                    if (preserved == null) preserved = new JSONArray();
                    verifying = true;
                    awaitingReload = true;
                    navigation = 0;
                    handler.removeCallbacks(tick);
                    handler.postDelayed(() -> {
                        if (!stopped) {
                            awaitingReload = false;
                            web.reload();
                            schedule(1500);
                        }
                    }, 4000);
                } else schedule(1200);
            } catch (Exception e) { finish(false, "解析网页结果失败（" + e.getClass().getSimpleName() + "）。"); }
        });
    }
    void close() {
        stopped = true;
        handler.removeCallbacksAndMessages(null);
        web.stopLoading();
        if (active == this) active = null;
    }
}
