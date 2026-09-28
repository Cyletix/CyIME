# 输入质量离线工具

Python 标准库，无联网、无账号访问。先运行 `python -m unittest discover -s tools/ime_lab` 和 `python tools/ime_lab/ime_lab.py selftest`。

- `ime_lab.py evaluate cases.json samples.jsonl output.json`：精确输入校验、TopK、类型验证和缺失项；延迟按用例与设备/构建/场景元数据分组。first-query 不等于进程冷启动。不能将引擎+Top100耗时称为端到端延迟。
- `ime_lab.py prior messages.jsonl private-prior.json`：仅 `is_self: true, kind: text`，稳定会话/消息 ID 去重，排除转发/引用，不跨消息或已移除的链接/号码边界生成 n-gram。产物是字符计数，不是语言模型，尚未加载进输入法。
- `inspect_backup.py backup.bak inventory.json`：只读哈希/ZIP目录/数据库头，不解压、恢复或修改账户。
- `neighbors.alternatives(text, keys)`：使用每个布局实际测得的按键矩形，限定一次替换、有距离惩罚、原输入首位、最多16个候选；当前仅离线实验，不改变正在使用的键盘。

私有聊天、数据库、sender-ID 映射、统计产物放在仓库与 OneDrive 外。本轮指定备份的检查报告放在 D:/QQ-Transfer/cyime-private-analysis。字数/次数筛选不是匿名化。

真实引擎采样：`InputQualityProbeTest` 在独立目录部署随包词典，不导入个人 userdb。输出保存在应用 files/ime-lab/samples-*.jsonl，含实际类型、分数、global index及匹配范围。T9-014 没有原始按键，保持缺失。
