--[[
  CyIME：中文 26 键中英混输（开关 cyime_mixed_input，默认关）。
  nihaohelloshijie → 你好hello世界：输入里找英文词库中的整词（且这段字母本身拼不成全拼音节），其余部分必须是全拼
  （最后一段可以是没打完的音节或首字母），拼音段交给主翻译器转成首选，英文词保持词库写法（打了大写按所打）。
  整段本来就能拼成全拼音节时不出混输候选（game、nice 这类像拼音的英文词因此打不出），只有一段英文都没有时也不出。
  混输候选只覆盖整段输入，放第一位；拼音段用另一个不学习的主翻译器实例查，选中混输候选不进用户词库。
--]]
local OPTION = "cyime_mixed_input"
local MAX_INPUT = 48
local MAX_WORD = 20
-- Two-letter English words are almost all abbreviations; only these are common enough as words. Three-letter
-- lowercase words (ton, don, con) mostly turn up inside mistyped pinyin (tonguo for tongguo): only listed acronyms
-- (PPT, KTV, API) and these common ones count.
local SHORT_WORDS = { ok = "ok", hi = "hi", go = "go", no = "no" }
local THREE_LETTER = {}
for w in ("app bug yes bye wow get low top max min new old hot bad mod buy pay fix job web emo cue"):gmatch("%a+") do
  THREE_LETTER[w] = true
end

local INITIALS = { "", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h", "j", "q", "x",
                   "zh", "ch", "sh", "r", "z", "c", "s", "y", "w" }
local SYLLABLES = {}
for s in ([[a ai an ang ao e ei en eng er o ou
ba bai ban bang bao bei ben beng bi bian biao bie bin bing bo bu
pa pai pan pang pao pei pen peng pi pian piao pie pin ping po pou pu
ma mai man mang mao me mei men meng mi mian miao mie min ming miu mo mou mu
fa fan fang fei fen feng fo fou fu
da dai dan dang dao de dei den deng di dia dian diao die ding diu dong dou du duan dui dun duo
ta tai tan tang tao te tei teng ti tian tiao tie ting tong tou tu tuan tui tun tuo
na nai nan nang nao ne nei nen neng ni nian niang niao nie nin ning niu nong nou nu nuan nun nuo nv nve nue
la lai lan lang lao le lei leng li lia lian liang liao lie lin ling liu lo long lou lu luan lun luo lv lve lue
ga gai gan gang gao ge gei gen geng gong gou gu gua guai guan guang gui gun guo
ka kai kan kang kao ke kei ken keng kong kou ku kua kuai kuan kuang kui kun kuo
ha hai han hang hao he hei hen heng hong hou hu hua huai huan huang hui hun huo
ji jia jian jiang jiao jie jin jing jiong jiu ju juan jue jun jv
qi qia qian qiang qiao qie qin qing qiong qiu qu quan que qun qv
xi xia xian xiang xiao xie xin xing xiong xiu xu xuan xue xun xv
zha zhai zhan zhang zhao zhe zhei zhen zheng zhi zhong zhou zhu zhua zhuai zhuan zhuang zhui zhun zhuo
cha chai chan chang chao che chen cheng chi chong chou chu chua chuai chuan chuang chui chun chuo
sha shai shan shang shao she shei shen sheng shi shou shu shua shuai shuan shuang shui shun shuo
ra ran rang rao re ren reng ri rong rou ru rua ruan rui run ruo
za zai zan zang zao ze zei zen zeng zi zong zou zu zuan zui zun zuo
ca cai can cang cao ce cei cen ceng ci cong cou cu cuan cui cun cuo
sa sai san sang sao se sen seng si song sou su suan sui sun suo
ya yan yang yao ye yi yin ying yo yong you yu yuan yue yun yv
wa wai wan wang wei wen weng wo wu]]):gmatch("%a+") do SYLLABLES[s] = true end
-- Unfinished syllables (a typed prefix of one) and initials, allowed only at the end of the input.
local PREFIXES = {}
for s in pairs(SYLLABLES) do
  for i = 1, #s do PREFIXES[s:sub(1, i)] = true end
end
for _, i in ipairs(INITIALS) do if i ~= "" then PREFIXES[i] = true end end

-- full[i]: s:sub(i) splits into whole syllables (memo per call).
local function full_pinyin(s)
  local n, ok = #s, {}
  ok[n + 1] = true
  for i = n, 1, -1 do
    for len = 1, 6 do
      if i + len - 1 <= n and ok[i + len] and SYLLABLES[s:sub(i, i + len - 1)] then
        ok[i] = true
        break
      end
    end
  end
  return ok
end

local words  -- lowercase spelling → dictionary text; loaded once per Lua state
local mixed_words = {}  -- the cn_en part of words
local function load_words()
  if words then return words end
  words = {}
  local dir = rime_api.get_shared_data_dir() .. "/en_dicts/"
  local user = rime_api.get_user_data_dir() .. "/en_dicts/"
  for _, name in ipairs({ "en_ext.dict.yaml", "en.dict.yaml" }) do
    local f = io.open(dir .. name, "r") or io.open(user .. name, "r")
    if f then
      local body = false
      for line in f:lines() do
        if body then
          local text, code = line:match("^(%a+)\t(%a+)")
          if text and #text <= MAX_WORD and text:lower() == code:lower() then
            local key = text:lower()
            -- The plain lowercase entry (ok) wins over a capitalized one (OK); otherwise the first seen (WiFi).
            if not words[key] or text == key then words[key] = text end
          end
        elseif line == "..." then
          body = true
        end
      end
      f:close()
    end
  end
  -- Mixed words of the cn_en table (打call, DNA检测) also count as one English word inside a sentence.
  local f = io.open(dir .. "cn_en.txt", "r") or io.open(user .. "cn_en.txt", "r")
  if f then
    for line in f:lines() do
      local text, code = line:match("^([^	#][^	]*)	(%a+)$")
      if text and #code <= MAX_WORD and not words[code:lower()] then
        words[code:lower()] = text
        mixed_words[code:lower()] = true
      end
    end
    f:close()
  end
  return words
end

-- English span s:sub(i, j): a dictionary word, or a run of capitals typed on purpose (DNA). Words that are
-- themselves whole pinyin (women, game) are left to pinyin. Returns the text and whether it is a cn_en word.
local function english(s, lower, i, j, full_from)
  local span = s:sub(i, j)
  if span:match("^%u%u+$") then return span, false end
  local key = lower:sub(i, j)
  local text = #key == 2 and SHORT_WORDS[key] or (#key >= 3 and load_words()[key])
  if not text then return nil end
  if #key == 3 and not THREE_LETTER[key] and not text:match("^%u%u%u$") and not mixed_words[key] then return nil end
  if full_from(key)[1] then return nil end
  -- A plural of a listed word (apps) and a syllable plus a listed word (decode = de + code) are read as the
  -- shorter word next to pinyin: Chinese drops the plural, and de, re … are common syllables.
  if mixed_words[key] then return text, true end
  local words = load_words()
  if #key >= 4 and key:sub(-1) == "s" and words[key:sub(1, -2)] then return nil end
  for k = 2, 3 do
    local head = key:sub(1, k)
    if head:match("^[^aoe]") and SYLLABLES[head] and #key - k >= 3 and words[key:sub(k + 1)] then return nil end
  end
  if span:find("%u") and text:lower() == key then return span, false end
  return text, false
end

-- A syllable without initial (o, e, an) next to an English word is usually a split word (hell + o, e + mail).
local ZERO_INITIAL = 4

local function better(x, y)
  for k = 1, 3 do
    if x[k] ~= y[k] then return x[k] < y[k] end
  end
  return false
end

-- Best split of the input into pinyin and English words. Costs, in order: letters in an unfinished last
-- syllable, number of English words, then the longest English words (deadline, not dead + lin e; da call
-- yields to the cn_en 打call) with ZERO_INITIAL letters off for each syllable without initial next to an English
-- word (hello, not hell + o). Returns {kind = "py"|"en", text = ...} or nil.
local function split(s)
  local n, lower = #s, s:lower()
  if n < 3 or n > MAX_INPUT or not s:match("^%a+$") then return nil end
  if full_pinyin(lower)[1] and not s:find("%u") then return nil end
  local cache = {}
  local function full_from(key)
    local r = cache[key]
    if not r then r = full_pinyin(key); cache[key] = r end
    return r
  end
  -- best[i]: cost of s:sub(i), the first step and where the rest starts; filled from the end.
  local best = { [n + 1] = { cost = { 0, 0, 0 } } }
  for i = n, 1, -1 do
    local b
    local function try(cost, step, tail)
      if not b or better(cost, b.cost) then b = { cost = cost, step = step, tail = tail } end
    end
    for len = 1, 6 do
      local j = i + len - 1
      if j > n then break end
      local syl, nxt = lower:sub(i, j), best[j + 1]
      if nxt and not s:sub(i, j):find("%u") and (SYLLABLES[syl] or (j == n and PREFIXES[syl])) then
        local c = nxt.cost
        local zero = SYLLABLES[syl] and syl:match("^[aoe]") and nxt.step and nxt.step.kind == "en"
        try({ c[1] + (SYLLABLES[syl] and 0 or len), c[2], c[3] + (zero and ZERO_INITIAL or 0) },
            { kind = "py", text = syl, whole = SYLLABLES[syl], zero = zero and true }, j + 1)
      end
    end
    for j = i + 1, math.min(n, i + MAX_WORD - 1) do
      local nxt = best[j + 1]
      if nxt then
        local text, mixed = english(s, lower, i, j, full_from)
        if text then
          local c = nxt.cost
          local zero = nxt.step and nxt.step.kind == "py" and nxt.step.whole and nxt.step.text:match("^[aoe]")
          try({ c[1], c[2] + 1, c[3] - (j - i + 1) + (zero and ZERO_INITIAL or 0) },
              { kind = "en", text = text }, j + 1)
        end
      end
    end
    best[i] = b
  end
  if not best[1] or best[1].cost[2] == 0 then return nil end
  -- Join consecutive syllables into pieces.
  local parts, i, whole = {}, 1, 0
  while i <= n do
    local step = best[i].step
    if step.kind == "py" and step.whole then whole = whole + 1 end
    if step.kind == "py" and #parts > 0 and parts[#parts].kind == "py" then
      parts[#parts].text = parts[#parts].text .. step.text
    else
      parts[#parts + 1] = { kind = step.kind, text = step.text }
    end
    i = best[i].tail
  end
  -- Initials only (cbdl, whyw) are abbreviated Chinese, not an acronym plus a word.
  if whole == 0 then return nil end
  return parts
end

-- First candidate of the pinyin translator that covers the whole piece.
local function convert(env, piece, seg)
  local segment = Segment(0, #piece)
  segment.tags = seg.tags
  local translation = env.pinyin:query(piece, segment)
  if not translation then return nil end
  local seen = 0
  for cand in translation:iter() do
    if cand._end - cand._start == #piece then return cand.text end
    seen = seen + 1
    if seen >= 8 then break end
  end
  return nil
end

-- A second pinyin translator for the pieces; it only reads (the main translator learns what is picked, this
-- copy must not learn the same commit again). Made when the option is on, so the switched-off default costs
-- nothing; never while composing (creating a translator there crashes librime).
local function make_pinyin(env)
  if env.pinyin then return end
  env.pinyin = Component.ScriptTranslator(env.engine, "translator", "script_translator")
  if env.pinyin then env.pinyin:set_memorize_callback(function() return true end) end
end

local function init(env)
  env.converted, env.cached = {}, 0
  local context = env.engine.context
  if context:get_option(OPTION) then make_pinyin(env) end
  env.option = context.option_update_notifier:connect(function(ctx, name)
    if name == OPTION and ctx:get_option(OPTION) then make_pinyin(env) end
  end)
  -- What is picked can change the next conversion (learning): start over after each commit.
  env.commit = context.commit_notifier:connect(function()
    env.converted, env.cached = {}, 0
  end)
end

local function fini(env)
  if env.option then env.option:disconnect() end
  if env.commit then env.commit:disconnect() end
end

local function translator(input, seg, env)
  if not env.engine.context:get_option(OPTION) then return end
  local parts = split(input)
  if not parts or not env.pinyin then return end
  local out = {}
  for _, part in ipairs(parts) do
    if part.kind == "en" then
      out[#out + 1] = part.text
    else
      -- Typing extends only the last piece: earlier pieces are converted once.
      local text = env.converted[part.text]
      if text == nil then
        text = convert(env, part.text, seg) or false
        if env.cached >= 64 then env.converted, env.cached = {}, 0 end
        env.converted[part.text], env.cached = text, env.cached + 1
      end
      if not text then return end
      out[#out + 1] = text
    end
  end
  local cand = Candidate("mixed", seg.start, seg._end, table.concat(out), "")
  cand.quality = 1000
  yield(cand)
end

return { init = init, func = translator, fini = fini }
