--[[
  CyIME：中文 26 键全拼、双拼、14 键和九键的生僻字（开关 cyime_rare_chars，默认关）。
  主词库只有 8105 字表，瀺、槻 这类字打不出来。打一个完整拼音音节（chan）时，在主翻译器的整段候选之后补上
  cyime_rare.txt 里这个读音的生僻字（构建时从雾凇大字表取 8105 没有的基本区和扩展 A 汉字，见 tasks-chinese.gradle.kts）。
  九键按一个音节的数字（484 = gui、hui）或左栏选定的拼音字母（gui）补；数字可能对应几个音节，按读音带上注释，
  拂动固定字母的筛选（t9_filter）照常按注释比对。
  双拼（GitHub #72，同一个开关）：方案带 cyime_rare_double_pinyin: true 时，和下面 14 键一样按这个方案自己的
  拼写规则算出每个读音的双拼编码再查，打满两个键（qu）补这个读音的字。原来按编码显示规则（preedit_format）换回
  拼音，显示规则为空的方案（自建布局上的双拼）一个字也不补（2026-11-03）。
  14 键（全拼和双拼，2026-11-02）：方案带 cyime_rare_merged_keys: true 时，按这个方案自己的拼写规则
  （speller/algebra）把每个读音算成精确字母编码，合并键（大写，AU = as 键 + ui 键）逐位换成键上的字母再查，
  对得上几个读音就补几个读音的字（AU 补 ai、si、su，带读音注释）；侧滑选定的字母（小写）只对自己。
  简拼、模糊音、纠错和手动分词的规则不算：只在打满一个完整读音时补。
  不进主词库：九键回放里整表导入让每键查词量翻了一倍多。开关关着时不读文件、不做任何事；选中的字不进用户词库。
--]]
local OPTION = "cyime_rare_chars"

local KEYPAD = "22233344455566677778889999"

-- Loaded once per Lua state, only after the option is first on. Each list is one string, not a table per character:
-- librime-lua runs a full garbage collection every 256 Lua translations, and with 2 万 small tables each one stalled
-- a key for about 3 ms on the PC host engine (16–21 times in 4,000 keys), also after the switch was turned off
-- again. As strings no key took over 0.6 ms (2026-11-02).
local chars  -- syllable → the characters of that reading, concatenated
local digits  -- nine-key digits → "字\tsyllable\n" rows in file order
local function load_chars()
  if chars then return chars end
  chars, digits = {}, {}
  local name = "/cyime_rare.txt"
  local f = io.open(rime_api.get_shared_data_dir() .. name, "r") or io.open(rime_api.get_user_data_dir() .. name, "r")
  if not f then return chars end
  local by_code, by_keys = {}, {}
  for line in f:lines() do
    local text, code = line:match("^([^\t]+)\t(%l+)$")
    if text then
      local list = by_code[code]
      if not list then list = {}; by_code[code] = list end
      list[#list + 1] = text
      local keys = code:gsub("%l", function(c) local i = c:byte() - 96; return KEYPAD:sub(i, i) end)
      local rows = by_keys[keys]
      if not rows then rows = {}; by_keys[keys] = rows end
      rows[#rows + 1] = text .. "\t" .. code .. "\n"
    end
  end
  f:close()
  for code, list in pairs(by_code) do chars[code] = table.concat(list) end
  for keys, rows in pairs(by_keys) do digits[keys] = table.concat(rows) end
  return chars
end

-- 14 键和双拼：每个方案按自己的拼写规则算一次「精确字母编码 → 读音」，同一个 Lua 环境里按方案缓存。
local merged_maps = {}

-- 一条拼写规则拆成 种类、各段；分隔符是种类名后面那个字符（同 Rime）。最后不以分隔符结尾的是带标签的规则。
local function rule_parts(rule)
  local kind = rule:match("^(%l+)")
  if not kind or #rule <= #kind then return nil end
  local sep = "%" .. rule:sub(#kind + 1, #kind + 1)
  local parts = {}
  for part in rule:sub(#kind + 2):gmatch("([^" .. sep .. "]*)" .. sep) do parts[#parts + 1] = part end
  return kind, parts, not rule:find(sep .. "$")
end

local function merged_map(env)
  local schema = env.engine.schema
  local map = merged_maps[schema.schema_id]
  if map then return map end
  map = { codes = {}, keys = {}, longest = 0 }  -- codes: exact-letter code → "ai si"
  merged_maps[schema.schema_id] = map
  local forms = {}  -- { code, syllable }
  for syllable in pairs(load_chars()) do forms[#forms + 1] = { syllable, syllable } end
  local list = schema.config:get_list("speller/algebra")
  for i = 0, (list and list.size or 0) - 1 do
    local rule = list:get_value_at(i).value
    local kind, parts, tagged = rule_parts(rule)
    local merged_key = kind == "derive" and parts[2] and parts[2]:match("%u")
    if merged_key then
      -- derive/^(.{1})[as](.*)$/$1A$2/：A 键是 a、s 两个字母。
      local letters = parts[1]:match("%[(%l+)%]")
      if letters then map.keys[merged_key] = letters end
    elseif (kind == "xform" or kind == "xlit" or kind == "erase" or (kind == "derive" and not tagged))
        and not rule:find("'", 1, true) then
      local one = ConfigList()
      one:append(ConfigValue(rule).element)
      local projection = Projection()
      projection:load(one)
      local next_forms, seen = {}, {}
      local function keep(code, syllable)
        local id = code .. " " .. syllable
        if code ~= "" and not seen[id] then seen[id] = true; next_forms[#next_forms + 1] = { code, syllable } end
      end
      for _, form in ipairs(forms) do
        local result = projection:apply(form[1], true)
        if kind == "derive" then keep(form[1], form[2]) end
        keep(result, form[2])
      end
      forms = next_forms
    end
  end
  -- One string per code (syllables separated by spaces), not a table: see load_chars.
  for _, form in ipairs(forms) do
    local code, syllable = form[1], form[2]
    -- 分号：微软、搜狗双拼把 ing 放在分号键上。
    if code:match("^[%l;]+$") then
      map.codes[code] = map.codes[code] and (map.codes[code] .. " " .. syllable) or syllable
      if #code > map.longest then map.longest = #code end
    end
  end
  return map
end

-- 输入（合并键大写、侧滑选的字母小写）能对上的读音，按键上字母的顺序。
local function merged_syllables(map, input)
  if #input > map.longest or not input:match("^[%a;]+$") then return {} end
  local found, seen = {}, {}
  local function walk(i, prefix)
    if i > #input then
      for syllable in (map.codes[prefix] or ""):gmatch("%S+") do
        if not seen[syllable] then seen[syllable] = true; found[#found + 1] = syllable end
      end
      return
    end
    local key = input:sub(i, i)
    local letters = map.keys[key] or key
    for j = 1, #letters do walk(i + 1, prefix .. letters:sub(j, j)) end
  end
  walk(1, "")
  return found
end

local function merged_translate(input, seg, env)
  local syllables = merged_syllables(merged_map(env), input)
  for _, syllable in ipairs(syllables) do
    local comment = #syllables > 1 and syllable or ""
    for text in (chars[syllable] or ""):gmatch(utf8.charpattern) do
      local cand = Candidate("rare", seg.start, seg._end, text, comment)
      cand.quality = -1000
      yield(cand)
    end
  end
end

local function translator(input, seg, env)
  if not env.engine.context:get_option(OPTION) then return end
  if env.rare_merged_keys == nil then
    local config = env.engine.schema.config
    env.rare_merged_keys = config:get_bool("cyime_rare_merged_keys") == true or
      config:get_bool("cyime_rare_double_pinyin") == true
  end
  if env.rare_merged_keys then return merged_translate(input, seg, env) end
  local list = load_chars()[input:lower()]
  if list then
    for text in list:gmatch(utf8.charpattern) do
      local cand = Candidate("rare", seg.start, seg._end, text, "")
      -- Rime orders whole-input candidates by quality: below every dictionary word of this reading.
      cand.quality = -1000
      yield(cand)
    end
    return
  end
  for text, code in (digits[input] or ""):gmatch("([^\t]+)\t(%l+)\n") do
    local cand = Candidate("rare", seg.start, seg._end, text, code)
    cand.quality = -1000
    yield(cand)
  end
end

return translator
