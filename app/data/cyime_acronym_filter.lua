--[[
  CyIME：小写打英文缩写（kfc → KFC）。
  英文词库给 2–3 个大写字母的缩写派生了小写编码（melt_eng 方案由构建脚本加的 derive）。
  这类输入同时是中文简拼（kfc 也是“开发出”），英文候选通常排在很后面，所以：前四个候选保持原样，
  在前 60 个候选里找到的第一个缩写挪到第五位（本来就在前四的不动，如 aa → AA、vip → VIP）。输入不是 2–3 个小写字母时不做任何事。
--]]
local SCAN = 60

local function cyime_acronym_filter(input, env)
    local code = env.engine.context.input
    if not code:match("^%l%l%l?$") then
        for cand in input:iter() do yield(cand) end
        return
    end
    local head, acronym, scanned, streaming = {}, nil, 0, false
    for cand in input:iter() do
        if streaming then
            if cand ~= acronym then yield(cand) end
        else
            scanned = scanned + 1
            -- Already among the first four (aa → AA, vip → VIP): it keeps its place.
            if not acronym and scanned > 4 and cand.text:match("^%u+$") and cand.text:lower() == code then
                acronym = cand
            else
                table.insert(head, cand)
            end
            if #head >= 4 and (acronym or scanned >= SCAN) then
                for i = 1, 4 do yield(head[i]) end
                if acronym then yield(acronym) end
                for i = 5, #head do yield(head[i]) end
                streaming = true
            end
        end
    end
    if not streaming then
        for i = 1, math.min(4, #head) do yield(head[i]) end
        if acronym then yield(acronym) end
        for i = 5, #head do yield(head[i]) end
    end
end

return cyime_acronym_filter
