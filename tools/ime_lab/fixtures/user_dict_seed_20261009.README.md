# 预置个人词库快照（速度门禁用）

`user_dict_seed_20261009.userdb.txt` 是 Rime userdb 备份格式（`code<TAB>text<TAB>c=.. d=.. t=..`）的 `rime_ice` 个人词库，共 3000 条，供 `t9_replay.py run --user-dict` 的“预置个人词库”场景使用。SHA256 `ca2d8f0006c0f010eeed2d1d0674ae56621671ae3c2ca9dee39a2f387ac33ccf`。

由 `python tools/ime_lab/make_user_dict_seed.py --output <新文件>` 生成，默认种子 20261009，相同输入逐字节可复现。词条只取自项目词库 `rime_ice/cn_dicts` 的 `8105.dict.yaml`（单字）和 `base.dict.yaml`（2–7 字词），文件头记录两者 SHA256；按词频比例无放回抽样，每个词取权重最高的读音。没有读取任何测试语料、夹具或回放结果。

组成：单字 300、两字 1500、三字 600、四字 450、五至七字 150。单字和长词视为整条上屏（c ≥ 1），两至四字词 60% 为上屏、40% 为句中组成词（c = 0，每次 +0.1）。使用次数为长尾分布，最后使用距今约 Exp(4000) tick，此前间隔约 Exp(600) tick，当前 tick 30000；`d` 按 librime 的衰减公式累计。

与评测集只有自然重叠，未据此筛选：原聊天语料中 22 个多字词条出现在句内（其中“你呢、真的、知道了”等 5 条恰为整段），日常短句集 60 个、用户反馈用例 4 个（我也、现在、也不、这样），均无整句。

回放时，运行器在部署后、创建会话前用 librime 自身的 `UserDictManager::Restore` 合并快照，并核对恢复条数；`run.json` 记录 `user_state: preset_user_dict` 和快照哈希，比较工具拒绝与空词库或其他快照的结果混比。回放不提交、不学习。
