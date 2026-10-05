package com.codex.zxhnqos;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.webkit.WebView;
import android.view.View;

public class AutomationService extends Service {
    private static final String CHANNEL = "qos_automation";
    private WebView web;
    private RouterAutomation engine;
    @Override public void onCreate() {
        super.onCreate(); createChannel();
        startForeground(1001, notification("正在准备定时执行…"));
        web = new WebView(this);
        web.onResume();
        web.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        web.layout(0, 0, 1280, 1920);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String upload = intent == null ? null : intent.getStringExtra("upload");
        if (engine != null) engine.close();
        engine = new RouterAutomation(this, web, new RouterAutomation.Listener() {
            @Override public void progress(String message) { RunHistory.progress(AutomationService.this, message); update(message); }
            @Override public void finished(boolean success, String message) { RunHistory.finished(AutomationService.this, success, message); update((success ? "完成：" : "失败：") + message); stopSelf(); }
        });
        engine.start(upload);
        return START_NOT_STICKY;
    }
    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CHANNEL, "QoS 定时执行", NotificationManager.IMPORTANCE_LOW));
    }
    private Notification notification(String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("ZXHN E1630 QoS").setContentText(text).setOngoing(true).build();
    }
    private void update(String text) { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(1001, notification(text)); }
    @Override public void onDestroy() { if (engine != null) engine.close(); if (web != null) web.destroy(); stopForeground(true); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
