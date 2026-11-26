# SyncClipboard 剪贴板同步

安卓端 `clipboard_sync` 插件，对接 [SyncClipboard](https://github.com/Jeric-X/SyncClipboard) 3.1.1 及以上的服务端协议，经自建服务器（官方 Docker 镜像）或 WebDAV 网盘与电脑等设备双向同步文本。电脑端使用 SyncClipboard 官方客户端，不依赖 CyIME Windows，也不限于同一局域网。

## 配置

1. 部署 SyncClipboard 服务端，例如官方镜像 `jericx/syncclipboard-server`；放在公网时经反向代理启用 HTTPS。也可以用支持 WebDAV 的网盘里一个专用文件夹。
2. 电脑安装 SyncClipboard 客户端，填写同一个服务器地址、用户名和密码。
3. 手机从文件安装本插件（`.xipk`），在插件设置填写服务器地址、用户名和密码，在「联网」里授权该域名，再点「测试连接」。
4. 「剪贴板与同步」›「备用：手动连接与其他服务」选择本服务并启用。

## 行为

- 只同步文本；服务器上的图片、文件、文件组跳过，不清空手机剪贴板。
- 手机复制：PUT `/SyncClipboard.json`，`hash` 为文本 UTF-8 字节的 SHA-256（大写十六进制），HTTP Basic 认证。
- 电脑复制：键盘显示期间约每 2 秒 GET `/SyncClipboard.json`。超过 10240 字符的文本，服务器在 JSON 里只放预览，插件再 GET `/file/<dataName>` 取全文并校验 `hash`。
- 宿主确认写入（`acknowledgePull`）后才记住远端 `hash`；自己推上去的内容不会被拉回。
- 服务端不支持 ETag，每次拉取都取完整 JSON。
- 官方服务端按冒号拆分 Basic 凭据，用户名和密码都不要含冒号；插件拒绝含冒号的用户名。
- 地址只接受 http(s) 源加可选路径，拒绝内嵌账号、查询串和片段。HTTP 下密码和剪贴板内容是明文，公网请用 HTTPS。
