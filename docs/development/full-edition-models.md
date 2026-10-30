# 公开完整版带哪些模型（2026-10-30 起）

有人反映完整版太大（1.15.6 为 708 MB）。用户同意砍语音模型。公开完整版改用打包器预设「完整发布包」：

```
py -3 -B scripts\packager\cyime_packager.py build --preset 完整发布包 --bump none --no-install --publish --notes <说明>
```

| 模型 | 完整版 | 说明 |
|---|---|---|
| SenseVoice 多语言（228 MB） | 内置 | 中文默认语音模型，停顿出字，整句定稿保留标点、结束时整段再识别一次；日语、英语、粤语、字幕也靠它 |
| Paraformer 中英流式（226 MB） | 不带，应用内下载 | 只负责说话中的预览文字，定稿本来就由 SenseVoice（或 X-ASR）出；下载后没手动选过模型的中文、英文自动改成「边说边出字＋SenseVoice 校正」 |
| X-ASR 整句定稿（136 MB） | 不带，应用内下载 | 和以前一样，装了 Paraformer＋SenseVoice 后可选 |
| 手写 ochwpro、联想 base/small、CAM++ 声纹 | 内置 | 不变 |
| 公开九键词组模型 v3 | 内置 | 不变 |

APK 里 Paraformer 三个文件压缩后占 218.7 MB（含 zip 记录），按 1.15.6 的内容去掉后完整版约 489 MB（原 708 MB）。

为什么留 SenseVoice、不留 Paraformer：「整句定稿」默认开，最终文字本来就是 SenseVoice 整句识别的结果（平板外放 83 段 8.1 % 对现方案 10.1 %，见项目文件 语音评估/整句识别对比-20261030.md）；Paraformer 单独用错字率 15–18 %（docs/development/speech-model-comparison-20261007.md），只留它会明显变差。只留 SenseVoice 失去的是说话中的预览文字，定稿准确度和标点不变。

老人包、朋友包仍按各自要求带全六个模型，不受影响。

未验收：只跑了电脑上的单元测试，没在手机上装过不带 Paraformer 的完整版。
