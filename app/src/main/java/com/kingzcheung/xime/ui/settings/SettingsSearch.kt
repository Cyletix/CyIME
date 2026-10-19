package com.kingzcheung.xime.ui.settings

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
 * [keywords] 放用户可能用的说法（如“长按”找到九键拂动）。新增设置时在这里补一条。
 */
internal data class SettingsSearchEntry(
    val title: String,
    val path: String,
    val target: String,
    val keywords: String = "",
)

/** 布局编辑器是独立的 Activity，不在设置的导航图里。 */
internal const val SEARCH_TARGET_CUSTOM_LAYOUT = "activity:custom_layout"

private fun layoutPage(page: String) = "${SettingsRoutes.LayoutDisplay}?page=$page"

internal val SettingsSearchIndex: List<SettingsSearchEntry> = run {
    val kb = "键盘"
    val layouts = "键盘 › 键盘布局"
    val qwerty = LayoutInputCategory.Qwerty.route
    val nine = LayoutInputCategory.Nine.route
    val layoutIndex = SettingsRoutes.LayoutDisplay
    val speech = "智能与输入方式 › 语音与手写"
    listOf(
        // 首页入口
        SettingsSearchEntry("语言与方案", "输入法", "languages", "语言 输入方案 键盘布局 切换方式 双拼 日语 英语 九键 26键 14键 语言管理"),
        SettingsSearchEntry("主题与外观", kb, SettingsRoutes.Theme, "主题 皮肤 颜色 配色 深色 浅色 夜间 玻璃 背景 外观 视觉样式 定制"),
        SettingsSearchEntry("候选词", kb, layoutPage("candidates"), "候选 候选栏"),
        SettingsSearchEntry("键盘布局", kb, layoutIndex, "布局 26键 九键 14键 布局与显示"),
        SettingsSearchEntry("按键与手势", kb, layoutPage("gestures"), "手势 角标 光标 空格 长按"),
        SettingsSearchEntry("按键效果", kb, SettingsRoutes.KeyEffect, "按键音 声音 音效 振动 震动 反馈 发光 动画"),
        SettingsSearchEntry("物理键盘", kb, SettingsRoutes.HardwareKeyboard, "实体键盘 蓝牙键盘 外接键盘 语言切换快捷键 候选跟随 浮条"),
        SettingsSearchEntry("智能联想", "智能与输入方式", SettingsRoutes.SmartPrediction, "联想 预测 个人学习 模型增强 下一个词"),
        SettingsSearchEntry("语音与手写", "智能与输入方式", SettingsRoutes.SpeechToText, "语音 识别 麦克风 说话 听写 在线 本地 语音转文本 手写"),
        SettingsSearchEntry("词库", "智能与输入方式", SettingsRoutes.Dictionary, "分类词库 个人词库 用户词 自定义短语 词典 词库管理"),
        SettingsSearchEntry("手写训练模式", speech, SettingsRoutes.HandwritingTraining, "手写 训练 样本 假名 字母 公式"),
        SettingsSearchEntry("扩展", "扩展", SettingsRoutes.Market, "下载 输入方案 模型 插件 商店 扩展商店"),
        SettingsSearchEntry("已安装的插件", "扩展 › 插件", SettingsRoutes.Plugins, "插件 已安装 插件管理"),
        SettingsSearchEntry("已安装的模型", "扩展 › 模型", SettingsRoutes.ModelLocal, "模型 识字 语音 手写 联想 本地 下载 模型管理"),
        SettingsSearchEntry("已安装的方案", "输入法 › 语言与方案", SettingsRoutes.Schema, "下载 导入 启停 方案 部署 输入资源 排序"),
        SettingsSearchEntry("语音转文本", speech, SettingsRoutes.SpeechToText, "语音 开关 开启 关闭 麦克风"),
        SettingsSearchEntry("屏蔽的候选", "智能与输入方式 › 词库", SettingsRoutes.Dictionary, "屏蔽 恢复 候选 九键"),
        SettingsSearchEntry("剪贴板与同步", "系统", SettingsRoutes.ClipboardSync, "剪贴板 同步 验证码 短信 复制 电脑"),
        SettingsSearchEntry("备份与恢复", "系统", SettingsRoutes.Backup, "备份 恢复 导出 导入 本地备份 换机"),
        SettingsSearchEntry("性能与省电", "系统", SettingsRoutes.Performance, "性能 省电 内存 卡顿 高配 中配 低配 自动 动效 炫光 语音常驻 释放 SenseVoice Paraformer"),
        SettingsSearchEntry("关于 CyIME", "系统", SettingsRoutes.About, "版本 开发者 联系 日志 许可"),
        // 键盘布局
        SettingsSearchEntry("自定义布局", layouts, SEARCH_TARGET_CUSTOM_LAYOUT, "自定义按键布局 布局管理 新建布局"),
        SettingsSearchEntry("按键符号", layouts, layoutIndex, "角标 符号编辑 字母键符号 上下角标 侧栏 笔画 短语"),
        SettingsSearchEntry("分体键盘", layouts, layoutIndex, "分体 分离 拆分 平板 两手"),
        SettingsSearchEntry("数字框自动数字键盘", layouts, layoutIndex, "数字 验证码 号码 电话 数字键盘"),
        SettingsSearchEntry("邻键纠错", "$layouts › 26 键", qwerty, "纠错 打错 误触 全拼"),
        SettingsSearchEntry("滑行输入", "$layouts › 26 键", qwerty, "滑行 划词 滑动输入 手势输入 英文 整词"),
        SettingsSearchEntry("三行字母错位", "$layouts › 26 键", qwerty, "错位 排布 实体键盘 对齐"),
        SettingsSearchEntry("空格两侧的标点", "$layouts › 26 键", qwerty, "逗号键 句号键 空格 标点 全角 半角"),
        SettingsSearchEntry("拂动选字母", "$layouts › 九键与 14 键", nine, "九键 拂动 滑动 长按 数字 字母 字母菜单"),
        SettingsSearchEntry("左右滑动选字母", "$layouts › 九键与 14 键", nine, "14键 双字母键 滑动 选字母 小鹤"),
        SettingsSearchEntry("滑动触发距离", "$layouts › 九键与 14 键", nine, "距离 灵敏度 误触 拂动 滑动"),
        SettingsSearchEntry("拂动时显示方向圆盘", "$layouts › 九键与 14 键", nine, "圆盘 方向 箭头 拂动 提示"),
        SettingsSearchEntry("假名方向提示", "$layouts › 九键与 14 键", nine, "日语 日文 假名 灰色小字 方向"),
        // 候选词、主题与外观、按键与手势
        SettingsSearchEntry("候选字大小", "$kb › 候选词", layoutPage("candidates"), "候选 字号 字体大小"),
        SettingsSearchEntry("候选栏取消按钮", "$kb › 候选词", layoutPage("candidates"), "取消 左箭头"),
        SettingsSearchEntry("候选注释", "$kb › 候选词", layoutPage("candidates"), "注释 拼音 释义 翻译 编码 候选显示"),
        SettingsSearchEntry("编码显示", "$kb › 候选词", layoutPage("candidates"), "预编辑 输入框 候选栏 编码位置"),
        SettingsSearchEntry("每页候选词数", "$kb › 候选词", layoutPage("candidates"), "候选数量 分页 每页"),
        SettingsSearchEntry("视觉样式", "$kb › 主题与外观", SettingsRoutes.Theme, "样式 圆角 扁平 立体"),
        SettingsSearchEntry("按键字号", "$kb › 主题与外观", SettingsRoutes.Theme, "字号 字体 文字大小 按键文字"),
        SettingsSearchEntry("上方角标提示", "$kb › 主题与外观", SettingsRoutes.Theme, "角标 提示 符号 显示"),
        SettingsSearchEntry("下方角标提示", "$kb › 主题与外观", SettingsRoutes.Theme, "角标 提示 符号 显示"),
        SettingsSearchEntry("点按弹出气泡", "$kb › 主题与外观", SettingsRoutes.Theme, "气泡 预览 弹出 按键放大"),
        SettingsSearchEntry("底部圆角与透明导航栏", "$kb › 主题与外观", SettingsRoutes.Theme, "圆角 导航栏 底部"),
        SettingsSearchEntry("按键角标的符号和数字", "$kb › 按键与手势", layoutPage("gestures"), "角标 滑动输入 长按输入 符号 数字"),
        SettingsSearchEntry("字母键长按弹出大小写", "$kb › 按键与手势", layoutPage("gestures"), "长按 字母 大写 小写 音调 气泡 弹出 拼音声调"),
        SettingsSearchEntry("反转上下滑方向", "$kb › 按键与手势", layoutPage("gestures"), "反转 方向 上滑 下滑 删除键 清空 撤销"),
        SettingsSearchEntry("长按语言键弹出列表", "$kb › 按键与手势", layoutPage("gestures"), "语言键 切换语言 长按 时长"),
        SettingsSearchEntry("滑动移动光标", "$kb › 按键与手势", layoutPage("gestures"), "光标 移动 滑动 空格"),
        SettingsSearchEntry("空格长按", "$kb › 按键与手势", layoutPage("gestures"), "空格 长按 连续空格 语音 时长"),
        SettingsSearchEntry("光标灵敏度", "$kb › 按键与手势", layoutPage("gestures"), "光标 距离 灵敏"),
        // 语音与手写
        SettingsSearchEntry("手写停顿识别", speech, SettingsRoutes.SpeechToText, "手写 停顿 识别 延迟 时长"),
        SettingsSearchEntry("手写公式两边的标记", speech, SettingsRoutes.SpeechToText, "公式 latex 美元 数学"),
    ).filter {
        // The developer-only handwriting training page is hidden in public, elder and friend packages.
        it.target != SettingsRoutes.HandwritingTraining || com.kingzcheung.xime.BuildConfig.DEV_TOOLS || com.kingzcheung.xime.BuildConfig.DEBUG
    }
}

/** 去空格、小写后匹配；标题命中排最前，其次说法和路径。 */
internal fun searchSettings(query: String, index: List<SettingsSearchEntry> = SettingsSearchIndex): List<SettingsSearchEntry> {
    fun norm(s: String) = s.replace(" ", "").lowercase()
    val terms = query.trim().split(Regex("\\s+")).map(::norm).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return emptyList()
    return index.mapNotNull { entry ->
        val title = norm(entry.title)
        val rest = norm(entry.keywords + entry.path)
        if (!terms.all { it in title || it in rest }) null
        else entry to terms.count { it in title }
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
        results.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Column(Modifier.fillMaxWidth().clickable { onOpen(entry) }.testTag("settings-search-result:${entry.title}")
                .padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                Text(entry.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    })
}
