--[[
  CyIME 英文单词方案（cyime_english.schema.yaml）的个人短语候选：
  读取用户目录的 cyime_en_phrases.txt（App 在英文模式上屏邮箱/域名/带点名字后写入），
  当前编码是它开头时把完整短语放到候选最前面。这样在「英文单词」方案里打 zhang
  也能直接选到完整的 zhang.san@example.com，而不是只有直接输入的补全有。
  只处理纯字母编码；文件不存在或编码不是短语开头时原样放行。
--]]
local FILE = "/cyime_en_phrases.txt"
local MAX = 2

local cache = { key = nil, phrases = {} }

local function load_phrases()
    local dir = rime_api and rime_api.get_user_data_dir and rime_api.get_user_data_dir()
    if not dir then return {} end
    local f = io.open(dir .. FILE, "r")
    if not f then
        cache.key, cache.phrases = nil, {}
        return cache.phrases
    end
    -- 文件名、大小和末尾内容一起判断变化：追加、压缩、删除都能发现。
    local size = f:seek("end") or 0
    f:seek("set", math.max(0, size - 64))
    local key = size .. ":" .. (f:read("*a") or "")
    if key ~= cache.key then
        f:seek("set", 0)
        local phrases = {}
        for line in f:lines() do
            local w = line:match("^%s*([%w@%._%+&'%-]+)%s*$")
            if w and #w >= 3 then phrases[#phrases + 1] = w end
        end
        cache.key, cache.phrases = key, phrases
    end
    f:close()
    return cache.phrases
end

local function cyime_en_phrases(input, env)
    local code = env.engine.context.input
    if #code < 2 or #code > 64 or not code:match("^%a[%a']*$") then
        for cand in input:iter() do yield(cand) end
        return
    end
    local phrases = load_phrases()
    local lower = code:lower()
    local shown, count = {}, 0
    -- 最近打过的在前，最多给两个，避免挤掉词库本身的候选。
    for i = #phrases, 1, -1 do
        local word = phrases[i]
        if #word > #code and word:lower():sub(1, #code) == lower and not shown[word] then
            shown[word] = true
            yield(Candidate("cyime_en_phrase", 0, #code, word, ""))
            count = count + 1
            if count >= MAX then break end
        end
    end
    for cand in input:iter() do
        if not shown[cand.text] then yield(cand) end
    end
end

return cyime_en_phrases
