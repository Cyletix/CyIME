package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * 键盘窗口里所有能滑的列表（候选栏、九键左栏标点、符号、表情、剪贴板、菜单翻页）都不要边缘拉伸回弹。
 *
 * 拉伸回弹从安卓 12 才有。回弹没收完时，Compose 的列表把每次按下都当成「接着滑」直接吃掉，里面的东西一下也点不上；
 * 有的手机拉到头以后回弹一直收不完，于是滑着滑着整块就点不动了，滑开一下才恢复（11-05 候选栏录屏、
 * 11-06 安卓 12 用户：标点点了没用、剪贴板拖动卡住）。安卓 11 及以前是发光效果，不会吃按下，所以只有新系统上出事。
 * 候选栏那几条在 CandidateBar 里也单独关了；这里管整个键盘窗口。
 */
@Composable
internal fun NoKeyboardOverscroll(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalOverscrollFactory provides null, content = content)
}
