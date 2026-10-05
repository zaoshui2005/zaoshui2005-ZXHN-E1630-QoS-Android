package com.codex.zxhnqos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        int index = intent.getIntExtra("index", -1);
        if (index < 0) index = actionIndex(intent.getAction());
        String upload = intent.getStringExtra("upload");
        if (upload == null || upload.trim().isEmpty()) upload = lookupUpload(context, index);
        RunHistory.triggered(context);
        if (upload == null || upload.trim().isEmpty()) {
            RunHistory.finished(context, false, "定时点没有读到上传带宽（时间点索引=" + index + "），请重新保存该时间点。");
            return;
        }
        RunHistory.started(context, "定时", upload);
        RunHistory.progress(context, "定时已触发，正在启动后台执行器");
        AlarmScheduler.scheduleAll(context);
        Intent run = new Intent(context, AutomationService.class).setAction("com.codex.zxhnqos.RUN_SCHEDULED").putExtra("upload", upload);
        try { context.startForegroundService(run); }
        catch (Exception e) { RunHistory.finished(context, false, "无法启动后台执行器：" + e.getMessage()); }
    }
    private String lookupUpload(Context context, int index) {
        try {
            String saved = context.getSharedPreferences("settings", 0).getString("alarm_upload_" + index, "");
            if (saved != null && !saved.trim().isEmpty()) return saved.trim();
            org.json.JSONArray points = new org.json.JSONArray(context.getSharedPreferences("settings", 0).getString("points", "[]"));
            return index >= 0 && index < points.length() ? points.getJSONObject(index).optString("upload", "") : "";
        } catch (Exception ignored) { return ""; }
    }
    private int actionIndex(String action) {
        if (action == null) return -1;
        try {
            int p = action.lastIndexOf('_');
            return Integer.parseInt(action.substring(p + 1));
        } catch (Exception ignored) { return -1; }
    }
}
