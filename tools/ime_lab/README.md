# 输入质量离线工具

Python 标准库，无联网、无账号访问。先运行 `python -m unittest discover -s tools/ime_lab` 和 `python tools/ime_lab/ime_lab.py selftest`。

- `ime_lab.py evaluate cases.json samples.jsonl output.json`：精确输入校验、TopK、类型验证和缺失项；延迟按用例与设备/构建/场景元数据分组。first-query 不等于进程冷启动。不能将引擎+Top100耗时称为端到端延迟。
- `neighbors.alternatives(text, keys)`：使用每个布局实际测得的按键矩形，限定一次替换、有距离惩罚、原输入首位、最多16个候选；当前仅离线实验，不改变正在使用的键盘。


真实引擎采样：`InputQualityProbeTest` 在独立目录部署随包词典，不导入个人 userdb。输出保存在应用 files/ime-lab/samples-*.jsonl，含实际类型、分数、global index及匹配范围。T9-014 没有原始按键，保持缺失。
