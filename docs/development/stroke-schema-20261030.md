# 笔画键盘随包带 stroke 方案（2026-10-30）

## 问题

笔画键盘（`InputLayout.STROKE`，xime.yaml `keyboard.stroke.schemas: [stroke]`）一直在，但 APK 里没有 `stroke` 方案和码表，方案市场也没有，只有自己导入后才能用。

## 改动

- `app/src/main/assets/rime_chinese/stroke.schema.yaml`：基于 rime/rime-stroke `1e8fff9b` 改编。名称「笔画」；去掉依赖 luna_pinyin 的拼音反查（CyIME 不带 luna_pinyin，笔画键盘也打不出反查前缀）；不再固定每页 9 个候选；`*` 进入编码。
- `lua/cyime_stroke_wildcard.lua`：`*` 代替一笔，最多三个 `*`（125 种组合），只找笔数相同的字。
- `lua/cyime_stroke_rank.lua`：前 200 个候选里，笔画打全的常用字 → 没打完的常用字按字频 → 生僻字。Rime 原本把没打完的字按码长排，`pnhs` 先出 仐 仝 㒰，`合` 在后面。
- `app/build-logic/tasks-stroke.gradle.kts`：构建时从 raw.githubusercontent 下载固定版本 `stroke.dict.yaml`（校验 SHA-256，缓存在 `.gradle/stroke-data/`），按 `app/data/rime_ice_8105_weights.tsv.gz` 加权（多音字各读音相加）。上游靠 essay.txt 排序，CyIME 没有 essay.txt，不加权时所有字同权按码位排（hs 先出 丅 后出 十）。去掉 8105 以外的增补平面字（扩展 B 起、西夏文，约 11.5 万行，安卓字体多显示成方框）和部首、笔画符号、兼容汉字（⼀ ㇀ 与 一 同形）。保留 47,373 行、27,790 个字。
- 安装走现有 `ChineseSchemas.installAssets("rime_chinese")` 与 `rime-bundled-manifest.tsv`（manifest 加了生成目录），不改 Kotlin。方案默认不启用，在输入方式里打开「笔画」即可。
- 方案市场不另加条目：已随包提供，再加一条就是重复。

## 体积

- 生成码表 1,009,117 字节，gzip -9 后 341,753 字节；APK 预计增加约 0.35 MB。
- 设备上部署后 `build/` 增加约 2.7 MB（table 1.28 MB、prism 1.01 MB、reverse 0.42 MB），只在启用笔画后生成。

## 检查

- 云端用 Ubuntu librime 1.10 + librime-lua 实测（与安卓同一份方案、码表、Lua）：
  `h` 一 去 大 下 不；`hs` 十 丁 去 过 下；`szh` 口…；`pssnz` 们；`pnhs` 拿 命 合 杀 盒；`pss` 川 但 们 修 似；`hspn` 木 本 机 相 林；`h*` 二 十 七 丁 厂；`*s` 了 十 丁；`h***z` 打 可 世 充 巧。
- 新增 `StrokeSchemaBundleTest`（方案绑定、字母表含 hspnz 和 *、Lua 模块存在、码表加权）。云端没有 Android SDK，`./gradlew test`、打包和真机均**未运行**，交给 PC 打包线程。
- **未真机验收。**
