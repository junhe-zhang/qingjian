# 青笺 Android 预览版

功能：待办、DDL、完成划线、搜索筛选、桌面小组件、系统通知提醒和延后 10 分钟。

Android 8.0（API 26）及以上；编译与目标版本为 Android 16（API 36）。不含联网权限、广告或第三方统计 SDK。Windows 与 Android 当前各自本地保存，不提供自动同步。

## 预览与正式版本

- 调试预览包 ID：`io.github.junhezhang.qingjian.debug`。
- 正式应用 ID：`io.github.junhezhang.qingjian`，在正式商店首次上传前应最终确认。
- 预览 APK 使用开发调试签名，只用于体验和验证，不是可提交商店的正式签名版本。
- 正式 AAB/APK 需使用开发者持有并长期备份的签名密钥。密码和密钥不得提交 Git。

## 构建

安装 JDK 17、Android SDK（API 36 与 build-tools 35.0.0），设置 `JAVA_HOME` 和 `ANDROID_HOME`。在 `android/` 目录运行：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug bundleRelease
```

Windows 上请把 Android 源码复制到不含中文或空格的构建目录，例如 `C:\work\QingJianAndroid`，并使用同样只含英文字符的 `GRADLE_USER_HOME`。已观察到 JDK 17 / Gradle 测试进程在中文路径下无法加载测试类。

正式签名读取环境变量：`QINGJIAN_KEYSTORE`、`QINGJIAN_STORE_PASSWORD`、`QINGJIAN_KEY_ALIAS`、`QINGJIAN_KEY_PASSWORD`。未提供时 release 产物保持未签名，不能标记为可上架。

## 使用

首次打开应用后，可通过「提醒设置」允许通知；Android 12 及以上还可允许「闹钟和提醒」来启用精确提醒。未允许精确提醒时会使用普通系统闹钟，并明确显示可能延迟。

点击「桌面组件」添加小组件，或在桌面长按进入系统小组件列表。只显示勾选「在桌面显示」的事项，空间不足时显示部分事项和打开完整清单入口。

Android 的强行停止、厂商后台限制和通知设置可能阻止提醒；强行停止后需重新打开。设备重启后会重建提醒。

## 商店后续

Google Play 或国内安卓应用市场的开发者账号、应用签名、上架地区和主体资质需要开发者确认。国内上架所需的备案、软著或主体材料以目标市场的最新要求为准，本文件不代表已经满足或已经提交。

第一步交付可体验 APK；商店提交使用完成设备验证后的正式签名 AAB/APK。若选择 Google Play，新个人账号可能还需按控制台要求完成测试流程。
