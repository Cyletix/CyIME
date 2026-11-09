-- 笔画分词打词：用分词键（'）隔开、每个字只打头几笔时出词（用户 11-09 录屏：丨乙丿'丨乙'乙'丶一丶 只出部件乱串）。
-- 码表 stroke 只有单字。词表 stroke_phrase（构建时生成）的编码是每个字的第一笔：
-- 先按各段第一笔查出一批词，再逐字核对打的那几笔是不是这个字笔顺的开头（笔顺从 stroke 码表反查）。
-- 只在打了分词键、至少两段时查；平时一笔一笔打单字不经过这里，单字的结果和排序不变。
-- 字数和段数一样的词按词频排在最前；段数少于四时，后面跟着多出字的长词（最后一段之后还没打的字不核对）。
-- 四段起没有整词对得上（或超过四段）时，按两到四字一组拼一句：组数最少，同组数取词频高的。
local MAX_WORD = 4
local MAX_SEGMENTS = 8
-- 每次最多看多少个词条：最常见的第一笔组合（横横）约九千个两字词；长词每个开头最多看 SCAN_LONGER 个。
local SCAN_EXACT = 12000
local SCAN_LONGER = 1500
local MAX_EXACT = 30
local MAX_LONGER = 20
local MAX_HEADS = 10
-- 排在单字和部件拼成的句子前面。
local QUALITY = 100

local M = {}
local emit_heads

function M.init(env)
  env.phrases = Memory(env.engine, Schema("stroke_phrase"))
  env.strokes = ReverseLookup("stroke")
  env.codes = {}
end

function M.fini(env)
  env.phrases = nil
  env.strokes = nil
  env.codes = nil
  collectgarbage()
end

-- 一个字的全部笔顺（个别字有几种写法），查过的记住。
local function codes_of(env, ch)
  local cached = env.codes[ch]
  if cached == nil then
    cached = {}
    local found = env.strokes:lookup(ch)
    if found then
      for code in found:gmatch("%S+") do cached[#cached + 1] = code end
    end
    env.codes[ch] = cached
  end
  return cached
end

local function starts_with(env, ch, part)
  for _, code in ipairs(codes_of(env, ch)) do
    if code:sub(1, #part) == part then return true end
  end
  return false
end

-- 词的前 #parts 个字逐个对上各段笔画。
local function matches(env, text, parts)
  local i = 0
  for _, cp in utf8.codes(text) do
    i = i + 1
    if i > #parts then return true end
    if not starts_with(env, utf8.char(cp), parts[i]) then return false end
  end
  return i >= #parts
end

local function split(input)
  if not input:find("'", 1, true) or input:find("[^hspnz']") then return nil end
  local parts = {}
  for part in (input .. "'"):gmatch("([^']*)'") do parts[#parts + 1] = part end
  -- 刚按完分词键时最后是空段，不算；中间连按两下分词键不查。
  if parts[#parts] == "" then parts[#parts] = nil end
  if #parts < 2 or #parts > MAX_SEGMENTS then return nil end
  for _, part in ipairs(parts) do
    if part == "" then return nil end
  end
  return parts
end

-- parts[from..to] 这几段对得上的词频最高的整词（同编码按词频排好，第一个对上的就是）。
local function best_word(env, parts, from, to)
  local slice, firsts = {}, {}
  for i = from, to do
    slice[#slice + 1] = parts[i]
    firsts[#firsts + 1] = parts[i]:sub(1, 1)
  end
  if not env.phrases:dict_lookup(table.concat(firsts), false, 0) then return nil end
  local scanned = 0
  for entry in env.phrases:iter_dict() do
    scanned = scanned + 1
    if scanned > SCAN_EXACT then break end
    if matches(env, entry.text, slice) then return entry end
  end
  return nil
end

-- 拼句：best[i] 是从第 i 段到最后的最好拼法（组数, 词频和, 文字）。
local function sentence(env, parts)
  local n = #parts
  local best = { [n + 1] = { groups = 0, weight = 0, text = "" } }
  for i = n, 1, -1 do
    for len = 2, MAX_WORD do
      local rest = best[i + len]
      if rest and i + len - 1 <= n then
        local entry = best_word(env, parts, i, i + len - 1)
        if entry then
          local groups, weight = rest.groups + 1, rest.weight + entry.weight
          local cur = best[i]
          if not cur or groups < cur.groups or (groups == cur.groups and weight > cur.weight) then
            best[i] = { groups = groups, weight = weight, text = entry.text .. rest.text }
          end
        end
      end
    end
  end
  return best[1] and best[1].groups > 1 and best[1].text or nil
end

function M.func(input, seg, env)
  local parts = split(input)
  if not parts or not env.phrases then return end
  local firsts = {}
  for i, part in ipairs(parts) do firsts[i] = part:sub(1, 1) end
  local code = table.concat(firsts)
  local seen = {}
  local function emit(text, stop)
    seen[text] = true
    local cand = Candidate("stroke_phrase", seg.start, stop or seg._end, text, "")
    cand.quality = QUALITY
    yield(cand)
  end

  -- 字数和段数一样的词：码表里同一编码按词频排好，对上的依次出，够数就停。
  local exact, scanned = 0, 0
  if #parts <= MAX_WORD and env.phrases:dict_lookup(code, false, 0) then
    for entry in env.phrases:iter_dict() do
      scanned = scanned + 1
      if scanned > SCAN_EXACT or exact >= MAX_EXACT then break end
      if not seen[entry.text] and matches(env, entry.text, parts) then
        exact = exact + 1
        emit(entry.text)
      end
    end
  end

  if #parts >= 4 and exact == 0 then
    local text = sentence(env, parts)
    if text and not seen[text] then emit(text) end
  end

  -- 更长的词：编码比段数多一笔起，分五个开头各查一次（不再翻一遍上面同编码的词）；
  -- 不同编码之间不按词频排，先收下来再排。
  if #parts >= MAX_WORD then
    emit_heads(env, parts, seg, emit, seen)
    return
  end
  local longer = {}
  for _, next_stroke in ipairs({ "h", "s", "p", "n", "z" }) do
    scanned = 0
    if env.phrases:dict_lookup(code .. next_stroke, true, 0) then
      for entry in env.phrases:iter_dict() do
        scanned = scanned + 1
        if scanned > SCAN_LONGER then break end
        local text = entry.text
        if not seen[text] and matches(env, text, parts) then
          seen[text] = true
          longer[#longer + 1] = { text = text, weight = entry.weight, order = #longer }
        end
      end
    end
  end
  table.sort(longer, function(a, b)
    if a.weight ~= b.weight then return a.weight > b.weight end
    return a.order < b.order
  end)
  for i = 1, math.min(#longer, MAX_LONGER) do emit(longer[i].text) end
  if #parts >= 3 then emit_heads(env, parts, seg, emit, seen) end
end

-- 三段起，也给前两段、前三段的词（只吃掉这几段和后面的分词号），选了接着打剩下的，和拼音选词一样一段一段上屏。
emit_heads = function(env, parts, seg, emit, seen)
  for len = math.min(#parts - 1, MAX_WORD - 1), 2, -1 do
    local slice, firsts, chars = {}, {}, 0
    for i = 1, len do
      slice[i] = parts[i]
      firsts[i] = parts[i]:sub(1, 1)
      chars = chars + #parts[i] + 1
    end
    local found, scanned = 0, 0
    if env.phrases:dict_lookup(table.concat(firsts), false, 0) then
      for entry in env.phrases:iter_dict() do
        scanned = scanned + 1
        if scanned > SCAN_EXACT or found >= MAX_HEADS then break end
        if not seen[entry.text] and matches(env, entry.text, slice) then
          found = found + 1
          emit(entry.text, seg.start + chars)
        end
      end
    end
  end
end

return M
