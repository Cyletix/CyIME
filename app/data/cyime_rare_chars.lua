--[[
  CyIME：中文 26 键全拼的生僻字（开关 cyime_rare_chars，默认关）。
  主词库只有 8105 字表，瀺 这类字打不出来。打一个完整拼音音节（chan）时，在主翻译器的整段候选之后补上
  cyime_rare.txt 里这个读音的生僻字（构建时从雾凇大字表取 8105 没有的基本区和扩展 A 汉字，见 tasks-chinese.gradle.kts）。
  不进主词库：九键回放里整表导入让每键查词量翻了一倍多。开关关着时不读文件、不做任何事；选中的字不进用户词库。
--]]
local OPTION = "cyime_rare_chars"

local chars  -- syllable → { text, ... }; loaded once per Lua state, only after the option is first on
local function load_chars()
  if chars then return chars end
  chars = {}
  local name = "/cyime_rare.txt"
  local f = io.open(rime_api.get_shared_data_dir() .. name, "r") or io.open(rime_api.get_user_data_dir() .. name, "r")
  if not f then return chars end
  for line in f:lines() do
    local text, code = line:match("^([^\t]+)\t(%l+)$")
    if text then
      local list = chars[code]
      if not list then list = {}; chars[code] = list end
      list[#list + 1] = text
    end
  end
  f:close()
  return chars
end

local function translator(input, seg, env)
  if not env.engine.context:get_option(OPTION) then return end
  local list = load_chars()[input:lower()]
  if not list then return end
  for _, text in ipairs(list) do
    local cand = Candidate("rare", seg.start, seg._end, text, "")
    -- Rime orders whole-input candidates by quality: below every dictionary word of this reading.
    cand.quality = -1000
    yield(cand)
  end
end

return translator
