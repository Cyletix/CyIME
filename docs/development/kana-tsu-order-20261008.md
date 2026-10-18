# 日语九宫格「つ」变形顺序

2026-10-08，用户要求第一次变形优先小写促音。唯一生产修改为 `JapaneseKanaInput.kt` 中的循环顺序：`tu → xtu → du → tu`，对应 `つ → っ → づ → つ`。仍由现有 `JapaneseKanaComposer` 替换未提交编码尾部，沿用原来的会话与组词保护。

其他假名循环、方向提示、滑动方向和按键布局保持原实现。片假名候选使用同一组小写编码；大写罗马音输入属于另一条既有路径，本次没有扩大变形适用范围。

## 检查与交付

- 修改现有 `JapaneseKanaComposerTest` 的顺序预期，检查第一次为小写、第二次为浊音、第三次恢复；同步更新已有 `JapaneseInputTest` 的真实引擎预期。
- `JapaneseKanaComposerTest` 9项与 `JapaneseKanaInputTest` 4项通过，日志 `.codex-artifacts/kana-tsu-20261008/unit-check.log`；覆盖尾部替换、已提交/删除/新会话保护、编码存在性及原滑动方向。
- 当前ADB无设备，真实引擎设备用例未运行、未验收。
- 上一份1.9.11 / 20261103正式草稿尚未安装，固定入口已先归档旧草稿，再沿用本批版本重建；构建日志为 `.codex-artifacts/kana-tsu-20261008/release-build.log`。
- `auto.cmd --build-only` 构建、签名、版本及源码校验通过；产物 `app/build/outputs/apk/editions/1.9.11/standard/release/CyIME-1.9.11-arm64-v8a.apk`，111151065字节，SHA256 `62f3ce041927186d91fdf5da0904a7c804d4ab71b64f410240d21dffc2a4c8f2`。构建后复查ADB仍无设备，未安装、未公开发布。
