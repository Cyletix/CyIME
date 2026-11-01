-- 在线语音插件（OpenAI 兼容 /audio/transcriptions，整段上传）
--
-- 宿主录音 16 kHz 单声道 16 位小端 PCM，经 processAudioChunk 灌进来；stop 时拼成 WAV
-- 用 multipart 上传，结果用 host.asr.emitFinal 交回。失败用 host.asr.emitError 报告，
-- 宿主（capabilities.speech.inputMode: batch）会用本地模型把这段录音再识别一次。

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

local function httpErrorText(status)
  if status == 401 or status == 403 then return "密钥不对或没有权限（" .. status .. "）" end
  if status == 404 then return "接口地址或模型名不对（404）" end
  if status == 413 then return "这段录音太长（413）" end
  if status == 429 then return "请求太多或余额不足（429）" end
  return "接口返回 " .. tostring(status)
end

-- 返回 text, err
local function transcribe(pcm, lang)
  local apiKey = trim(host.config.get(KEY_API_KEY) or "")
  if apiKey == "" then return nil, "还没填 API 密钥" end
  local baseUrl = configOr(KEY_BASE_URL, DEFAULT_BASE_URL):gsub("/+$", "")
  local boundary = "----CyIMEVoice" .. tostring(math.random(100000000, 999999999))
  local function field(name, value)
    return "--" .. boundary .. "\r\nContent-Disposition: form-data; name=\"" .. name .. "\"\r\n\r\n"
      .. value .. "\r\n"
  end
  local parts = {
    field("model", configOr(KEY_MODEL, DEFAULT_MODEL)),
    field("response_format", "json"),
  }
  if lang ~= nil and lang ~= "" and lang ~= "und" then parts[#parts + 1] = field("language", lang) end
  parts[#parts + 1] = "--" .. boundary .. "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n"
    .. "Content-Type: audio/wav\r\n\r\n" .. wav(pcm) .. "\r\n--" .. boundary .. "--\r\n"
  local headers = {
    ["Content-Type"] = "multipart/form-data; boundary=" .. boundary,
    ["Authorization"] = "Bearer " .. apiKey,
  }
  local resp = host.http.request("POST", baseUrl .. "/audio/transcriptions", headers, table.concat(parts), TIMEOUT_MS)
  if resp == nil then return nil, "连不上接口：" .. (host.http.lastError() or "未知错误") end
  if resp.status ~= 200 then
    host.logError("在线语音 " .. httpErrorText(resp.status) .. ": " .. tostring(resp.text))
    return nil, httpErrorText(resp.status)
  end
  local data = host.json.decode(resp.text or "")
  if type(data) ~= "table" or type(data.text) ~= "string" then return nil, "接口返回的格式看不懂" end
  return trim(data.text), nil
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
      helpText = "OpenAI 兼容接口，到 /v1 为止。例：https://api.groq.com/openai/v1、https://api.siliconflow.cn/v1" },
    { key = KEY_MODEL, label = "模型", type = "text", defaultValue = DEFAULT_MODEL,
      helpText = "例：whisper-1、whisper-large-v3、FunAudioLLM/SenseVoiceSmall" },
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

return plugin
