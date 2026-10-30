-- 注音方案的声调：词库不分声调，打了声调的字要对得上。
-- 候选的 preedit 按音节分好（ㄕㄨㄟˋ ㄈㄨˊ），一个音节对一个字；打了第一到第四声的音节，
-- 字的读音里没有这个声调（稅負 的 負 没有 ˊ）就排到声调都对得上的候选后面。轻声、没打声调的音节不管；
-- 字的声调表（cyime_zhuyin_tones.lua，小麦注音 BPMFBase 生成）里没有的字也不管。
-- 只重排前 100 个候选，后面的照原样跟上；字数和音节数对不上（英文、符号）的候选不动。
local LIMIT = 100
local TONES = require("cyime_zhuyin_tones")
local MARKS = { ["ˉ"] = "1", ["ˊ"] = "2", ["ˇ"] = "3", ["ˋ"] = "4" }

local allowed = {}
for tones, chars in pairs(TONES) do
  for _, cp in utf8.codes(chars) do allowed[utf8.char(cp)] = tones end
end

local function fits(cand)
  local preedit = cand.preedit
  if not preedit or preedit == "" then return true end
  local typed = {}
  for syllable in preedit:gmatch("[^ ']+") do typed[#typed + 1] = MARKS[syllable:sub(-2)] or "" end
  if utf8.len(cand.text) ~= #typed then return true end
  local i = 0
  for _, cp in utf8.codes(cand.text) do
    i = i + 1
    local tone, tones = typed[i], allowed[utf8.char(cp)]
    if tone ~= "" and tones and not tones:find(tone, 1, true) then return false end
  end
  return true
end

return function(input, env)
  local later = {}
  local count = 0
  for cand in input:iter() do
    if count >= LIMIT then
      for _, held in ipairs(later) do yield(held) end
      later = nil
      yield(cand)
      break
    end
    count = count + 1
    if fits(cand) then yield(cand) else later[#later + 1] = cand end
  end
  if later then
    for _, held in ipairs(later) do yield(held) end
  else
    for cand in input:iter() do yield(cand) end
  end
end
