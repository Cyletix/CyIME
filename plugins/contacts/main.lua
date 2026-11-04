-- 通讯录插件（Lua 脚本插件）
--
-- 名字和号码由宿主读、宿主查、宿主上屏（manifest capabilities.contacts）：
-- 本脚本拿不到通讯录，也不联网，只提供设置页的两个开关。
--
-- 宿主保证：
--   通讯录只在内存里，不写文件，不进备份和同步；关掉或卸载插件、收回权限立刻清掉。
--   点了名字或号码：不进词库、不进联想、不进打字统计，不发给其他插件；密码框里不出。

local plugin = {}

function plugin.getSettingsSchema()
  return {
    { key = "names", label = "人名", type = "switch", defaultValue = "true", section = "候选",
      helpText = "打全拼出名字，三个字以上的名字也能打首字母" },
    { key = "phones", label = "手机号", type = "switch", defaultValue = "true", section = "候选",
      helpText = "号码排在名字后面" },
  }
end

return plugin
