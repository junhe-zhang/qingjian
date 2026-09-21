# 青笺 QingJian — Microsoft Store 提交资料

状态：软件适配和材料准备中；尚未关联开发者账号，尚未提交商店审核。

## 开发者需要提供的产品身份

注册入口：[Microsoft Store 开发者注册](https://storedeveloper.microsoft.com/)

注册完成后的管理后台：[Partner Center Apps & Games](https://aka.ms/submitwindowsapp)

完成身份验证后，在 Partner Center → Apps & Games → New product → MSIX or PWA app 保留应用名称。建议先尝试「青笺 QingJian」，实际可用性由商店确认。

打开产品的 Product identity 页面，将下面三个字段复制给打包者。这些是包身份信息，不是密码或访问令牌：

- Package/Identity/Name
- Package/Identity/Publisher（完整的 `CN=...`）
- Package/Properties/PublisherDisplayName

正式包必须使用商店分配的真实身份。`LocalPreview` 预览包不能直接提交为正式产品。

## 商店文案

应用名称：青笺 QingJian（以实际保留名称为准）

建议分类：Productivity / 效率

短说明：本地待办、桌面小窗与截止提醒，让重要的事留在眼前。

详细说明：

青笺是一款轻量的 Windows 桌面备忘录。记录待办事项、备注和截止时间，勾选完成后自动划线；也可以将重要事项放进可拖动、可置顶的桌面小窗。

支持按未完成、临近截止、已逾期和已完成筛选，并搜索标题和备注。提醒时间可设为截止时、提前 10 分钟至 7 天，或关闭提醒。提醒出现时可以直接完成事项，或延后 10 分钟。

关闭主窗口后，青笺仍在系统托盘运行。完全退出程序、电脑关机或休眠时无法提醒；恢复运行后会检查尚未提醒的事项。

所有待办保存在本机，无需注册账户，无广告，无分析或跟踪 SDK。当前版本不提供跨设备云同步。

功能要点：

1. 待办、备注、DDL 与完成状态管理。
2. 勾选完成划线，支持恢复。
3. 可拖动、可置顶的桌面小窗。
4. 每条事项独立选择是否显示在桌面。
5. 临近截止和到期提醒，可延后 10 分钟。
6. 本地自动保存，保留上一个保存版本。

搜索关键词建议：待办；备忘录；桌面便签；截止提醒；DDL；todo；QingJian

支持页面：https://junhe-zhang.github.io/qingjian/support.html

隐私说明：https://junhe-zhang.github.io/qingjian/privacy.html

上述网页应在提交前确认已能公开访问。

## 给审核人员的说明

- 无需账号，无登录墙，无付费功能，无广告。
- 主要功能可离线使用。
- `runFullTrust` 用于现有 WPF 桌面窗口、Windows 托盘、桌面小窗和本地文件保存；应用不需要管理员权限。
- 点击「新建待办」，设置几分钟后的截止时间，并选择「截止时提醒」即可验证提醒。
- 关闭主窗口会隐藏到系统托盘；通过托盘菜单「退出」彻底退出。
- 软件无需摄像头、麦克风、定位或联系人权限。
- 桌面小窗可以在主窗口中关闭；不是自启动后台服务。

## 正式打包

```powershell
.\windows\build-msix.ps1 -MakeAppx 'C:\path\to\makeappx.exe' `
  -IdentityName '从商店复制的名称' `
  -Publisher 'CN=从商店复制的完整发布者' `
  -PublisherDisplayName '从商店复制的显示名称'
```

输出：`dist/QingJian-1.2.0.0-x64-store.msix`。商店签名在通过审核并发布时处理。不要把本地测试证书或私钥上传仓库。

## 提交前仍需完成

- 开发者本人完成注册、身份验证与协议接受。
- 保留名称，关联真实包身份并重新生成包。
- 在真实签名/商店测试安装环境中验证安装、更新、卸载、数据保存及桌面提醒。
- 运行 Windows App Certification Kit；当前 MakeAppx 的打包校验不等同于完整商店认证。
- 上传合规截图；填写年龄分级、定价和分发地区。首版免费是建议，最终按开发者选择填写。
- 审阅隐私说明与商店介绍，确认支持渠道。
- 最终提交审核，并等待微软结果。材料准备完成不代表已上架。
