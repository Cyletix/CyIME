-- SyncClipboard (github.com/Jeric-X/SyncClipboard) 3.1.1+ protocol, text only.
-- GET/PUT <url>/SyncClipboard.json with HTTP Basic auth; text over 10240 chars keeps a preview
-- in the JSON and the whole string in <url>/file/<dataName>. Hashes are SHA-256 of the UTF-8
-- text, uppercase on the wire. Protocol only; scheduling and retries belong to the host.
local plugin = {}
local MAX_TEXT = 1048576
local MAX_BODY = 4194304
local TIMEOUT = 8000
local identity, appliedHash, pendingHash

local function trim(s) return ((s or ""):match("^%s*(.-)%s*$")) end

local function connection()
    local base = (trim(host.config.get("serverUrl")):gsub("/+$", ""))
    local user = trim(host.config.get("username"))
    local password = host.config.get("password") or ""
    -- Origin plus an optional WebDAV folder; never userinfo/query/fragment that could carry credentials.
    local authority, path = base:match("^https?://([^/]+)(.*)$")
    if not authority or authority:find("[%s@?#]") or path:find("[%s?#]") then
        return nil, "请填写服务器地址，如 https://clip.example.com，或 WebDAV 专用文件夹地址"
    end
    if user == "" or user:find("[:%c]") then return nil, "请填写用户名（不能含冒号）" end
    if password == "" or password:find("%c") then return nil, "请填写密码" end
    local key = base .. "\n" .. user .. "\n" .. password
    if key ~= identity then identity, appliedHash, pendingHash = key, nil, nil end
    return {identity = key, base = base, headers = {
        Authorization = "Basic " .. host.crypto.base64(host.bin.utf8(user .. ":" .. password)),
        Accept = "application/json",
    }}
end

local function stillConfigured(c)
    local current = connection()
    if not current or current.identity ~= c.identity then error("连接配置已更改，请重试") end
end

local function failure(resp)
    if not resp then return "无法连接服务器：" .. (host.http.lastError() or "请检查地址和网络") end
    if resp.status == 401 then return "用户名或密码不正确" end
    if resp.status == 404 then return "服务器上没有 SyncClipboard.json：请检查地址；WebDAV 需先从电脑同步一次" end
    if resp.status == 413 then return "文本过大，服务器拒收" end
    return "同步失败（HTTP " .. resp.status .. "）"
end

local function sha256Upper(bytes) return host.crypto.hex(host.crypto.sha256(bytes)):upper() end

-- The official server answers camelCase; tolerate PascalCase files written by other tools.
local function field(t, name)
    local v = t[name]
    if v == nil then v = t[name:sub(1, 1):upper() .. name:sub(2)] end
    return v
end

local function request(c, method, path, body)
    local resp = host.http.request(method, c.base .. path, c.headers, body, TIMEOUT)
    stillConfigured(c)
    return resp
end

-- Whole text of a long item, or nil when it exceeds what this plugin syncs.
local function transferText(c, p)
    local name = field(p, "dataName")
    if type(name) ~= "string" or name == "" or name:find("[/\\]") or name == "." or name == ".." then
        error("服务器返回的长文本文件名无效")
    end
    local encoded = name:gsub("[^%w%-%._~]", function(ch) return string.format("%%%02X", ch:byte()) end)
    local resp = request(c, "GET", "/file/" .. encoded)
    if not resp or resp.status ~= 200 then error(failure(resp)) end
    if #resp.body > MAX_TEXT + 3 then return nil end
    return (resp.text:gsub("^\239\187\191", "")) -- Drop a UTF-8 BOM; the hash covers the text only.
end

function plugin.getSettingsSchema()
    return {
        {key = "serverUrl", label = "服务器地址", type = "text", required = true,
         placeholder = "https://clip.example.com",
         helpText = "SyncClipboard 服务端地址；用 WebDAV 网盘时填一个专用文件夹。公网请用 https，否则密码和剪贴板内容是明文。"},
        {key = "username", label = "用户名", type = "text", required = true},
        {key = "password", label = "密码", type = "secret", required = true,
         helpText = "与电脑端 SyncClipboard 填的相同。保存后测试连接，再到「剪贴板与同步」开启同步。"},
        {key = "testConnection", label = "测试连接", type = "button"},
    }
end

function plugin.onLoad()
    if not host.bin or not host.bin.utf8 then error("请先更新 CyIME") end
    return true
end

function plugin.onUnload() identity, appliedHash, pendingHash = nil, nil, nil end

function plugin.push(profile)
    if type(profile) ~= "table" or profile.type ~= "text" or profile.has_data
        or type(profile.text) ~= "string" or profile.text == "" then
        error("只同步文本")
    end
    local c, reason = connection()
    if not c then error(reason) end
    local hash = sha256Upper(host.bin.utf8(profile.text))
    local body = host.bin.utf8(host.json.encode({type = "Text", hash = hash, text = profile.text, hasData = false}))
    if #body > MAX_BODY then error("文本过大，未同步") end
    c.headers["Content-Type"] = "application/json"
    local resp = request(c, "PUT", "/SyncClipboard.json", body)
    if not resp or resp.status < 200 or resp.status >= 300 then error(failure(resp)) end
    appliedHash, pendingHash = hash, nil -- The server now holds this text: do not pull it back.
    return true
end

function plugin.pull()
    local c, reason = connection()
    if not c then error(reason) end
    local resp = request(c, "GET", "/SyncClipboard.json")
    if resp and resp.status == 404 then return nil end -- WebDAV folder with nothing synced yet.
    if not resp or resp.status ~= 200 then error(failure(resp)) end
    if #resp.body > MAX_BODY then error("服务器响应过大") end
    local ok, p = pcall(host.json.decode, resp.text)
    if not ok or type(p) ~= "table" then error("服务器返回的不是 SyncClipboard 数据") end
    local text = field(p, "text")
    -- Images, files and groups are not synced; an empty text is the server's initial state.
    if field(p, "type") ~= "Text" or type(text) ~= "string" or text == "" then return nil end
    local remote = field(p, "hash")
    remote = type(remote) == "string" and remote:upper() or ""
    if remote ~= "" and remote == appliedHash then return nil end
    if field(p, "hasData") == true then
        text = transferText(c, p)
        if not text then appliedHash = remote; return nil end -- Too large: skip this item once.
    end
    local bytes = host.bin.utf8(text)
    local hash = sha256Upper(bytes)
    if remote ~= "" and hash ~= remote then error("服务器上的剪贴板文本校验失败") end
    if hash == appliedHash then return nil end
    pendingHash = hash
    return {type = "text", hash = hash:lower(), text = text, has_data = false, size = #bytes}
end

-- A read alone is not a delivered item; remember it only after the host accepted it.
function plugin.acknowledgePull(hash)
    connection() -- Drops the pending hash if the configuration changed after GET.
    if pendingHash and type(hash) == "string" and hash:upper() == pendingHash then
        appliedHash, pendingHash = pendingHash, nil
    end
end

function plugin.testConnection()
    local c, reason = connection()
    if not c then return reason end
    local resp = host.http.request("GET", c.base .. "/SyncClipboard.json", c.headers, nil, TIMEOUT)
    local current = connection()
    if not current or current.identity ~= c.identity then return "连接配置已更改，请重新测试" end
    if not resp or resp.status ~= 200 then return failure(resp) end
    local ok, p = pcall(host.json.decode, resp.text)
    if ok and type(p) == "table" and field(p, "type") ~= nil then return nil end
    return "该地址没有返回 SyncClipboard 数据，请检查地址"
end

return plugin
