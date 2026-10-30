-- 笔画方案的 * 通配：每个 * 代替一笔（横竖撇捺折任一），只找笔数相同的字，按字频排序。
-- 最多展开 125 种笔画组合（三个 *），再多就不查，免得打字卡顿。
local STROKES = { "h", "s", "p", "n", "z" }
local MAX_CODES = 125

local M = {}

function M.init(env)
  env.mem = Memory(env.engine, env.engine.schema)
end

function M.fini(env)
  env.mem = nil
  collectgarbage()
end

function M.func(input, seg, env)
  if not input:find("*", 1, true) or not input:find("[^*]") then return end
  local codes = { "" }
  for ch in input:gmatch(".") do
    local options = ch == "*" and STROKES or { ch }
    local expanded = {}
    for _, prefix in ipairs(codes) do
      for _, option in ipairs(options) do expanded[#expanded + 1] = prefix .. option end
    end
    if #expanded > MAX_CODES then return end
    codes = expanded
  end
  local found, seen = {}, {}
  for _, code in ipairs(codes) do
    if env.mem:dict_lookup(code, false, 0) then
      for entry in env.mem:iter_dict() do
        if not seen[entry.text] then
          seen[entry.text] = true
          found[#found + 1] = { text = entry.text, weight = entry.weight, order = #found }
        end
      end
    end
  end
  table.sort(found, function(a, b)
    if a.weight ~= b.weight then return a.weight > b.weight end
    return a.order < b.order
  end)
  for _, item in ipairs(found) do
    yield(Candidate("stroke", seg.start, seg._end, item.text, ""))
  end
end

return M
