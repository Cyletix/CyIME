-- 在线语音插件（整段上传）
--
-- 宿主录音 16 kHz 单声道 16 位小端 PCM，经 processAudioChunk 灌进来；stop 时拼成 WAV 上传，
-- 结果用 host.asr.emitFinal 交回。失败用 host.asr.emitError 报告，
-- 宿主（capabilities.speech.inputMode: batch）会用本地模型把这段录音再识别一次。
--
-- 两种接口，按地址区分：
--   OpenAI 兼容：地址到 /v1 为止（也接受填到 /audio/transcriptions），multipart 上传 file
--   腾讯 TokenHub：地址是 …/asrproxy/sync_transcribe，JSON 上传 {model, data = WAV 的 Base64,
--     voice_encode_format = "wav"}，文字在 output.text（腾讯云文档 TokenHub「Hy 同步语音识别」）

local plugin = {}

local KEY_API_KEY = "apiKey"
local KEY_BASE_URL = "baseUrl"
local KEY_MODEL = "model"

local DEFAULT_BASE_URL = "https://api.openai.com/v1"
local DEFAULT_MODEL = "whisper-1"
local SAMPLE_RATE = 16000
local TIMEOUT_MS = 30000

local chunks = {}
local language = ""

local function trim(s)
  return (s:gsub("^%s+", ""):gsub("%s+$", ""))
end

local function configOr(key, default)
  local v = host.config.get(key)
  if v == nil or trim(v) == "" then return default end
  return trim(v)
end

local function le16(n)
  return string.char(n % 256, math.floor(n / 256) % 256)
end

local function le32(n)
  return string.char(n % 256, math.floor(n / 256) % 256,
    math.floor(n / 65536) % 256, math.floor(n / 16777216) % 256)
end

local function wav(pcm)
  return "RIFF" .. le32(36 + #pcm) .. "WAVE"
    .. "fmt " .. le32(16) .. le16(1) .. le16(1)
    .. le32(SAMPLE_RATE) .. le32(SAMPLE_RATE * 2) .. le16(2) .. le16(16)
    .. "data" .. le32(#pcm) .. pcm
end

-- 按字节截断，不切开 UTF-8 多字节字符
local function shorten(s, maxBytes)
  s = trim((s:gsub("%s+", " ")))
  if #s <= maxBytes then return s end
  local cut = maxBytes
  while cut > 0 do
    local b = s:byte(cut + 1)
    if b == nil or b < 0x80 or b >= 0xC0 then break end
    cut = cut - 1
  end
  return s:sub(1, cut) .. "…"
end

local function decodeJson(text)
  local ok, data = pcall(host.json.decode, text or "")
  if ok and type(data) == "table" then return data end
  return nil
end

-- 接口自己给的出错原因（OpenAI：error.message；腾讯：message / msg / Response.Error.Message）
local function serverMessage(data, text)
  if data ~= nil then
    local e = data.error
    if type(e) == "table" then e = e.message or e.msg or e.code end
    local tencentCloud = type(data.Response) == "table" and type(data.Response.Error) == "table" and data.Response.Error.Message or nil
    -- 中间可能是 nil，不能用 ipairs
    local candidates = { e, data.message, data.msg, data.Message, data.error_msg, data.err_msg, data.detail, tencentCloud }
    for i = 1, 8 do
      local m = candidates[i]
      if type(m) == "string" and trim(m) ~= "" then return shorten(m, 120) end
    end
    return nil
  end
  -- 不是 JSON：短的纯文字才显示，网页之类不显示
  if type(text) == "string" and trim(text) ~= "" and not text:find("<", 1, true) then return shorten(text, 120) end
  return nil
end

local function httpErrorText(status, detail)
  local base
  if status == 401 or status == 403 then base = "密钥不对或没有权限（" .. status .. "）"
  elseif status == 404 then base = "接口地址或模型名不对（404）"
  elseif status == 413 then base = "这段录音太长（413）"
  elseif status == 429 then base = "请求太多或余额不足（429）"
  elseif status == 400 then base = "接口不认这个请求（400）"
  else base = "接口返回 " .. tostring(status) end
  if detail ~= nil then return base .. "：" .. detail end
  return base
end

-- 从返回里找识别出的文字：先找 text / transcript，再进 data / output / result 之类的外层，
-- 句子列表按顺序拼起来。找不到返回 nil（空字符串算找到：一秒静音就该是空）。
local TEXT_KEYS = { "text", "transcript", "Text", "Transcript" }
local WRAPPER_KEYS = { "data", "output", "result", "Result", "Data", "Response", "results", "sentences", "segments" }

local function joinPieces(pieces)
  local out = ""
  for _, p in ipairs(pieces) do
    if out ~= "" and p ~= "" and out:sub(-1):match("[%w%p]") and p:sub(1, 1):match("%w") then
      out = out .. " " .. p
    else
      out = out .. p
    end
  end
  return out
end

local function extractText(v, depth)
  if depth > 6 then return nil end
  if type(v) ~= "table" then return nil end
  if #v > 0 then
    local pieces = {}
    for _, item in ipairs(v) do
      local t
      if type(item) == "string" then t = item else t = extractText(item, depth + 1) end
      if t ~= nil then pieces[#pieces + 1] = trim(t) end
    end
    if #pieces == 0 then return nil end
    return joinPieces(pieces)
  end
  for _, k in ipairs(TEXT_KEYS) do
    if type(v[k]) == "string" then return v[k] end
  end
  for _, k in ipairs(WRAPPER_KEYS) do
    local t = extractText(v[k], depth + 1)
    if t ~= nil then return t end
  end
  -- 有的接口把文字直接放在 result 字符串里
  if type(v.result) == "string" then return v.result end
  if type(v.Result) == "string" then return v.Result end
  return nil
end

-- HTTP 200 但 code 不为 0、带 error、或 status 为 failed 也算失败
local function businessError(data)
  local code = data.code
  local failed = code ~= nil and code ~= 0 and code ~= "0" and code ~= 200 and code ~= "200"
  if data.error ~= nil or data.status == "failed" then failed = true end
  if type(data.Response) == "table" and data.Response.Error ~= nil then failed = true end
  if not failed then return nil end
  local detail = serverMessage(data, nil)
  local head = "接口报错"
  if code ~= nil then head = head .. "（" .. tostring(code) .. "）" end
  if detail ~= nil then return head .. "：" .. detail end
  return head
end

-- 地址 → 请求地址和接口种类
local function isTokenHub(url)
  return url:find("/asrproxy/", 1, true) ~= nil or url:find("sync_transcribe$") ~= nil
end

local function endpoint(baseUrl)
  local url = baseUrl:gsub("/+$", "")
  if isTokenHub(url) then return url, "tokenhub" end
  if url:find("/audio/transcriptions$") then return url, "openai" end
  return url .. "/audio/transcriptions", "openai"
end

-- 腾讯 TokenHub（Hy / WAND 同步语音识别）：JSON，音频放 data（Base64），文字在 output.text。
-- 语种不传 source，交给后端自动检测（中英混说、方言都靠它认）
local function tokenHubRequest(url, apiKey, model, pcm)
  local body = host.json.encode({ model = model, data = host.crypto.base64(wav(pcm)), voice_encode_format = "wav" })
  local headers = {
    ["Content-Type"] = "application/json",
    ["Authorization"] = "Bearer " .. apiKey,
  }
  return host.http.request("POST", url, headers, body, TIMEOUT_MS)
end

-- OpenAI 兼容：multipart，音频放 file
local function openAiRequest(url, apiKey, model, pcm, lang)
  local boundary = "----CyIMEVoice" .. tostring(math.random(100000000, 999999999))
  local function field(name, value)
    return "--" .. boundary .. "\r\nContent-Disposition: form-data; name=\"" .. name .. "\"\r\n\r\n"
      .. value .. "\r\n"
  end
  local parts = {
    field("model", model),
    field("response_format", "json"),
  }
  if lang ~= nil and lang ~= "" and lang ~= "und" then parts[#parts + 1] = field("language", lang) end
  parts[#parts + 1] = "--" .. boundary .. "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n"
    .. "Content-Type: audio/wav\r\n\r\n" .. wav(pcm) .. "\r\n--" .. boundary .. "--\r\n"
  local headers = {
    ["Content-Type"] = "multipart/form-data; boundary=" .. boundary,
    ["Authorization"] = "Bearer " .. apiKey,
  }
  return host.http.request("POST", url, headers, table.concat(parts), TIMEOUT_MS)
end

-- 返回 text, err
local function transcribe(pcm, lang)
  local apiKey = trim(host.config.get(KEY_API_KEY) or "")
  if apiKey == "" then return nil, "还没填 API 密钥" end
  local url, kind = endpoint(configOr(KEY_BASE_URL, DEFAULT_BASE_URL))
  local model = configOr(KEY_MODEL, DEFAULT_MODEL)
  local resp
  if kind == "tokenhub" then
    resp = tokenHubRequest(url, apiKey, model, pcm)
  else
    resp = openAiRequest(url, apiKey, model, pcm, lang)
  end
  if resp == nil then return nil, "连不上接口：" .. (host.http.lastError() or "未知错误") end
  local data = decodeJson(resp.text)
  if resp.status ~= 200 then
    local err = httpErrorText(resp.status, serverMessage(data, resp.text))
    host.logError("在线语音 " .. err .. ": " .. tostring(resp.text))
    return nil, err
  end
  if data == nil then return nil, "接口返回的格式看不懂" end
  local text = extractText(data, 0)
  -- 腾讯：一秒静音识别完可能连 output.text 都不给，任务完成就当空结果
  if text == nil and data.status == "completed" and businessError(data) == nil then text = "" end
  if text == nil then
    local err = businessError(data) or "接口返回的格式看不懂"
    host.logError("在线语音 " .. err .. ": " .. tostring(resp.text))
    return nil, err
  end
  return trim(text), nil
end

-- ================= 设置 =================

function plugin.getSettingsSchema()
  -- 默认地址也写进配置：设置页点「保存」时宿主才会给配置里的域名联网授权
  if trim(host.config.get(KEY_BASE_URL) or "") == "" then host.config.set(KEY_BASE_URL, DEFAULT_BASE_URL) end
  if trim(host.config.get(KEY_MODEL) or "") == "" then host.config.set(KEY_MODEL, DEFAULT_MODEL) end
  return {
    { key = KEY_API_KEY, label = "API 密钥", type = "secret", placeholder = "sk-…", required = true,
      helpText = "只存在这台手机里，不进备份，只发给下面的接口地址" },
    { key = KEY_BASE_URL, label = "接口地址", type = "text", defaultValue = DEFAULT_BASE_URL,
      helpText = "OpenAI 兼容接口填到 /v1 为止，例：https://api.groq.com/openai/v1、https://api.siliconflow.cn/v1。"
        .. "腾讯 TokenHub 填完整地址：https://tokenhub.tencentmaas.com/v1/wand/asrproxy/sync_transcribe" },
    { key = KEY_MODEL, label = "模型", type = "text", defaultValue = DEFAULT_MODEL,
      helpText = "例：whisper-1、whisper-large-v3、FunAudioLLM/SenseVoiceSmall、hy-asr-3.0-preview（腾讯）" },
    { key = "testConnection", label = "测试接口", type = "button" },
  }
end

function plugin.isConfigured()
  return trim(host.config.get(KEY_API_KEY) or "") ~= ""
end

-- 设置页「测试接口」：上传一秒静音，地址、密钥、模型都对才算通过。nil = 成功
function plugin.testConnection()
  local _, err = transcribe(string.rep("\0", SAMPLE_RATE * 2), "zh")
  return err
end

-- ================= 识别 =================

function plugin.initialize()
  return plugin.isConfigured()
end

-- lang：宿主传的语言代码（zh / en / ja），老宿主不传
function plugin.start(lang)
  chunks = {}
  language = lang or "zh"
  host.asr.emitState(1)
  return true
end

function plugin.processAudioChunk(pcm)
  chunks[#chunks + 1] = pcm
end

function plugin.stop()
  local pcm = table.concat(chunks)
  chunks = {}
  if #pcm == 0 then
    host.asr.emitState(0)
    return
  end
  host.asr.emitState(2)
  local ok, text, err = pcall(transcribe, pcm, language)
  if not ok then err = tostring(text) end
  if err ~= nil then
    host.asr.emitError(err)
    return
  end
  host.asr.emitFinal(text)
  host.asr.emitState(0)
end

function plugin.cancel()
  chunks = {}
  host.asr.emitState(0)
end

-- 测试用（宿主不调用）
plugin._wav = wav
plugin._endpoint = endpoint
plugin._extractText = function(data) return extractText(data, 0) end

return plugin
