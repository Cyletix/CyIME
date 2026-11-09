--[[
  CyIME 英文单词方案（cyime_english.schema.yaml）的候选整理：
  - 大小写跟着打的字母走：首字母大写（Hello）候选首字母大写，两个字母以上全大写（HELLO）候选全大写；
    小写打时词库里本来带大写的词（iPhone、NASA、Aaron）照原样。
  - 所打字母不是首选的整词时（hel 的首选是 hello），原样的字母排第二位（回车上屏的也是它）；
    一个候选都没有（词库没有的词）时原样字母就是唯一候选，空格照样上屏。
  标点等不是字母的候选不动。
--]]
local function recase(text, code)
    if not code:find("^%u") then return text end
    if #code > 1 and not code:find("%l") then return text:upper() end
    return text:sub(1, 1):upper() .. text:sub(2)
end

local function is_word_code(code)
    return code:match("^%a[%a']*$") ~= nil
end

local function cyime_english_words(input, env)
    local ctx = env.engine.context
    local first = true
    for cand in input:iter() do
        local code = ctx.input:sub(cand.start + 1, cand._end)
        if not is_word_code(code) then
            yield(cand)
        else
            local text = recase(cand.text, code)
            if text ~= cand.text then
                yield(ShadowCandidate(cand, cand.type, text, cand.comment))
            else
                yield(cand)
            end
            if first then
                first = false
                if text:lower() ~= code:lower() then
                    yield(Candidate("raw", cand.start, cand._end, code, ""))
                end
            end
        end
    end
    if first then
        local seg = ctx.composition:back()
        if seg then
            local code = ctx.input:sub(seg.start + 1, seg._end)
            if is_word_code(code) then yield(Candidate("raw", seg.start, seg._end, code, "")) end
        end
    end
end

return cyime_english_words
