package com.codex.zxhnqos;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AutomationActivity extends Activity {
    private WebView web;
    private TextView status;
    private RouterAutomation engine;
    private String upload;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        upload = getIntent().getStringExtra("upload");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(this);
        status.setPadding(16, 12, 16, 12);
        layout.addView(status);
        LinearLayout buttons = new LinearLayout(this);
        Button retry = new Button(this);
        retry.setText("重新执行");
        retry.setOnClickListener(v -> startRun());
        buttons.addView(retry);
        Button wifi = new Button(this);
        wifi.setText("Wi-Fi 设置");
        wifi.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        buttons.addView(wifi);
        layout.addView(buttons);
        web = new WebView(this);
        layout.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
        startRun();
    }

    private void startRun() {
        if (engine != null) engine.close();
        RunHistory.started(this, "手动", upload);
        engine = new RouterAutomation(this, web, new RouterAutomation.Listener() {
            @Override public void progress(String message) {
                status.setText(message + "\n目标：JER-TN10 上传 " + upload + " Mbps");
                RunHistory.progress(AutomationActivity.this, message);
            }
            @Override public void finished(boolean success, String message) {
                status.setText((success ? "执行完成：" : "执行停止：") + message);
                RunHistory.finished(AutomationActivity.this, success, message);
                if (success) new android.os.Handler().postDelayed(AutomationActivity.this::finish, 1200);
            }
        });
        engine.start(upload);
    }

    @Override protected void onDestroy() {
        if (engine != null) engine.close();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
