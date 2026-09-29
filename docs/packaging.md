# 打包与安装

当前正式版本为 **1.2.9 / 20261021**（本次按明确指定版本发布），版本以 `app/build.gradle.kts` 为唯一来源。

版本规则：本地小改增加末位（PATCH）；正式对外发包增加中间位（MINOR），末位保留。例如 1.0.5 → 1.1.5（正式）→ 1.1.6（开发）→ 1.2.6（正式）。versionCode 独立递增。

开发修改仍需提供 Debug 测试包；“不发正式版”只表示不生成或发布正式 Release，不表示跳过 Debug。用户已授权每次开发验证后自动通过 ADB 覆盖安装到唯一连接的设备；无设备时交付包，多设备时先明确目标。不卸载、不清数据。

```powershell
# 正式包（默认）
./scripts/build-apk.ps1
# 调试包，文件名带 -debug，避免误认为正式包
./scripts/build-apk.ps1 -BuildType Debug
```

脚本会把当前构建类型的旧包、另一类型的旧版本包移入
`app/build/outputs/apk/archive/<时间>/<类型>/`，不会删除历史包，也不运行 clean。
构建失败会报错，不会把旧包当成新包。成功后打印实际绝对路径，并生成 `latest.json`（版本、类型、路径、SHA256）。
正式包在 `app/build/outputs/apk/release/`，调试包在 `app/build/outputs/apk/debug/`。
直接执行 Gradle 不会归档旧包，本地发包统一使用上述脚本。

```powershell
# 安装当前 Debug 包（自动识别版本、设备架构和完整文件名）
./scripts/install-debug.ps1
# 正式包安装
./scripts/install-release.ps1
```

安装脚本只选择当前源码版本的对应类型安装包，自动识别设备架构，拒绝旧包或多设备歧义。
有 `app/keystore.properties` 时，Debug 与 Release 沿用同一发布签名与应用 ID。
没有正式密钥的 Debug 是开发构建，不能覆盖正式版；不可通过卸载绕过签名冲突。
`-Action uninstall` 是显式卸载，会删除应用数据，正常更新不要使用。