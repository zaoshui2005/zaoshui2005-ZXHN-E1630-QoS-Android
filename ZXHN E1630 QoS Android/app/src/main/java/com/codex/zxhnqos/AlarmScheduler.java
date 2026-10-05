package com.codex.zxhnqos;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;

final class AlarmScheduler {
    private static final int MAX_POINTS = 64;
    static void scheduleAll(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        for (int i = 0; i < MAX_POINTS; i++) alarms.cancel(pending(context, i, "com.codex.zxhnqos.ALARM_" + i));
        JSONArray points;
        try { points = new JSONArray(context.getSharedPreferences("settings", 0).getString("points", "[]")); }
        catch (Exception ignored) { return; }
        long now = System.currentTimeMillis();
        long nextAlarm = Long.MAX_VALUE;
        int scheduledCount = 0;
        android.content.SharedPreferences.Editor saved = context.getSharedPreferences("settings", 0).edit();
        for (int i = 0; i < points.length(); i++) {
            try {
                JSONObject point = points.getJSONObject(i);
                if (!point.optBoolean("enabled", true)) continue;
                String upload = point.getString("upload").trim();
                if (upload.isEmpty()) throw new IllegalArgumentException("上传带宽为空");
                saved.putString("alarm_upload_" + i, upload);
                String[] hm = point.getString("time").split(":", 2);
                Calendar when = Calendar.getInstance();
                when.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0]));
                when.set(Calendar.MINUTE, Integer.parseInt(hm[1]));
                when.set(Calendar.SECOND, 0); when.set(Calendar.MILLISECOND, 0);
                if (when.getTimeInMillis() <= now) when.add(Calendar.DAY_OF_YEAR, 1);
                Intent intent = new Intent(context, AlarmReceiver.class)
                        .setAction("com.codex.zxhnqos.ALARM_" + i)
                        .putExtra("index", i).putExtra("upload", upload);
                PendingIntent operation = pending(context, i, intent);
                if (Build.VERSION.SDK_INT >= 31 && alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when.getTimeInMillis(), operation);
                else if (Build.VERSION.SDK_INT >= 23) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when.getTimeInMillis(), operation);
                else alarms.set(AlarmManager.RTC_WAKEUP, when.getTimeInMillis(), operation);
                scheduledCount++;
                if (when.getTimeInMillis() < nextAlarm) nextAlarm = when.getTimeInMillis();
            } catch (Exception e) { RunHistory.scheduleError(context, "时间点 " + i + " 未能安排：" + e.getMessage()); }
        }
        saved.apply();
        RunHistory.scheduled(context, nextAlarm == Long.MAX_VALUE ? 0 : nextAlarm, scheduledCount);
    }
    private static PendingIntent pending(Context c, int index, String action) {
        Intent intent = new Intent(c, AlarmReceiver.class).setAction(action);
        return pending(c, index, intent);
    }
    private static PendingIntent pending(Context c, int index, Intent intent) {
        return PendingIntent.getBroadcast(c, index, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
