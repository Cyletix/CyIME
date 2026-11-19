# AI 助手（内置）

工具栏／菜单里的「AI 助手」打开一个面板：左边输入，右边显示回复。和「AI 回复」「AI 改写」插件不同，
这一份跑在宿主里，不用装插件；接口地址、密钥、模型都在设置 › AI 助手里填。

## 能做什么

- **左边输入、右边输出**：输入框沿用工具面板那条按键路由，中文拼音照常走 Rime，候选上屏进输入框；
  回车或点「发送」把问题发出去，回复在右边边生成边显示。
- **模型切换**：面板顶部中间的胶囊显示当前模型，点开选择；没拉过列表时点它先向接口要一次。
- **配置**：右上角齿轮进设置 › AI 助手，填接口地址、密钥、模型，也可以「获取模型」「测试连接」。
- **网页版**：右上角地球图标打开 DeepSeek 网页版，登录后直接在网页里提问，回来后点「取最新回复」，
  最新一条回复会进右侧输出区；没有 API 密钥时也能用这条路。

## 接口约定

只支持 OpenAI 兼容接口：`GET {base}/models` 列模型，`POST {base}/chat/completions` 流式对话（`stream: true`）。
预设了 DeepSeek、OpenAI、硅基流动、智谱 GLM、月之暗面；其他服务选「自定义」后自己填地址。

## 数据与隐私

- 密钥用 Android Keystore 加密后存进单独的设置文件（`ai_assistant`），**不进备份**（换设备解不开）。
- 「同步码」是给换设备用的：一行 JSON，含地址、密钥、模型和模型列表。**只在自己设备之间复制**，别发到群里。
- 面板不带任何统计上报；请求只发往你填的接口地址，以及你主动打开的 DeepSeek 网页版。
- 网页版那条路走的是 WebView 里的登录会话，宿主只读取页面里最新一条回复的文本，不注入脚本、
  不转发 Cookie、不额外发请求。

## 相关代码

- 逻辑与协议：`app/src/main/java/com/kingzcheung/xime/ai/`（`AiConfig`、`AiClient`、`AiChatProtocol`、`AiAssistant`）
- 面板：`app/src/main/java/com/kingzcheung/xime/ui/keyboard/AiAssistantPanel.kt`
- 设置页：`app/src/main/java/com/kingzcheung/xime/ui/settings/AiAssistantSettingsScreen.kt`
- 网页版：`app/src/main/java/com/kingzcheung/xime/ui/ai/DeepSeekWebActivity.kt`
