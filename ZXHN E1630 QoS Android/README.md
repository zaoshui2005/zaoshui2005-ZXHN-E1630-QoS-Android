# ZXHN E1630 QoS Android

这是 Android APK 工程，面向 Nova 7 Pro/EMUI。功能包括：

- 配置多个每日时间点和上传 Mbps；
- 手动立即执行；
- Android 开机后重新注册定时任务；
- 通过 `WifiNetworkSuggestion` 请求连接目标 Wi-Fi；
- 使用 WebView 自动登录 `router.ctc`、进入 QoS、修改 `JER-TN10` 的上传最大带宽；
- 路由器密码使用 Android Keystore 加密保存。

## 构建

电脑需要 Android Studio、Android SDK 35 和 JDK 17。用 Android Studio 打开本目录，等待 Gradle 同步，然后运行 `Build > Build APK(s)`。

当前电脑没有 Android SDK/Android Studio，所以这里还没有生成已签名 APK。安装工具链后即可构建。

Workflow 会直接把合法的 Android SDK 包传给 `setup-android`，避免安装已经废弃的 `tools` 包。

## 用 GitHub Actions 在线编译

工程内已经包含 `.github/workflows/android.yml`。操作步骤：

1. 在 GitHub 新建一个 **Private** 仓库。
2. 上传本目录中的全部内容，使 `settings.gradle` 位于仓库根目录。
3. 打开仓库的 `Actions` 页面，选择 `Build Android APK`。
4. 点击 `Run workflow`，选择 `main`，再次点击运行。
5. 等待任务完成，打开该次运行页面，在 `Artifacts` 下载 `zxhn-e1630-qos-debug-apk`。

不要上传 Windows 版本目录中的路由器配置、日志或浏览器会话；云端只需要本 Android 工程。Debug APK 安装到手机后，还需要在家中连接目标 Wi-Fi 才能访问 `router.ctc`。

## 手机端限制

Android 10 及以上不允许普通应用静默打开 Wi-Fi 或强制切换网络。应用会请求系统连接授权；第一次连接可能需要用户确认。华为手机还需要在“应用启动管理”中允许自启动，并关闭该应用的电池优化，否则后台定时可能被系统清理。

定时触发会打开自动操作页面；如果系统阻止后台启动 Activity，需点击通知进入执行页面。这是 Android 的后台启动限制，不能由普通 APK 绕过。
