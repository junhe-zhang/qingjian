# 同步协议与验证

Windows 与 Android 使用同一组 12 个合并案例：`android/app/src/androidTest/assets/cases.json`。运行 `python sync/make-fixtures.py` 重新生成。

## 协议 v1

WebDAV 目录的固定文件 `qingjian-tasks-v1.json`，UTF-8 JSON：

```json
{"schema":1,"items":[{"id":"11111111111111111111111111111111","title":"示例","notes":"","due":0,"created":1789990000123,"done":false,"lead":60,"deleted":false}]}
```

- ID 为 32 位小写 UUID。时间为 UTC Unix 毫秒，due=0 无 DDL；lead=-1 不提醒，否则为提前分钟数。
- 字段全部必填；最多 10000 项（含删除标记）、4 MiB。标题限 200 字符、备注 4000 字符、lead 上限 525600。
- 删除记录保留 ID、deleted=true，其余字段空或零。删除标记不自动清理。
- 上次成功结果为基线，按事项三方合并。双方不同修改需显式解决；已知云端记录缺失则拒绝合并。
- GET 要求强 ETag，PUT 使用 If-Match 或首次创建的 If-None-Match:*。412 后重新读取与合并，最多 3 次。独立临时文件验证服务是否执行条件写入。
- 本机独立记录删除，覆盖上传成功但响应丢失后再删除的情况。本机清单与基线同时落盘；网络期间本机有修改则保留并提示重试。
- HTTPS，不跟随重定向。HTTP 仅供内部回环测试，Android release 不含明文网络例外。

## 隔离互通测试

仅操作 `verification/`、专用模拟器数据库与内存服务器，不读取个人待办。测试凭据 `test-user:test-password` 无实际效力。

1. 启动 `python sync/mock-webdav.py`，只监听 127.0.0.1:18766。每轮完整测试重启服务，清空内存状态。
2. Windows 构建后运行 `QingJian.exe --sync-test`，检查 `verification/sync-tests/result.txt`。
3. 构建并安装 debug APK 与 androidTest APK，在专用模拟器执行：

```text
adb shell am instrument -w -e syncPhase exchange io.github.junhezhang.qingjian.debug.test/io.github.junhezhang.qingjian.SmokeTest
```

4. Windows 运行 `QingJian.exe --sync-verify`，检查 `verification/sync-tests/verify-result.txt`，确认 Android 完成和新增，并发布删除。
5. Android 再执行上述命令，把 exchange 改为 verify，确认删除到达。
6. 不传 syncPhase 执行 Android 仪器测试，覆盖原有功能、同步配置与截图。检查输出 PASS，不能只看 adb 返回码。

另外运行 Windows `--self-test`、Android `testDebugUnitTest lintDebug`。测试覆盖共享合并案例、凭据加密、数据库升级、删除标记、事务回滚、401、302、无 ETag、不执行条件写入及并发 412 重试。生产服务、真实手机、省电/重启后的长时同步仍需验收，模拟测试不能证明坚果云兼容。
