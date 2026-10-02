# Windows 剪贴板协议联调

这个本地测试宿主直接编译同级 `CyIME-Windows/settings/CyIME.Settings/ClipboardServer.cs`，
替换的只有 `IClipboardPort`（内存剪贴板）。不启动桌面设置窗口、不注册输入法，
不读取系统剪贴板、不使用真实配对码、不修改防火墙，监听 `127.0.0.1` 随机端口。

不复制 Windows 源码或将其分发到安卓。需要本机已有 .NET 9 SDK / ASP.NET Core shared framework，
无额外 NuGet 依赖。编译和测试按顺序运行：

```powershell
dotnet build tools/clipboard-sync-fixture/ClipboardFixture.csproj -m:1 -p:UseSharedCompilation=false -nodeReuse:false
./gradlew.bat :plugin-core:testDebugUnitTest --tests '*WindowsClipboardInteropTest' --max-workers=1
```

Windows 仓库位置不同可在第一条命令加 `-p:CyimeWindowsRoot=绝对路径`。
测试找不到 fixture DLL 时显式跳过，因此必须检查报告的 skipped 数量，不能把跳过称为通过。
输出仅为本地测试可执行文件，不是输入法 APK、安装包或发布产物。

更多针对性测试：`ClipboardSyncSessionTest`、`LuaWindowsClipboardSyncTest`，以及旧 ximed / WebDAV 协议和 manifest / 注册表回归测试。
