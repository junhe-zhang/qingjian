<p align="center">
  <img src="app-icon.png" width="128" alt="青笺图标：绿色便签、勾选与金色时钟">
</p>

<h1 align="center">青笺 QingJian</h1>

<p align="center">一张青笺，记下待办，也记得截止时间。</p>
<p align="center">Windows 桌面备忘录 · 桌面小窗 · DDL 提醒 · 本地保存</p>

青笺是一款轻量的 Windows 待办应用。把事项、截止时间和完成状态放在一个清单里，也可以挑选几件重要的事，留在桌面上。

![青笺主界面，内容为演示数据](docs/main.png)

## 功能

- **待办管理**：新建、编辑和删除事项，支持备注、搜索与分类筛选。
- **完成划线**：勾选完成，再次点击即可恢复；主窗口与桌面小窗同步。
- **截止时间**：按 DDL 排序，区分 24 小时内截止与已逾期事项。
- **桌面小窗**：每条事项独立选择是否显示；小窗可拖动、调整大小和置顶。
- **截止提醒**：默认提前 1 天，也可选择提前 10 分钟至 7 天、仅截止时提醒或关闭提醒。
- **延后提醒**：弹窗中可以直接完成事项，或延后 10 分钟。
- **托盘运行**：关闭主窗口后继续提醒，从托盘随时打开。
- **本地保存**：数据自动保存，并保留上一个版本的备份。无需账号。

| 桌面小窗 | 截止提醒 |
| --- | --- |
| ![桌面小窗](docs/desktop.png) | ![提醒弹窗](docs/reminder.png) |

以上截图均使用自检生成的演示事项，不包含真实个人数据。

## 开始使用

在 [Releases 下载 Windows 便携版](https://github.com/junhe-zhang/qingjian/releases/latest)，解压到可写入的文件夹，双击 `QingJian.exe`。

适用于安装了 .NET Framework 4.x 的 Windows 10 / 11。便携版不需要安装步骤，也不需要网络连接。完整操作说明见 [使用说明](使用说明.md)。

> 提醒需要程序保持运行。关闭主窗口会留在系统托盘；完全退出、电脑关机或休眠时无法提醒。恢复运行后会检查尚未提醒的事项。

## 从源码构建

使用 Windows 自带的 .NET Framework C# 编译器与 WPF，无需 NuGet 依赖。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
.\QingJian.exe
```

`build.ps1` 默认使用 `%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe`，并将 `app.ico` 嵌入程序。若需从原始 PNG 重新生成图标，可运行 `make-icon.ps1`。

## 自检

```powershell
$test = Start-Process .\QingJian.exe -ArgumentList '--self-test' -PassThru -Wait
$test.ExitCode
Get-Content .\verification\result.txt
```

成功时退出码为 `0`。检查覆盖提醒触发边界、重复抑制、完成与撤销、延后提醒、数据读写与备份、损坏数据处理、表单验证、新建与编辑、筛选与搜索，以及界面渲染。自检会短暂打开窗口，输出保存在 `verification/`，不会读取或修改个人清单。

## 数据存储

- `data/tasks.json`：个人待办与桌面设置。
- `data/tasks.json.bak`：上一次保存的版本。

两个文件均位于程序旁。移动或备份软件时，请先退出，再复制整个文件夹；更新软件时保留 `data/`。数据文件、构建产物和测试输出已通过 `.gitignore` 排除。

## 项目结构

```text
App.cs               WPF 界面、保存、提醒与自检
build.ps1            编译程序
make-icon.ps1        生成多尺寸 Windows 图标
app-icon.png         图标原图
app.ico              嵌入程序的图标
docs/                使用演示数据的界面截图
使用说明.md           操作与备份说明
图标设计说明.md       AI 图标来源、提示词与转换方式
```

图标由内置图像生成工具创作，再转换为多尺寸 Windows ICO；详见 [图标设计说明](图标设计说明.md)。
