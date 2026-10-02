-- CyIME Windows protocol/clipboard-v1.md. Protocol only; scheduling belongs to the host.
local plugin = {}
local MAX_TEXT = 65536
local MAX_BODY = 262144
local identity, appliedEtag, pendingHash, pendingEtag

local function connection()
    local base = (host.config.get("serverUrl") or ""):match("^%s*(.-)%s*$"):gsub("/+$", "")
    local password = host.config.get("pairingCode") or ""
    -- Accept only an origin, never a path/query/userinfo that could carry credentials.
    local scheme, authority = base:match("^(https?)://([^/]+)$")
    if not scheme or authority:find("[%s@?#]") then
        return nil, "请填写电脑显示的完整地址，如 http://192.168.1.50:18740，不要添加路径"
    end
    if #password < 16 or #password > 128 or password:find("[%c%s]") then
        return nil, "请填写 CyIME Windows 显示的完整配对码"
    end
    local key = base .. "\n" .. password
    if key ~= identity then
        identity, appliedEtag, pendingHash, pendingEtag = key, nil, nil, nil
    end
    return {identity = key, url = base .. "/api/clipboard", headers = {
        Authorization = "Basic " .. host.crypto.base64("cyime:" .. password),
        Accept = "application/json",
    }}
end

local function stillConfigured(c)
    local current = connection()
    if not current or current.identity ~= c.identity then error("连接配置已更改，请重试") end
end

local function validProfile(p)
    if type(p) ~= "table" or p.type ~= "text" or p.has_data ~= false or p.data_name ~= nil then return false end
    if type(p.text) ~= "string" or #p.text == 0 or p.text:match("^%s*$") or p.text:find("%z") then return false end
    local bytes = host.bin.utf8(p.text)
    if #bytes > MAX_TEXT or p.size ~= #bytes or p.hash ~= host.crypto.hex(host.crypto.sha256(bytes)) then return false end
    return p.source == nil or (type(p.source) == "string" and #p.source <= 128)
end

local function responseError(resp)
    if not resp then return "无法连接电脑，请检查地址、电脑同步开关和局域网连接" end
    if resp.status == 401 then return "配对码不正确，请重新填写电脑显示的配对码" end
    if resp.status == 403 then return "电脑端未允许这个同步方向，请检查发送/接收设置" end
    if resp.status == 409 then return "电脑剪贴板刚发生变化，将稍后重试最新手机内容" end
    if resp.status == 503 then return "电脑剪贴板正在使用中，将稍后重试" end
    if resp.status == 413 then return "文本超过 64 KiB，未同步" end
    return "同步失败（HTTP " .. resp.status .. "）"
end

local function etag(resp)
    for k, v in pairs(resp.headers or {}) do
        if k:lower() == "etag" then return v end
    end
end

local function readProfile(resp)
    if type(resp.text) ~= "string" or #resp.text > MAX_BODY then return nil end
    local ok, profile = pcall(host.json.decode, resp.text)
    if ok and validProfile(profile) then return profile end
end

function plugin.getSettingsSchema()
    return {
        {key = "serverUrl", label = "电脑地址", type = "text", required = true,
         placeholder = "http://192.168.1.50:18740",
         helpText = "在 CyIME Windows 的手机互联中开启可信局域网连接，填写那里显示的地址。当前 HTTP 协议未加密，仅用于可信局域网。"},
        {key = "pairingCode", label = "配对码", type = "secret", required = true,
         helpText = "填写电脑显示的配对码；用户名自动使用 cyime。保存后测试连接，再打开同步开关。"},
        {key = "testConnection", label = "测试电脑连接", type = "button"},
    }
end

function plugin.onLoad()
    if not host.bin or not host.bin.utf8 then error("请先更新支持 Windows 同步的 CyIME 宿主") end
    return true
end
function plugin.onUnload() identity, appliedEtag, pendingHash, pendingEtag = nil, nil, nil, nil end

function plugin.push(profile)
    if not validProfile(profile) then error("只同步不超过 64 KiB 的有效文本") end
    local c, reason = connection()
    if not c then error(reason) end
    c.headers["Content-Type"] = "application/json"
    local body = host.bin.utf8(host.json.encode(profile))
    if #body > MAX_BODY then error("文本编码后超过请求大小限制") end
    local resp = host.http.request("PUT", c.url, c.headers, body, 3000)
    stillConfigured(c)
    if not resp or resp.status ~= 200 then error(responseError(resp)) end
    local received = readProfile(resp)
    if not received or received.hash ~= profile.hash then error("电脑未确认接收到相同文本") end
    appliedEtag, pendingHash, pendingEtag = etag(resp), nil, nil
    return true
end

function plugin.pull()
    local c, reason = connection()
    if not c then error(reason) end
    if appliedEtag then c.headers["If-None-Match"] = appliedEtag end
    local resp = host.http.request("GET", c.url, c.headers, nil, 3000)
    stillConfigured(c)
    if resp and resp.status == 304 then return nil end
    if resp and resp.status == 204 then
        appliedEtag, pendingHash, pendingEtag = nil, nil, nil
        return nil -- Empty/image clipboard must never clear the phone clipboard.
    end
    if not resp or resp.status ~= 200 then error(responseError(resp)) end
    local profile = readProfile(resp)
    if not profile then error("电脑返回的剪贴板文本校验失败") end
    pendingHash, pendingEtag = profile.hash, etag(resp)
    return profile
end

-- An HTTP read alone is not a delivered clipboard item. Cache only after host acceptance.
function plugin.acknowledgePull(hash)
    connection() -- Drop the pending ETag if pairing changed after GET.
    if hash == pendingHash then
        appliedEtag, pendingHash, pendingEtag = pendingEtag, nil, nil
    end
end

function plugin.testConnection()
    local c, reason = connection()
    if not c then return reason end
    local resp = host.http.request("GET", c.url, c.headers, nil, 3000)
    local current = connection()
    if not current or current.identity ~= c.identity then return "连接配置已更改，请重新测试" end
    if resp and resp.status == 204 then return nil end
    if resp and resp.status == 200 then
        if readProfile(resp) then return nil end
        return "该地址没有返回有效的 CyIME Windows 剪贴板数据"
    end
    return responseError(resp)
end

return plugin
