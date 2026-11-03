--[[
  CyIME：雾凇的时间日期快捷码（sj rq xq dt ts nl）和简拼是同一个编码。
  上游把 10:16、10:16:22、晚上 10:16… 全排在最前，打 sj 第一屏只剩时间，手机、世界、司机这些简拼词看不到。
  这里改成和九键一样：第 1 个词不动，第一个时间/日期放第 2 位，其余格式排在再往后 3 个词之后
  （sj → 时间 10:16 手机 世界 司机 10:16:22 晚上 10:16 10:16 PM 升级 …）。
  只挪 date_translator（上游类型为空，时间戳为 timestamp）和农历（lunar）的候选，词的先后不变。
  整段输入不是这些快捷码时 tags_match 返回 false，Rime 整个跳过这个滤镜，不进 Lua 循环。
--]]
local LEAD = 1 -- 第一个时间/日期之前保留的词数（九键 t9/date_translator/idx: 2 也是第 2 位）
local GAP = 3  -- 第一个格式之后、其余格式之前再放的词数

local SHORTCUT_TYPES = { [""] = true, timestamp = true, lunar = true }

local function init(env)
  local config = env.engine.schema.config
  local codes = {}
  local function add(path, default)
    local code = config:get_string(path) or default
    if code and code ~= "" then codes[code] = true end
  end
  add("date_translator/date", "rq")
  add("date_translator/time", "sj")
  add("date_translator/week", "xq")
  add("date_translator/datetime", "dt")
  add("date_translator/timestamp", "ts")
  add("date_translator/datezh", "rqzh")
  add("date_translator/dateen", "rqen")
  add("lunar", "nl")
  env.codes = codes
end

local function tags_match(seg, env)
  local input = env.engine.context.input:sub(seg.start + 1, seg._end)
  return env.codes[input] == true
end

local function filter(input, env)
  local words, held, lead_out = 0, {}, false
  local function release()
    if not lead_out and words >= LEAD and #held > 0 then
      yield(table.remove(held, 1))
      lead_out = true
    end
    if lead_out and words >= LEAD + GAP then
      for _, cand in ipairs(held) do yield(cand) end
      held = {}
    end
  end
  for cand in input:iter() do
    if SHORTCUT_TYPES[cand.type] then
      table.insert(held, cand)
      release()
    else
      yield(cand)
      words = words + 1
      release()
    end
  end
  for _, cand in ipairs(held) do yield(cand) end
end

return { init = init, func = filter, tags_match = tags_match }
