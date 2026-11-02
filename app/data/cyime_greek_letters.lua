--[[
  CyIME：中文拼音打希腊字母名出希腊字母（开关 cyime_greek_letters，默认关；别人请求，手机自带输入法有）。
  整段输入正好是一个字母名（xita、pi、alpha、afa）时，在第 3 个候选的位置插入小写和大写字母（θ Θ），
  前两个候选不动（pi 仍是 批 皮 在前），后面的词往后挪两位。只看整段：xitai、pinyin 不出。
  怎么认字母名：
  - 输入全是小写字母（26 键全拼、双拼、九键在左栏选定的拼音、14 键侧滑的精确字母）：直接按输入比对；
  - 九键数字、14 键合并键：按第一个候选的拼音注释（spelling_hints）比对，9482 第一个是 西塔（xi ta）就出 θ；
    74 第一个是 是（shi）就不出，要 π 在左栏选 pi；
  - 双拼（方案带 cyime_rare_double_pinyin）：再按第一个候选的拼音（preedit_format 换成全拼）比对，ljmuda 也出 λ。
  开关关着时 tags_match 返回 false，Rime 整个跳过这个滤镜，不碰候选、不进 Lua 循环。
--]]
local OPTION = "cyime_greek_letters"
local POSITION = 3

-- 小写、大写、名字。英文名加常见中文读音的拼音；单音节的中文读音（派 pai、套 tao、肉 rou、凯 kai、纽 niu、缪 miu）
-- 是常打的字，不收，免得打这些字总多出希腊字母。
local LETTERS = {
  { "α", "Α", "alpha alfa afa aerfa" },
  { "β", "Β", "beta beita" },
  { "γ", "Γ", "gamma gama jiama" },
  { "δ", "Δ", "delta deerta dierta" },
  { "ε", "Ε", "epsilon yipuxilong aipuxilong" },
  { "ζ", "Ζ", "zeta jieta" },
  { "η", "Η", "eta yita aita" },
  { "θ", "Θ", "theta xita" },
  { "ι", "Ι", "iota yueta yaota" },
  { "κ", "Κ", "kappa kapa" },
  { "λ", "Λ", "lambda lamda lanmuda lamuda lanbuda" },
  { "μ", "Μ", "mu" },
  { "ν", "Ν", "nu" },
  { "ξ", "Ξ", "xi kexi ksi" },
  { "ο", "Ο", "omicron aomikerong" },
  { "π", "Π", "pi" },
  { "ρ", "Ρ", "rho" },
  { "σ", "Σ", "sigma xigema" },
  { "τ", "Τ", "tau" },
  { "υ", "Υ", "upsilon yupuxilong" },
  { "φ", "Φ", "phi fai" },
  { "χ", "Χ", "chi" },
  { "ψ", "Ψ", "psi puxi" },
  { "ω", "Ω", "omega oumijia aomijia oumiga" },
}

local NAMES = {}
for _, row in ipairs(LETTERS) do
  for name in row[3]:gmatch("%l+") do NAMES[name] = row end
end

-- 去掉音节分隔（空格、'），全是小写字母时返回，否则 nil。
local function key_of(text)
  if not text or text == "" then return nil end
  local key = text:gsub("[%s']", "")
  return key:match("^%l+$")
end

local function lookup(text)
  local key = key_of(text)
  return key and NAMES[key]
end

local function init(env)
  env.greek_double_pinyin = env.engine.schema.config:get_bool("cyime_rare_double_pinyin") == true
end

local function tags_match(seg, env)
  return env.engine.context:get_option(OPTION)
end

local function filter(input, env)
  local context = env.engine.context
  local seg = context.composition:back()
  if not seg then
    for cand in input:iter() do yield(cand) end
    return
  end
  local typed = context.input:sub(seg.start + 1, seg._end)
  local row = typed:match("^[%l']+$") and lookup(typed)
  local index = 0
  local function emit()
    yield(Candidate("greek", seg.start, seg._end, row[1], ""))
    yield(Candidate("greek", seg.start, seg._end, row[2], ""))
    row = nil
  end
  for cand in input:iter() do
    index = index + 1
    if index == 1 and not row and cand._end == seg._end then
      row = lookup(cand.comment) or (env.greek_double_pinyin and lookup(cand.preedit)) or nil
    end
    if row and index == POSITION then emit() end
    yield(cand)
  end
  if row then emit() end
end

return { init = init, func = filter, tags_match = tags_match }
