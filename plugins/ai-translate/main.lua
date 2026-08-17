-- AI 翻译插件（Lua 脚本插件）
--
-- 职责划分：
--   Lua   = prompt 模板组装（{context}/{targetLang}）+ 同步调用 LLM + 解析译文
--   宿主  = 通用工具面板 + 选区替换上屏（选中文本优先预填，结果替换原选区）
--     host.http.request  同步 HTTP（第 5 参 timeoutMillis）
--     host.json          JSON 编解码
--     host.config        配置存储

local plugin = {}

local KEY_API_KEY = "apiKey"
local KEY_BASE_URL = "baseUrl"
local KEY_MODEL = "model"
local KEY_TARGET_LANG = "targetLang"
local KEY_PROMPT = "prompt"

local DEFAULTS = {
  baseUrl = "https://api.openai.com/v1",
  model = "gpt-4o-mini",
  targetLang = "简体中文",
  prompt = "你是专业翻译。请把下面的内容翻译成{targetLang}，只输出译文，不要解释、不要引号。\n{context}",
}

local lastContext = ""
local cachedItems = {}
local generating = false

-- ================= 配置 schema（与 manifest 一致，插件中心表单数据源） =================

function plugin.getSettingsSchema()
  return {
    {
      key = KEY_API_KEY,
      label = "API Key",
      type = "secret",
      placeholder = "输入 LLM API Key",
      helpText = "OpenAI 兼容接口的 API Key",
    },
    {
      key = KEY_BASE_URL,
      label = "接口地址",
      type = "text",
      defaultValue = DEFAULTS.baseUrl,
      helpText = "OpenAI 兼容接口地址（/chat/completions 前缀），域名将自动获得联网授权",
    },
    {
      key = KEY_MODEL,
      label = "模型",
      type = "text",
      defaultValue = DEFAULTS.model,
    },
    {
      key = KEY_TARGET_LANG,
      label = "目标语言",
      type = "text",
      defaultValue = DEFAULTS.targetLang,
      helpText = "翻译目标语言（如 简体中文 / English / 日本語）",
    },
    {
      key = KEY_PROMPT,
      label = "翻译模板",
      type = "textarea",
      defaultValue = DEFAULTS.prompt,
      helpText = "翻译 prompt 模板，{context} 替换为待翻译文本，{targetLang} 替换为目标语言",
    },
  }
end

function plugin.getPanelState(inputText)
  return {
    inputText = inputText,
    items = cachedItems,
    actions = { { id = "generate", label = "翻译" } },
    loading = generating,
  }
end

function plugin.onPanelInput(text)
  lastContext = text or ""
end

function plugin.onPanelAction(actionId)
  if actionId ~= "generate" then return end
  local context = lastContext
  if context == "" then
    host.logError("请先输入待翻译内容")
    return
  end
  if generating then return end

  local apiKey = host.config.get(KEY_API_KEY) or ""
  if apiKey == "" then
    host.logError("AI 翻译未配置 API Key")
    return
  end

  generating = true
  cachedItems = {}

  local baseUrl = host.config.get(KEY_BASE_URL) or DEFAULTS.baseUrl
  baseUrl = baseUrl:gsub("/+$", "")
  local model = host.config.get(KEY_MODEL) or DEFAULTS.model
  local targetLang = host.config.get(KEY_TARGET_LANG) or DEFAULTS.targetLang
  local prompt = (host.config.get(KEY_PROMPT) or DEFAULTS.prompt)
  prompt = string.gsub(prompt, "{context}", context)
  prompt = string.gsub(prompt, "{targetLang}", targetLang)

  local body = host.json.encode({
    model = model,
    messages = {
      { role = "system", content = "你是专业翻译，只输出译文。" },
      { role = "user", content = prompt },
    },
    temperature = 0.3,
  })
  local headers = {
    ["Content-Type"] = "application/json",
    ["Authorization"] = "Bearer " .. apiKey,
  }
  local url = baseUrl .. "/chat/completions"

  local resp = host.http.request("POST", url, headers, body, 60000)
  if resp == nil then
    host.logError("AI 请求失败: " .. (host.http.lastError() or "未知错误"))
    generating = false
    return
  end
  if resp.status ~= 200 then
    host.logError("AI 接口返回 " .. tostring(resp.status) .. ": " .. resp.text)
    generating = false
    return
  end

  local data = host.json.decode(resp.text)
  local items = {}
  if data ~= nil and data.choices ~= nil and #data.choices > 0 then
    local content = data.choices[1].message.content or ""
    local trimmed = content:gsub("^%s+", ""):gsub("%s+$", "")
    if trimmed ~= "" then
      items[#items + 1] = { id = "1", text = trimmed }
    end
  end
  cachedItems = items
  generating = false
end

function plugin.onPanelItemClick(itemId)
  -- 上屏由宿主完成（选区替换）
end

return plugin