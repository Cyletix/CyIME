# 候选拼音开关与输入入口漏接

用户目标：设置开启时26/14/9键均显示候选对应的真实拼音，不因为布局或窄窗口忽然隐藏；候选条、展开页、物理浮条保持同一规则。

## 两个实际原因

1. 旧布尔“显示”仍先受480dp及字号阈值约束，开启并不保证显示。新增自动/显示/隐藏；明确显示不受宽度和字号阈值压制，自动才按实际候选区宽度计算。已有明确true/false迁移为显示/隐藏，无旧值才默认自动。
2. 真机检查仍不显示，追到JNI：之前只在`processKey`开启`_cyime_candidate_comments`，实际打字优化入口`processKeyAndGetResult`直接调用Rime而没有设置。桌面探针只走前者，漏测真实入口。两条路径现在调用同一个设置函数，在解码前保证引擎提供注释；UI开关只控制渲染。

这是“测试走一个入口、产品走另一个入口”的重复错误，不能用已有桌面测试通过替代实际输入验证。

## 检查记录

- 注释显示规则4项单测、OCR映射4项单测通过；菜单4项设备测试通过。
- 组件测试验证窄宽、字号、实时设置、展开候选、物理浮条及旧开关迁移。截图函数首次误用`onRoot`遇到独立编码Popup双根，后改为候选条明确标签；不是删除原断言。
- 新增真实引擎入口回归：26/14/9分别通过旧入口和批量入口输入，每次故意关闭引擎注释选项以防前一个用例掩盖漏接；保存恢复原方案和选项，不选词学习、不修改用户词库。
- 引擎不涉及词库、解码或排序变更，本轮只修主机注释开关与UI显示规则，不重跑全套九键算法语料。

最终设备检查：`AdaptiveCandidateCommentsTest` 4项通过，真实输入入口 `CandidateCommentEntryPointsTest` 1项通过（覆盖三个布局的旧/批量入口），菜单4项通过。九键入口测试首次误用通用按键且未执行九键专用刷新，断言失败；已改成产品实际使用的 `processQueuedT9Key`/`processQueuedT9KeyAndGetResult` 并执行必要刷新后通过，没有放宽拼音断言。结果分别见 `.codex-artifacts/candidate-comments-final-tests.log`、`candidate-comments-entry-tests.log` 与 `candidate-comments-device-tests.log`。

已查看组件渲染截图 `.codex-artifacts/ocr-entry-verification/candidate-comments-show-narrow.png`、`candidate-comments-expanded-show-narrow.png`，确认窄窗口明确显示时拼音实际绘制。它们是组件测试图，不能冒充三个布局在完整输入法中的逐项截图。最终完整输入法人工显示验收仍待完成；设备随后正在被用户操作，本轮未继续抢占屏幕。

## 交付

通过主目录 `auto.cmd` 成功生成轻量签名 Release，保留数据覆盖安装到唯一授权平板 SM-X800。实际安装为 **1.8.0 / 20261080**，无 DEBUGGABLE 标记，设备 base.apk 与产物 SHA256 一致。日志 `.codex-artifacts/ocr-comments-delivery.log`；固定留档 `D:/CyIME-Data/deliveries/1.8.0/20261080/`，APK为88,660,423字节，SHA256 `b41a9da83f9fcb7a3de44e2dadc83e9ad50831f75b91271bea1ec96ee55810a2`。沿用同一未发布批次版本名，未发布 GitHub、未更新手机、未构建内置语音模型包。

本包包含此前键盘内相机流程，但OCR旋转/倾斜/多块文字与暗画面识别质量验收失败，没有替换引擎，不能因构建安装成功标为功能通过。
