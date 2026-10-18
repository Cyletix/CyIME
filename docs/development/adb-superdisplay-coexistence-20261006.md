# ADB 与 SuperDisplay 共存修复（2026-10-06）

用户要求先解决 ADB 互相重启，明确保留 SuperDisplay 的 ADB 连接。没有关闭 SuperDisplay、ADB、AOAP 或 Wi-Fi 连接方式，没有重启电脑。

## 两个实际原因

1. SuperDisplay 1.1.10 的 LocalSystem 自动服务从自身目录启动 ADB 1.0.40，开发 SDK 使用 1.0.41。改 PATH 不会改变服务使用的私有路径；两个客户端反复替换共享的 5037 服务。服务父子进程与命令记录见 `.codex-artifacts/adb-service-commands.json`。
2. 统一版本后仍约每 10 秒重启，不能据此宣布修复。继续捕获发现服务反复运行 `adb forward tcp:1445 tcp:1445`，该命令返回绑定错误 10013。Windows 的 IPv4、IPv6 TCP 动态范围均被设为 1024–15000，WinNAT 生成的 1355–1454 保留范围包含 SuperDisplay 的固定端口 1445。端口没有监听者也不能绑定，重试和重启 ADB 均无效。

## 已应用的持久修改

- 备份 SuperDisplay 私有 `adb.exe`、`AdbWinApi.dll`、`AdbWinUsbApi.dll` 后，复制当前 SDK 的三件套至受保护的安装目录，逐文件校验 SHA256；不把 SYSTEM 服务链接到用户可写目录。两端现在均为协议 1.0.41、SDK 37.0.1-15733141。
- IPv4、IPv6 TCP 动态范围恢复 Windows 默认的 `start=49152, num=16384`，刷新 WinNAT 保留范围。UDP 原为默认值，未修改。操作前核对没有运行的 WSL 发行版／Docker 工作负载。
- 1445 的绑定检查通过；SuperDisplay 与 WinNAT 服务均恢复运行，自动启动类型不变，`DisableADB=0`、`DisableAOAP=0`。平板转发恢复为 `R52T800SKWZ tcp:1445 tcp:1445`。
- 固定入口 `auto.cmd` 在连接 ADB 服务前，检查 SuperDisplay 私有 ADB 协议版本，以及启用该连接时 1445 是否落入 Windows IPv4／IPv6 保留范围。复发时明确阻止本次交付并指出修复入口，不继续触发重启循环。只读设备查询仅对明确的瞬时服务断开重试一次，不重试安装写入或换目标设备。
- 可复用修复入口为 `scripts/repair-adb-conflict.ps1`；默认只核对／同步三件套，`-RepairDynamicPorts` 才修改 TCP 范围。需要改受保护文件时走 Windows 正常 UAC，备份、回滚和回执齐全；文件相同且未要求修复端口时不提权、不重启服务。第三方更新仍可能覆盖私有 ADB，交付入口会再次检查，未承诺能阻止其他软件修改系统。

## 结果与边界

- 两套 ADB 交替执行设备列表、型号和转发查询，共 20 组、80 条命令；全部成功，5037 所属 PID 持续为 46120，1445 转发全程存在。报告：`.codex-artifacts/adb-compat-stability.json`，约 22.2 秒。只统一版本时的失败报告另存 `adb-compat-stability-before-portfix.json`。
- ADB 入口相关 10 项测试通过，覆盖旧协议、IPv4／IPv6 保留端口、边界值、正常端口及有限只读重试；PowerShell 语法解析通过。此前交付脚本全量 61 项测试通过，之后新增检查运行对应专项。
- 修复后的实际轻量包构建和覆盖安装继续用 `auto.cmd`，结果在[语音交付报告](speech-low-volume-20261006.md)记录。构建期间及安装结束再核对同一服务进程和转发。
- 没有查看设备画面，SuperDisplay 投屏画面、触控／笔操作及电脑重启后的现场表现未验收；这里只确认后台服务、ADB 命令和端口转发共存。

## 本次弯路及固定入口修正

- 第一次替换文件时，停止服务并不代表子进程已经退出，复制遇到文件占用，已回滚。入口改为等待服务进程及已识别 ADB 进程实际退出，再复制与校验。
- 统一版本后仍重启，说明先前“只有版本冲突”的解释不完整。保留失败记录，继续追到具体转发命令与 Windows 端口保留，未用无限重试掩盖。
- 刷新 WinNAT 时，`Stop-Service` 等待一个可能被 HNS 立即自动启动而错过的 Stopped 状态，导致修复助手卡住。仅结束对应修复助手，核对 TCP 范围和端口已经恢复，再恢复 SuperDisplay。固定脚本改为 `sc stop` 加有界状态等待；该修正已语法检查，未为测试而再次打断已恢复的 WinNAT。

原始三件套备份：`D:/CyIME-Data/adb-compat/20261006-084640-911/`。TCP 原配置及最终回执：`D:/CyIME-Data/adb-compat/20261006-085610-612/`。仓库回执：`.codex-artifacts/adb-compat-repair.json`、`adb-compat-port-repair.json`。备份未清除。

依据：[SuperDisplay 官方排障](https://superdisplay.app/help/)、[Microsoft TCP 动态端口范围说明](https://learn.microsoft.com/en-us/troubleshoot/windows-client/networking/tcp-ip-port-exhaustion-troubleshooting)、[Microsoft 保留端口与 10013 错误说明](https://learn.microsoft.com/en-us/troubleshoot/windows-server/networking/error-10013-wsaeacces-is-returned)。上述文档说明机制，本机根因以实际命令、失败和前后检查为据。
