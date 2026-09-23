# CyIME 中文方案

中文26键、小鹤双拼及共享词库来自 iDvel/rime-ice（GPL-3.0），固定版本 `9e66b0729083b37d217312294f6d516c8d7234be`。官方文件原样打包，LICENSE/README 和词库原始注释一并保留；构建时校验 SHA-256，安装后无需联网下载。

中文九键沿用 Xime 的原生 librime-t9 适配；中文14键源自本仓库 `docs/schemas_examples/pinyin_14jian.schema.yaml`。两者的词典引用改为 rime_ice，其余键位映射保留。市场同名方案和个人 .custom.yaml 优先，不覆盖用户修改。

来源：https://github.com/iDvel/rime-ice/tree/9e66b0729083b37d217312294f6d516c8d7234be
