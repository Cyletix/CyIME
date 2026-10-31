--[[
  CyIME：中文 26 键全拼和九键的生僻字（开关 cyime_rare_chars，默认关）。
  主词库只有 8105 字表，瀺、槻 这类字打不出来。打一个完整拼音音节（chan）时，在主翻译器的整段候选之后补上
  cyime_rare.txt 里这个读音的生僻字（构建时从雾凇大字表取 8105 没有的基本区和扩展 A 汉字，见 tasks-chinese.gradle.kts）。
  九键按一个音节的数字（484 = gui、hui）或左栏选定的拼音字母（gui）补；数字可能对应几个音节，按读音带上注释，
  拂动固定字母的筛选（t9_filter）照常按注释比对。
  不进主词库：九键回放里整表导入让每键查词量翻了一倍多。开关关着时不读文件、不做任何事；选中的字不进用户词库。
--]]
local OPTION = "cyime_rare_chars"

local KEYPAD = "22233344455566677778889999"

local chars  -- syllable → { text, ... }; loaded once per Lua state, only after the option is first on
local digits  -- nine-key digits → { { text, syllable }, ... }
local function load_chars()
  if chars then return chars end
  chars, digits = {}, {}
  local name = "/cyime_rare.txt"
  local f = io.open(rime_api.get_shared_data_dir() .. name, "r") or io.open(rime_api.get_user_data_dir() .. name, "r")
  if not f then return chars end
  for line in f:lines() do
    local text, code = line:match("^([^\t]+)\t(%l+)$")
    if text then
      local list = chars[code]
      if not list then list = {}; chars[code] = list end
      list[#list + 1] = text
      local keys = code:gsub("%l", function(c) local i = c:byte() - 96; return KEYPAD:sub(i, i) end)
      local by_keys = digits[keys]
      if not by_keys then by_keys = {}; digits[keys] = by_keys end
      by_keys[#by_keys + 1] = { text, code }
    end
  end
  f:close()
  return chars
end

local function translator(input, seg, env)
  if not env.engine.context:get_option(OPTION) then return end
  local list = load_chars()[input:lower()]
  if list then
    for _, text in ipairs(list) do
      local cand = Candidate("rare", seg.start, seg._end, text, "")
      -- Rime orders whole-input candidates by quality: below every dictionary word of this reading.
      cand.quality = -1000
      yield(cand)
    end
    return
  end
  for _, item in ipairs(digits[input] or {}) do
    local cand = Candidate("rare", seg.start, seg._end, item[1], item[2])
    cand.quality = -1000
    yield(cand)
  end
end

return translator
