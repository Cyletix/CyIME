package com.kingzcheung.xime.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * 设置搜索的一条索引：[title] 是页面上的原名，[path] 是去那里的路径，[target] 是导航路由，
 * [keywords] 放说明里的词和用户可能用的说法（如“长按”找到九键拂动）。新增设置时在这里补一条。
 * 点开后目标页上标着 [anchor] 的那一项滚到可见处并渐变高亮一次；那一项此刻不显示时（如按键音关着时的音量）
 * 退而高亮 [section] 那一组。[anchor] 为 null 的是整页，只打开页面。
 */
internal data class SettingsSearchEntry(
    val title: String,
    val path: String,
    val target: String,
    val keywords: String = "",
    val anchor: String? = title,
    val section: String? = null,
)

/** 布局编辑器是独立的 Activity，不在设置的导航图里。 */
internal const val SEARCH_TARGET_CUSTOM_LAYOUT = "activity:custom_layout"

private fun layoutPage(page: String) = "${SettingsRoutes.LayoutDisplay}?page=$page"

internal val SettingsSearchIndex: List<SettingsSearchEntry> = run {
    val input = "输入"
    val look = "外观"
    val smart = "智能"
    val system = "系统"
    val languages = "$input › 语言与方案"
    val layouts = "$input › 键盘布局"
    val qwertyPath = "$layouts › 26 键"
    val ninePath = "$layouts › 九键与 14 键"
    val gestures = "$input › 按键与手势"
    val hardware = "$input › 物理键盘"
    val theme = "$look › 主题与外观"
    val candidates = "$look › 候选词"
    val effect = "$look › 按键效果"
    val dictionary = "$smart › 词库"
    val prediction = "$smart › 智能联想"
    val speech = "$smart › 语音转文本"
    val handwriting = "$smart › 手写"
    val clipboard = "$system › 剪贴板与同步"
    val backup = "$system › 备份与恢复"
    val performance = "$system › 性能与省电"
    val about = "$system › 关于 CyIME"
    val qwerty = LayoutInputCategory.Qwerty.route
    val nine = LayoutInputCategory.Nine.route
    val layoutIndex = SettingsRoutes.LayoutDisplay
    val gesturePage = layoutPage("gestures")
    val candidatePage = layoutPage("candidates")
    fun page(title: String, path: String, target: String, keywords: String) =
        SettingsSearchEntry(title, path, target, keywords, anchor = null)
    listOf(
        // 首页入口（整页）
        page("语言与方案", input, "languages", "语言 输入方案 键盘布局 切换方式 双拼 日语 英语 九键 26键 14键 语言管理 中文 日文 英文 拼音"),
        page("键盘布局", input, layoutIndex, "布局 26键 九键 14键 布局与显示 自定义布局 按键符号 分体"),
        page("按键与手势", input, gesturePage, "手势 角标 光标 空格 长按 上滑 下滑 滑动"),
        page("物理键盘", input, SettingsRoutes.HardwareKeyboard, "实体键盘 蓝牙键盘 外接键盘 硬键盘 语言切换快捷键 候选跟随 浮条 浮窗"),
        page("主题与外观", look, SettingsRoutes.Theme, "主题 皮肤 颜色 配色 深色 浅色 夜间 玻璃 背景 外观 视觉样式 定制"),
        page("候选词", look, candidatePage, "候选 候选栏 字号 候选注释 每页候选数"),
        page("按键效果", look, SettingsRoutes.KeyEffect, "按键音 声音 音效 振动 震动 反馈 发光 动画 炫光"),
        page("词库", smart, SettingsRoutes.Dictionary, "我的短语 导入的词库 分类词库 删掉的候选 查词 个人词库 用户词 自定义短语 方案词库 词典 词库管理 导入词库"),
        page("智能联想", smart, SettingsRoutes.SmartPrediction, "联想 预测 个人学习 模型增强 下一个词"),
        page("语音转文本", smart, SettingsRoutes.SpeechToText, "语音 识别 麦克风 说话 听写 在线 本地 语音输入 开关 字幕"),
        page("手写", smart, SettingsRoutes.Handwriting, "手写 写字 笔迹 公式 手写模型"),
        page("剪贴板与同步", system, SettingsRoutes.ClipboardSync, "剪贴板 同步 验证码 短信 复制 电脑 Windows 互联"),
        page("扩展", system, SettingsRoutes.Market, "下载 输入方案 模型 插件 商店 扩展商店"),
        page("备份与恢复", system, SettingsRoutes.Backup, "备份 恢复 导出 导入 本地备份 换机 迁移"),
        page("性能与省电", system, SettingsRoutes.Performance, "性能 省电 内存 卡顿 高配 中配 低配 自动 动效 炫光 语音常驻 释放 SenseVoice Paraformer"),
        page("关于 CyIME", system, SettingsRoutes.About, "版本 开发者 联系 许可 检查更新 最新版本 源代码"),
        // 输入 › 语言与方案
        SettingsSearchEntry("语言键切换方式", languages, "languages", "语言键 切换语言 中英切换 循环切换 指定语言 英文 地球键 点按语言键"),
        page("已安装的方案", languages, SettingsRoutes.Schema, "下载 导入 启停 方案 部署 输入资源 排序 方案资源"),
        // 输入 › 键盘布局
        page("26 键", layouts, qwerty, "26键 全键盘 全拼 qwerty 邻键纠错 滑行输入"),
        page("九键与 14 键", layouts, nine, "九键 9键 14键 拂动 假名 触发距离"),
        SettingsSearchEntry("自定义布局", layouts, SEARCH_TARGET_CUSTOM_LAYOUT, "自定义按键布局 布局管理 新建布局 复制 编辑按键排列", anchor = null),
        SettingsSearchEntry("按键符号", layouts, layoutIndex, "角标 符号编辑 字母键符号 上下角标 侧栏 笔画 短语 常用符号栏 手写符号"),
        SettingsSearchEntry("分体键盘", layouts, layoutIndex, "分体 分离 拆分 平板 两手 双手 间距"),
        SettingsSearchEntry("数字框自动数字键盘", layouts, layoutIndex, "数字 验证码 号码 电话 数字键盘 自动弹出"),
        SettingsSearchEntry("邻键纠错", qwertyPath, qwerty, "纠错 打错 误触 全拼 按错 相邻"),
        SettingsSearchEntry("滑行输入", qwertyPath, qwerty, "滑行 划词 滑动输入 手势输入 英文 整词 swype"),
        SettingsSearchEntry("三行字母错位", qwertyPath, qwerty, "错位 排布 实体键盘 对齐 手机排布 字母行 偏移"),
        SettingsSearchEntry("空格两侧的标点", qwertyPath, qwerty, "逗号键 句号键 空格 标点 全角 半角 宽度"),
        SettingsSearchEntry("空格左侧逗号键", qwertyPath, qwerty, "逗号 空格 加宽 标点"),
        SettingsSearchEntry("空格右侧句号键", qwertyPath, qwerty, "句号 空格 标点"),
        SettingsSearchEntry("拂动选字母", ninePath, nine, "九键 拂动 滑动 长按 数字 字母 字母菜单 上下左右"),
        SettingsSearchEntry("左右滑动选字母", ninePath, nine, "14键 双字母键 滑动 选字母 小鹤 双拼 左滑 右滑"),
        SettingsSearchEntry("滑动触发距离", ninePath, nine, "距离 灵敏度 误触 拂动 滑动 假名 dp"),
        SettingsSearchEntry("拂动时显示方向圆盘", ninePath, nine, "圆盘 方向 箭头 拂动 提示 假名 日语"),
        SettingsSearchEntry("假名方向提示", ninePath, nine, "日语 日文 假名 灰色小字 方向"),
        // 输入 › 按键与手势
        SettingsSearchEntry("手势预设", gestures, gesturePage, "预设 滑动 长按 英文滑行 一键 整套 手势 自定义"),
        SettingsSearchEntry("按键角标的符号和数字", gestures, gesturePage, "角标 滑动输入 长按输入 符号 数字 上滑 下滑 输入方式"),
        SettingsSearchEntry("长按弹出字母气泡", gestures, gesturePage, "长按 字母 大写 小写 音调 气泡 弹出 拼音声调 九键 字母键长按弹出大小写 字母菜单"),
        SettingsSearchEntry("反转上下滑方向", gestures, gesturePage, "反转 方向 上滑 下滑 上拉 下拉 删除键 清空 撤销"),
        SettingsSearchEntry("长按语言键弹出列表", gestures, gesturePage, "语言键 切换语言 长按 时长 语言列表 秒"),
        SettingsSearchEntry("符号键松手打开符号的时限", gestures, gesturePage, "符号键 !@# Ctrl 控制键 快捷键 复制 粘贴 剪切 撤销 全选 长按 时长 时限 过期 秒"),
        SettingsSearchEntry("滑动移动光标", gestures, gesturePage, "光标 移动 滑动 空格 整个键盘 关闭", section = "光标与空格"),
        SettingsSearchEntry("长按空格多久开始", gestures, gesturePage, "长按空格 时长 秒 延迟 移动光标 连续输入空格 开关语音", section = "光标与空格"),
        SettingsSearchEntry("从中间滑到边缘走几行", gestures, gesturePage, "光标 一行 行数 灵敏 灵敏度 速度 平板 大屏 滑到边缘", section = "光标与空格"),
        SettingsSearchEntry("光标每移动一字所需距离", gestures, gesturePage, "光标 距离 灵敏 灵敏度 速度 dp 毫米", section = "光标与空格"),
        SettingsSearchEntry("移动光标时振动", gestures, gesturePage, "光标 振动 震动 触感", section = "光标与空格"),
        SettingsSearchEntry("移动光标时播放按键音", gestures, gesturePage, "光标 声音 按键音", section = "光标与空格"),
        SettingsSearchEntry("长按空格", gestures, gesturePage, "空格 长按 连续空格 语音 麦克风", section = "光标与空格"),
        // 输入 › 物理键盘
        SettingsSearchEntry("Ctrl + 空格", hardware, SettingsRoutes.HardwareKeyboard, "ctrl 空格 语言切换 快捷键 循环切换"),
        SettingsSearchEntry("单独轻按 Shift", hardware, SettingsRoutes.HardwareKeyboard, "shift 中英切换 快捷键 语言切换"),
        SettingsSearchEntry("工具快捷键", hardware, SettingsRoutes.HardwareKeyboard, "快捷键 ctrl alt 标点 剪贴板 语音 alt+v alt+h"),
        SettingsSearchEntry("候选翻页", hardware, SettingsRoutes.HardwareKeyboard, "翻页 上一页 下一页 减号 等号 中括号 逗号 句号"),
        SettingsSearchEntry("候选跟随光标", hardware, SettingsRoutes.HardwareKeyboard, "候选 跟随 光标 浮窗 浮条 位置"),
        SettingsSearchEntry("自动避开输入区域", hardware, SettingsRoutes.HardwareKeyboard, "避让 遮挡 输入框 浮窗 浮条"),
        SettingsSearchEntry("靠边停靠", hardware, SettingsRoutes.HardwareKeyboard, "停靠 吸附 边缘 竖条 浮窗 浮条"),
        SettingsSearchEntry("底边使用三星式通栏", hardware, SettingsRoutes.HardwareKeyboard, "三星 通栏 底部 底边 候选栏 编号"),
        // 外观 › 主题与外观
        SettingsSearchEntry("显示模式", theme, SettingsRoutes.Theme, "深色 浅色 跟随系统 夜间 暗色 黑色 白色 明暗 日间"),
        SettingsSearchEntry("玻璃效果", theme, SettingsRoutes.Theme, "玻璃 毛玻璃 透明 材质 磨砂", section = "材质效果"),
        SettingsSearchEntry("透明度与模糊", theme, SettingsRoutes.Theme, "透明度 模糊 模糊强度 背景不透明度 按键不透明度 玻璃", section = "材质效果"),
        SettingsSearchEntry("配色方案", theme, SettingsRoutes.Theme, "配色 颜色 主题色 皮肤 预览"),
        SettingsSearchEntry("视觉样式", theme, SettingsRoutes.Theme, "样式 圆角 扁平 立体 材质"),
        SettingsSearchEntry("按键字号", theme, SettingsRoutes.Theme, "字号 字体 文字大小 按键文字 字体大小"),
        SettingsSearchEntry("上方角标提示", theme, SettingsRoutes.Theme, "角标 提示 符号 显示 上方"),
        SettingsSearchEntry("下方角标提示", theme, SettingsRoutes.Theme, "角标 提示 符号 显示 下方"),
        SettingsSearchEntry("点按弹出气泡", theme, SettingsRoutes.Theme, "气泡 预览 弹出 按键放大"),
        SettingsSearchEntry("底部圆角与透明导航栏", theme, SettingsRoutes.Theme, "圆角 导航栏 底部 直角 键盘底部样式"),
        SettingsSearchEntry("图标设置", theme, SettingsRoutes.Theme, "桌面图标 应用图标 底板 图标样式 图标联动 跟随主题色 同步图标 恢复原有图标"),
        // 外观 › 候选词
        SettingsSearchEntry("候选字大小", candidates, candidatePage, "候选 字号 字体大小 文字大小"),
        SettingsSearchEntry("候选栏取消按钮", candidates, candidatePage, "取消 左箭头 取消输入"),
        SettingsSearchEntry("候选注释", candidates, candidatePage, "注释 显示 自动 隐藏 候选显示"),
        SettingsSearchEntry("候选注释内容", candidates, candidatePage, "注释 拼音 释义 翻译 编码 中英 英文意思 词典"),
        SettingsSearchEntry("编码显示", candidates, candidatePage, "预编辑 输入框 候选栏 编码位置 拼音显示"),
        SettingsSearchEntry("每页候选词数", candidates, candidatePage, "候选数量 分页 每页 个数"),
        // 外观 › 按键效果
        SettingsSearchEntry("启用按键音", effect, SettingsRoutes.KeyEffect, "按键音 声音 音效 静音 打字声"),
        SettingsSearchEntry("按键音色", effect, SettingsRoutes.KeyEffect, "音色 Rain75 紫薇轴 线性轴 机械键盘 实体键盘 按键声音 音效", section = "按键音效"),
        SettingsSearchEntry("音量大小", effect, SettingsRoutes.KeyEffect, "音量 声音大小 按键音", section = "按键音效"),
        SettingsSearchEntry("录屏时包含按键音", effect, SettingsRoutes.KeyEffect, "录屏 录制 屏幕录制 按键音 媒体音量 声音", section = "按键音效"),
        SettingsSearchEntry("触觉反馈模式", effect, SettingsRoutes.KeyEffect, "振动 震动 触感 触觉 跟随系统 关闭振动 马达"),
        SettingsSearchEntry("按键抬起振动", effect, SettingsRoutes.KeyEffect, "抬起 松手 振动 震动", section = "振动反馈"),
        SettingsSearchEntry("按键振动时长", effect, SettingsRoutes.KeyEffect, "振动 震动 时长 强度", section = "振动反馈"),
        SettingsSearchEntry("长按振动时长", effect, SettingsRoutes.KeyEffect, "长按 振动 震动 时长", section = "振动反馈"),
        SettingsSearchEntry("按键振动振幅", effect, SettingsRoutes.KeyEffect, "振动 震动 振幅 强度 力度 马达", section = "振动反馈"),
        SettingsSearchEntry("长按振动振幅", effect, SettingsRoutes.KeyEffect, "长按 振动 震动 振幅 强度", section = "振动反馈"),
        SettingsSearchEntry("按键动效", effect, SettingsRoutes.KeyEffect, "动效 动画 缩放 回弹 键帽"),
        SettingsSearchEntry("键内炫光", effect, SettingsRoutes.KeyEffect, "炫光 发光 光效 渐变 彩虹"),
        // 智能 › 词库
        page("导入的词库", dictionary, SettingsRoutes.CellDictionaries, "分类词库 细胞词库 搜狗 scel txt json 导入词库 下载词库 本地导入 删除词库"),
        SettingsSearchEntry("我的短语", dictionary, SettingsRoutes.Dictionary, "自定义短语 短语 快捷短语 常用语 编码", anchor = null),
        SettingsSearchEntry("查词", dictionary, SettingsRoutes.Dictionary, "方案词库 词库 词条 编码 查找", anchor = null),
        SettingsSearchEntry("删掉的候选", dictionary, SettingsRoutes.Dictionary, "屏蔽的候选 屏蔽 恢复 候选 九键 删词 忘记", anchor = null),
        // 智能 › 智能联想
        SettingsSearchEntry("启用智能联想", prediction, SettingsRoutes.SmartPrediction, "联想 开关 预测 基础联想"),
        SettingsSearchEntry("空格上屏联想候选", prediction, SettingsRoutes.SmartPrediction, "空格 联想 上屏", section = "功能开关"),
        SettingsSearchEntry("连续联想", prediction, SettingsRoutes.SmartPrediction, "连续 联想 推理", section = "功能开关"),
        SettingsSearchEntry("单次联想", prediction, SettingsRoutes.SmartPrediction, "单次 联想 停止", section = "功能开关"),
        SettingsSearchEntry("选择模型", prediction, SettingsRoutes.SmartPrediction, "联想模型 模型 下载 增强", section = "功能开关"),
        SettingsSearchEntry("用户学习数据", prediction, SettingsRoutes.SmartPrediction, "学习 记忆 个人语料 导出 导入 保存 统计 学习文件"),
        // 智能 › 语音转文本
        SettingsSearchEntry("本地识别", speech, SettingsRoutes.SpeechToText, "本地 离线 模型 识别方式 在线 无需网络"),
        SettingsSearchEntry("保存语音调优录音", speech, SettingsRoutes.SpeechToText, "录音 保存 调优 回放 分析", section = "本地识别"),
        SettingsSearchEntry("字幕", speech, SettingsRoutes.SpeechToText, "字幕 听障 老人 对话 实时字幕"),
        SettingsSearchEntry("桌面显示“字幕”图标", speech, SettingsRoutes.SpeechToText, "字幕 桌面图标 快捷方式"),
        SettingsSearchEntry("在线服务", speech, SettingsRoutes.SpeechToText, "在线 语音插件 api 密钥 联网"),
        // 智能 › 手写
        SettingsSearchEntry("已安装的手写模型", handwriting, SettingsRoutes.Handwriting, "手写模型 公式 个人手写 下载 切换 删除"),
        SettingsSearchEntry("手写停顿识别", handwriting, SettingsRoutes.Handwriting, "手写 停顿 识别 延迟 时长 秒"),
        SettingsSearchEntry("手写公式两边的标记", handwriting, SettingsRoutes.Handwriting, "公式 latex 美元 数学"),
        SettingsSearchEntry("手写训练模式", handwriting, SettingsRoutes.HandwritingTraining, "手写 训练 样本 假名 字母 公式", anchor = null),
        SettingsSearchEntry("手写数据采集", handwriting, SettingsRoutes.HandwritingCapture, "手写 采集 样本 笔迹 标注", anchor = null),
        // 系统 › 剪贴板与同步
        SettingsSearchEntry("连接状态", clipboard, SettingsRoutes.ClipboardSync, "扫码连接电脑 连接电脑 Windows 配对 连接码 二维码 互联"),
        SettingsSearchEntry("同步剪贴板", clipboard, SettingsRoutes.ClipboardSync, "同步 剪贴板 电脑 Windows 我的设备", section = "连接状态"),
        SettingsSearchEntry("让其他手机或平板连接本机", clipboard, SettingsRoutes.ClipboardSync, "手机 平板 互相同步 二维码 允许其他设备连接"),
        SettingsSearchEntry("新短信验证码自动复制", clipboard, SettingsRoutes.ClipboardSync, "验证码 短信 自动复制 code 分格框"),
        SettingsSearchEntry("上屏后自动关闭剪贴板", clipboard, SettingsRoutes.ClipboardSync, "剪贴板 关闭 粘贴 连续贴"),
        SettingsSearchEntry("剪贴板卡片最多显示", clipboard, SettingsRoutes.ClipboardSync, "剪贴板 卡片 行数 几行 显示 记录 折叠"),
        SettingsSearchEntry("导出剪贴板", clipboard, SettingsRoutes.ClipboardSync, "导出 剪贴板 记录 固定内容 备份"),
        page("已安装的插件", "$system › 扩展 › 插件", SettingsRoutes.Plugins, "插件 已安装 插件管理"),
        page("已安装的模型", "$system › 扩展 › 模型", SettingsRoutes.ModelLocal, "模型 识字 语音 手写 联想 本地 下载 模型管理"),
        // 系统 › 备份与恢复
        SettingsSearchEntry("包含个人词库与学习数据", backup, SettingsRoutes.Backup, "个人词库 学习数据 自造词 调频 隐私"),
        SettingsSearchEntry("导出备份", backup, SettingsRoutes.Backup, "导出 备份 zip 换机", anchor = "操作"),
        SettingsSearchEntry("从备份恢复", backup, SettingsRoutes.Backup, "恢复 导入 还原 备份文件", anchor = "操作"),
        // 系统 › 性能与省电
        SettingsSearchEntry("性能档位", performance, SettingsRoutes.Performance, "档位 自动 推荐 高配 中配 低配 自定义 省电", anchor = "档位"),
        SettingsSearchEntry("语音二次校正空闲后释放", performance, SettingsRoutes.Performance, "SenseVoice 释放 空闲 内存 语音"),
        SettingsSearchEntry("语音模型空闲后全部释放", performance, SettingsRoutes.Performance, "Paraformer 释放 常驻 空闲 内存 语音 永不"),
        // 系统 › 关于 CyIME
        SettingsSearchEntry("使用文档", about, SettingsRoutes.About, "说明 帮助 文档 教程 用法"),
        page("隐私策略", about, SettingsRoutes.Privacy, "隐私 数据 联网 收集"),
        page("开源许可证", about, SettingsRoutes.Licenses, "许可 开源 license 第三方"),
        page("日志查看器", about, SettingsRoutes.LogViewer, "日志 log 崩溃 错误"),
        page("存储空间", about, SettingsRoutes.StorageSpace, "存储 空间 占用 清理 缓存 容量"),
    ).filter {
        // The developer-only handwriting training page is hidden in public, elder and friend packages.
        it.target !in setOf(SettingsRoutes.HandwritingTraining, SettingsRoutes.HandwritingCapture) || com.kingzcheung.xime.BuildConfig.DEV_TOOLS || com.kingzcheung.xime.BuildConfig.DEBUG
    }
}

/**
 * 同一个意思的不同说法，以及语音输入常写错的同音词（拳角＝全角、扶窗＝浮窗）：查询里出现其中一个，
 * 也按组里其他说法去找。只放意思真的相同或几乎相同的词，免得什么都搜得到。
 */
private val SearchSynonyms: List<List<String>> = listOf(
    listOf("振动", "震动", "震感", "触感", "触觉"),
    listOf("按键音", "按键声", "键盘音", "键盘声", "打字声", "打字音"),
    listOf("声音", "音效"),
    listOf("字号", "字体大小", "文字大小", "字大小"),
    listOf("主题", "皮肤"),
    listOf("配色", "颜色"),
    listOf("深色", "暗色", "夜间", "暗黑", "黑暗"),
    listOf("浅色", "亮色", "日间"),
    listOf("玻璃", "毛玻璃", "磨砂"),
    listOf("透明", "半透明"),
    listOf("剪贴板", "剪切板", "粘贴板", "剪帖板"),
    listOf("候选", "备选", "选词"),
    listOf("九键", "9键", "t9"),
    listOf("26键", "全键盘", "qwerty"),
    listOf("14键", "十四键"),
    listOf("全角", "拳角", "全脚"),
    listOf("半角", "板角", "半脚"),
    listOf("浮窗", "浮创", "扶窗", "悬浮窗", "浮条"),
    listOf("语音", "说话", "麦克风", "话筒"),
    listOf("光标", "游标"),
    listOf("长按", "按住"),
    listOf("拂动", "滑动", "划动"),
    listOf("词库", "词典", "字典"),
    listOf("学习", "记忆", "调频"),
    listOf("省电", "耗电", "电量", "续航"),
    listOf("卡顿", "掉帧", "不流畅", "性能"),
    listOf("电脑", "windows", "pc"),
    listOf("物理键盘", "实体键盘", "外接键盘", "蓝牙键盘", "硬键盘"),
    listOf("恢复", "还原"),
    listOf("图标", "icon"),
).map { group -> group.map(::normalizeSearchText) }

/** 小写、全角转半角，去掉空格和标点：查询和索引都先这样处理。 */
internal fun normalizeSearchText(s: String): String = buildString {
    for (raw in s.lowercase()) {
        val c = if (raw in '！'..'～') (raw.code - 0xFEE0).toChar() else raw
        if (c.isWhitespace() || c in "，。、·›：:；;（）()「」“”\"'+-_/．.,") continue
        append(c)
    }
}

/** 一个查询词连同同义说法：词里含组里某个说法，就把它换成同组其他说法各算一个变体（第一个是原词）。 */
private fun searchVariants(term: String): List<String> {
    val out = linkedSetOf(term)
    for (group in SearchSynonyms) for (word in group) {
        if (word.isEmpty() || word !in term) continue
        for (alt in group) if (alt != word) out += term.replace(word, alt)
    }
    return out.toList()
}

private const val FUZZY_MIN_COVERAGE = 0.6

/**
 * 一个查询词对一条索引的得分，0 表示不匹配：标题里有 > 说法里有 > 路径里有；同义说法打九折。
 * 都没有时按相邻两字逐段找，覆盖六成以上算弱匹配（“按键声音大小”能找到“音量大小”，“候选字号”找到“候选字大小”）。
 */
private fun termScore(term: String, title: String, keywords: String, path: String): Int {
    val variants = searchVariants(term)
    var best = 0
    variants.forEachIndexed { i, v ->
        val s = when {
            title == v -> 130
            title.startsWith(v) -> 115
            v in title -> 100
            v in keywords -> 60
            v in path -> 40
            else -> 0
        }
        best = maxOf(best, if (i == 0) s else s * 9 / 10)
    }
    if (best > 0) return best
    val all = "$title\u0000$keywords\u0000$path"
    var coverage = 0.0
    for (v in variants) {
        if (v.length < 3) continue
        val pieces = v.windowed(2)
        val hit = pieces.count { it in all }
        if (hit >= 2) coverage = maxOf(coverage, hit.toDouble() / pieces.size)
    }
    return if (coverage >= FUZZY_MIN_COVERAGE) (30 * coverage).toInt() else 0
}

/** 按空格分词，每个词都要匹配上；得分高的在前，同分保持索引顺序（整页在前，页内按页面顺序）。 */
internal fun searchSettings(query: String, index: List<SettingsSearchEntry> = SettingsSearchIndex): List<SettingsSearchEntry> {
    val terms = query.trim().split(Regex("\\s+")).map(::normalizeSearchText).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return emptyList()
    return index.mapNotNull { entry ->
        val title = normalizeSearchText(entry.title)
        val keywords = normalizeSearchText(entry.keywords)
        val path = normalizeSearchText(entry.path)
        var total = 0
        for (term in terms) {
            val s = termScore(term, title, keywords, path)
            if (s == 0) return@mapNotNull null
            total += s
        }
        entry to total
    }.sortedByDescending { it.second }.map { it.first }
}

@Composable
internal fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
            BasicTextField(value = query, onValueChange = onQueryChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().testTag("settings-search"))
            if (query.isEmpty()) Text("搜索设置，如“拂动”“长按”“字号”", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
        if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, "清除搜索") }
        else Spacer(Modifier.size(48.dp))
    }
}

@Composable
internal fun SettingsSearchResults(query: String, onOpen: (SettingsSearchEntry) -> Unit) {
    val results = searchSettings(query)
    if (results.isEmpty()) {
        Text("没有找到「$query」相关的设置", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        return
    }
    SettingsSection(title = "搜索结果", content = {
        results.take(30).forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Column(Modifier.fillMaxWidth().clickable { SettingsSearchFocus.open(entry); onOpen(entry) }.testTag("settings-search-result:${entry.title}")
                .padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                Text(entry.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    })
}

private fun nowMs() = System.nanoTime() / 1_000_000

/** 页面切换动画（[SettingsPageMotion] 300 ms）结束后再滚动、高亮，用户看得到是哪一项。 */
private const val SEARCH_SETTLE_MS = 360L

/** 超过这个时间还没找到就放弃，免得以后进别的页面时突然亮起来。 */
private const val SEARCH_EXPIRE_MS = 8_000L

/**
 * 点开搜索结果后要定位的那一项。目标页上标了 [settingsSearchAnchor] 的项出现时，
 * 按请求里的先后（先具体那一行，再它所在的组）选一个滚到可见处并渐变高亮一次，然后清掉请求。
 */
internal object SettingsSearchFocus {
    internal class Request(val keys: List<String>, val at: Long)

    var request by mutableStateOf<Request?>(null)
        private set

    private val shown = mutableMapOf<String, Int>()

    fun open(entry: SettingsSearchEntry) {
        val keys = listOfNotNull(entry.anchor, entry.section)
        request = if (keys.isEmpty()) null else Request(keys, nowMs())
    }

    internal fun register(key: String) { shown[key] = (shown[key] ?: 0) + 1 }

    internal fun unregister(key: String) {
        val n = (shown[key] ?: 0) - 1
        if (n > 0) shown[key] = n else shown.remove(key)
    }

    internal fun isLive(r: Request) = request === r && nowMs() - r.at < SEARCH_EXPIRE_MS

    /** 请求里有一项已经出现在页面上（懒加载列表据此停止往下翻）。 */
    internal fun anyShown(r: Request) = r.keys.any { it in shown }

    /** [key] 是不是该亮的那一项：排在它前面的项已在页面上就让给那一项。认领后清掉请求，只亮一次。 */
    internal fun claim(key: String, r: Request): Boolean {
        if (!isLive(r)) return false
        val rank = r.keys.indexOf(key)
        if (rank < 0 || r.keys.take(rank).any { it in shown }) return false
        request = null
        return true
    }
}

/** 设置首页本身不作为定位目标：它和子页面有同名项，切页动画期间首页还在，不能抢先亮。 */
internal val LocalSettingsSearchAnchors = staticCompositionLocalOf { true }

/**
 * 标出设置搜索能定位到的一项，[key] 与索引里的 anchor / section 相同（通常就是这一行的标题）。
 * 被搜索点中时滚到可见处（上下留出空隙，不贴边），再用主题色渐变亮起、停一下、慢慢褪去。
 * 高亮叠在内容上面，有自己底色的卡片也看得见；[bleed] 让内边距里的项把高亮铺到卡片两边。
 */
internal fun Modifier.settingsSearchAnchor(key: String, bleed: Dp = 0.dp, bleedVertical: Dp = 0.dp): Modifier = composed {
    if (!LocalSettingsSearchAnchors.current) return@composed Modifier
    val requester = remember { BringIntoViewRequester() }
    val glow = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    DisposableEffect(key) {
        SettingsSearchFocus.register(key)
        onDispose { SettingsSearchFocus.unregister(key) }
    }
    val request = SettingsSearchFocus.request
    LaunchedEffect(request, key) {
        val r = request ?: return@LaunchedEffect
        if (key !in r.keys) return@LaunchedEffect
        val wait = r.at + SEARCH_SETTLE_MS - nowMs()
        if (wait > 0) delay(wait)
        withFrameNanos { }
        if (!SettingsSearchFocus.claim(key, r)) return@LaunchedEffect
        // 认领后请求清空会让本 effect 重启，动画放到不随它取消的作用域里。
        scope.launch {
            val margin = with(density) { 96.dp.toPx() }
            requester.bringIntoView(Rect(0f, -margin, size.width.toFloat(), size.height + margin))
            glow.animateTo(1f, tween(220))
            delay(650)
            glow.animateTo(0f, tween(1400, easing = LinearOutSlowInEasing))
        }
    }
    val color = MaterialTheme.colorScheme.primary
    Modifier
        .bringIntoViewRequester(requester)
        .onSizeChanged { size = it }
        .drawWithContent {
            drawContent()
            val a = glow.value
            if (a > 0f) {
                val bx = bleed.toPx()
                val by = bleedVertical.toPx()
                drawRoundRect(color.copy(alpha = 0.2f * a), topLeft = Offset(-bx, -by),
                    size = Size(this.size.width + 2 * bx, this.size.height + 2 * by),
                    cornerRadius = CornerRadius(12.dp.toPx()))
            }
        }
        .testTag("settings-search-anchor:$key")
}

/**
 * 懒加载列表页用的 [LazyListState]：要定位的项还没组合出来时往下翻，直到它出现或到底；
 * 出现后由 [settingsSearchAnchor] 自己滚准并高亮。
 */
@Composable
internal fun rememberSettingsSearchListState(): LazyListState = rememberLazyListState().also { SettingsSearchAutoScroll(it) }

/** 已有自己 [LazyListState] 的页面（如可拖动排序的语言列表）直接调这个。 */
@Composable
internal fun SettingsSearchAutoScroll(state: LazyListState) {
    val request = SettingsSearchFocus.request
    LaunchedEffect(request) {
        val r = request ?: return@LaunchedEffect
        withFrameNanos { }
        while (SettingsSearchFocus.isLive(r) && !SettingsSearchFocus.anyShown(r) && state.canScrollForward) {
            state.scrollBy(state.layoutInfo.viewportSize.height * 0.8f)
            withFrameNanos { }
        }
    }
}
