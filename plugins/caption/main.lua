-- 字幕插件（Lua 脚本插件）
--
-- 字幕页面、录音、识别、分说话人都在宿主里（manifest capabilities.caption）：
-- 本脚本只是开关，装上并启用后，宿主才放出键盘菜单「字幕」、下拉快捷开关、桌面图标和通话字幕；
-- 停用或卸载就全部收起。字幕的设置项由宿主画在本插件页里。

local plugin = {}

-- 字幕没有要脚本管的设置项；字幕的开关在插件页里由宿主画。
function plugin.getSettingsSchema()
  return {}
end

return plugin
