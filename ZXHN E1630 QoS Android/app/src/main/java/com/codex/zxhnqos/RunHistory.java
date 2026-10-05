package com.codex.zxhnqos;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.DateFormat;
import java.util.Date;

final class RunHistory {
    static void started(Context c, String source, String upload) {
        c.getSharedPreferences("runtime", 0).edit().putLong("run_at", System.currentTimeMillis())
                .putString("source", source).putString("upload", upload)
                .putString("result", "执行中").putString("step", "正在启动执行器").apply();
    }
    static void triggered(Context c) {
        c.getSharedPreferences("runtime", 0).edit().putLong("alarm_at", System.currentTimeMillis()).remove("alarm_error").apply();
    }
    static void scheduleError(Context c, String message) {
        c.getSharedPreferences("runtime", 0).edit().putString("alarm_error", message).apply();
    }
    static void scheduled(Context c, long next, int count) {
        c.getSharedPreferences("runtime", 0).edit().putLong("next_alarm_at", next).putInt("scheduled_count", count).apply();
    }
    static void progress(Context c, String message) {
        c.getSharedPreferences("runtime", 0).edit().putString("step", message).apply();
    }
    static void finished(Context c, boolean success, String message) {
        c.getSharedPreferences("runtime", 0).edit().putString("result", success ? "成功" : "失败")
                .putString("step", message).apply();
    }
    static String summary(Context c) {
        SharedPreferences p = c.getSharedPreferences("runtime", 0);
        long trigger = p.getLong("alarm_at", 0), run = p.getLong("run_at", 0), next = p.getLong("next_alarm_at", 0);
        String s = trigger == 0 ? "尚未收到定时触发" : "最近定时触发：" + format(trigger);
        int count = p.getInt("scheduled_count", 0);
        s += "\n已安排定时点：" + count + (next == 0 ? "" : "，下次：" + format(next));
        if (run != 0) s += "\n最近执行：" + format(run) + "（" + p.getString("source", "") + "） "
                + p.getString("upload", "") + " Mbps\n" + p.getString("result", "") + "：" + p.getString("step", "");
        String error = p.getString("alarm_error", "");
        if (!error.isEmpty()) s += "\n定时启动失败：" + error;
        return s;
    }
    static String format(long time) { return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(time)); }
}
