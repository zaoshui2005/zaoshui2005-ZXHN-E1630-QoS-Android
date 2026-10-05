package com.codex.zxhnqos;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private final Pattern TIME = Pattern.compile("(?:[01]\\d|2[0-3]):[0-5]\\d");
    private final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");
    private LinearLayout rows; private EditText ssid, wifiPassword, routerUrl, username, password; private TextView status;
    private JSONArray points; private SecretStore secrets;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); secrets = new SecretStore(this); points = loadPoints(); build(); requestPermissionsIfNeeded(); AlarmScheduler.scheduleAll(this);
    }
    private JSONArray loadPoints() {
        String raw = getSharedPreferences("settings", 0).getString("points", null);
        if (raw != null) try { return new JSONArray(raw); } catch (Exception ignored) { }
        return new JSONArray().put(point("08:00", "10", true));
    }
    private JSONObject point(String time, String upload, boolean enabled) { JSONObject p = new JSONObject(); try { p.put("time", time); p.put("upload", upload); p.put("enabled", enabled); } catch (Exception ignored) { } return p; }
    private void build() {
        ScrollView scroll = new ScrollView(this); LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28, 24, 28, 24); scroll.addView(root);
        TextView title = new TextView(this); title.setText("ZXHN E1630 QoS 定时限速  v1.5（电脑模式）"); title.setTextSize(22); root.addView(title);
        root.addView(label("Wi-Fi 设置")); ssid = field("目标 SSID", getSharedPreferences("settings", 0).getString("ssid", "ChinaNet-kydAhx-5G")); root.addView(ssid);
        wifiPassword = field("Wi-Fi 密码（用于系统连接请求）", secrets.get("wifi_password")); wifiPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); root.addView(wifiPassword);
        routerUrl = field("路由器地址", getSharedPreferences("settings", 0).getString("router", "http://router.ctc/")); root.addView(routerUrl);
        root.addView(label("路由器登录")); username = field("用户名", getSharedPreferences("settings", 0).getString("username", "admin")); root.addView(username);
        password = field("密码", secrets.get("router_password")); password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); root.addView(password);
        LinearLayout settingsButtons = new LinearLayout(this); Button save = button("保存设置", v -> saveSettings()); Button connect = button("请求连接目标 Wi-Fi", v -> requestWifi()); Button alarm = button("允许定时执行", v -> requestAlarmPermission()); settingsButtons.addView(save); settingsButtons.addView(connect); settingsButtons.addView(alarm); root.addView(settingsButtons);
        root.addView(label("上传带宽时间点（Mbps）")); rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); root.addView(rows); Button add = button("新增时间点", v -> editPoint(-1)); root.addView(add);
        Button run = button("立即执行选中/输入带宽", v -> chooseManual()); root.addView(run); status = new TextView(this); status.setText(RunHistory.summary(this)); root.addView(status); setContentView(scroll); renderRows();
    }
    private TextView label(String text) { TextView v = new TextView(this); v.setText(text); v.setTextSize(17); v.setPadding(0, 20, 0, 8); return v; }
    private EditText field(String hint, String value) { EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setSingleLine(); return e; }
    private Button button(String text, View.OnClickListener listener) { Button b = new Button(this); b.setText(text); b.setOnClickListener(listener); return b; }
    private void renderRows() {
        rows.removeAllViews();
        for (int i = 0; i < points.length(); i++) { final int index = i; try { JSONObject p = points.getJSONObject(i); LinearLayout row = new LinearLayout(this); row.setPadding(0, 6, 0, 6); TextView t = new TextView(this); t.setText((p.optBoolean("enabled", true) ? "启用  " : "停用  ") + p.getString("time") + "  " + p.getString("upload") + " Mbps"); t.setTextSize(16); row.addView(t, new LinearLayout.LayoutParams(0, -2, 1)); row.addView(button("修改", v -> editPoint(index))); row.addView(button("立即执行", v -> launchAutomation(p.optString("upload")))); row.addView(button("删除", v -> { points.remove(index); persistPoints(); renderRows(); })); rows.addView(row); } catch (Exception ignored) { } }
    }
    private void editPoint(int index) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(30, 5, 30, 0); EditText time = field("时间 HH:MM", index >= 0 ? points.optJSONObject(index).optString("time") : "08:00"); EditText upload = field("上传 Mbps", index >= 0 ? points.optJSONObject(index).optString("upload") : "10"); CheckBox enabled = new CheckBox(this); enabled.setText("启用"); enabled.setChecked(index < 0 || points.optJSONObject(index).optBoolean("enabled", true)); box.addView(time); box.addView(upload); box.addView(enabled);
        new android.app.AlertDialog.Builder(this)
                .setTitle(index < 0 ? "新增时间点" : "修改时间点")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String pointTime = time.getText().toString().trim();
                    String pointUpload = upload.getText().toString().trim();
                    if (!TIME.matcher(pointTime).matches()
                            || !NUMBER.matcher(pointUpload).matches()) {
                        toast("时间或 Mbps 数值格式错误");
                        return;
                    }
                    JSONObject p = point(pointTime, pointUpload, enabled.isChecked());
                    try {
                        if (index < 0) {
                            points.put(p);
                        } else {
                            points.put(index, p);
                        }
                    } catch (org.json.JSONException e) {
                        toast("保存时间点失败: " + e.getMessage());
                        return;
                    }
                    persistPoints();
                    renderRows();
                })
                .show();
    }
    private void persistPoints() { getSharedPreferences("settings", 0).edit().putString("points", points.toString()).apply(); AlarmScheduler.scheduleAll(this); status.setText("计划已保存\n" + RunHistory.summary(this)); }
    private void saveSettings() { getSharedPreferences("settings", 0).edit().putString("ssid", ssid.getText().toString().trim()).putString("router", routerUrl.getText().toString().trim()).putString("username", username.getText().toString().trim()).apply(); try { secrets.put("wifi_password", wifiPassword.getText().toString()); secrets.put("router_password", password.getText().toString()); toast("设置已保存"); } catch (Exception e) { toast("安全保存失败: " + e.getMessage()); } }
    private void chooseManual() { final EditText input = field("上传 Mbps", "10"); new android.app.AlertDialog.Builder(this).setTitle("立即执行").setView(input).setNegativeButton("取消", null).setPositiveButton("执行", (d, w) -> { if (NUMBER.matcher(input.getText().toString().trim()).matches()) launchAutomation(input.getText().toString().trim()); else toast("请输入数字 Mbps"); }).show(); }
    private void launchAutomation(String upload) { saveSettings(); startActivity(new Intent(this, AutomationActivity.class).putExtra("upload", upload)); }
    private void requestWifi() {
        saveSettings(); if (Build.VERSION.SDK_INT < 29) { toast("请在系统 Wi-Fi 设置中连接目标网络"); return; }
        WifiNetworkSuggestion suggestion = new WifiNetworkSuggestion.Builder().setSsid(ssid.getText().toString().trim()).setWpa2Passphrase(wifiPassword.getText().toString()).build(); int result = ((WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE)).addNetworkSuggestions(java.util.Collections.singletonList(suggestion)); status.setText("已向 Android 请求连接 Wi-Fi，系统可能显示授权提示，返回码=" + result + "\n");
    }
    private void requestAlarmPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            android.app.AlarmManager alarms = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarms != null && !alarms.canScheduleExactAlarms()) {
                try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))); return; }
                catch (Exception ignored) { }
            }
        }
        AlarmScheduler.scheduleAll(this);
        status.setText("定时权限已检查\n" + RunHistory.summary(this));
    }
    @Override protected void onResume() { super.onResume(); AlarmScheduler.scheduleAll(this); if (status != null) status.setText(RunHistory.summary(this)); }
    private void requestPermissionsIfNeeded() { ArrayList<String> p = new ArrayList<>(); if (Build.VERSION.SDK_INT >= 33) { p.add(Manifest.permission.NEARBY_WIFI_DEVICES); p.add(Manifest.permission.POST_NOTIFICATIONS); } if (Build.VERSION.SDK_INT >= 23) p.add(Manifest.permission.ACCESS_FINE_LOCATION); if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), 42); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
